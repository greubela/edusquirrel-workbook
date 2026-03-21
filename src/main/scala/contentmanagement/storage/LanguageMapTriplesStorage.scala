package contentmanagement.storage

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import contentmanagement.model.file.{FileDescription, LoadedFile}
import contentmanagement.model.language.{AppLanguage, HumanLanguage, LanguageMap}
import contentmanagement.storage.LanguageMapTriplesStorage.*
import fs2.Stream
import fs2.data.csv.*

import scala.concurrent.{ExecutionContext, Future}

case class LanguageMapTriplesStorage(val fileDataStorage: DataStorage[FileDescription, LoadedFile]) extends DataStorage[FileDescription, List[MapEntryTripel]]("tripleStorage", false) {

  override protected def executeLoading(file: FileDescription)(ec: ExecutionContext): Future[List[MapEntryTripel]] = {
    case class FullCsvFileInfo(fileDescription: LoadedFile, csvData: List[List[String]], fileLanguageOp: Option[HumanLanguage], mapGroupIdOp: Option[String])
    val futFile: Future[LoadedFile] = fileDataStorage.loadAsFuture(file)(ec)
    val futAndCsv: Future[(LoadedFile, List[List[String]])] = futFile.map(curLoadedFile => {
      parseCsv(curLoadedFile.fileDataAsUtf8String).map(res => (curLoadedFile, res))(ec)
    })(ec).flatten
    
    val futInfo: Future[FullCsvFileInfo] = futAndCsv.map(tup => FullCsvFileInfo(tup._1, tup._2 , languageFromFileDescription(tup._1.description), mapGroupId(tup._1.description)))(ec)
    val futTriples: Future[List[MapEntryTripel]] = futInfo.map(curFileInfo => {
       if (curFileInfo.fileLanguageOp.isEmpty || curFileInfo.mapGroupIdOp.isEmpty) {
        List.empty[MapEntryTripel]
      } else {
        curFileInfo.csvData.filter(_.size >= 2).map(curColumns => {
          MapEntryTripel(s"${curFileInfo.mapGroupIdOp.get}/${curColumns.head}", curFileInfo.fileLanguageOp.get, curColumns(1))
        })
      }
    })(ec)
    futTriples
  }

  override protected def initialValueWhileLoading(in: FileDescription): Option[List[MapEntryTripel]] = None

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

  private def parseCsv(content: String): Future[List[List[String]]] = {
    val res =     Stream
      .emit(content).covary[IO]
      .through(lowlevel.rows(separator = ';'))
      .map(_.values.toList) // Row[String] → List[String]
      .compile
      .toList
      .unsafeToFuture()
    println("called parse csv!")
    res
  }

  private def languageFromFileDescription(file: FileDescription): Option[HumanLanguage] = {
    val langSuffix: Option[String] = file.nameWithoutExtension.split("-").lastOption.map(_.toLowerCase)

    println("languageFromFileDescription for file: " + file + " -> " + langSuffix)
    langSuffix.map(languageByFileSuffix)
  }

  private def mapGroupId(file: FileDescription): Option[String] = {
    val parts: Array[String] = file.nameWithoutExtension.split("-")
    if (parts.length < 2) None else Some(parts.dropRight(1).mkString("-"))
  }

}
