package it.evadid.homepage.control

import it.evadid.core.datastructures.file.*
import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.core.datastructures.language.{AppLanguage, LanguageMap}
import it.evadid.homepage.util.serializing.IoSerialization
import todomove.datastructures.web.file.FileFactory
import todomove.datastructures.web.storage.AsyncDataCache

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

object WorkbookLanguageInfo {

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

      allTriples.map(_.flatten)(using ec).map(triples => {
        val languageMaps: Set[(String, LanguageMap[HumanLanguage])] = triplesToLanguageMaps(triples)
        languageMaps.find(_._1 == id).map(_._2).getOrElse(languageMapNonExistentMap(id))
      })(using ec)
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
  }


  private case class LanguageMapWithId(id: String, languageMap: LanguageMap[HumanLanguage])

  case class MapEntryTripel(mapId: String, language: HumanLanguage, value: String)


  private def triplesFromFile(file: LoadedFile): List[MapEntryTripel] = {
    val languageOp = languageFromFileDescription(file.description)
    val mapGroupIdOp = mapGroupId(file.description)
    if (languageOp.isEmpty || mapGroupIdOp.isEmpty) {
      List.empty[MapEntryTripel]
    }
    else if (file.description.extension == "json") {
      IoSerialization.parseJson(file.fileDataAsUtf8String).toList.map(tup => {
        MapEntryTripel(mapGroupIdOp.get + "/" + tup._1, languageOp.get, tup._2)
      })
    } else if (file.description.extension == "csv") {
      IoSerialization.parseCsv(file.fileDataAsUtf8String).filter(_.size >= 2).map(curColumns => {
        MapEntryTripel(s"${mapGroupIdOp.get}/${curColumns.head}", languageOp.get, curColumns(1))
      })
    } else {
      List.empty[MapEntryTripel]
    }
  }

  private def languageFromFileDescription(file: FileDescription): Option[HumanLanguage] = {
    val langSuffix: Option[String] = file.filenameWithoutExtension.split("-").lastOption.map(_.toLowerCase)
    langSuffix.map(languageByFileSuffix)
  }

  private def mapGroupId(file: FileDescription): Option[String] = {
    val parts: Array[String] = file.filenameWithoutExtension.split("-")
    if (parts.length < 2) None else Some(parts.dropRight(1).mkString("-"))
  }

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

  private def triplesToLanguageMaps(triples: List[MapEntryTripel]): Set[(String, LanguageMap[HumanLanguage])] = {
    triples
      .groupBy(_.mapId)
      .map { case (mapId, entries) =>
        mapId -> LanguageMap.mapBasedLanguageMap(entries.map(entry => entry.language -> entry.value).toMap)
      }
      .toSet
  }

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
