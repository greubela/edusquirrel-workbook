package it.evadid.homepage.control.singletons

import it.evadid.core.datastructures.file.*
import it.evadid.core.datastructures.language.*
import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.core.datastructures.state.storage.AsyncDataCache
import it.evadid.homepage.control.model.{FullInfo, HomepageLoggerInfo}
import it.evadid.homepage.control.singletons.WorkbookContentStorage.*
import it.evadid.homepage.util.serializing.IoSerialization
import it.evadid.util.logging.Logger
import it.evadid.util.logging.derived.PrintToStdLogger
import it.evadid.workbook.model.elements.ImageElement
import org.scalajs.dom.URL
import todomove.datastructures.web.file.FileFactory

import scala.collection.mutable
import scala.concurrent.*
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.*

case class WorkbookContentStorage(contentStorageLogger: Logger, fileStore: AsyncDataCache[FileDescription, LoadedFile]) {

  private var lastFinishedCache: Option[LanguageMapTripleStore] = None
  private val loadedFiles: mutable.HashSet[LoadedFile] = mutable.HashSet()

  def getSyncIfLoaded(languageMapContentId: LanguageMapContentId): Option[LanguageMap[HumanLanguage]] = {
    lastFinishedCache.flatMap(_.getMap(languageMapContentId))
  }


  lazy val asStorage: AsyncDataCache[LanguageMapContentId, LanguageMap[HumanLanguage]] = new AsyncDataCache[LanguageMapContentId, LanguageMap[HumanLanguage]](contentStorageLogger) {

    private val maxTries: Int = 2

    protected def executeLoadingWithMaxTries(in: LanguageMapContentId, curTries: Int)(ec: ExecutionContext): Future[LanguageMap[HumanLanguage]] = {
      fileStore.synchronized {
        if (lastFinishedCache.nonEmpty && lastFinishedCache.get.contains(in)) {
          val res: LanguageMap[HumanLanguage] = lastFinishedCache.get.getMap(in).get
          Future.successful(res)
        } else if (curTries > maxTries) {
          val cacheBegin = lastFinishedCache.map(_.triples.take(20).map(_.contentId.fullId).mkString("[", ",", "]")).getOrElse("[empty]")
          Future.failed(new IllegalStateException(s"cannot load '${formatInputForLogging(in)}' (='${in.fullId}') even after $maxTries attempts! First 20 in cache: ${cacheBegin}"))
        } else {
          val res = Promise[LanguageMap[HumanLanguage]]()
          futureForDefaultsLoaded.onComplete(finished => res.completeWith(executeLoadingWithMaxTries(in, curTries + 1)(ec)))
          res.future
        }
      }
    }

    override protected def executeLoading(in: LanguageMapContentId)(ec: ExecutionContext): Future[LanguageMap[HumanLanguage]] =
      executeLoadingWithMaxTries(in, 1)(ec)

    override protected def formatInputForLogging(in: LanguageMapContentId): String = in.fullId

    override protected def formatOutputForLogging(out: LanguageMap[HumanLanguage]): String = out.toString
  }

  lazy val futureForDefaultsLoaded: Future[Unit] = fileStore.synchronized {
    val res = Promise[Unit]()
    withDirsLoaded(WorkbookContentStorage.languageMapDirs)
      //withFilesEnsured(WorkbookContentStorage.languageMapFiles.toSet)
      .onComplete {
        case Success(any) => res.success(())
        case Failure(err) => {
          contentStorageLogger.logExceptionWarn("futuresForDefaultsLoaded failed, there might be problems down the road", err)
          res.failure(err)
        }
      }
    res.future
  }

  def addTriples(triples: Set[LanguageMapEntry]): Unit = fileStore.synchronized {
    val existingTriples: Set[LanguageMapEntry] = lastFinishedCache.map(_.triples).getOrElse(Set.empty)
    val newStore = LanguageMapTripleStore(contentStorageLogger, existingTriples ++ triples)
    lastFinishedCache = Some(newStore)
    // ensure caches are working
    asStorage.reloadAll()
    triples.foreach(curTriple => asStorage.loadAsFuture(curTriple.contentId))
  }

  def addLoadedFile(loadedFile: LoadedFile): Unit = fileStore.synchronized {
    loadedFiles += loadedFile
    addTriples(triplesFromFile(contentStorageLogger, loadedFile))
  }

  def addFile(fileDescription: FileDescription): Unit = fileStore.synchronized {
    fileStore.loadAsFuture(fileDescription)(using ExecutionContext.global)
      .onComplete {
        case Success(loadedFile) => {
          addLoadedFile(loadedFile)
        }
        case Failure(err) => {
          contentStorageLogger.logExceptionWarn(s"failed to load file ${fileDescription.fullPath}, ignoring its content", err)
        }
      }
  }

  def withDirsLoaded(dirs: Set[FileDescription]): Future[LanguageMapTripleStore] = fileStore.synchronized {
    val res: Promise[LanguageMapTripleStore] = Promise[LanguageMapTripleStore]()
    WorkbookContentStorage.loadAllFilesInDirs(contentStorageLogger, fileStore, dirs).onComplete {
      case Success(loadedFiles) => {
        loadedFiles.foreach(addLoadedFile)
        res.success(lastFinishedCache.get)
      }
      case Failure(err) => res.failure(err)
    }
    res.future
  }

  def withFilesEnsured(logger: Logger, fileDescriptions: Set[FileDescription]): Future[LanguageMapTripleStore] = fileStore.synchronized {
    val mappedFiles = loadedFiles.map(_.description)
    val notYetLoaded = fileDescriptions.diff(mappedFiles)
    if (notYetLoaded.isEmpty && lastFinishedCache.nonEmpty) {
      Future.successful(lastFinishedCache.get)
    } else {
      val res: Promise[LanguageMapTripleStore] = Promise[LanguageMapTripleStore]()
      val triplesFut = WorkbookContentStorage.ensureLoaded(logger, fileStore, fileDescriptions)
      triplesFut.onComplete {
        case Success(triples) => {
          addTriples(triples.toSet)
          res.success(lastFinishedCache.get)
        }
        case Failure(err) => res.failure(err)
      }
      res.future
    }
  }


}


object WorkbookContentStorage {

  case class LanguageMapTripleStore(logger: Logger, triples: Set[LanguageMapEntry]) {
    private lazy val toLanguageMaps: Set[LanguageMapWithId] = triplesToLanguageMaps(logger, triples)

    def contains(contentId: LanguageMapContentId): Boolean = toLanguageMaps.exists(_.contentId == contentId)

    def getMap(contentId: LanguageMapContentId): Option[LanguageMap[HumanLanguage]] = toLanguageMaps.find(_.contentId == contentId).map(_.languageMap)

    def getContentIfPresent(contentId: LanguageMapContentId, language: HumanLanguage): Option[String] =
      toLanguageMaps.find(_.contentId == contentId).map(_.languageMap.getInLanguage(language))
  }

  case class LanguageMapWithId(contentId: LanguageMapContentId, languageMap: LanguageMap[HumanLanguage])

  sealed trait LanguageMapEntry {
    def contentId: LanguageMapContentId

    def value: String
  }

  case class MapEntryTripel(contentId: LanguageMapContentId,
                            language: HumanLanguage,
                            value: String) extends LanguageMapEntry

  case class UniversalMapEntry(contentId: LanguageMapContentId,
                               value: String) extends LanguageMapEntry

  private sealed trait LanguageMapFileKind

  private case class RegularLanguageMapFile(language: HumanLanguage) extends LanguageMapFileKind

  private case object UniversalLanguageMapFile extends LanguageMapFileKind

  private def ensureLoaded(logger: Logger, fileStore: AsyncDataCache[FileDescription, LoadedFile], ensureFiles: Set[FileDescription]): Future[List[LanguageMapEntry]] = {

    val resPromise = Promise[List[LanguageMapEntry]]()
    val filesFinished = mutable.ListBuffer[FileDescription]()
    val triplesLoaded = mutable.ListBuffer[LanguageMapEntry]()

    def onFileFailed(err: Throwable): Unit = {
      resPromise.failure(err)
    }

    def onFileLoaded(file: LoadedFile): Unit = resPromise.synchronized {
      filesFinished += file.description
      triplesLoaded.addAll(triplesFromFile(logger, file))
      if (filesFinished.size == ensureFiles.size) {
        resPromise.success(triplesLoaded.toList)
      }
    }

    ensureFiles.foreach(curFile => fileStore
      .loadAsFuture(curFile)(using ExecutionContext.global)
      .onComplete {
        case Success(loadedFile) => onFileLoaded(loadedFile)
        case Failure(err) => onFileFailed(err)
      })

    resPromise.future
  }

  private def triplesToLanguageMaps(logger: Logger, triples: Set[LanguageMapEntry]): Set[LanguageMapWithId] = {
    triples
      .groupBy(_.contentId)
      .map {
        case (mapId, entries) =>
          val baseLanguageMap: Map[HumanLanguage, String] = entries
            .collect { case MapEntryTripel(_, language, value) => language -> value }
            .toMap
          val baseEmpty: Boolean = baseLanguageMap.isEmpty
          val explicitLanguageMap: LanguageMap[HumanLanguage] = LanguageMap.mapBasedLanguageMap(baseLanguageMap)
          entries.collectFirst {
            case UniversalMapEntry(_, value) => LanguageMap.universalMap[HumanLanguage](value)
          }.match {
            case Some(universalMap) if baseEmpty => LanguageMapWithId(mapId, universalMap)
            case Some(universalMap) => LanguageMapWithId(mapId, explicitLanguageMap.withFallback(universalMap))
            case None if baseEmpty => {
              logger.logWarn(s"No content read for language map with id $mapId")
              LanguageMapWithId(mapId, LanguageMap.emptyMap())
            }
            case None => LanguageMapWithId(mapId, explicitLanguageMap)
          }
      }
      .toSet
  }

  private def languageMapFileKindFromFileDescription(file: FileDescription): Option[LanguageMapFileKind] = {
    val langSuffix: Option[String] = file.filenameWithoutExtension.split("-").lastOption.map(_.toLowerCase)
    langSuffix.flatMap {
      case "universal" => Some(UniversalLanguageMapFile)
      case suffix => WorkbookContentStorage.languageByFileSuffix.get(suffix).map(RegularLanguageMapFile.apply)
    }
  }

  private def languageMapEntry(contentId: LanguageMapContentId,
                               fileKind: LanguageMapFileKind,
                               value: String): LanguageMapEntry = fileKind match {
    case RegularLanguageMapFile(language) => MapEntryTripel(contentId, language, value)
    case UniversalLanguageMapFile => UniversalMapEntry(contentId, value)
  }

  private def triplesFromFile(logger: Logger, file: LoadedFile): Set[LanguageMapEntry] = {
    val languageOp = languageMapFileKindFromFileDescription(file.description)
    val languageMapIdOp = file.description.dirNames.lastOption
    logger.logInfo(s"Loading triples from file '${file.description.fullPath}, corresponding language map id: $languageMapIdOp, language: $languageOp")
    if (languageOp.isEmpty || languageMapIdOp.isEmpty) {
      Set.empty[LanguageMapEntry]
    }
    else {
      if (file.description.extensionOrEmpty == "json") {
        IoSerialization.parseJson(file.fileDataAsUtf8String).toList.map(tup => {
          languageMapEntry(LanguageMapContentId(languageMapIdOp.get.toLowerCase, tup._1.toLowerCase), languageOp.get, tup._2)
        }).toSet
      } else if (file.description.extensionOrEmpty == "csv") {
        IoSerialization.parseCsv(file.fileDataAsUtf8String).filter(_.size >= 2).map(curColumns => {
          languageMapEntry(LanguageMapContentId(languageMapIdOp.get.toLowerCase, curColumns.head.toLowerCase), languageOp.get, curColumns(1))
        }).toSet
      } else {
        Set.empty[LanguageMapEntry]
      }
    }
  }

  private lazy val languageMapDirs: Set[FileDescription] = Set(
    FileFactory.relativeToResourceFolder("/languageMaps/basic"),
    FileFactory.relativeToResourceFolder("/languageMaps/turtlestitch"),
    FileFactory.relativeToResourceFolder("/languageMaps/blockeditor"),
    FileFactory.relativeToResourceFolder("/languageMaps/embroideryworkbook"),
    FileFactory.relativeToResourceFolder("/languageMaps/testworkbook"),
    FileFactory.relativeToResourceFolder("/languageMaps/plantworkshop"),
    FileFactory.relativeToResourceFolder("/languageMaps/prompts"),
    FileFactory.relativeToResourceFolder("/languageMaps/compressionworkbook"),

  )

  def loadAllFilesInDirs(logger: Logger, fileStore: AsyncDataCache[FileDescription, LoadedFile], dirs: Set[FileDescription]): Future[Set[LoadedFile]] = {
    val allFiles = dirs
      .flatMap(curDir => languageMapFileSuffixes.map(curSuffix => curDir.fullPath + "/map-" + curSuffix + ".json"))
      .map(urlStr => FileFactory.fromUrl(URL(urlStr), CopyrightInfo.unknownCopyrightInfo))

    val allFutures: Future[Set[Try[LoadedFile]]] = Future.traverse(allFiles)(curFile => {
      fileStore.loadAsFuture(curFile, false).map(Success(_)).recover { case e => Failure(e) }
    })

    allFutures.onComplete {
      case Success(tryList) => {
        val successCount = tryList.count(_.isSuccess)
        val failedCount = tryList.count(_.isFailure)
        logger.logInfo(s"WorkbookContentStorage loaded language maps: $successCount loaded, $failedCount not found!")
      }
      case Failure(err) => {
        logger.logExceptionWarn("Could not load language maps, ignoring them", err)
      }
    }

    allFutures.map(_.collect { case Success(loadedFile) => loadedFile })
  }

  private lazy val languageMapFiles: List[FileDescription] = List(
    FileFactory.relativeToResourceFolder("/languageMaps/basic/map-en.json"),
    FileFactory.relativeToResourceFolder("/languageMaps/basic/map-de.json"),
    FileFactory.relativeToResourceFolder("/languageMaps/basic/map-ua.json"),
    FileFactory.relativeToResourceFolder("/languageMaps/basic/map-dk.json"),
    FileFactory.relativeToResourceFolder("/languageMaps/basic/map-tr.json"),
    FileFactory.relativeToResourceFolder("/languageMaps/basic/map-fr.json"),
    FileFactory.relativeToResourceFolder("/languageMaps/basic/map-es.json"),
    FileFactory.relativeToResourceFolder("/languageMaps/turtlestitch/map-en.json"),
    FileFactory.relativeToResourceFolder("/languageMaps/turtlestitch/map-de.json"),
    FileFactory.relativeToResourceFolder("/languageMaps/turtlestitch/map-ua.json"),
    FileFactory.relativeToResourceFolder("/languageMaps/turtlestitch/map-dk.json"),
    FileFactory.relativeToResourceFolder("/languageMaps/turtlestitch/map-tr.json"),
    FileFactory.relativeToResourceFolder("/languageMaps/turtlestitch/map-fr.json"),
    FileFactory.relativeToResourceFolder("/languageMaps/turtlestitch/map-es.json"),
    FileFactory.relativeToResourceFolder("/languageMaps/blockeditor/map-en.json"),
    FileFactory.relativeToResourceFolder("/languageMaps/blockeditor/map-de.json"),
    FileFactory.relativeToResourceFolder("/languageMaps/embroideryworkbook/map-en.json"),
    FileFactory.relativeToResourceFolder("/languageMaps/embroideryworkbook/map-de.json"),
    FileFactory.relativeToResourceFolder("/languageMaps/embroideryworkbook/map-ua.json"),
    FileFactory.relativeToResourceFolder("/languageMaps/embroideryworkbook/map-dk.json"),
    FileFactory.relativeToResourceFolder("/languageMaps/embroideryworkbook/map-tr.json"),
    FileFactory.relativeToResourceFolder("/languageMaps/embroideryworkbook/map-fr.json"),
    FileFactory.relativeToResourceFolder("/languageMaps/embroideryworkbook/map-es.json"),
    FileFactory.relativeToResourceFolder("/languageMaps/testworkbook/map-en.json"),
    FileFactory.relativeToResourceFolder("/languageMaps/testworkbook/map-de.json"),
    FileFactory.relativeToResourceFolder("/languageMaps/plantworkshop/map-en.json"),
    FileFactory.relativeToResourceFolder("/languageMaps/plantworkshop/map-de.json"),
  )

  private val languageByFileSuffix: Map[String, HumanLanguage] = Map(
    "en" -> AppLanguage.English,
    "de" -> AppLanguage.German,
    "fr" -> AppLanguage.French,
    "ua" -> AppLanguage.Ukrainian,
    "ru" -> AppLanguage.Russian,
    "tr" -> AppLanguage.Turkish,
    "dk" -> AppLanguage.Danish,
    "es" -> AppLanguage.Spanish
  )

  private val universalLanguageMapFileSuffix: String = "universal"
  private val languageMapFileSuffixes: Set[String] = languageByFileSuffix.keySet + universalLanguageMapFileSuffix

  def languageMapError(id: LanguageMapContentId, cause: Throwable): LanguageMap[HumanLanguage] = LanguageMap.mapBasedLanguageMap(
    Map(
      AppLanguage.German -> s"[Fehler beim Laden von id '${id.toString}: ${cause.getMessage}]",
      AppLanguage.English -> s"[Error while loading id '${id.toString}: ${cause.getMessage}]"
    )
  )

  def languageMapImageError(img: Option[ImageElement], cause: Throwable): LanguageMap[HumanLanguage] = LanguageMap.mapBasedLanguageMap(
    Map(
      AppLanguage.German -> s"[Fehler beim Laden von Bild '${img.toString}: ${cause.getMessage}]",
      AppLanguage.English -> s"[Error while loading id '${img.toString}: ${cause.getMessage}]"
    )
  )

  val languageMapLoadingMap: LanguageMap[HumanLanguage] = LanguageMap.mapBasedLanguageMap(
    Map(
      AppLanguage.German -> "[Sprachdaten werden geladen]",
      AppLanguage.English -> "[language data is loading]",
    )
  )

  private def languageMapNonExistentMap(id: String): LanguageMap[HumanLanguage] = LanguageMap.mapBasedLanguageMap(
    Map(
      AppLanguage.German -> s"[Keine Sprachdaten für ID: '${id}']",
      AppLanguage.English -> s"[No Language Data for ID: '${id}']",
    )
  )


  val dataLoadingMap: LanguageMap[HumanLanguage] = LanguageMap.mapBasedLanguageMap(
    Map(
      AppLanguage.German -> "[Daten werden geladen]",
      AppLanguage.English -> "[data is loading]",
    )
  )

  val imageLoadingMap: LanguageMap[HumanLanguage] = LanguageMap.mapBasedLanguageMap(Map(
    AppLanguage.German -> "[Bild wird geladen]",
    AppLanguage.English -> "[Image is loading]",
  ))

}