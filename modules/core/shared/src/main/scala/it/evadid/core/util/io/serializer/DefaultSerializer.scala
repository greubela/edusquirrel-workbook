package it.evadid.core.util.io.serializer

import it.evadid.core.datastructures.chat.*
import it.evadid.core.datastructures.language.AppLanguage.HumanLanguage
import it.evadid.core.datastructures.language.{AppLanguage, LanguageMap}
import it.evadid.core.util.io.Serializer
import it.evadid.distribution.command.*
import it.evadid.distribution.command.ExecutionInfo.*
import it.evadid.distribution.command.ExecutionResult.*
import it.evadid.distribution.command.SerializedException.SimpleStackTraceElement
import it.evadid.distribution.commandTypes.LLMCommands.*
import it.evadid.distribution.commandTypes.MailCommands.{SendMailRequest, SendMailResponse}
import it.evadid.distribution.commandTypes.SQLCommands.*
import it.evadid.workbook.model.interaction.sync.*
import it.evadid.workbook.model.interaction.sync.SyncFormatter.InteractionSyncRequest
import it.evadid.workbook.model.interaction.sync.SyncInformation.SyncSuccess
import it.evadid.workbook.model.interaction.variable.{InteractionVariableHistorySerialized, InteractionVariableStateSerialized}
import upickle.ReadWriter
import upickle.default.*

import java.time.LocalDateTime
import scala.util.*

object DefaultSerializer {

  private[serializer] given ReadWriter[LanguageMap[HumanLanguage]] =
    upickle.default.readwriter[String].bimap[LanguageMap[HumanLanguage]](
      _.getInLanguage(AppLanguage.default()),
      value => LanguageMap.universalMap(value)
    )

  private[serializer] given ldt: ReadWriter[LocalDateTime] =
    upickle.default.readwriter[String].bimap[LocalDateTime](_.toString, LocalDateTime.parse)

  private[serializer] given [T: ReadWriter]: ReadWriter[Try[T]] =
    upickle.default.readwriter[ujson.Value].bimap[Try[T]](
      {
        case Success(value) => ujson.Obj("success" -> writeJs(value))
        case Failure(exception) => ujson.Obj("failure" -> exception.getMessage)
      },
      json =>
        json.obj.get("success") match {
          case Some(success) => Success(read[T](success))
          case None => Failure(new RuntimeException(json.obj.get("failure").map(_.str).getOrElse("Unknown failure")))
        }
    )

  private[serializer] given errStack: ReadWriter[SimpleStackTraceElement] = macroRW

  private[serializer] given errSer: ReadWriter[SerializedException] = macroRW

  private[serializer] given bec: ReadWriter[ExecutionCommand] = macroRW

  private[serializer] given uer: ReadWriter[ExecutionResultUntyped] = macroRW

  private[serializer] given ReadWriter[ExecutionHistory] = macroRW

  private[serializer] given er: ReadWriter[ExecutionResult] = serializerExecutionResultJson.uPickleReadWrite

  private[serializer] given rw1: upickle.default.ReadWriter[ExecutionInfoUntyped] = macroRW[ExecutionInfoUntyped]

  private[serializer] given rw5: upickle.default.ReadWriter[ExecutionInfoUntyped] = macroRW[ExecutionInfoUntyped]

  private[serializer] given rwRole: ReadWriter[SenderRole] = readwriter[String].bimap[SenderRole](_.showName, str => SenderRole.allRoles.find(_.showName == str).getOrElse(throw new RuntimeException(s"Unknown role: $str")))

  private[serializer] given rwPerson: ReadWriter[Person] = macroRW

  private[serializer] given rwMessage: ReadWriter[Message] = macroRW

  private[serializer] given rwMessageModel: ReadWriter[MessengerModel] = macroRW

  private[serializer] given mccres: ReadWriter[MessengerChatCompletionResponse] = macroRW

  private[serializer] given mccreq: ReadWriter[MessengerChatCompletionRequest] = macroRW

  private[serializer] given flreq: ReadWriter[FeedbackLlmRequest] = macroRW

  private[serializer] given screq: ReadWriter[SyncContext] = macroRW

  private[serializer] given ReadWriter[UsageContext] = macroRW

  private[serializer] given ReadWriter[InteractionSyncRequest] = macroRW

  private[serializer] given stdbreq: ReadWriter[StoreToDbRequest] = macroRW

  private[serializer] given dbresreq: ReadWriter[SyncSuccess] = macroRW

  private[serializer] given fdbreq: ReadWriter[FetchAllFromDbRequest] = macroRW

  private[serializer] given ReadWriter[InteractionVariableStateSerialized] = macroRW

  private[serializer] given ReadWriter[Set[InteractionVariableStateSerialized]] =
    readwriter[Seq[InteractionVariableStateSerialized]].bimap[Set[InteractionVariableStateSerialized]](_.toSeq, _.toSet)

  private[serializer] given ivhsRW: ReadWriter[InteractionVariableHistorySerialized] = macroRW

  private[serializer] given ReadWriter[UpdateImportance] = readwriter[String].bimap[UpdateImportance](_.toString, UpdateImportance.valueOf)

  private[serializer] given dbfresreq: ReadWriter[DbFetchResponse] = macroRW

  private[serializer] given cdbreq: ReadWriter[DeleteInDbRequest] = macroRW

  private[serializer] given sendMailReq: ReadWriter[SendMailRequest] = macroRW

  private[serializer] given sendMailRes: ReadWriter[SendMailResponse] = macroRW


  lazy val serializerLocalDateTimeString: Serializer[LocalDateTime] = Serializer.fromUpickleJson[LocalDateTime](ldt)

  lazy val serializeExecutionCommandJson: Serializer[ExecutionCommand] = new Serializer[ExecutionCommand] {
    override def serialize(obj: ExecutionCommand): String = write(obj)(using bec)

    override def deserialize(str: String): ExecutionCommand = read(str)(using bec)
  }

  lazy val serializerExecutionResultJson: Serializer[ExecutionResult] = new Serializer[ExecutionResult]() {
    override def serialize(obj: ExecutionResult): String = write(obj.untyped)(using uer)

    override def deserialize(str: String): ExecutionResult = read(str)(using uer)
  }

  lazy val serializerExecutionInfoJson: Serializer[ExecutionInfoUntyped] = new Serializer[ExecutionInfoUntyped]() {
    override def serialize(obj: ExecutionInfoUntyped): String = write(obj)(using rw5)

    override def deserialize(str: String): ExecutionInfoUntyped = read(str)(using rw5)
  }

  lazy val serializerMessageModelJson: Serializer[MessengerModel] = Serializer.fromUpickleJson(rwMessageModel)

  lazy val serializerInteractionVariableHistoryIgnoreErrors: Serializer[InteractionVariableHistorySerialized] = Serializer.fromUpickleJson(ivhsRW)

  lazy val serializerChatRequestJson: Serializer[MessengerChatCompletionRequest] = Serializer.fromUpickleJson[MessengerChatCompletionRequest](mccreq)
  lazy val serializerChatResponseJson: Serializer[MessengerChatCompletionResponse] = Serializer.fromUpickleJson[MessengerChatCompletionResponse](mccres)
  lazy val serializerFeedbackLlmRequestJson: Serializer[FeedbackLlmRequest] = Serializer.fromUpickleJson[FeedbackLlmRequest](flreq)
  lazy val serializerStoreToDbRequestJson: Serializer[StoreToDbRequest] = Serializer.fromUpickleJson[StoreToDbRequest](stdbreq)
  lazy val serializerFetchAllFromDbRequestJson: Serializer[FetchAllFromDbRequest] = Serializer.fromUpickleJson[FetchAllFromDbRequest](fdbreq)
  lazy val serializerDeleteInDbRequestJson: Serializer[DeleteInDbRequest] = Serializer.fromUpickleJson[DeleteInDbRequest](cdbreq)
  lazy val serializerSyncSuccess: Serializer[SyncSuccess] = Serializer.fromUpickleJson[SyncSuccess](dbresreq)
  lazy val serializerDbFetchResponse: Serializer[DbFetchResponse] = Serializer.fromUpickleJson[DbFetchResponse](dbfresreq)
  lazy val serializerSendMailRequestJson: Serializer[SendMailRequest] = Serializer.fromUpickleJson[SendMailRequest](sendMailReq)
  lazy val serializerSendMailResponseJson: Serializer[SendMailResponse] = Serializer.fromUpickleJson[SendMailResponse](sendMailRes)
  lazy val serializerStringJson: Serializer[String] = Serializer.stringIO
  lazy val serializerExceptionS: Serializer[SerializedException] = Serializer.fromUpickleJson(errSer)
  lazy val serializerException: Serializer[Throwable] = new Serializer[Throwable] {
    override def serialize(obj: Throwable): String = serializerExceptionS.serialize(SerializedException(obj))
    override def deserialize(str: String): Throwable = serializerExceptionS.deserialize(str)
  }


  private given stringMapRW: ReadWriter[Map[String, String]] = readwriter[Map[String, String]].bimap[Map[String, String]](_.toMap, _.toMap)

  val serializerStringMap: Serializer[Map[String, String]] = Serializer.fromUpickleJson(stringMapRW)

  /*


  private given rwRole: ReadWriter[SenderRole] = readwriter[String].bimap[SenderRole](_.toString, SenderRole.valueOf)
  private given serializerP: ReadWriter[BasicPerson] = macroRW
  private given rwPerson: ReadWriter[Person] = readwriter[BasicPerson].bimap[Person]({ case person: BasicPerson => person }, basicPerson => basicPerson)
  private given serializerM: ReadWriter[Message] = macroRW
  given serializerMM: ReadWriter[MessengerModel] = macroRW
   */
}
