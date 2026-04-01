package workbook.singletons

import datastructures.core.language.{AppLanguage, HumanLanguage, LanguageMap}
import datastructures.web.file.{FileDescription, LoadedFile}
import datastructures.web.storage.AsyncDataCache
import datastructures.web.storage.AsyncDataCache.*
import fs2.data.csv.lowlevel
import fs2.{Fallible, Stream}
import util.serializing.IoSerialization

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}
import scala.scalajs.js
import scala.scalajs.js.{Dictionary, JSON}

object WorkbookLanguageInfo {

  val languageMapFiles: List[FileDescription] = List(
    FileDescription.relativeToResourceFolder("/languageMaps/basic-en.json"),
    FileDescription.relativeToResourceFolder("/languageMaps/basic-de.json"),
    FileDescription.relativeToResourceFolder("/languageMaps/TurtleStitch-en.json"),
    FileDescription.relativeToResourceFolder("/languageMaps/TurtleStitch-de.json"),
    FileDescription.relativeToResourceFolder("/languageMaps/BlockEditor-en.json"),
    FileDescription.relativeToResourceFolder("/languageMaps/BlockEditor-de.json"),
    FileDescription.relativeToResourceFolder("/languageMaps/EmbroideryWorkbook-en.json"),
    FileDescription.relativeToResourceFolder("/languageMaps/EmbroideryWorkbook-de.json"),
    FileDescription.relativeToResourceFolder("/languageMaps/TestWorkbook-en.json"),
    FileDescription.relativeToResourceFolder("/languageMaps/TestWorkbook-de.json"),
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

    private val languageTriplesStorage: LanguageMapTriplesStorage = LanguageMapTriplesStorage(fileDataStorage)

    override protected def executeLoading(id: String)(ec: ExecutionContext): Future[LanguageMap[HumanLanguage]] = {

      val allTriples: Future[List[List[MapEntryTripel]]] = Future.traverse(languageMapFiles)(file => {
        languageTriplesStorage.loadAsFuture(file, false)(ec)
      })

      allTriples.map(_.flatten)(ec).map(triples => {
        val languageMaps: Set[(String, LanguageMap[HumanLanguage])] = triplesToLanguageMaps(triples)
        languageMaps.find(_._1 == id).map(_._2).getOrElse(languageMapNonExistentMap(id))
      })(ec)
    }

    override protected def defaultValueWhileLoading(in: String): Option[LanguageMap[HumanLanguage]] = Some(languageMapLoadingMap)

    override protected def formatInputForLogging(in: String): String = in

    override protected def formatOutputForLogging(out: LanguageMap[HumanLanguage]): String = out.toString

  }

  private case class LanguageMapTriplesStorage(fileDataStorage: AsyncDataCache[FileDescription, LoadedFile]) extends AsyncDataCache[FileDescription, List[MapEntryTripel]]("tripleStorage", false) {

    override protected def executeLoading(file: FileDescription)(ec: ExecutionContext): Future[List[MapEntryTripel]] = {
      case class FullCsvFileInfo(fileDescription: LoadedFile, csvData: List[List[String]], fileLanguageOp: Option[HumanLanguage], mapGroupIdOp: Option[String])
      val futFile: Future[LoadedFile] = fileDataStorage.loadAsFuture(file)(ec)
      futFile.map(loadedFile => triplesFromFile(loadedFile))(ec)
    }

    override protected def defaultValueWhileLoading(in: FileDescription): Option[List[MapEntryTripel]] = None

    override protected def formatInputForLogging(in: FileDescription): String = in.toString

    override protected def formatOutputForLogging(out: List[MapEntryTripel]): String = out.toString
  }


  private case class LanguageMapWithId(id: String, languageMap: LanguageMap[HumanLanguage])

  private case class MapEntryTripel(mapId: String, language: HumanLanguage, value: String)


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
    val langSuffix: Option[String] = file.nameWithoutExtension.split("-").lastOption.map(_.toLowerCase)
    langSuffix.map(languageByFileSuffix)
  }

  private def mapGroupId(file: FileDescription): Option[String] = {
    val parts: Array[String] = file.nameWithoutExtension.split("-")
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
