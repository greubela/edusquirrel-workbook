package it.evadid.core.datastructures.language.control

import it.evadid.core.datastructures.language.*
import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.core.datastructures.language.serialization.*
import it.evadid.core.datastructures.language.serialization.abstractions.*
import it.evadid.core.util.io.Serializer
import it.evadid.util.logging.Logger
import upickle.default
import upickle.default.*

case class LanguageMapStorage
(
  parsedTriples: ParsedTriples,
  loadedSources: Set[LanguageMapInputSource]
) {

  lazy val languageMaps: Map[LanguageMapContentId, LanguageMap[HumanLanguage]] = {
    val mapWithIds = parsedTriples.createMapsFromTriples()
    mapWithIds.map(lm => lm.contentId -> lm.languageMap).toMap
  }

  private def languageCoverage(maps: Map[LanguageMapContentId, LanguageMap[HumanLanguage]]): String = {
    val counts = maps.toList.map((id, map) => id -> map.availableLanguages.size)
    (counts.minByOption(_._2), counts.maxByOption(_._2)) match {
      case (Some((fewestId, fewestCount)), Some((mostId, mostCount))) =>
        s"$fewestId has the fewest entries ($fewestCount) and $mostId the most entries ($mostCount)"
      case _ => "no maps are present"
    }
  }

  def withLoadedTriples(logger: Logger, additionalSources: IterableOnce[LanguageMapInputSource], additionalTriples: ParsedTriples): LanguageMapStorage = {
    val unionTriples: ParsedTriples = parsedTriples.withOverrides(additionalTriples)
    val newStorage = LanguageMapStorage(unionTriples, loadedSources ++ additionalSources)
    val stats = languageCoverage(newStorage.languageMaps)
    logger.logInfo(s"Increased Language Map Storage from ${parsedTriples.size} to ${newStorage.parsedTriples.size} triples, (stats: ${stats})!")
    newStorage
  }
}

object LanguageMapStorage {

  lazy val empty = LanguageMapStorage(ParsedTriples(Set(), Set()), Set())

  def languageMapLoading(languageMapId: LanguageMapContentId): LanguageMap[HumanLanguage] = LanguageMap.mapBasedLanguageMap(Map(
    English -> s"[Language data loading: ${languageMapId.fullId}]",
    German -> s"[Sprachdaten werden geladen: ${languageMapId.fullId}]"
  )).withFallback(LanguageMap.universalMap(s"[${languageMapId.fullId}]"))

  /** Saved as its triples; the loaded sources are not kept. */
  given rw: ReadWriter[LanguageMapStorage] = ParsedTriples.rw.bimap[LanguageMapStorage](_.parsedTriples, LanguageMapStorage(_, Set()))


}