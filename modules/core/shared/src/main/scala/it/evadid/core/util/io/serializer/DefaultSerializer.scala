package it.evadid.core.util.io.serializer

import it.evadid.core.datastructures.language.{AppLanguage, LanguageMap}
import it.evadid.core.datastructures.language.AppLanguage.HumanLanguage
import it.evadid.core.util.io.{Serializer, TypeConverter}
import upickle.default.*
import upickle.{ReadWriter, readwriter}

import java.time.LocalDateTime
import scala.util.*

object DefaultSerializer {

  given ReadWriter[LanguageMap[HumanLanguage]] =
    upickle.default.readwriter[String].bimap[LanguageMap[HumanLanguage]](
      _.getInLanguage(AppLanguage.default()),
      value => LanguageMap.universalMap(value)
    )

  given ReadWriter[LocalDateTime] =
    upickle.default.readwriter[String].bimap[LocalDateTime](_.toString, LocalDateTime.parse)

  given [T: ReadWriter]: ReadWriter[Try[T]] =
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

}
