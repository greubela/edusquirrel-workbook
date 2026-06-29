package it.evadid.core.datastructures.user

import it.evadid.core.datastructures.chat.{Person, SenderRole}


case class User(name: String, mail: String, avatarSvg: Option[String] = None) {
  def id: String = mail

  def toPerson: Person = Person(name, id, SenderRole.USER, avatarSvg)

  def initials: String = {
    val parts = name.trim.split("\\s+").filter(_.nonEmpty).toList
    val initials = parts.take(2).flatMap(_.headOption).mkString.toUpperCase
    if (initials.nonEmpty) initials else "?"
  }

}


