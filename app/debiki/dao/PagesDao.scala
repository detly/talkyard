/**
 * Copyright (C) 2014 Kaj Magnus Lindberg (born 1979)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package debiki.dao

import com.debiki.core._
import com.debiki.core.Prelude._
import com.debiki.core.User.SystemUserId
import debiki._
import debiki.DebikiHttp._
import java.{util => ju}
import requests.PageRequest
import scala.collection.immutable
import CachingDao.CacheKey
import com.debiki.core.{PostActionPayload => PAP}


/** Loads and saves pages and page parts (e.g. posts and patches).
  *
  * (There's also a class PageDao (with no 's' in the name) that focuses on
  * one specific single page.)
  */
trait PagesDao {
  self: SiteDao =>


  def nextPageId(): PageId = siteDbDao.nextPageId()


  def createPage(pageNoId: Page): Page = {
    siteDbDao.createPage(pageNoId)
    // COULD generate and save notfs [notifications]
  }


  def savePageActionGenNotfs[A](pageReq: PageRequest[_], action: RawPostAction[A]) = {
    val (pageAfter, actionsWithId) = savePageActionsGenNotfs(pageReq, Seq(action))
    (pageAfter, actionsWithId.head.asInstanceOf[RawPostAction[A]])
  }


  /** Saves page actions and places messages in users' inboxes, as needed.
    * Returns a pair with 1) the page including new actions plus the current user,
    * and 2) the actions, but with ids assigned.
    */
  def savePageActionsGenNotfs(pageReq: PageRequest[_], actions: Seq[RawPostAction[_]])
      : (PageNoPath, Seq[RawPostAction[_]]) = {
    val pagePartsNoAuthor = pageReq.thePageNoPath.parts
    // We're probably going to render parts of the page later, and then we
    // need the user, so add it to the page — it's otherwise absent if this is
    // the user's first contribution to the page.
    val pageParts = pagePartsNoAuthor ++ pageReq.anyMeAsPeople
    val page = PageNoPath(pageParts, pageReq.ancestorIdsParentFirst_!, pageReq.thePageMeta)
    savePageActionsGenNotfsImpl(page, actions)
  }


  def savePageActionsGenNotfs(pageId: PageId, actions: Seq[RawPostAction[_]], authors: People)
      : (PageNoPath, Seq[RawPostAction[_]]) = {

    val pageMeta = siteDbDao.loadPageMeta(pageId) getOrElse
      throwNotFound("DwE115Xf3", s"Page `${pageId}' does not exist")

    // BUG race condition: What if page deleted, here? Then we'd falsely return an empty page.

    var pageNoAuthor = loadPageParts(pageId) getOrElse PageParts(pageId)

    val page = pageNoAuthor ++ authors

    val ancestorPageIds = loadAncestorIdsParentFirst(pageId)

    savePageActionsGenNotfsImpl(PageNoPath(page, ancestorPageIds, pageMeta), actions)
  }


  def savePageActionsGenNotfsImpl(page: PageNoPath, actions: Seq[RawPostAction[_]])
      : (PageNoPath, Seq[RawPostAction[_]]) = {
    if (actions isEmpty)
      return (page, Nil)

    // COULD check that e.g. a deleted post is really a post, an applied edit is
    // really an edit, an action undone is not itself an Undo action,
    // and lots of other similar tests.

    val (pageWithNewActions, actionsWithId) =
      siteDbDao.savePageActions(page, actions.toList)

    val notfs = NotificationGenerator(page, this).generateNotifications(actionsWithId)
    siteDbDao.saveDeleteNotifications(notfs)

    (pageWithNewActions, actionsWithId)
  }


  def createPost(newPostPerhapsNoId: Post2): Post2 = {
    readWriteTransaction { transaction =>
      val page = PageDao(newPostPerhapsNoId.pageId, transaction)
      var newPost =
        if (newPostPerhapsNoId.hasAnId) {
          newPostPerhapsNoId
        }
        else {
          // (We get to here for replies only. The title and body have fix ids.)
          val newId = page.parts.highestReplyId + 1
          newPostPerhapsNoId.copy(id = newId)
        }

      val parentStatuses = newPost.parentId match {
        case Some(parentId) => page.parts.derivePostStatuses(parentId)
        case None => PostStatuses.Default
      }
      newPost = newPost.copyWithParentStatuses(parentStatuses)
      //val oldMeta = page.meta
      //val newMeta = oldMeta.copy(numRepliesInclDeleted = page.parts.numRepliesInclDeleted + 1)
      transaction.saveNewPost(newPost)
      //transaction.savePageMeta(newMeta)

      // TODO generate notifications
      // transaction.saveNotifications(...)

      newPost
    }
  }


  def editPost(pageId: PageId, postId: PostId, editorId: UserId2, newText: String) {
    readWriteTransaction { transaction =>
      val page = PageDao(pageId, transaction)
      var postToEdit = page.parts.post(postId).getOrElse(
        throwNotFound("DwE404GKF2", s"Post not found, id: '$postId'"))

      if (postToEdit.currentSource == newText)
        return

      val editor = transaction.loadUser(editorId.toString).getOrElse(
        throwNotFound("DwE30HY21", s"User not found, id: '$editorId'"))

      // For now: (add back edit suggestions later. And should perhaps use PermsOnPage.)
      val editsOwnPost = editorId == postToEdit.createdById
      val mayEdit = editsOwnPost || editor.isAdmin
      if (!mayEdit)
        throwForbidden("DwE8KF32", "Currently you may edit your own posts only.")

      //val appliedDirectly = true // for now, add back edit suggestions later
      //val approvedDirectly = true // later: && editor.isWellBehavedUser

      var editedPost = postToEdit.copy(
        lastEditedAt = Some(transaction.currentTime),
        lastEditedById = Some(editorId))

      val approvedHtmlSanitized =
        if (postId == PageParts.TitleId) {
          siteDbDao.commonMarkRenderer.sanitizeHtml(newText)
        }
        else {
          siteDbDao.commonMarkRenderer.renderAndSanitizeCommonMark(newText,
            allowClassIdDataAttrs = postId == PageParts.BodyId,
            followLinks = postToEdit.createdByUser(page.parts).isAdmin && editor.isAdmin)
        }

      val approverId = if (editor.isAdmin) editor.id2 else SystemUserId
      val nextVersion = postToEdit.currentVersion + 1

      // TODO send current version from browser to server, reject edits if != oldPost.currentVersion
      // to stop the lost update problem.

      // Later, if post not approved directly: currentSourcePatch = makePatch(from, to)

      editedPost = editedPost.copy(
        lastApprovedEditAt = Some(transaction.currentTime),
        lastApprovedEditById = Some(editorId),
        approvedSource = Some(newText),
        approvedHtmlSanitized = Some(approvedHtmlSanitized),
        approvedAt = Some(transaction.currentTime),
        approvedById = Some(approverId),
        approvedVersion = Some(nextVersion),
        currentSourcePatch = None,
        currentVersion = nextVersion)

      if (editorId != editedPost.createdById) {
        editedPost = editedPost.copy(numDistinctEditors = 2)  // for now
      }

      transaction.saveUpdatedPost(editedPost)

      if (postId == PageParts.TitleId) {
        val oldMeta = page.meta
        val newMeta = oldMeta.copy(cachedTitle = Some(newText))
        transaction.updatePageMeta(newMeta, oldMeta = oldMeta)
      }
  
      // TODO generate notifications
      // transaction.saveNotifications(...)
    }
  }


  def changePostStatus(postId: PostId, pageId: PageId, action: PostActionPayload, userId: UserId2) {
    readWriteTransaction { transaction =>
      val page = PageDao(pageId, transaction)

      val postBefore = page.parts.thePost(postId)
      val user = transaction.loadUser(userId) getOrElse throwForbidden("DwE3KFW2", "Bad user id")

      // Authorization.
      if (!user.isAdmin) {
        if (postBefore.createdById != userId)
          throwForbidden("DwE0PK24", "You may not modify that post, it's not yours")

        if (!action.isInstanceOf[PAP.DeletePost] && action != PAP.CollapsePost)
          throwForbidden("DwE5JKF7", "You may not modify the whole tree")
      }

      // Update the directly affected post.
      val postAfter = action match {
        case PAP.CloseTree =>
          if (postBefore.closedStatus.isDefined)
            return
          postBefore.copy(
            closedAt = Some(transaction.currentTime),
            closedById = Some(userId),
            closedStatus = Some(ClosedStatus.TreeClosed))
        case PAP.CollapsePost =>
          if (postBefore.collapsedStatus.isDefined)
            return
          postBefore.copy(
            collapsedAt = Some(transaction.currentTime),
            collapsedById = Some(userId),
            collapsedStatus = Some(CollapsedStatus.PostCollapsed))
        case PAP.CollapseTree =>
          if (postBefore.collapsedStatus.isDefined)
            return
          postBefore.copy(
            collapsedAt = Some(transaction.currentTime),
            collapsedById = Some(userId),
            collapsedStatus = Some(CollapsedStatus.TreeCollapsed))
        case PAP.DeletePost(clearFlags) =>
          if (postBefore.deletedStatus.isDefined)
            return
          postBefore.copy(
            deletedAt = Some(transaction.currentTime),
            deletedById = Some(userId),
            deletedStatus = Some(DeletedStatus.PostDeleted))
        case PAP.DeleteTree =>
          if (postBefore.deletedStatus.isDefined)
            return
          postBefore.copy(
            deletedAt = Some(transaction.currentTime),
            deletedById = Some(userId),
            deletedStatus = Some(DeletedStatus.TreeDeleted))
      }

      transaction.saveUpdatedPost(postAfter)

      // Update any indirectly affected posts, e.g. subsequent comments in the same
      // thread that are being deleted recursively.
      for (successor <- page.parts.successorsOf(postId)) {
        val anyUpdatedSuccessor: Option[Post2] = action match {
          case PAP.CloseTree =>
            if (successor.closedStatus.isDefined) None
            else Some(successor.copy(
              closedStatus = Some(ClosedStatus.AncestorClosed),
              closedById = Some(userId),
              closedAt = Some(transaction.currentTime)))
          case PAP.CollapsePost =>
            None
          case PAP.CollapseTree =>
            if (successor.collapsedStatus.isDefined) None
            else Some(successor.copy(
              collapsedStatus = Some(CollapsedStatus.AncestorCollapsed),
              collapsedById = Some(userId),
              collapsedAt = Some(transaction.currentTime)))
          case PAP.DeletePost(clearFlags) =>
            None
          case PAP.DeleteTree =>
            if (successor.deletedStatus.isDefined) None
            else Some(successor.copy(
              deletedStatus = Some(DeletedStatus.AncestorDeleted),
              deletedById = Some(userId),
              deletedAt = Some(transaction.currentTime)))
        }

        anyUpdatedSuccessor foreach { updatedSuccessor =>
          transaction.saveUpdatedPost(updatedSuccessor)
        }
      }

      // TODO: update page meta post counts.
      //val oldMeta = page.meta
      //val newMeta = oldMeta.copy(...)
      //transaction.updatePageMeta(newMeta, oldMeta = oldMeta)
    }
  }


  /*
  def saveVote(pageId: PageId, voteNoId: RawPostAction[PAP.Vote],
        postIdsRead: Set[PostId]) {
    readWriteTransaction { transaction =>
      if (voteNoId.payload == PostActionPayload.VoteLike) {
        val post = transaction.loadPost(voteNoId.postId).getOrElse(
          throwNotFound("DwE5GH7", "Post not found"))
        if (post.userId == voteNoId.userId)
          throwBadReq("DwE84QM0", "Cannot like own post")
      }

      val voteWithId = try {
        transaction.savePostAction(pageId, voteNoId)
      }
      catch {
        case DbDao.DuplicateVoteException =>
          throwConflict("DwE26FX0", "Duplicate votes")
      }

      // Downvotes (wrong, off-topic) should result in only the downvoted post
      // being marked as read. Its ancestors shouldn't be marked as read,
      // because a post *not* being downvoted shouldn't
      // give that post worse rating. (Remember that the rating of a post is
      // roughly the number of Like votes / num-times-it's-been-read.)
      val postsToMarkAsRead =
        if (voteNoId.payload == PostActionPayload.VoteLike)
          postIdsRead
        else
          Set(voteNoId.postId)

      transaction.updatePostsReadStats(pageId, postsToMarkAsRead, voteWithId)

      and, in overriding function: refreshPageInCache(page.id)
    }
  } */


  def deleteVoteAndNotf(userIdData: UserIdData, pageId: PageId, postId: PostId,
        voteType: PostActionPayload.Vote) {
    siteDbDao.deleteVote(userIdData, pageId, postId, voteType)
    // Delete vote notf too once they're being generated, see [953kGF21X].
  }


  def updatePostsReadStats(pageId: PageId, postIdsRead: Set[PostId],
        actionMakingThemRead: RawPostAction[_]) {
    siteDbDao.updatePostsReadStats(pageId, postIdsRead, actionMakingThemRead)
  }


  def loadPostsReadStats(pageId: PageId): PostsReadStats =
    siteDbDao.loadPostsReadStats(pageId)


  def loadPostsOnPage(pageId: PageId): immutable.Seq[Post2] =
    readOnlyTransaction(_.loadPostsOnPage(pageId))


  def loadPageParts(debateId: PageId): Option[PageParts] =
    siteDbDao.loadPageParts(debateId)


  def loadPageAnyTenant(sitePageId: SitePageId): Option[PageParts] =
    loadPageAnyTenant(tenantId = sitePageId.siteId, pageId = sitePageId.pageId)


  def loadPageAnyTenant(tenantId: SiteId, pageId: PageId): Option[PageParts] =
    siteDbDao.loadPageParts(pageId, tenantId = Some(tenantId))

}



trait CachingPagesDao extends PagesDao {
  self: CachingSiteDao =>


  onPageSaved { sitePageId =>
    uncachePageParts(sitePageId)
  }


  override def createPage(page: Page): Page = {
    val pageWithIds = super.createPage(page)
    firePageCreated(pageWithIds)
    pageWithIds
  }


  override def savePageActionsGenNotfsImpl(page: PageNoPath, actions: Seq[RawPostAction[_]])
      : (PageNoPath, Seq[RawPostAction[_]]) = {

    if (actions isEmpty)
      return (page, Nil)

    val newPageAndActionsWithId =
      super.savePageActionsGenNotfsImpl(page, actions)

    refreshPageInCache(page.id)
    newPageAndActionsWithId
  }

  override def createPost(newPostPerhapsNoId: Post2): Post2 = {
    val newPostWithId = super.createPost(newPostPerhapsNoId)
    refreshPageInCache(newPostWithId.pageId)
    newPostWithId
  }


  override def editPost(pageId: PageId, postId: PostId, editorId: UserId2, newText: String) {
    super.editPost(pageId = pageId, postId = postId, editorId = editorId, newText = newText)
    refreshPageInCache(pageId)
  }


  override def changePostStatus(postId: PostId, pageId: PageId, action: PostActionPayload,
        userId: UserId2) {
    super.changePostStatus(postId = postId, pageId = pageId, action, userId = userId)
    refreshPageInCache(pageId)
  }


  private def refreshPageInCache(pageId: PageId) {
    // Possible optimization: Examine all actions, and refresh cache e.g.
    // the RenderedPageHtmlDao cache only
    // if there are e.g. EditApp:s or approved Post:s (but ignore Edit:s --
    // unless applied & approved). Include that info in the call to `firePageSaved` below.

    firePageSaved(SitePageId(siteId = siteId, pageId = pageId))

    // if (is _site.conf || is any stylesheet or script)
    // then clear all asset bundle related caches. For ... all websites, for now??

    // Would it be okay to simply overwrite the in mem cache with this
    // updated page? — Only if I make `++` avoid adding stuff that's already
    // present!
    //val pageWithNewActions =
    // page_! ++ actionsWithId ++ pageReq.login_! ++ pageReq.user_!

    // In the future, also refresh page index cache, and cached page titles?
    // (I.e. a cache for DW1_PAGE_PATHS.)

    // ------ Page action cache (I'll probably remove it)
    // COULD instead update value in cache (adding the new actions to
    // the cached page). But then `savePageActionsGenNotfs` also needs to know
    // which users created the actions, so their login/idty/user instances
    // can be cached as well (or it won't be possible to render the page,
    // later, when it's retrieved from the cache).
    // So: COULD save login, idty and user to databaze *lazily*.
    // Also, logins that doesn't actually do anything won't be saved
    // to db, which is goood since they waste space.
    // (They're useful for statistics, but that should probably be
    // completely separated from the "main" db?)

    /*  Updating the cache would be something like: (with ~= Google Guava cache)
      val key = Key(tenantId, debateId)
      var replaced = false
      while (!replaced) {
        val oldPage =
           _cache.tenantDaoDynVar.withValue(this) {
             _cache.cache.get(key)
           }
        val newPage = oldPage ++ actions ++ people-who-did-the-actions
        // newPage might == oldPage, if another thread just refreshed
        // the page from the database.
        replaced = _cache.cache.replace(key, oldPage, newPage)
    */
    // ------ /Page action cache
  }


  override def deleteVoteAndNotf(userIdData: UserIdData, pageId: PageId, postId: PostId,
        voteType: PostActionPayload.Vote) {
    super.deleteVoteAndNotf(userIdData, pageId, postId, voteType)
    refreshPageInCache(pageId)
  }


  override def loadPageParts(pageId: PageId): Option[PageParts] =
    lookupInCache[PageParts](pagePartsKey(siteId, pageId),
      orCacheAndReturn = {
        super.loadPageParts(pageId)
      })


  private def uncachePageParts(sitePageId: SitePageId) {
    removeFromCache(pagePartsKey(sitePageId.siteId, sitePageId.pageId))
  }


  def pagePartsKey(siteId: SiteId, pageId: PageId) = CacheKey(siteId, s"$pageId|PageParts")

}
