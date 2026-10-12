package it.evadid.core.datastructures.language.serialization.abstractions

import it.evadid.core.datastructures.language.AppLanguage.{HumanLanguage, SpecialLanguage}
import it.evadid.core.datastructures.language.serialization.abstractions.LanguageMapEntry.LanguageTripel
import it.evadid.core.datastructures.language.{AppLanguage, LanguageMapContentId}
import it.evadid.core.util.io.Serializer
import it.evadid.core.util.io.serializer.ConstructorLikeSerializer
import it.evadid.distribution.command.SerializedException
import upickle.default.*
import scala.util.control.NonFatal


object LanguageMapEntry {


  case class LanguageTripel(langNr: Int, idNr: Int, value: String) {

    def resolveRegular(ids: Seq[LanguageMapContentId], regularLanguages: Seq[HumanLanguage]): LanguageMapEntry[HumanLanguage] = try {
      LanguageMapEntry[HumanLanguage](ids(idNr), regularLanguages(langNr), value)
    } catch case (err: Throwable) => {
      val res = SerializedException(s"Cannot Resolve LanguageTripel(${langNr}, ${idNr}, ${value}) as regular: ${err.getMessage} (${ids.size} ids, ${regularLanguages.size} languages)")
      // println(s"[UGLY LANGUAGE-MAP-ENTRY] ${res.getMessage}")
      throw res
    }

    def resolveSpecial(ids: Seq[LanguageMapContentId], specialLanguage: Seq[SpecialLanguage]): LanguageMapEntry[SpecialLanguage] = try {
      LanguageMapEntry[SpecialLanguage](ids(idNr), specialLanguage(langNr), value)
    } catch case (err: Throwable) => {
      val res = SerializedException(s"Cannot Resolve LanguageTripel(${langNr}, ${idNr}, ${value}) as special: ${err.getMessage} (${ids.size} ids, ${specialLanguage.size} languages)")
      // println(s"[UGLY LANGUAGE-MAP-ENTRY] ${res.getMessage}")
      throw res
    }
  }

  object LanguageTripel {
    given ReadWriter[LanguageTripel] = tripSerializer.uPickleReadWrite
  }

  val tripSerializer: Serializer[LanguageTripel] = new Serializer[LanguageTripel] {
    val constructorName: String = "Trip"

    def serialize(obj: LanguageTripel): String = s"${constructorName}(${obj.langNr})(${obj.idNr})(${ujson.write(obj.value)})"

    def deserialize(str: String): LanguageTripel = {
      val res = ConstructorLikeSerializer.deserialize(str)
      if (res.elementType == constructorName) try {
        require(res.jsonPayloads.size == 3, "Trip requires exactly three payloads")
        LanguageTripel(res.jsonPayloads(0).toInt, res.jsonPayloads(1).toInt, ujson.read(res.jsonPayloads(2)).str)
      } catch case NonFatal(err) => {
        val f = SerializedException(s"Cannot parse LanguageTripel '${str}': ${err.getMessage}'", err)
        throw f
      } else {
        throw SerializedException(s"Cannot parse element of type ${res.elementType} with serialized of type ${constructorName}!")

      }
    }
  }

}


case class LanguageMapEntry[T <: AppLanguage](contentId: LanguageMapContentId, language: T, value: String) derives ReadWriter {

  def serializeWith(ids: Seq[LanguageMapContentId], langs: Seq[T]): LanguageTripel = LanguageTripel(langs.indexOf(language), ids.indexOf(contentId), value)

}

