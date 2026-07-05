package it.evadid.homepage.control.singletons

import it.evadid.core.datastructures.file.*
import it.evadid.core.datastructures.language.*
import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.core.datastructures.state.storage.AsyncDataCache
import it.evadid.homepage.control.singletons.WorkbookLanguageStorage.*
import it.evadid.homepage.util.serializing.IoSerialization
import it.evadid.util.logging.Logger
import org.scalajs.dom.URL
import todomove.datastructures.web.file.FileFactory

import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}

case class WorkbookLanguageStorage(parsedTriples: ParsedTriples, languageMaps: Map[LanguageMapContentId, LanguageMap[HumanLanguage]], loadedFiles: Set[FileDescription]) {

  def withLoadedFiles(logger: Logger, newFiles: IterableOnce[LoadedFile]): WorkbookLanguageStorage = {
    val maybeAdd: Set[LoadedFile] = newFiles.iterator.toSet
    val filesToLoad: IterableOnce[LoadedFile] = maybeAdd.filter((curNewFile: LoadedFile) => !loadedFiles.contains(curNewFile.description))
    logger.logInfo(s"Adding ${filesToLoad.size} from the proposed ${maybeAdd.size} to WorkbookLanguageStorage (others already loaded)!")
    val triples: ParsedTriples = WorkbookLanguageStorage.loadParsedTriples(logger, filesToLoad)
    withLoaded(logger, filesToLoad.map(_.description), triples)
  }

  private def withLoaded(logger: Logger, additionalFiles: IterableOnce[FileDescription], additionalTriples: ParsedTriples): WorkbookLanguageStorage = {
    val unionTriples: ParsedTriples = parsedTriples.union(additionalTriples)
    val newMaps: Set[LanguageMapWithId] = WorkbookLanguageStorage.createMapsFromTriples(logger, unionTriples)
    val res = copy(parsedTriples = unionTriples, languageMaps = languageMaps ++ newMaps.map(lm => lm.contentId -> lm.languageMap).toMap, loadedFiles = loadedFiles ++ additionalFiles)
    logger.logInfo(s"Language Map Loading Successfull. Now loaded:  ${res.languageMaps.size} maps (from ${languageMaps.size}), ${res.parsedTriples.size} triples (from ${parsedTriples.size}, ${res.loadedFiles.size} files (from ${loadedFiles.size})")
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


  private def createMapsFromTriples(logger: Logger, allTriples: ParsedTriples): Set[LanguageMapWithId] = {
    val resMap: Map[LanguageMapContentId, Set[MapEntryTripel]] = allTriples.regularTriples.groupBy(_.contentId)
    val universal: Map[LanguageMapContentId, Set[UniversalMapEntry]] = allTriples.universalTriples.groupBy(_.contentId)

    val resMaps: Set[LanguageMapWithId] = (resMap.keySet ++ universal.keySet).map((curKey: LanguageMapContentId) => {
      val regularMap: Map[HumanLanguage, String] = resMap.getOrElse(curKey, Set()).map(trip => trip.language -> trip.value).toMap
      val universalValue: Option[String] = universal.get(curKey).flatMap(_.headOption).map(_.value)
      val languageMap: LanguageMap[HumanLanguage] =
        if (regularMap.isEmpty && universalValue.isEmpty) LanguageMap.emptyMap() // this should be impossible because of key iteration -> no warning
        else if (regularMap.isEmpty) LanguageMap.universalMap(universalValue.getOrElse("[WorkbookContentStorage::createLanguageMaps... this should never be visible]"))
        else if (universalValue.isEmpty) LanguageMap.mapBasedLanguageMap(regularMap)
        else LanguageMap.mapBasedLanguageMap(regularMap).withFallback(LanguageMap.universalMap(universalValue.get))
      LanguageMapWithId(curKey, languageMap)
    })

    logger.logInfo(s"Created ${resMaps.size} language maps for the ids: ${resMaps.map(_.contentId).mkString}. Fewest language entries: ${resMaps.minByOption(_.languageMap.availableLanguages.size).map(_.contentId)}")
    resMaps
  }

  private def languageMapFileKindFromFileDescription(file: FileDescription): Option[LanguageMapFileKind] = {
    val langSuffix: Option[String] = file.filenameWithoutExtension.split("-").lastOption.map(_.toLowerCase)
    langSuffix.flatMap {
      case "universal" => Some(UniversalLanguageMapFile)
      case suffix => languageByFileSuffix.get(suffix).map(RegularLanguageMapFile.apply)
    }
  }

  private def parseCsv(str: String): Set[List[String]] = IoSerialization.parseCsv(str).filter(_.size >= 2).toSet

  private def parseJson(str: String): Set[List[String]] = IoSerialization.parseJson(str).toList.map(tup => List(tup(0), tup(1))).toSet

  private def parseContent(content: String, extension: String): Set[List[String]] = {
    if (extension.toLowerCase == "json") parseJson(content)
    else if (extension.toLowerCase == "csv") parseCsv(content)
    else Set()
  }

  private def triplesFromFile(logger: Logger, file: LoadedFile): ParsedTriples = {
    val languageOp: Option[LanguageMapFileKind] = languageMapFileKindFromFileDescription(file.description)
    val languageMapIdOp: Option[String] = file.description.dirNames.lastOption
    if (languageOp.isEmpty || languageMapIdOp.isEmpty) {
      logger.logWarn(s"could not read map id ${languageMapIdOp.getOrElse("[none]")} and/or file kind (${languageOp.getOrElse("[none]")} from file: ${file.description.fullPath}, skipping file")
      ParsedTriples(Set(), Set())
    } else if (!List[String]("csv", "json").contains(file.description.extensionOrEmpty.toLowerCase)) {
      logger.logWarn(s"could not read file ${file.description.fullPath} because it has an unknown file extension ('${file.description.extensionOrEmpty}'), skipping file")
      ParsedTriples(Set(), Set())
    } else {
      val contentAsList: Set[List[String]] = parseContent(file.fileDataAsUtf8String, file.description.extensionOrEmpty.toLowerCase)
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
  }

  def loadParsedTriples(logger: Logger, filesLoaded: IterableOnce[LoadedFile]): ParsedTriples = {
    val filesSet: Set[LoadedFile] = filesLoaded.iterator.toSet
    val triples: Set[ParsedTriples] = filesSet.map(triplesFromFile(logger, _))
    val allTriples: ParsedTriples = ParsedTriples(triples.flatMap(_.regularTriples), triples.flatMap(_.universalTriples))
    logger.logInfo(s"Read Parsed Triples from ${filesSet.size} files: ${allTriples.toString}")
    allTriples
  }

  def loadParsedTriples(logger: Logger, fileStore: AsyncDataCache[FileDescription, LoadedFile], files: IterableOnce[FileDescription]): Future[ParsedTriples] = {
    given ExecutionContext = ExecutionContext.global

    val fut = fileStore.loadAllAsFuture(files, LocalDateTime.now()).map(_.values.collect { case Right(loadedFile) => loadedFile }.toSet)
    fut.map((filesLoaded: Set[LoadedFile]) => {
      logger.logInfo(Logger.formatPerformance("FileLoading", filesLoaded.size, files.size, "files loaded", "files potentiall available"))
      loadParsedTriples(logger, filesLoaded)
    })
  }


  /**
   * information about file location etc.
   */

  lazy val potentialLanguageMapFiles: Set[FileDescription] = {
    for {
      a <- languageMapDirs
      b <- languageByFileSuffix.keySet + "universal"
    } yield (a, b)
  }.toList.map(tup => s"${tup._1.fullPath}/map-${tup._2}.json").map(urlStr => FileFactory.fromUrl(URL(urlStr), CopyrightInfo.unknownCopyrightInfo)).toSet

  private lazy val languageMapDirs: Set[FileDescription] = Set(
    FileFactory.relativeToResourceFolder("/languageMaps/basic"),
    FileFactory.relativeToResourceFolder("/languageMaps/entitynames"),
    FileFactory.relativeToResourceFolder("/languageMaps/turtlestitch"),
    FileFactory.relativeToResourceFolder("/languageMaps/blockeditor"),
    FileFactory.relativeToResourceFolder("/languageMaps/embroideryworkbook"),
    FileFactory.relativeToResourceFolder("/languageMaps/testworkbook"),
    FileFactory.relativeToResourceFolder("/languageMaps/plantworkshop"),
    FileFactory.relativeToResourceFolder("/languageMaps/prompts"),
    FileFactory.relativeToResourceFolder("/languageMaps/compressionworkbook"),
  )
  private lazy val languageByFileSuffix: Map[String, HumanLanguage] = Map(
    "en" -> AppLanguage.English,
    "de" -> AppLanguage.German,
    "fr" -> AppLanguage.French,
    "ua" -> AppLanguage.Ukrainian,
    "ru" -> AppLanguage.Russian,
    "tr" -> AppLanguage.Turkish,
    "dk" -> AppLanguage.Danish,
    "es" -> AppLanguage.Spanish
  )

  /*
  Helper classes for parser
   */

  case class ParsedTriples(regularTriples: Set[MapEntryTripel], universalTriples: Set[UniversalMapEntry]) {
    def union(other: ParsedTriples) = ParsedTriples(regularTriples ++ other.regularTriples, universalTriples ++ other.universalTriples)

    lazy val size = regularTriples.size + universalTriples.size

    lazy override val toString: String = s"ParsedTriples($size triples: ${regularTriples.size} regular + ${universalTriples.size} universal)"
  }

  case class MapEntryTripel(contentId: LanguageMapContentId, language: HumanLanguage, value: String) extends LanguageMapEntry {
    override val kind: LanguageMapFileKind = RegularLanguageMapFile(language)
  }


  case class UniversalMapEntry(contentId: LanguageMapContentId, value: String) extends LanguageMapEntry {
    override val kind: LanguageMapFileKind = UniversalLanguageMapFile
  }


  sealed trait LanguageMapEntry {
    def contentId: LanguageMapContentId

    def value: String

    def kind: LanguageMapFileKind
  }

  sealed trait LanguageMapFileKind

  private case class RegularLanguageMapFile(language: HumanLanguage) extends LanguageMapFileKind

  private case object UniversalLanguageMapFile extends LanguageMapFileKind

  case class LanguageMapWithId(contentId: LanguageMapContentId, languageMap: LanguageMap[HumanLanguage])
}