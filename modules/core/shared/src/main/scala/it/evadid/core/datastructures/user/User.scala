package it.evadid.core.datastructures.user

import it.evadid.core.datastructures.chat.{Person, SenderRole}

import java.security.SecureRandom
import java.time.LocalDateTime
import java.util.HexFormat

object User {

  /*object UserToken {
    val empty = UserToken("", LocalDateTime.now())



  }


  case class UserToken(token: String, expires: LocalDateTime) {
    def toCode(user: User): String = token + "#" + user.id
  }*/

  case class SingleAccessToken(token: String, expires: LocalDateTime) {

  }

  object SingleAccessToken {
    def generateSecureToken(): SingleAccessToken = {
      val secureRandom = SecureRandom()
      val bytes = new Array[Byte](20)
      secureRandom.nextBytes(bytes)
      val tokenStr = HexFormat.of.formatHex(bytes)
      val expiresAt = LocalDateTime.now().plusMinutes(30)
      SingleAccessToken(tokenStr, expiresAt)
    }

    def apply(token: String): SingleAccessToken = SingleAccessToken(token, LocalDateTime.now())
  }


  case class UserInDatabase(user: User, configJson: String) {

  }

  def cleanUserName(name: String): String = {
    name.trim.replaceAll("\\s", " ").split(" ").map(_.trim.toLowerCase.capitalize).mkString(" ")
  }

  def createNewUser(firstName: String, lastName: String, mail: String): User = {
    createNewUser(firstName + " " + lastName, mail)
  }

  def createNewUser(name: String, mail: String): User = {
    val id = java.util.UUID.randomUUID().toString
    val user: User = User(cleanUserName(name), id, mail)
    user
  }

  private val emailRegex = """^[a-zA-Z0-9\.!#$%&'*+/=?^_`{|}~-]+@[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(?:\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)*$""".r

  def isEmail(mail: String): Boolean = mail match {
    case null => false
    case e if e.trim.isEmpty => false
    case e if emailRegex.findFirstMatchIn(e.trim).isDefined => true
    case _ => false
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


