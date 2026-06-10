package it.evadid.core.util.io.serializer

import it.evadid.core.datastructures.chat.{Message, MessengerModel, Person, SenderRole}
import it.evadid.core.util.io.serializer.DefaultSerializer.{*, given}
import it.evadid.core.util.io.{Serializer, TypeConverter}
import it.evadid.distribution.command.*
import it.evadid.distribution.command.ExecutionResult.*
import it.evadid.distribution.command.ExecutionInfo.*
import it.evadid.distribution.commandTypes.LLMCommands.{FeedbackLlmRequest, MessengerChatCompletionRequest, MessengerChatCompletionResponse}
import it.evadid.distribution.commandTypes.SQLCommands.{SyncToDbRequest, SyncToDbResponse}
import upickle.ReadWriter
import upickle.default.*

import scala.util.Try

object DistributionSerializer {

  private[serializer] given bec: ReadWriter[ExecutionCommand] = macroRW

  private[serializer] given uer: ReadWriter[UntypedExecutionResult] = macroRW

  private[serializer] given ReadWriter[ExecutionHistory] = macroRW

  private[serializer] given tei: ReadWriter[ExecutionInfo] = serializerExecutionInfoJson.uPickleReadWrite

  private[serializer] given er: ReadWriter[ExecutionResult] = serializerExecutionResultJson.uPickleReadWrite

  private[serializer] given uei: ReadWriter[UntypedExecutionInfo] = macroRW

  private[serializer] given rwRole: ReadWriter[SenderRole] = readwriter[String].bimap[SenderRole](_.showName, str => SenderRole.allRoles.find(_.showName == str).getOrElse(throw new RuntimeException(s"Unknown role: $str")))

  private[serializer] given rwPerson: ReadWriter[Person] = macroRW

  private[serializer] given rwMessage: ReadWriter[Message] = macroRW

  private[serializer] given rwMessageModel: ReadWriter[MessengerModel] = macroRW

  private[serializer] given mccres: ReadWriter[MessengerChatCompletionResponse] = macroRW

  private[serializer] given mccreq: ReadWriter[MessengerChatCompletionRequest] = macroRW

  private[serializer] given flreq: ReadWriter[FeedbackLlmRequest] = macroRW

  private[serializer] given stdbreq: ReadWriter[SyncToDbRequest] = macroRW

  private[serializer] given stdbres: ReadWriter[SyncToDbResponse] = macroRW


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


  lazy val serializerChatRequestJson: Serializer[MessengerChatCompletionRequest] = Serializer.fromUpickleJson[MessengerChatCompletionRequest](mccreq)
  lazy val serializerChatResponseJson: Serializer[MessengerChatCompletionResponse] = Serializer.fromUpickleJson[MessengerChatCompletionResponse](mccres)
  lazy val serializerFeedbackLlmRequestJson: Serializer[FeedbackLlmRequest] = Serializer.fromUpickleJson[FeedbackLlmRequest](flreq)
  lazy val serializerSyncToDbRequestJson: Serializer[SyncToDbRequest] = Serializer.fromUpickleJson[SyncToDbRequest](stdbreq)
  lazy val serializerSyncToDbResponseJson: Serializer[SyncToDbResponse] = Serializer.fromUpickleJson[SyncToDbResponse](stdbres)
  lazy val serializerStringJson: Serializer[String] = Serializer.stringIO
  /*


  private given rwRole: ReadWriter[SenderRole] = readwriter[String].bimap[SenderRole](_.toString, SenderRole.valueOf)
  private given serializerP: ReadWriter[BasicPerson] = macroRW
  private given rwPerson: ReadWriter[Person] = readwriter[BasicPerson].bimap[Person]({ case person: BasicPerson => person }, basicPerson => basicPerson)
  private given serializerM: ReadWriter[Message] = macroRW
  given serializerMM: ReadWriter[MessengerModel] = macroRW
   */
}
