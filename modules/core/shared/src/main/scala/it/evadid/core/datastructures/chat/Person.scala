package it.evadid.core.datastructures.chat

import it.evadid.core.datastructures.chat.Person.{BasicPerson, SerializablePerson}

trait Person {
  def name: String

  def personId: String

  def role: SenderRole

  def abbreviation: Option[String]

  def toBasic: BasicPerson = BasicPerson(name, personId, role, abbreviation)
  def toSerializable: SerializablePerson = SerializablePerson(name, personId, role, abbreviation.getOrElse(""))
}

object Person {

  case class SerializablePerson(name: String, personId: String, role: SenderRole, avatarSvg: String) extends Person {
    override def abbreviation: Option[String] = if(avatarSvg == null || avatarSvg.strip().isEmpty) None else Some(avatarSvg.strip())
  }

  case class BasicPerson(name: String, personId: String, role: SenderRole, abbreviation: Option[String]) extends Person {
  }

  def apply(name: String, personId: String, role: SenderRole, abbreviation: Option[String] = None): Person = BasicPerson(name, personId, role, abbreviation.map(_.strip()))

}
