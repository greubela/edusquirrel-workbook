package it.evadid.vm.naming

import it.evadid.core.datastructures.language.AppLanguage
import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.core.datastructures.language.LanguageMap
import upickle.default.*

sealed trait BeEntityName derives ReadWriter{

  def getNameIn(humanLanguage: HumanLanguage, namingStyle: NamingStyle): String

  def universalInterpretation(): String = getNameIn(English, NamingStyle.SnakeCase)

  /** The name as written in `humanLanguage`, before a naming style normalizes its case and separators. */
  def spelling(humanLanguage: HumanLanguage = English): String

  def asLanguageMap(namingStyle: NamingStyle): LanguageMap[HumanLanguage] = LanguageMap.mapBasedLanguageMap(
    AppLanguage.humanLanguages.map(language => language -> getNameIn(language, namingStyle)).toMap
  )

}

object BeEntityName {

  def fromCodeString(stringInCode: String): BeEntityName = fromUniversalNameInParts(stringInCode)

  def fromLiteral(literalName: String): BeEntityName = BeEntityNameLiteral(literalName)

  def fromUniversalNameInParts(universalNameInParts: String): BeEntityName = BeEntityNamePartsBased(LanguageMap.universalMap(universalNameInParts))

  def fromMapInCodeNotation(partsMap: LanguageMap[HumanLanguage]): BeEntityName = BeEntityNamePartsBased(partsMap)

  def fromMapInCodeNotation(partsMap: Map[HumanLanguage, String]): BeEntityName = BeEntityNamePartsBased(LanguageMap.mapBasedLanguageMap(partsMap))

  private case class BeEntityNamePartsBased(languageMapWithPartsString: LanguageMap[HumanLanguage]) extends BeEntityName derives upickle.default.ReadWriter {

    override def getNameIn(humanLanguage: HumanLanguage, namingStyle: NamingStyle): String = {
      val partsString: String = languageMapWithPartsString.getInLanguage(humanLanguage)
      val parts: List[String] = NamingStyle.fromAnyNotationToParts(partsString)
      namingStyle.applyStyle(parts)
    }

    override def spelling(humanLanguage: HumanLanguage): String = languageMapWithPartsString.getInLanguage(humanLanguage)
  }

  private case class BeEntityNameLiteral(literalName: String) extends BeEntityName derives upickle.default.ReadWriter {
    override def getNameIn(humanLanguage: HumanLanguage, namingStyle: NamingStyle): String = literalName

    override def spelling(humanLanguage: HumanLanguage): String = literalName
  }

}
