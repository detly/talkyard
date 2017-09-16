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

package com.debiki.core

import org.scalactic.{Good, Bad, Or, ErrorMessage}
import Prelude._


object Validation {

  private val EmailOkCharsRegex = """\S+@\S+\.\S+$""".r

  private val UsernameOkCharsRegex = "[A-Za-z0-9_]*".r
  private val UsernameOkFirstCharRegex = "[A-Za-z0-9]".r


  def checkName(name: Option[String]): Option[String] Or ErrorMessage = {
    if (name.map(_.trim) != name)
      return Bad("Name starts or ends with blanks")

    if (name.exists(_.length > 100))
      return Bad("The name is too long")

    Good(name)
  }


  def checkEmail(email: String): String Or ErrorMessage = {
    if (!email.isEmpty && EmailOkCharsRegex.unapplySeq(email).isEmpty)
      return Bad("Invalid email address")

    Good(email)
  }

  def requireOkEmail(email: String, errorCode: String) {
    checkEmail(email) badMap { errorMessage =>
      Prelude.throwIllegalArgument(errorCode, s"Bad email: $errorMessage")
    }
  }


  val StackExchangeUsernameRegex = "^__sx_[a-z]+_[0-9]+__$".r

  /** Allows only usernames like '123_some_username', 3 - 20 chars.
    */
  def checkUsername(username: String): String Or ErrorMessage = {  CLEAN_UP // merge with ReservedNames [2PGKR8ML]
    // WOULD add unit tests

    if (StackExchangeUsernameRegex.matches(username))  // [2QWGRC8P]
      return Good(username)

    if (username.length < 3)
      return Bad("The username is too short; it must be at least 3 characters")

    if (username.length > 20)
      return Bad("The username is too long; it must be at most 20 characters")

    if (UsernameOkCharsRegex.unapplySeq(username).isEmpty)
      return Bad("The username must use characters a-z, A-Z, 0-9 and _ only")

    if (UsernameOkFirstCharRegex.unapplySeq(username.charAt(0)).isEmpty)
      return Bad("The username's first character must be one of a-z, A-Z, 0-9")

    Good(username)
  }


  def checkPassword(password: String): String Or ErrorMessage = {
    if (password.length < 8)
      return Bad("The password is too short")

    // If it's too long, then it's not a password? It's some other weird thing, perhaps bad?
    if (password.length > 80)
      return Bad("The password is too long (80 chars max)")

    // SHO-ULD check that the password is strong, use https://github.com/dropbox/zxcvbn?
    // CLEAN_UP remove the server side password strength check instead? The JS is far too slow.
    // (Then, do client side only).
    Good(password)
  }

}
