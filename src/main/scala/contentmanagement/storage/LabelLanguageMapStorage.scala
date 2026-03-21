package contentmanagement.storage

import contentmanagement.model.file.FileDescription
import contentmanagement.model.language.{AppLanguage, HumanLanguage, LanguageMap}
import contentmanagement.storage.LabelLanguageMapStorage.languageMapLoadingMap

import scala.concurrent.{ExecutionContext, Future}

case class LabelLanguageMapStorage() extends DataStorage[String, LanguageMap[HumanLanguage]]("languageMap", false) {

  override protected def executeLoading(in: String)(ec: ExecutionContext): Future[LanguageMap[HumanLanguage]] = ???

  override protected def initialValueWhileLoading(in: String): Option[LanguageMap[HumanLanguage]] = Some(languageMapLoadingMap)

  override protected def formatInputForLogging(in: String): String = in

  override protected def formatOutputForLogging(out: LanguageMap[HumanLanguage]): String = out.toString

  private def loadLanguageMap(file: FileDescription): Future[LanguageMap[HumanLanguage]] = {
    ???
  }

}

object LabelLanguageMapStorage {


  private val languageMapFiles: List[FileDescription] = List(
    FileDescription.relativeToResourceFolder("/languageMaps/basic-de.csv"),
    FileDescription.relativeToResourceFolder("/languageMaps/basic-en.csv")
  )

  def getLanguageMapByName(name: String): LanguageMap[HumanLanguage] = {
    name match {
      case "dataLoadingMap" => dataLoadingMap
      case "imageLoadingMap" => imageLoadingMap
      case "languageMapLoadingMap" => languageMapLoadingMap
      case "noSectionSelected" => noSectionSelectedMap
    }
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
      AppLanguage.English -> "[Data is loading]",
    )
  )

  val imageLoadingMap: LanguageMap[HumanLanguage] = LanguageMap.mapBasedLanguageMap(Map(
    AppLanguage.German -> "[Bild wird geladen]",
    AppLanguage.English -> "[Image is loading]",
  ))


}