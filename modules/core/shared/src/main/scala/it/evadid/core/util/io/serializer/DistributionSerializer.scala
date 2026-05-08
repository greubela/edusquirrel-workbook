package it.evadid.core.util.io.serializer

import it.evadid.core.datastructures.chat.MessengerModel.{BasicPerson, Person}
import it.evadid.core.datastructures.chat.{Message, MessengerModel, SenderRole}
import it.evadid.core.util.io.serializer.DefaultSerializer.{*, given}
import it.evadid.core.util.io.{Serializer, TypeConverter}
import it.evadid.distribution.command.*
import it.evadid.distribution.command.ExecutionResult.*
import it.evadid.distribution.command.ExecutionInfo.*
import upickle.ReadWriter
import upickle.default.*

import scala.util.Try

object DistributionSerializer {

  given bec: ReadWriter[ExecutionCommand] = macroRW

  given uer: ReadWriter[UntypedExecutionResult] = macroRW

  given ReadWriter[ExecutionHistory] = macroRW

  given tei: ReadWriter[ExecutionInfo] = serializerExecutionInfoJson.uPickleReadWrite

  given er: ReadWriter[ExecutionResult] = serializerExecutionResultJson.uPickleReadWrite

  given uei: ReadWriter[UntypedExecutionInfo] = macroRW

  given rwRole: ReadWriter[SenderRole] = readwriter[String].bimap[SenderRole](_.toString, SenderRole.valueOf)

  given rwBPerson: ReadWriter[BasicPerson] = macroRW

  given rwPerson: ReadWriter[Person] = readwriter[BasicPerson].bimap[Person]({ case person: BasicPerson => person }, basicPerson => basicPerson)

  given rwMessage: ReadWriter[Message] = macroRW

  given rwMessageModel: ReadWriter[MessengerModel] = macroRW


  lazy val serializeExecutionCommandJson: Serializer[ExecutionCommand] = new Serializer[ExecutionCommand] {
    override def serialize(obj: ExecutionCommand): String = write(obj)(using bec)

    override def deserialize(str: String): ExecutionCommand = read(str)(using bec)
  }
  lazy val serializerExecutionResultJson: Serializer[ExecutionResult] = new Serializer[ExecutionResult]() {
    override def serialize(obj: ExecutionResult): String = write(obj.untyped)(using uer)

    override def deserialize(str: String): ExecutionResult = read(str)(using uer)
  }

  lazy val serializerExecutionInfoJson: Serializer[ExecutionInfo] = new Serializer[ExecutionInfo]() {
    override def serialize(obj: ExecutionInfo): String = write(obj.untyped)(using uei)

    override def deserialize(str: String): ExecutionInfo = read(str)(using uei)
  }

  lazy val serializerMessageModelJson: Serializer[MessengerModel] = Serializer.fromUpickleJson(rwMessageModel)


  /*


  private given rwRole: ReadWriter[SenderRole] = readwriter[String].bimap[SenderRole](_.toString, SenderRole.valueOf)
  private given serializerP: ReadWriter[BasicPerson] = macroRW
  private given rwPerson: ReadWriter[Person] = readwriter[BasicPerson].bimap[Person]({ case person: BasicPerson => person }, basicPerson => basicPerson)
  private given serializerM: ReadWriter[Message] = macroRW
  given serializerMM: ReadWriter[MessengerModel] = macroRW
   */
}
