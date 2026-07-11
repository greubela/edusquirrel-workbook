package it.evadid.core.datastructures.user

import it.evadid.core.datastructures.chat.{Person, SenderRole}


case class User(name: String, mail: String, avatarSvg: Option[String] = None) extends Person{

  def initials: String = {
    val parts = name.trim.split("\\s+").filter(_.nonEmpty).toList
    val initials = parts.take(2).flatMap(_.headOption).mkString.toUpperCase
    if (initials.nonEmpty) initials else "?"
  }

  override def personId: String = mail

  override def role: SenderRole = SenderRole.USER

  override def abbreviation: Option[String] = Some(initials)
}


