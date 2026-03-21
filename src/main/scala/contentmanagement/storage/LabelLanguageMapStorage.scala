package contentmanagement.storage

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import contentmanagement.model.file.FileDescription
import contentmanagement.model.language.{AppLanguage, HumanLanguage, LanguageMap}
import contentmanagement.storage.LabelLanguageMapStorage.languageMapLoadingMap
import fs2.Stream
import fs2.data.csv.lowlevel

import scala.concurrent.{ExecutionContext, Future}

case class LabelLanguageMapStorage() extends DataStorage[String, LanguageMap[HumanLanguage]]("languageMap", false) {

  override protected def executeLoading(in: String)(ec: ExecutionContext): Future[LanguageMap[HumanLanguage]] = {
    LabelLanguageMapStorage.loadLanguageMaps(LabelLanguageMapStorage.languageMapFiles)(using ec).map { maps =>
      maps.find(_._1 == in).map(_._2).getOrElse(throw new NoSuchElementException(s"Unknown language map '$in'"))
    }(ec)
  }

  override protected def initialValueWhileLoading(in: String): Option[LanguageMap[HumanLanguage]] = Some(languageMapLoadingMap)

  override protected def formatInputForLogging(in: String): String = in

  override protected def formatOutputForLogging(out: LanguageMap[HumanLanguage]): String = out.toString

  private def loadLanguageMap(file: FileDescription)(using ec: ExecutionContext): Future[Map[String, String]] = {
    DataStorage.fileDataStore.load(file).map { loadedFile =>
      LabelLanguageMapStorage.parseLanguageMapCsv(loadedFile.fileDataAsUtf8String, file)
    }(ec).flatten
  }

}

object LabelLanguageMapStorage {

  private[storage] val languageMapFiles: List[FileDescription] = List(
    FileDescription.relativeToResourceFolder("/languageMaps/basic-de.csv"),
    FileDescription.relativeToResourceFolder("/languageMaps/basic-en.csv")
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

  def loadLanguageMaps(files: List[FileDescription])(using ec: ExecutionContext): Future[Set[(String, LanguageMap[HumanLanguage])]] = {
    val storage = LabelLanguageMapStorage()
    Future.traverse(files) { file =>
      val language = languageFromFileDescription(file)
      storage.loadLanguageMap(file).map(_.map((mapKey, value) => (s"${mapGroupId(file)}/$mapKey", language, value)))
    }.map { loadedPerFile =>
      loadedPerFile
        .flatten
        .groupBy(_._1)
        .map { case (mapId, entries) =>
          mapId -> LanguageMap.mapBasedLanguageMap(entries.map(entry => entry._2 -> entry._3).toMap)
        }
        .toSet
    }
  }

  def getLanguageMapByName(name: String): LanguageMap[HumanLanguage] = {
    name match {
      case "dataLoadingMap" | "basic/dataLoadingMap" => dataLoadingMap
      case "imageLoadingMap" | "basic/imageLoadingMap" => imageLoadingMap
      case "languageMapLoadingMap" => languageMapLoadingMap
      case "noSectionSelected" => noSectionSelectedMap
    }
  }

  private[storage] def parseLanguageMapCsv(content: String, file: FileDescription): Future[Map[String, String]] = {
    Stream
      .emit(content).covary[IO]
      .through(lowlevel.rows(separator = ';'))
      .compile
      .toList
      .map(_.collect {
        case row if row.values.toList.nonEmpty =>
          row.values.toList match {
            case key :: value :: Nil => key -> value
            case other => throw new IllegalArgumentException(s"Expected exactly two columns in ${file.filename} but got ${other.size}: ${other.mkString(" | ")}")
          }
      }
      .toMap)
      .unsafeToFuture()
  }

  private def languageFromFileDescription(file: FileDescription): HumanLanguage = {
    val suffix = file.nameWithoutExtension.split("-").lastOption.map(_.toLowerCase).getOrElse("")
    languageByFileSuffix.getOrElse(suffix, throw new IllegalArgumentException(s"Could not determine language from file '${file.filename}'"))
  }

  private def mapGroupId(file: FileDescription): String = {
    val parts = file.nameWithoutExtension.split("-")
    if (parts.length < 2) throw new IllegalArgumentException(s"Could not determine language-map group from file '${file.filename}'")
    parts.dropRight(1).mkString("-")
  }

  val noSectionSelectedMap: LanguageMap[HumanLanguage] = LanguageMap.mapBasedLanguageMap(
    Map(
      AppLanguage.German -> "[Kein Abschnitt des Arbeitsheftes ausgewählt]",
      AppLanguage.English -> "[No workbook section selected]",
    )
  )

  val languageMapLoadingMap: LanguageMap[HumanLanguage] = LanguageMap.mapBasedLanguageMap(
    Map(
      AppLanguage.German -> "[Sprachdaten werden geladen]",
      AppLanguage.English -> "[language data is loading]",
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
