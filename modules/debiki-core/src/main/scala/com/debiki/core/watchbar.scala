/**
 * Copyright (c) 2016 Kaj Magnus Lindberg
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

package com.debiki.core

import play.api.libs.json._
import scala.collection.immutable


sealed abstract class WatchbarSection(val IntVal: Int) { def toInt = IntVal }
object WatchbarSection {
  case object RecentTopics extends WatchbarSection(1)
  case object Notifications extends WatchbarSection(2)
  case object ChatChannels extends WatchbarSection(3)
  case object DirectMessages extends WatchbarSection(4)
}


case class WatchbarTopic(
  pageId: PageId,
  unread: Boolean)


object WatchbarTopic {

  import play.api.libs.functional.syntax._

  implicit val topicReads: Reads[WatchbarTopic] = (
    (JsPath \ "pageId").read[PageId] and
    (JsPath \ "unread").read[Boolean])(WatchbarTopic.apply _)

  implicit val topicWritesNoTitles: Writes[WatchbarTopic] = (
      (JsPath \ "pageId").write[PageId] and
      (JsPath \ "unread").write[Boolean]
    )(unlift(WatchbarTopic.unapply))

  // load from db or other cache, so as not to dupl state, before sending watchbar to browser:
    //(JsPath \ "numMembers").read[Boolean] and
    //(JsPath \ "url").read[Option[String]] and

  // load from the notfs in the React.js store, in the browser:
    //(JsPath \ "notfsToMe").read[Int] and
    //(JsPath \ "notfsToMany").read[Int])
}


/** Immutable.
  */
trait Watchbar {

  def recentTopics: immutable.Seq[WatchbarTopic]
  def notifications: immutable.Seq[WatchbarTopic]
  def chatChannels: immutable.Seq[WatchbarTopic]
  def directMessages: immutable.Seq[WatchbarTopic]

  def watchedPageIds: Set[PageId] = {
    (recentTopics.map(_.pageId) ++ notifications.map(_.pageId) ++
      chatChannels.map(_.pageId) ++ directMessages.map(_.pageId)).toSet
  }
}



case class WatchbarWithTitles(
  recentTopics: immutable.Seq[WatchbarTopic],
  notifications: immutable.Seq[WatchbarTopic],
  chatChannels: immutable.Seq[WatchbarTopic],
  directMessages: immutable.Seq[WatchbarTopic],
  titlesEtcById: Map[PageId, PageTitleRole]) extends Watchbar {


  def toJsonWithTitles: JsValue = {
    import WatchbarTopic.topicWritesNoTitles
    Json.obj(
      "1" -> recentTopics.map(topicToJsonWithTitleEtc),
      "2" -> Nil,
      "3" -> Nil,
      "4" -> Nil)
  }


  private def topicToJsonWithTitleEtc(topic: WatchbarTopic): JsValue = {
    val titleEtc = titlesEtcById.getOrElse(topic.pageId, NoTitleEtc)
    Json.obj(
      "pageId" -> topic.pageId,
      "url" -> "", // ?
      "title" ->  titleEtc.title,
      "private" -> false,
      "numMembers" -> 0,
      "unread" -> topic.unread)
  }


  /** Used if a page has been deleted or made private, so that title etc is no longer available */
  private def NoTitleEtc = new PageTitleRole {
    def title = "(page gone, DwM5YKW24)"
    def role = PageRole.Discussion
  }
}



case class BareWatchbar(
  recentTopics: immutable.Seq[WatchbarTopic],
  notifications: immutable.Seq[WatchbarTopic],
  chatChannels: immutable.Seq[WatchbarTopic],
  directMessages: immutable.Seq[WatchbarTopic]) extends Watchbar {

  def addTitlesEtc(titlesEtcById: Map[PageId, PageTitleRole]) = WatchbarWithTitles(
    recentTopics, notifications, chatChannels, directMessages, titlesEtcById)


  def addRecentTopic(topic: WatchbarTopic): BareWatchbar = {
    if (recentTopics.exists(_.pageId == topic.pageId)) this
    else copy(recentTopics = topic +: recentTopics)
  }

  def markPageAsSeen(pageId: PageId): Unit = {
    ???
  }

  def markPageAsUnread(pageId: PageId) {
    ???
  }

  def toJsonNoTitles: JsValue = {
    import WatchbarTopic.topicWritesNoTitles
    Json.obj(
      "1" -> recentTopics,
      "2" -> Nil,
      "3" -> Nil,
      "4" -> Nil)
  }
}


object BareWatchbar {

  val empty = BareWatchbar(Nil, Nil, Nil, Nil)

  /** The json looks like interface WatchbarTopic in model.ts, for example:
    *
    * {
    *   1: [
    *     { pageId: 111 },
    *     { pageId: 222, unread: true },
    *     { pageId: 333, unread: true, notfsToMe: 1 }]
    *   2: [...]
    *   3: [
    *     { pageId: 111, private: true, notfsToMany: 2 }]
    *   4: [
    *     { pageId: 111, numMembers: 3 }]
    * }
    *
    * (and 1:, 2:, ... are WatchbarSection int values)
    */
  def fromJson(json: JsObject): BareWatchbar = {
    import WatchbarTopic.topicReads
    val recentTopicsJson = (json \ WatchbarSection.RecentTopics.toInt.toString).as[JsArray]
    val recentTopics = recentTopicsJson.as[immutable.Seq[WatchbarTopic]]
    BareWatchbar(recentTopics = recentTopics, Nil, Nil, Nil)
  }

}