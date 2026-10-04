package it.evadid.core.datastructures.language.serialization.abstractions

import it.evadid.core.datastructures.language.AppLanguage.{HumanLanguage, SpecialLanguage}
import it.evadid.core.datastructures.language.serialization.abstractions.*
import it.evadid.core.datastructures.language.{LanguageMap, LanguageMapContentId}
import it.evadid.util.logging.Logger
import upickle.default.*

case class ParsedTriples(regularTriples: Set[LanguageMapEntry[HumanLanguage]], universalTriples: Set[LanguageMapEntry[SpecialLanguage]]) derives ReadWriter{
  def union(other: ParsedTriples) = ParsedTriples(regularTriples ++ other.regularTriples, universalTriples ++ other.universalTriples)

  lazy val size = regularTriples.size + universalTriples.size

  lazy override val toString: String = s"ParsedTriples($size triples: ${regularTriples.size} regular + ${universalTriples.size} universal)"

  def createMapsFromTriples(): Set[LanguageMapWithId] = {
    val resMap: Map[LanguageMapContentId, Set[LanguageMapEntry[HumanLanguage]]] = regularTriples.groupBy(_.contentId)
    val universal: Map[LanguageMapContentId, Set[LanguageMapEntry[SpecialLanguage]]] = universalTriples.groupBy(_.contentId)

    val resMaps: Set[LanguageMapWithId] = (resMap.keySet ++ universal.keySet).map((curKey: LanguageMapContentId) => {
      val regularMap: Map[HumanLanguage, String] = resMap.getOrElse(curKey, Set()).map(trip => trip.language -> trip.value).toMap
      val universalValue: Option[String] = universal.get(curKey).flatMap(_.headOption).map(_.value)
      val languageMap: LanguageMap[HumanLanguage] =
        if (regularMap.isEmpty && universalValue.isEmpty) LanguageMap.empty // this should be impossible because of key iteration -> no warning
        else if (regularMap.isEmpty) LanguageMap.universalMap(universalValue.getOrElse("[WorkbookContentControl::createLanguageMaps... this should never be visible]"))
        else if (universalValue.isEmpty) LanguageMap.mapBasedLanguageMap(regularMap)
        else LanguageMap.mapBasedLanguageMap(regularMap).withFallback(LanguageMap.universalMap(universalValue.get))
      LanguageMapWithId(curKey, languageMap)
    })

     resMaps
  }


}

object ParsedTriples {


  /*sealed trait LanguageMapFileKind

  private case class RegularLanguageMapFile(language: HumanLanguage) extends LanguageMapFileKind

  private case object UniversalLanguageMapFile extends LanguageMapFileKind*/

}