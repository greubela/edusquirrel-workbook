package it.evadid.core.datastructures.language.control

import it.evadid.core.datastructures.language.*
import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.core.datastructures.language.serialization.LanguageMapInputSource
import it.evadid.core.datastructures.language.serialization.abstractions.ParsedTriples
import it.evadid.core.datastructures.language.serialization.abstractions.ParsedTriples.LanguageMapWithId
import it.evadid.util.logging.Logger

case class LanguageMapStorage(parsedTriples: ParsedTriples, languageMaps: Map[LanguageMapContentId, LanguageMap[HumanLanguage]], loadedSources: Set[LanguageMapInputSource]) {

  def withLoadedTriples(logger: Logger, additionalSources: IterableOnce[LanguageMapInputSource], additionalTriples: ParsedTriples): LanguageMapStorage = {
    val unionTriples: ParsedTriples = parsedTriples.union(additionalTriples)
    val newMaps: Set[LanguageMapWithId] = additionalTriples.createMapsFromTriples(logger)
    val res = copy(parsedTriples = unionTriples, languageMaps = languageMaps ++ newMaps.map(lm => lm.contentId -> lm.languageMap).toMap, loadedSources = loadedSources ++ additionalSources)
    logger.logInfo(s"Language Map Loading Successfull. Now loaded:  ${res.languageMaps.size} maps (from ${languageMaps.size}), ${res.parsedTriples.size} triples (from ${parsedTriples.size}, ${res.loadedSources.size} files (from ${loadedSources.size})")
    res
  }

}

object LanguageMapStorage {

  lazy val empty = LanguageMapStorage(ParsedTriples(Set(), Set()), Map(), Set())

  def languageMapLoading(languageMapId: LanguageMapContentId): LanguageMap[HumanLanguage] = LanguageMap.mapBasedLanguageMap(Map(
    English -> s"[Language data loading: ${languageMapId.fullId}]",
    German -> s"[Sprachdaten werden geladen: ${languageMapId.fullId}]"
  )).withFallback(LanguageMap.universalMap(s"[${languageMapId.fullId}]"))

}