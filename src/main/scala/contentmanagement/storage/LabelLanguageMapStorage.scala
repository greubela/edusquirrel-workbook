package contentmanagement.storage

import contentmanagement.model.file.FileDescription
import contentmanagement.model.language.{AppLanguage, HumanLanguage, LanguageMap}
import contentmanagement.storage.LabelLanguageMapStorage.*
import contentmanagement.storage.LanguageMapTriplesStorage.MapEntryTripel

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

case class LabelLanguageMapStorage(languageTriplesStorage: LanguageMapTriplesStorage) extends DataStorage[String, LanguageMap[HumanLanguage]]("languageMap", false) {

  override protected def executeLoading(id: String)(ec: ExecutionContext): Future[LanguageMap[HumanLanguage]] = {

    val allTriples: Future[List[List[MapEntryTripel]]] = Future.traverse(LabelLanguageMapStorage.languageMapFiles)(file => {
      languageTriplesStorage.loadAsFuture(file, false)(ec)
    })

    allTriples.map(_.flatten)(ec).map(triples => {
      val languageMaps: Set[(String, LanguageMap[HumanLanguage])] = triplesToLanguageMaps(triples)
      languageMaps.find(_._1 == id).map(_._2).getOrElse(LabelLanguageMapStorage.languageMapNonExistentMap(id))
    })(ec)
  }

  override protected def defaultValueWhileLoading(in: String): Option[LanguageMap[HumanLanguage]] = Some(languageMapLoadingMap)

  override protected def formatInputForLogging(in: String): String = in

  override protected def formatOutputForLogging(out: LanguageMap[HumanLanguage]): String = out.toString

}

object LabelLanguageMapStorage {
  val languageMapLoadingMap: LanguageMap[HumanLanguage] = LanguageMap.mapBasedLanguageMap(
    Map(
      AppLanguage.German -> "[Sprachdaten werden geladen]",
      AppLanguage.English -> "[language data is loading]",
    )
  )

  def languageMapNonExistentMap(id: String): LanguageMap[HumanLanguage] = LanguageMap.mapBasedLanguageMap(
    Map(
      AppLanguage.German -> s"[Keine Sprachdaten für ID: '${id}']",
      AppLanguage.English -> s"[No Language Data for ID: '${id}']",
    )
  )

  private val languageMapFiles: List[FileDescription] = List(
    FileDescription.relativeToResourceFolder("/languageMaps/EmbroideryWorkbook-de.json"),
    FileDescription.relativeToResourceFolder("/languageMaps/EmbroideryWorkbook-en.csv"),
    FileDescription.relativeToResourceFolder("/languageMaps/basic-en.csv"),
    FileDescription.relativeToResourceFolder("/languageMaps/basic-de.json"),
    //   FileDescription.relativeToResourceFolder("/languageMaps/basic-fr.csv")
  )

  private def triplesToLanguageMaps(triples: List[MapEntryTripel]): Set[(String, LanguageMap[HumanLanguage])] = {
    triples
      .groupBy(_.mapId)
      .map { case (mapId, entries) =>
        mapId -> LanguageMap.mapBasedLanguageMap(entries.map(entry => entry.language -> entry.value).toMap)
      }
      .toSet
  }

  /*
    val noSectionSelectedMap: LanguageMap[HumanLanguage] = LanguageMap.mapBasedLanguageMap(
      Map(
        AppLanguage.German -> "[Kein Abschnitt des Arbeitsheftes ausgewählt]",
        AppLanguage.English -> "[No workbook section selected]",
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
  */
}
