package it.evadid.workbook.vm.naming

import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.core.datastructures.language.LanguageMap

sealed trait BeEntityName {

  def getNameIn(humanLanguage: HumanLanguage, namingStyle: NamingStyle): String

  def universalInterpretation(): String = getNameIn(English, NamingStyle.SnakeCase)

}

object BeEntityName {

  def fromUniversalNameInParts(universalNameInParts: String): BeEntityName = BeEntityNamePartsBased(LanguageMap.universalMap(universalNameInParts))

  def fromMapInCodeNotation(partsMap: LanguageMap[HumanLanguage]): BeEntityName = BeEntityNamePartsBased(partsMap)

  def fromMapInCodeNotation(partsMap: Map[HumanLanguage, String]): BeEntityName = BeEntityNamePartsBased(LanguageMap.mapBasedLanguageMap(partsMap))

  private case class BeEntityNamePartsBased(languageMapWithPartsString: LanguageMap[HumanLanguage]) extends BeEntityName {

    override def getNameIn(humanLanguage: HumanLanguage, namingStyle: NamingStyle): String = {
      val partsString: String = languageMapWithPartsString.getInLanguage(humanLanguage)
      val parts: List[String] = NamingStyle.fromAnyNotationToParts(partsString)
      namingStyle.applyStyle(parts)
    }
  }

}