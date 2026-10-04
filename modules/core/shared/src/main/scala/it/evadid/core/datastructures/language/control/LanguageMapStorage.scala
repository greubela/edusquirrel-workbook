package it.evadid.core.datastructures.language.control

import it.evadid.core.datastructures.language.*
import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.core.datastructures.language.control.LanguageMapStorage.LanguageMapStorageSerializable
import it.evadid.core.datastructures.language.serialization.*
import it.evadid.core.datastructures.language.serialization.abstractions.*
import it.evadid.core.util.io.Serializer
import it.evadid.core.util.io.serializer.AutoSerializable
import it.evadid.core.util.io.serializer.AutoSerializable.{AutoSerializableMainType, AutoSerializableSubType}
import it.evadid.util.logging.Logger
import it.evadid.util.logging.derived.SyncLogger
import it.evadid.workbook.interaction.sync.destination.{SyncDestination, SyncDestinationRaw}
import upickle.default
import upickle.default.*

import scala.concurrent.Future

case class LanguageMapStorage
(
  parsedTriples: ParsedTriples,
  loadedSources: Set[LanguageMapInputSource]
) extends AutoSerializableMainType[LanguageMapStorage, LanguageMapStorageSerializable] {

  lazy val languageMaps: Map[LanguageMapContentId, LanguageMap[HumanLanguage]] = {
    val mapWithIds = parsedTriples.createMapsFromTriples()
    mapWithIds.map(lm => lm.contentId -> lm.languageMap).toMap
  }

  private def langMapStats(languageMaps: Map[LanguageMapContentId, LanguageMap[HumanLanguage]]): String = {
    val fewestLanguages = languageMaps.minByOption(_._2.availableLanguages.size)
    val mostLanguages = languageMaps.maxByOption(_._2.availableLanguages.size)
    if (fewestLanguages.nonEmpty && mostLanguages.nonEmpty) {
      val fewId = fewestLanguages.get._1
      val fewNum = fewestLanguages.get._2
      val mostId = mostLanguages.get._1
      val mostNum = mostLanguages.get._2
      s"${fewId} has the fewest entries (${fewNum}) and ${mostId} the most entries (${mostNum})"
    } else {
      "no maps are present"
    }

  }

  def withLoadedTriples(logger: Logger, additionalSources: IterableOnce[LanguageMapInputSource], additionalTriples: ParsedTriples): LanguageMapStorage = {
    val unionTriples: ParsedTriples = parsedTriples.union(additionalTriples)
    val newStorage = LanguageMapStorage(unionTriples, loadedSources ++ additionalSources)
    val stats = langMapStats(newStorage.languageMaps)
    logger.logInfo(s"Increased Language Map Storage from ${parsedTriples.size} to ${newStorage.languageMaps.size} triples, (stats: ${stats})!")
    newStorage
  }

  override lazy val rwSub: default.ReadWriter[LanguageMapStorageSerializable] = macroRW
  override lazy val toSerializableSubType: LanguageMapStorageSerializable = LanguageMapStorageSerializable(parsedTriples)
}

object LanguageMapStorage {

  case class LanguageMapStorageSerializable(parsedTriples: ParsedTriples) extends AutoSerializableSubType[LanguageMapStorage, LanguageMapStorageSerializable] {
    override lazy val toTypedMainType: LanguageMapStorage = LanguageMapStorage(parsedTriples, Set())
  }

  lazy val empty = LanguageMapStorage(ParsedTriples(Set(), Set()), Set())

  def languageMapLoading(languageMapId: LanguageMapContentId): LanguageMap[HumanLanguage] = LanguageMap.mapBasedLanguageMap(Map(
    English -> s"[Language data loading: ${languageMapId.fullId}]",
    German -> s"[Sprachdaten werden geladen: ${languageMapId.fullId}]"
  )).withFallback(LanguageMap.universalMap(s"[${languageMapId.fullId}]"))

  given rw: ReadWriter[LanguageMapStorage] = AutoSerializable.getReadWriter(using macroRW)

  val serializerMain: Serializer[LanguageMapStorage] = Serializer.fromUpickleJson(rw)
  

  
}