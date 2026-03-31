package contentmanagement.storage

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import contentmanagement.model.file.{FileDescription, LoadedFile}
import contentmanagement.model.language.{AppLanguage, HumanLanguage, LanguageMap}
import contentmanagement.storage.LanguageMapTriplesStorage.*
import fs2.data.csv.*
import fs2.{Fallible, Stream}

import scala.concurrent.{ExecutionContext, Future}
import scala.scalajs.js
import scala.scalajs.js.{Dictionary, JSON}

case class LanguageMapTriplesStorage(val fileDataStorage: DataStorage[FileDescription, LoadedFile]) extends DataStorage[FileDescription, List[MapEntryTripel]]("tripleStorage", false) {


  override protected def executeLoading(file: FileDescription)(ec: ExecutionContext): Future[List[MapEntryTripel]] = {
    case class FullCsvFileInfo(fileDescription: LoadedFile, csvData: List[List[String]], fileLanguageOp: Option[HumanLanguage], mapGroupIdOp: Option[String])
    val futFile: Future[LoadedFile] = fileDataStorage.loadAsFuture(file)(ec)
    futFile.map(loadedFile => triplesFromFile(loadedFile))(ec)
  }

  override protected def defaultValueWhileLoading(in: FileDescription): Option[List[MapEntryTripel]] = None

  override protected def formatInputForLogging(in: FileDescription): String = in.toString

  override protected def formatOutputForLogging(out: List[MapEntryTripel]): String = out.toString
}

object LanguageMapTriplesStorage {

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

  case class LanguageMapWithId(id: String, languageMap: LanguageMap[HumanLanguage])

  case class MapEntryTripel(mapId: String, language: HumanLanguage, value: String)

  private def parseJson(content: String): Map[String, String] = {
    val parsed: Dictionary[String] = JSON.parse(content).asInstanceOf[js.Dictionary[String]]
    parsed.toMap
  }

  private def triplesFromFile(file: LoadedFile): List[MapEntryTripel] = {
    val languageOp = languageFromFileDescription(file.description)
    val mapGroupIdOp = mapGroupId(file.description)
    if (languageOp.isEmpty || mapGroupIdOp.isEmpty) {
      List.empty[MapEntryTripel]
    }
    else if (file.description.extension == "json") {
      parseJson(file.fileDataAsUtf8String).toList.map(tup => {
        MapEntryTripel(mapGroupIdOp.get + "/" + tup._1, languageOp.get, tup._2)
      })
    } else if (file.description.extension == "csv") {
      parseCsv(file.fileDataAsUtf8String).filter(_.size >= 2).map(curColumns => {
        MapEntryTripel(s"${mapGroupIdOp.get}/${curColumns.head}", languageOp.get, curColumns(1))
      })
    } else {
      List.empty[MapEntryTripel]
    }
  }

  private def parseCsv(content: String): List[List[String]] = {
    val res: Either[Throwable, List[List[String]]] = Stream
      .emit(content)
      .covary[Fallible]
      .through(lowlevel.rows[Fallible, String](separator = ';'))
      .map(_.values.toList)
      .compile
      .toList

    res.getOrElse(List.empty[List[String]])

  }

  private def languageFromFileDescription(file: FileDescription): Option[HumanLanguage] = {
    val langSuffix: Option[String] = file.nameWithoutExtension.split("-").lastOption.map(_.toLowerCase)
    langSuffix.map(languageByFileSuffix)
  }

  private def mapGroupId(file: FileDescription): Option[String] = {
    val parts: Array[String] = file.nameWithoutExtension.split("-")
    if (parts.length < 2) None else Some(parts.dropRight(1).mkString("-"))
  }

}
