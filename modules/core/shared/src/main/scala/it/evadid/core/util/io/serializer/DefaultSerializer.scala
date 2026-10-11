package it.evadid.core.util.io.serializer

import it.evadid.core.datastructures.chat.*
import it.evadid.core.datastructures.chat.ChatParticipant.*
import it.evadid.core.datastructures.language.control.LanguageMapStorage
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
import it.evadid.workbook.interaction.sync.SyncInformation.*
import it.evadid.workbook.interaction.sync.*
import it.evadid.workbook.interaction.sync.{SyncContext, UpdateImportance, UsageContext}
import it.evadid.workbook.interaction.variable.*
import upickle.ReadWriter
import upickle.default.*

import java.net.InetAddress
import java.time.LocalDateTime
import scala.util.*

object DefaultSerializer {


  given anySer: ReadWriter[Any] = Serializer.anySerializer.uPickleReadWrite

  given ldt: ReadWriter[LocalDateTime] =
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

  given ReadWriter[InetAddress] = new Serializer[InetAddress] {
    override def serialize(obj: InetAddress): String = obj.getCanonicalHostName

    override def deserialize(str: String): InetAddress = InetAddress.getByName(str)
  }.uPickleReadWrite

  private given sat: ReadWriter[SingleAccessToken] = macroRW

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


  given rwMessage: ReadWriter[Message] = macroRW




  lazy val serializerExceptionS: Serializer[SerializedException] = Serializer.fromUpickleJson(SerializedException.derived$ReadWriter)
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
