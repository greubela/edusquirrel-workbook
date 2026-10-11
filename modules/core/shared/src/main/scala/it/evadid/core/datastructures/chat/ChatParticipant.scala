package it.evadid.core.datastructures.chat

import upickle.default.*
import it.evadid.core.datastructures.chat.ChatParticipant.{BasicPerson, *}
import it.evadid.core.util.io.Serializer

trait ChatParticipant {
  def name: String
  def personId: String
  def role: SenderRole
  def abbreviation: Option[String]
  def toBasic: BasicPerson = BasicPerson(name, personId, role, abbreviation)
  def toSerializable: BasicPerson = BasicPerson(name, personId, role, abbreviation)
}

object ChatParticipant {

  given rwPerson: ReadWriter[ChatParticipant] = new Serializer[ChatParticipant] {
    override def serialize(obj: ChatParticipant): String = {
      write(obj.toSerializable)(using BasicPerson.derived$ReadWriter)
    }
    override def deserialize(str: String): ChatParticipant = {
      read(str)(using BasicPerson.derived$ReadWriter)
    }
  }.uPickleReadWrite

  case class BasicPerson(name: String, personId: String, role: SenderRole, abbreviation: Option[String]) extends ChatParticipant derives ReadWriter {
  }

  def apply(name: String, personId: String, role: SenderRole, abbreviation: Option[String] = None): ChatParticipant = BasicPerson(name, personId, role, abbreviation.map(_.strip()))

}
