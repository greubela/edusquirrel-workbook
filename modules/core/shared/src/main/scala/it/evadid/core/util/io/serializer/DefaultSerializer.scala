package it.evadid.core.util.io.serializer

import it.evadid.core.datastructures.chat.*
import it.evadid.core.datastructures.chat.Person.SerializablePerson
import it.evadid.core.datastructures.language.{AppLanguage, LanguageMapContentId}
import it.evadid.core.datastructures.user.User.SingleAccessToken
import it.evadid.core.datastructures.user.UserTokenInfo.SignedToken
import it.evadid.core.datastructures.user.{AllUserInfo, User, UserConfig, UserTokenInfo}
import it.evadid.core.util.io.Serializer
import it.evadid.distribution.command.*
import it.evadid.distribution.command.ExecutionInfo.*
import it.evadid.distribution.command.ExecutionResult.*
import it.evadid.distribution.command.SerializedException.SimpleStackTraceElement
import it.evadid.distribution.commandTypes.LLMCommands.*
import it.evadid.distribution.commandTypes.MailCommands.{SendMailRequest, SendMailResponse}
import it.evadid.distribution.commandTypes.SQLCommands.*
import it.evadid.distribution.commandTypes.UserCommands.*
import it.evadid.workbook.abstractions.TypeOfTextDisplay
import it.evadid.workbook.interaction.sync.SyncFormatter.InteractionSyncRequest
import it.evadid.workbook.interaction.sync.SyncInformation.SyncSuccess
import it.evadid.workbook.interaction.sync.{SyncContext, UpdateImportance, UsageContext}
import it.evadid.workbook.interaction.variable.{InteractionVariableHistorySerialized, InteractionVariableStateSerialized}
import upickle.ReadWriter
import upickle.default.*

import java.net.InetAddress
import java.time.LocalDateTime
import scala.util.*

object DefaultSerializer {


  private[serializer] given serLMID: ReadWriter[LanguageMapContentId] = serializerLangMapId.uPickleReadWrite

  private[serializer] given serLMIDs: ReadWriter[List[LanguageMapContentId]] =
    readwriter[Seq[LanguageMapContentId]].bimap[List[LanguageMapContentId]](_.toSeq, _.toList)

  private[serializer] given rwAL: ReadWriter[AppLanguage] =
    readwriter[String].bimap[AppLanguage](_.name, value => AppLanguage.allLanguages.find(_.name == value).get)

  private[serializer] given rwALs: ReadWriter[List[AppLanguage]] =
    readwriter[Seq[AppLanguage]].bimap[List[AppLanguage]](_.toSeq, _.toList)

  private[serializer] given strs: ReadWriter[List[String]] =
    readwriter[Seq[String]].bimap[List[String]](_.toSeq, _.toList)

  val serializerStrings: Serializer[List[String]] = Serializer.fromUpickleJson(strs)

  val serializerAppLanguage: Serializer[AppLanguage] = Serializer.fromUpickleJson(rwAL)
  val serializerAppLanguages: Serializer[List[AppLanguage]] = Serializer.fromUpickleJson(rwALs)


  val serializerLangMapId: Serializer[LanguageMapContentId] = Serializer.constructorLikeSerializer("LangMapId", new Serializer[LanguageMapContentId]() {
    override def serialize(obj: LanguageMapContentId): String = obj.fullId

    override def deserialize(str: String): LanguageMapContentId = LanguageMapContentId(str)
  })

  val serializerLangMapIds: Serializer[List[LanguageMapContentId]] = Serializer.fromUpickleJson(serLMIDs)

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

  private[serializer] given rwTypeTextDisplay: ReadWriter[TypeOfTextDisplay] =
    readwriter[String].bimap[TypeOfTextDisplay](_.serializerName, str => TypeOfTextDisplay.allElements.find(_.serializerName == str).getOrElse(throw new RuntimeException(s"Unknown type of text display: $str")))

  val serializerTextDisplay: Serializer[TypeOfTextDisplay] = Serializer.fromUpickleJson(rwTypeTextDisplay)

  private[serializer] given rwBasicPerson: ReadWriter[SerializablePerson] = macroRW

  private[serializer] given rwPerson: ReadWriter[Person] = new Serializer[Person] {
    override def serialize(obj: Person): String = {
      //println(s"ReadWriter::Person, serializing${obj}")
      write(obj.toSerializable)(using rwBasicPerson)
    }

    override def deserialize(str: String): Person = {
      //println(s"ReadWriter::Person, deserializing${str}")
      read(str)(using rwBasicPerson)
    }
  }.uPickleReadWrite


  private given ReadWriter[InetAddress] = new Serializer[InetAddress] {
    override def serialize(obj: InetAddress): String = obj.getCanonicalHostName

    override def deserialize(str: String): InetAddress = InetAddress.getByName(str)
  }.uPickleReadWrite

  private given sat: ReadWriter[SingleAccessToken] = macroRW

  private given use: ReadWriter[User] = macroRW

  private given uti: ReadWriter[UserTokenInfo] = macroRW

  private given suti: ReadWriter[SignedToken] = macroRW

  private given ReadWriter[Either[SingleAccessToken, SignedToken]] = new Serializer[Either[SingleAccessToken, SignedToken]]() {
    val serializerSat: Serializer[SingleAccessToken] = Serializer.fromUpickleJson(sat)
    val serializerSig: Serializer[SignedToken] = Serializer.fromUpickleJson(suti)

    override def serialize(obj: Either[SingleAccessToken, SignedToken]): String = obj.match {
      case Left(singleAccessToken) => "Left(" + serializerSat.serialize(singleAccessToken) + ")"
      case Right(signedToken: SignedToken) => "Right(" + serializerSig.serialize(signedToken) + ")"
    }

    override def deserialize(str: String): Either[SingleAccessToken, SignedToken] = {
      if (!str.endsWith(")")) {
        throw new IllegalArgumentException("Serializer[Either[SingleAccessToken,SignedToken]]: str does not end with ')'!")
      } else {
        val cleaned: String =
          if (str.startsWith("Left(")) str.substring("Left(".length, str.length - 1)
          else if (str.startsWith("Right(")) str.substring("Right(".length, str.length - 1)
          else throw new IllegalArgumentException(s"Serializer[Either[SingleAccessToken,SignedToken]]: '${str}' does neither start with 'Left(' nor 'Right('!")
        println(s"### Default Serializer for Token Either, deserializing '${cleaned}'")
        try {
          if (str.startsWith("Left(")) Left(serializerSat.deserialize(cleaned))
          else if (str.startsWith("Right(")) Right(serializerSig.deserialize(cleaned))
          else throw new IllegalArgumentException(s"String '${cleaned}' is not an instance of Either[SingleAccessToken,SignedToken]'!")
        } catch case e: Throwable => {
          throw new IllegalArgumentException(s"String '${cleaned}' is not an instance of Either[SingleAccessToken,SignedToken]' (${e.getMessage}!")
        }
      }
    }
  }.uPickleReadWrite


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


  private[serializer] given authReqRW: ReadWriter[LoginRequest] = macroRW

  private[serializer] given authResRW: ReadWriter[LoginResponse] = macroRW


  private[serializer] given upsertAccReqRW: ReadWriter[CreateAccountRequest] = macroRW

  private[serializer] given upsertAccResRW: ReadWriter[CreateAccountResponse] = macroRW

  private[serializer] given upsertAccReqRW2: ReadWriter[UpdateAccountRequest] = macroRW

  private[serializer] given upsertAccResRW2: ReadWriter[UpdateAccountResponse] = macroRW


  private[serializer] given authMailReq: ReadWriter[AuthMailRequest] = macroRW

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

  lazy val serializerMessageJson: Serializer[Message] = Serializer.fromUpickleJson(rwMessage)
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
  lazy val serializerAuthMailRequestJson: Serializer[AuthMailRequest] = Serializer.fromUpickleJson[AuthMailRequest](authMailReq)
  lazy val serializerSendMailRequestJson: Serializer[SendMailRequest] = Serializer.fromUpickleJson[SendMailRequest](sendMailReq)
  lazy val serializerSendMailResponseJson: Serializer[SendMailResponse] = Serializer.fromUpickleJson[SendMailResponse](sendMailRes)

  lazy val serializerVerifyAuthenticationRequest: Serializer[LoginRequest] = Serializer.fromUpickleJson[LoginRequest](authReqRW)
  lazy val serializerVerifyAuthenticationResponse: Serializer[LoginResponse] = Serializer.fromUpickleJson[LoginResponse](authResRW)

  lazy val serializerCreateAccountRequest: Serializer[CreateAccountRequest] = Serializer.fromUpickleJson[CreateAccountRequest](upsertAccReqRW)
  lazy val serializerCreateAccountResponse: Serializer[CreateAccountResponse] = Serializer.fromUpickleJson[CreateAccountResponse](upsertAccResRW)

  lazy val serializerUpdateAccountRequest: Serializer[UpdateAccountRequest] = Serializer.fromUpickleJson[UpdateAccountRequest](upsertAccReqRW2)
  lazy val serializerUpdateAccountResponse: Serializer[UpdateAccountResponse] = Serializer.fromUpickleJson[UpdateAccountResponse](upsertAccResRW2)

  lazy val serializerUserTokenInfo: Serializer[UserTokenInfo] = Serializer.fromUpickleJson(uti)
  lazy val serializerSignedUserTokenInfo: Serializer[SignedToken] = Serializer.fromUpickleJson(suti)

  lazy val serializerStringJson: Serializer[String] = Serializer.stringIO
  lazy val serializerExceptionS: Serializer[SerializedException] = Serializer.fromUpickleJson(errSer)
  lazy val serializerException: Serializer[Throwable] = new Serializer[Throwable] {
    override def serialize(obj: Throwable): String = serializerExceptionS.serialize(SerializedException(obj))

    override def deserialize(str: String): Throwable = serializerExceptionS.deserialize(str)
  }


  private given stringMapRW: ReadWriter[Map[String, String]] = readwriter[Map[String, String]].bimap[Map[String, String]](_.toMap, _.toMap)

  val serializerJsonStringMap: Serializer[Map[String, String]] = Serializer.fromUpickleJson(stringMapRW)

  def serializerAllUserInfo(serializerUserConfig: Serializer[UserConfig]): Serializer[AllUserInfo] = {
    given ucrw: ReadWriter[UserConfig] = serializerUserConfig.uPickleReadWrite

    given rw: ReadWriter[AllUserInfo] = macroRW

    Serializer.fromUpickleJson(rw)
  }


  /*


  private given rwRole: ReadWriter[SenderRole] = readwriter[String].bimap[SenderRole](_.toString, SenderRole.valueOf)
  private given serializerP: ReadWriter[BasicPerson] = macroRW
  private given rwPerson: ReadWriter[Person] = readwriter[BasicPerson].bimap[Person]({ case person: BasicPerson => person }, basicPerson => basicPerson)
  private given serializerM: ReadWriter[Message] = macroRW
  given serializerMM: ReadWriter[MessengerModel] = macroRW
   */
}
