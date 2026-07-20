package it.evadid.homepage.control.singletons

import it.evadid.core.datastructures.file.*
import it.evadid.core.datastructures.language.*
import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.core.datastructures.language.serialization.abstractions.ParsedTriples.LanguageMapWithId
import it.evadid.core.datastructures.language.serialization.abstractions.{LanguageMapEntry, ParsedTriples}
import it.evadid.core.datastructures.language.serialization.{LanguageMapCollectionSource, LanguageMapInputSource}
import it.evadid.core.datastructures.storage.AsyncDataCache
import it.evadid.homepage.control.singletons.WorkbookLanguageStorage.*
import it.evadid.homepage.util.serializing.IoSerialization
import it.evadid.util.logging.Logger
import todomove.datastructures.web.file.FileFactory

import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}

case class WorkbookLanguageStorage(parsedTriples: ParsedTriples, languageMaps: Map[LanguageMapContentId, LanguageMap[HumanLanguage]], loadedSources: Set[LanguageMapInputSource]) {

  def withLoadedTriples(logger: Logger, additionalSources: IterableOnce[LanguageMapInputSource], additionalTriples: ParsedTriples): WorkbookLanguageStorage = {
    val unionTriples: ParsedTriples = parsedTriples.union(additionalTriples)
    val newMaps: Set[LanguageMapWithId] = additionalTriples.createMapsFromTriples(logger)
    val res = copy(parsedTriples = unionTriples, languageMaps = languageMaps ++ newMaps.map(lm => lm.contentId -> lm.languageMap).toMap, loadedSources = loadedSources ++ additionalSources)
    logger.logInfo(s"Language Map Loading Successfull. Now loaded:  ${res.languageMaps.size} maps (from ${languageMaps.size}), ${res.parsedTriples.size} triples (from ${parsedTriples.size}, ${res.loadedSources.size} files (from ${loadedSources.size})")
    res
  }

}

object WorkbookLanguageStorage {

  lazy val empty = WorkbookLanguageStorage(ParsedTriples(Set(), Set()), Map(), Set())

  /*
  REASONABLE DEFAULTS
   */

  def languageMapLoading(languageMapId: LanguageMapContentId): LanguageMap[HumanLanguage] = LanguageMap.mapBasedLanguageMap(Map(
    English -> s"[Language data loading: ${languageMapId.fullId}]",
    German -> s"[Sprachdaten werden geladen: ${languageMapId.fullId}]"
  )).withFallback(LanguageMap.universalMap(s"[${languageMapId.fullId}]"))

  /*
  PARSING
   */


/*
  private def languageMapFileKindFromFileDescription(file: FileDescription): Option[LanguageMapFileKind] = {
    val langSuffix: Option[String] = file.filenameWithoutExtension.split("-").lastOption.map(_.toLowerCase)
    langSuffix.flatMap {
      case "universal" => Some(UniversalLanguageMapFile)
      case suffix => languageByEvaFileSuffix.get(suffix).map(RegularLanguageMapFile.apply)
    }
  }
*/


  /*private def triplesFromFile(logger: Logger, file: LoadedFile): ParsedTriples = {
    val languageOp: Option[LanguageMapFileKind] = languageMapFileKindFromFileDescription(file.description)
    val languageMapIdOp: Option[String] = file.description.dirNames.lastOption
    if (languageOp.isEmpty || languageMapIdOp.isEmpty) {
      logger.logWarn(s"could not read map id ${languageMapIdOp.getOrElse("[none]")} and/or file kind (${languageOp.getOrElse("[none]")} from file: ${file.description.fullPath}, skipping file")
      ParsedTriples(Set(), Set())
    } else if (!List[String]("csv", "json").contains(file.description.extensionOrEmpty.toLowerCase)) {
      logger.logWarn(s"could not read file ${file.description.fullPath} because it has an unknown file extension ('${file.description.extensionOrEmpty}'), skipping file")
      ParsedTriples(Set(), Set())
    } else {
      val contentAsList: Set[List[String]] = parseContent(logger, file)
      val parsed: ParsedTriples = languageOp.get.match {
        case RegularLanguageMapFile(language: HumanLanguage) =>
          ParsedTriples(contentAsList.map(tup => MapEntryTripel(LanguageMapContentId(languageMapIdOp.get.toLowerCase, tup(0).toLowerCase), language, tup(1))), Set())
        case UniversalLanguageMapFile =>
          ParsedTriples(Set(), contentAsList.map(tup => UniversalMapEntry(LanguageMapContentId(languageMapIdOp.get.toLowerCase, tup(0).toLowerCase), tup(1))))
        case _ => ParsedTriples(Set(), Set())
      }
      //logger.logInfo(s"Read ${parsed.regularTriples.size + parsed.universalTriples.size} LanguageMapEntries from file ${file.description.fullPath} (${languageOp.get}/${languageMapIdOp.get})")
      parsed
    }
  }*/
/*
  def loadParsedTriples(logger: Logger, sourcesToLoad: Set[LanguageMapInputSource]): ParsedTriples = {
    val filesSet: Set[LoadedFile] = sourcesToLoad.iterator.toSet
    val triples: Set[ParsedTriples] = filesSet.map(triplesFromFile(logger, _))
    val allTriples: ParsedTriples = ParsedTriples(triples.flatMap(_.regularTriples), triples.flatMap(_.universalTriples))
    logger.logInfo(s"Read Parsed Triples from ${filesSet.size} files: ${allTriples.toString}")
    allTriples
  }
*/
  /*def loadParsedTriples(logger: Logger, fileStore: AsyncDataCache[FileDescription, LoadedFile], files: IterableOnce[FileDescription]): Future[ParsedTriples] = {
    given ExecutionContext = ExecutionContext.global

    val fut = fileStore.loadAllAsFuture(files, LocalDateTime.now()).map(_.values.collect { case Right(loadedFile) => loadedFile }.toSet)
    fut.map((filesLoaded: Set[LoadedFile]) => {
      logger.logInfo(Logger.formatPerformance("FileLoading", filesLoaded.size, files.size, "files loaded", "files potentiall available"))
      loadParsedTriples(logger, filesLoaded)
    })
  }*/


  /**
   * information about file location etc.
   */


  //snapFiles


  /*
  Helper classes for parser
   */


}