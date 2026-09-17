package it.evadid.core.datastructures.user

import it.evadid.core.datastructures.chat.{Person, SenderRole}

import java.security.SecureRandom
import java.time.LocalDateTime
import java.util.HexFormat

object User {

  object UserToken {
    val empty = UserToken("", LocalDateTime.now())

    def generateSecureToken(): UserToken = {
      val secureRandom = SecureRandom()
      val bytes = new Array[Byte](100)
      secureRandom.nextBytes(bytes)
      val tokenStr = HexFormat.of.formatHex(bytes)
      val expiresAt = LocalDateTime.now().plusYears(1)
      UserToken(tokenStr, expiresAt)
    }

  }


  case class UserToken(token: String, expires: LocalDateTime) {
    def toCode(user: User): String = token + "#" + user.id
  }

  case class UserInDatabase(user: User, token: UserToken, configJson: String) {
    val toCode: String = token.token + "#" + user.id
  }

  def cleanUserName(name: String): String = {
    name.trim.replaceAll("\\s", " ").split(" ").map(_.trim.toLowerCase.capitalize).mkString(" ")
  }

  def createNewUser(firstName: String, lastName: String, mail: Option[String]): User = {
    createNewUser(Some(firstName + " " + lastName), mail)
  }

  def createNewUser(name: Option[String], mail: Option[String]): User = {
    val id = java.util.UUID.randomUUID().toString
    val user: User = User(name.map(cleanUserName).getOrElse(""), id, mail.getOrElse(""))
    user
  }


}

case class User(name: String, id: String, mail: String) extends Person {

  def initials: String = {
    val parts = name.trim.split("\\s+").filter(_.nonEmpty).toList
    val initials = parts.take(2).flatMap(_.headOption).mkString.toUpperCase
    if (initials.nonEmpty) initials else "?"
  }

  override def personId: String = id

  override def role: SenderRole = SenderRole.USER

  override def abbreviation: Option[String] = Some(initials)

}


