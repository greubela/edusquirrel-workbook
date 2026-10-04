package it.evadid.core.datastructures.language.serialization.abstractions

import it.evadid.core.datastructures.language.AppLanguage.{HumanLanguage, SpecialLanguage}
import it.evadid.core.datastructures.language.serialization.abstractions.*
import it.evadid.core.datastructures.language.serialization.abstractions.LanguageMapEntry.LanguageTripel
import it.evadid.core.datastructures.language.serialization.abstractions.ParsedTriples.ParsedTriplesSerialized
import it.evadid.core.datastructures.language.{AppLanguage, LanguageMap, LanguageMapContentId}
import it.evadid.core.util.io.Serializer
import it.evadid.core.util.io.serializer.AutoSerializable.{AutoSerializableMainType, AutoSerializableSubType}
import it.evadid.core.util.io.serializer.{AutoSerializable, ConstructorLikeSerializer}
import it.evadid.core.util.io.serializer.ConstructorLikeSerializer.VariableDisplayConfig
import it.evadid.distribution.command.SerializedException
import upickle.default
import upickle.default.*

import scala.util.Try


object ParsedTriples {

  private given ReadWriter[LanguageTripel] = LanguageMapEntry.tripSerializer.uPickleReadWrite


  private val subAuto: Writer[ParsedTriplesSerialized] = macroW

  private given ReadWriter[Seq[LanguageTripel]] = LanguageMapEntry.tripSerializer.safeSeqReadWriter

  private given sub: ReadWriter[ParsedTriplesSerialized] = macroRW

  val serializer: Serializer[ParsedTriples] = AutoSerializable.getSerializer(using sub)

  given ReadWriter[ParsedTriples] = serializer.uPickleReadWrite

  case class ParsedTriplesSerialized(
                                      contentIds: Seq[LanguageMapContentId],
                                      regularLanguages: Seq[AppLanguage],
                                      specialLanguages: Seq[AppLanguage],
                                      regularTriples: Seq[LanguageTripel],
                                      specialTriples: Seq[LanguageTripel]
                                    ) extends AutoSerializableSubType[ParsedTriples, ParsedTriplesSerialized] {
    lazy val toTypedMainType: ParsedTriples = try {

      val allRegular = regularTriples.flatMap(trip => Try {
        trip.resolveRegular(contentIds, regularLanguages.map(_.asInstanceOf[HumanLanguage]))
      }.toOption).toSet
      val allSpecial = specialTriples.flatMap(trip => Try {
        trip.resolveSpecial(contentIds, specialLanguages.map(_.asInstanceOf[SpecialLanguage]))
      }.toOption).toSet
      ParsedTriples(allRegular, allSpecial)
    } catch case (err: Throwable) => {
      err.printStackTrace()
      throw SerializedException(s"ParsedTriplesSerialized::toTypedMainType had an exception: ${err.getMessage}", err)
    }
  }

}

case class ParsedTriples(regularTriples: Set[LanguageMapEntry[HumanLanguage]], universalTriples: Set[LanguageMapEntry[SpecialLanguage]]) extends AutoSerializableMainType[ParsedTriples, ParsedTriplesSerialized] {
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


  override lazy val rwSub: default.ReadWriter[ParsedTriplesSerialized] = ParsedTriples.sub
  override lazy val toSerializableSubType: ParsedTriplesSerialized = {
    val regLanguages = regularTriples.map(_.language).toSet.toList
    val uniLanguages = universalTriples.map(_.language).toSet.toList
    val ids = (regularTriples.map(_.contentId) ++ universalTriples.map(_.contentId)).toSet.toList
    ParsedTriplesSerialized(
      ids, regLanguages, uniLanguages, regularTriples.map(_.serializeWith(ids, regLanguages)).toList, universalTriples.map(_.serializeWith(ids, uniLanguages)).toList
    )

  }
}
