package it.evadid.core.datastructures.language.serialization.abstractions

import it.evadid.core.datastructures.language.AppLanguage.{HumanLanguage, SpecialLanguage}
import it.evadid.core.datastructures.language.serialization.abstractions.LanguageMapEntry.LanguageTripel
import it.evadid.core.datastructures.language.{AppLanguage, LanguageMap, LanguageMapContentId}
import it.evadid.core.util.io.Serializer
import it.evadid.core.util.io.serializer.ConstructorLikeSerializer
import it.evadid.distribution.command.SerializedException
import upickle.ReadWriter


object LanguageMapEntry {


  case class LanguageTripel(langNr: Int, idNr: Int, value: String) {
    def resolveRegular(ids: Seq[LanguageMapContentId], regularLanguages: Seq[HumanLanguage]): LanguageMapEntry[HumanLanguage] = {
      LanguageMapEntry[HumanLanguage](ids(idNr), regularLanguages(langNr), value)
    }

    def resolveSpecial(ids: Seq[LanguageMapContentId], specialLanguage: Seq[SpecialLanguage]): LanguageMapEntry[SpecialLanguage] = {
      LanguageMapEntry[SpecialLanguage](ids(idNr), specialLanguage(langNr), value)
    }
  }

  val tripSerializer: Serializer[LanguageTripel] = new Serializer[LanguageTripel] {

    val constructorName: String = "Trip"

    def serialize(obj: LanguageTripel): String = constructorName + "(" + obj.langNr + ")(" + obj.idNr + ")( " + obj.value + ")"

    def deserialize(str: String): LanguageTripel = {
      val res = ConstructorLikeSerializer.deserialize(str)
      if (res.elementType == constructorName) LanguageTripel(res.jsonPayloads(0).toInt, res.jsonPayloads(1).toInt, res.jsonPayloads(2))
      else throw SerializedException(s"Cannot parse element of type ${res.elementType} with serialized of type ${constructorName}!")
    }
  }

}


case class LanguageMapEntry[T <: AppLanguage](contentId: LanguageMapContentId, language: T, value: String) {
  
  def serializeWith(ids: Seq[LanguageMapContentId], langs: Seq[T]): LanguageTripel = LanguageTripel(ids.indexOf(contentId), langs.indexOf(language), value)

}

