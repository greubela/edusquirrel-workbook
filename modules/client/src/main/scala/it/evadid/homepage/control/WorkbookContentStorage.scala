package it.evadid.homepage.control

import it.evadid.core.datastructures.file.*
import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.core.datastructures.language.{AppLanguage, LanguageMap, LanguageMapContentId}
import it.evadid.homepage.control.WorkbookContentStorage.{LanguageMapTripleStore, MapEntryTripel, triplesFromFile}
import it.evadid.homepage.util.serializing.IoSerialization
import todomove.datastructures.web.file.FileFactory
import todomove.datastructures.web.storage.AsyncDataCache

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.*

case class WorkbookContentStorage(fileStore: AsyncDataCache[FileDescription, LoadedFile]) {

  private var lastFinishedCache: Option[LanguageMapTripleStore] = None
  private val loadedFiles: mutable.HashSet[LoadedFile] = mutable.HashSet()

  lazy val asStorage: AsyncDataCache[LanguageMapContentId, LanguageMap[HumanLanguage]] = new AsyncDataCache[LanguageMapContentId, LanguageMap[HumanLanguage]]("contentIdCache", false) {

    protected def executeLoadingWithMaxTries(in: LanguageMapContentId, curTries: Int)(ec: ExecutionContext): Future[LanguageMap[HumanLanguage]] = {
      fileStore.synchronized {
        if (curTries > 3) {
          Future.failed(new IllegalStateException("cannot load '" + formatInputForLogging(in) + "' even after three attempts!"))
        }
        else if (lastFinishedCache.nonEmpty && lastFinishedCache.get.contains(in)) {
          val res: LanguageMap[HumanLanguage] = lastFinishedCache.get.getMap(in).get
          Future.successful(res)
        } else {
          val res = Promise[LanguageMap[HumanLanguage]]()
          futureForDefaultsLoaded()
            .onComplete(finished => res.completeWith(executeLoadingWithMaxTries(in, curTries + 1)(ec)))
          res.future
        }
      }
    }

    override protected def executeLoading(in: LanguageMapContentId)(ec: ExecutionContext): Future[LanguageMap[HumanLanguage]] =
      executeLoadingWithMaxTries(in, 0)(ec)


    override protected def defaultValueWhileLoading(in: LanguageMapContentId): Option[LanguageMap[HumanLanguage]] = None

    override protected def formatInputForLogging(in: LanguageMapContentId): String = in.toString

    override protected def formatOutputForLogging(out: LanguageMap[HumanLanguage]): String = out.toString
  }

  def futureForDefaultsLoaded(): Future[Unit] = fileStore.synchronized {
    val res = Promise[Unit]()
    withFilesEnsured(WorkbookContentStorage.languageMapFiles.toSet).onComplete {
      case Success(any) => res.success(())
      case Failure(err) => {
        println("[ERROR] WorkbookContentStorage: " + err.getMessage)
        res.failure(err)
      }
    }
    res.future
  }

  def addTriples(triples: Set[MapEntryTripel]): Unit = fileStore.synchronized {
    val existingTriples: Set[MapEntryTripel] = lastFinishedCache.map(_.triples).getOrElse(Set.empty)
    val newStore = LanguageMapTripleStore(existingTriples ++ triples)
    lastFinishedCache = Some(newStore)
  }

  def addFile(fileDescription: FileDescription): Unit = fileStore.synchronized {
    fileStore.loadAsFuture(fileDescription)(using ExecutionContext.global)
      .onComplete {
        case Success(loadedFile) => {
          loadedFiles += loadedFile
          addTriples(triplesFromFile(loadedFile))
        }
        case Failure(err) =>
      }
  }

  def withFilesEnsured(fileDescriptions: Set[FileDescription]): Future[LanguageMapTripleStore] = fileStore.synchronized {
    val mappedFiles = loadedFiles.map(_.description)
    val notYetLoaded = fileDescriptions.diff(mappedFiles)
    if (notYetLoaded.isEmpty && lastFinishedCache.nonEmpty) {
      Future.successful(lastFinishedCache.get)
    } else {
      val res: Promise[LanguageMapTripleStore] = Promise[LanguageMapTripleStore]()
      val triplesFut = WorkbookContentStorage.ensureLoaded(fileStore, fileDescriptions)
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

  case class LanguageMapTripleStore(triples: Set[MapEntryTripel]) {
    private lazy val toLanguageMaps: Set[LanguageMapWithId] = triplesToLanguageMaps(triples)

    def contains(contentId: LanguageMapContentId): Boolean = toLanguageMaps.exists(_.contentId == contentId)

    def getMap(contentId: LanguageMapContentId): Option[LanguageMap[HumanLanguage]] = toLanguageMaps.find(_.contentId == contentId).map(_.languageMap)

    def getContentIfPresent(contentId: LanguageMapContentId, language: HumanLanguage): Option[String] =
      toLanguageMaps.find(_.contentId == contentId).map(_.languageMap.getInLanguage(language))
  }


  case class LanguageMapWithId(contentId: LanguageMapContentId, languageMap: LanguageMap[HumanLanguage])

  case class MapEntryTripel(contentId: LanguageMapContentId, language: HumanLanguage, value: String)

  private def ensureLoaded(fileStore: AsyncDataCache[FileDescription, LoadedFile], ensureFiles: Set[FileDescription]): Future[List[MapEntryTripel]] = {

    val resPromise = Promise[List[MapEntryTripel]]()
    val filesFinished = mutable.ListBuffer[FileDescription]()
    val triplesLoaded = mutable.ListBuffer[MapEntryTripel]()

    def onFileFailed(err: Throwable): Unit = {
      resPromise.failure(err)
    }

    def onFileLoaded(file: LoadedFile): Unit = resPromise.synchronized {
      filesFinished += file.description
      triplesLoaded.addAll(triplesFromFile(file))
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

  private def triplesToLanguageMaps(triples: Set[MapEntryTripel]): Set[LanguageMapWithId] = {
    triples
      .groupBy(_.contentId)
      .map { case (mapId, entries) =>
        mapId -> LanguageMap.mapBasedLanguageMap(entries.map(entry => entry.language -> entry.value).toMap)
      }
      .map(tup => LanguageMapWithId(tup._1, tup._2))
      .toSet
  }

  private def mapGroupId(file: FileDescription): Option[String] = {
    val parts: Array[String] = file.filenameWithoutExtension.split("-")
    if (parts.length < 2) None else Some(parts.dropRight(1).mkString("-"))
  }


  private def languageFromFileDescription(file: FileDescription): Option[HumanLanguage] = {
    val langSuffix: Option[String] = file.filenameWithoutExtension.split("-").lastOption.map(_.toLowerCase)
    langSuffix.map(WorkbookContentStorage.languageByFileSuffix)
  }

  private def triplesFromFile(file: LoadedFile): Set[MapEntryTripel] = {
    val languageOp = languageFromFileDescription(file.description)
    val mapGroupIdOp = mapGroupId(file.description)
    if (languageOp.isEmpty || mapGroupIdOp.isEmpty) {
      Set.empty[MapEntryTripel]
    }
    else if (file.description.extension == "json") {
      IoSerialization.parseJson(file.fileDataAsUtf8String).toList.map(tup => {
        MapEntryTripel(LanguageMapContentId(mapGroupIdOp.get, tup._1), languageOp.get, tup._2)
      }).toSet
    } else if (file.description.extension == "csv") {
      IoSerialization.parseCsv(file.fileDataAsUtf8String).filter(_.size >= 2).map(curColumns => {
        MapEntryTripel(LanguageMapContentId(mapGroupIdOp.get, curColumns.head), languageOp.get, curColumns(1))
      }).toSet
    } else {
      Set.empty[MapEntryTripel]
    }
  }


  val languageMapFiles: List[FileDescription] = List(
    FileFactory.relativeToResourceFolder("/languageMaps/basic-en.json"),
    FileFactory.relativeToResourceFolder("/languageMaps/basic-de.json"),
    FileFactory.relativeToResourceFolder("/languageMaps/basic-ua.json"),
    FileFactory.relativeToResourceFolder("/languageMaps/basic-dk.json"),
    FileFactory.relativeToResourceFolder("/languageMaps/basic-tr.json"),
    FileFactory.relativeToResourceFolder("/languageMaps/basic-fr.json"),
    FileFactory.relativeToResourceFolder("/languageMaps/basic-es.json"),
    FileFactory.relativeToResourceFolder("/languageMaps/TurtleStitch-en.json"),
    FileFactory.relativeToResourceFolder("/languageMaps/TurtleStitch-de.json"),
    FileFactory.relativeToResourceFolder("/languageMaps/TurtleStitch-ua.json"),
    FileFactory.relativeToResourceFolder("/languageMaps/TurtleStitch-dk.json"),
    FileFactory.relativeToResourceFolder("/languageMaps/TurtleStitch-tr.json"),
    FileFactory.relativeToResourceFolder("/languageMaps/TurtleStitch-fr.json"),
    FileFactory.relativeToResourceFolder("/languageMaps/TurtleStitch-es.json"),
    FileFactory.relativeToResourceFolder("/languageMaps/BlockEditor-en.json"),
    FileFactory.relativeToResourceFolder("/languageMaps/BlockEditor-de.json"),
    FileFactory.relativeToResourceFolder("/languageMaps/EmbroideryWorkbook-en.json"),
    FileFactory.relativeToResourceFolder("/languageMaps/EmbroideryWorkbook-de.json"),
    FileFactory.relativeToResourceFolder("/languageMaps/EmbroideryWorkbook-ua.json"),
    FileFactory.relativeToResourceFolder("/languageMaps/EmbroideryWorkbook-dk.json"),
    FileFactory.relativeToResourceFolder("/languageMaps/EmbroideryWorkbook-tr.json"),
    FileFactory.relativeToResourceFolder("/languageMaps/EmbroideryWorkbook-fr.json"),
    FileFactory.relativeToResourceFolder("/languageMaps/EmbroideryWorkbook-es.json"),
    FileFactory.relativeToResourceFolder("/languageMaps/TestWorkbook-en.json"),
    FileFactory.relativeToResourceFolder("/languageMaps/TestWorkbook-de.json"),
    FileFactory.relativeToResourceFolder("/languageMaps/PlantWorkshop-en.json"),
    FileFactory.relativeToResourceFolder("/languageMaps/PlantWorkshop-de.json"),
  )

  private val languageByFileSuffix: Map[String, HumanLanguage] = Map(
    "en" -> AppLanguage.English,
    "de" -> AppLanguage.German,
    "fr" -> AppLanguage.French,
    "ua" -> AppLanguage.Ukrainian,
    "uk" -> AppLanguage.Ukrainian,
    "ru" -> AppLanguage.Russian,
    "tr" -> AppLanguage.Turkish,
    "dk" -> AppLanguage.Danish,
    "es" -> AppLanguage.Spanish
  )

  // Impl :
  /*
    case class LabelLanguageMapStorage(fileDataStorage: AsyncDataCache[FileDescription, LoadedFile]) extends AsyncDataCache[String, LanguageMap[HumanLanguage]]("languageMap", false) {

      val languageTriplesStorage: LanguageMapTriplesStorage = LanguageMapTriplesStorage(fileDataStorage)
      private var languageFilesToLoad: List[FileDescription] = WorkbookLanguageInfo.languageMapFiles

      def addLanguageFile(fileDescription: FileDescription): Unit = {
        if (!languageFilesToLoad.contains(fileDescription)) {
          languageFilesToLoad = languageFilesToLoad ++ List(fileDescription)
        }
      }

      def addLanguageFiles(fileDescriptions: List[FileDescription]): Unit = {
        fileDescriptions.foreach(addLanguageFile)
      }

      def allLanguageFiles: List[FileDescription] = languageFilesToLoad

      override protected def executeLoading(id: String)(ec: ExecutionContext): Future[LanguageMap[HumanLanguage]] = {

        val allTriples: Future[List[List[MapEntryTripel]]] = Future.traverse(allLanguageFiles)(file => {
          languageTriplesStorage.loadAsFuture(file, false)(using ec)
        })


      }

      override protected def defaultValueWhileLoading(in: String): Option[LanguageMap[HumanLanguage]] = Some(languageMapLoadingMap)

      override protected def formatInputForLogging(in: String): String = in

      override protected def formatOutputForLogging(out: LanguageMap[HumanLanguage]): String = out.toString

    }

    case class LanguageMapTriplesStorage(fileDataStorage: AsyncDataCache[FileDescription, LoadedFile]) extends AsyncDataCache[FileDescription, List[MapEntryTripel]]("tripleStorage", false) {

      override protected def executeLoading(file: FileDescription)(ec: ExecutionContext): Future[List[MapEntryTripel]] = {
        case class FullCsvFileInfo(fileDescription: LoadedFile, csvData: List[List[String]], fileLanguageOp: Option[HumanLanguage], mapGroupIdOp: Option[String])
        val futFile: Future[LoadedFile] = fileDataStorage.loadAsFuture(file)(using ec)
        futFile.map(loadedFile => triplesFromFile(loadedFile))(using ec)
      }

      override protected def defaultValueWhileLoading(in: FileDescription): Option[List[MapEntryTripel]] = None

      override protected def formatInputForLogging(in: FileDescription): String = in.toString

      override protected def formatOutputForLogging(out: List[MapEntryTripel]): String = out.toString
    }*/


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
