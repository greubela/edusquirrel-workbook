package it.evadid.core.datastructures.user

import it.evadid.core.datastructures.chat.{Person, SenderRole}

object User {

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


