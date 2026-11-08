package it.evadid.core.datastructures.language.serialization

import fs2.data.csv.lowlevel
import fs2.{Fallible, Stream}
import it.evadid.core.datastructures.file.FileDescription
import it.evadid.core.datastructures.language.AppLanguage
import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.core.datastructures.language.serialization.abstractions.ParsedTriples
import it.evadid.core.datastructures.state.ExecutionMethod.ExecuteLocalSync
import it.evadid.util.logging.Logger

import scala.concurrent.{ExecutionContext, Future}

trait LanguageMapInputSource {

  def loadAllTriples(logger: Logger): Future[ParsedTriples]

}

object LanguageMapInputSource {


  case class LanguageMapLocalStorageSourceInfo[T <: AppLanguage]() extends LanguageMapInputSource derives upickle.default.ReadWriter {

    override def loadAllTriples(logger: Logger): Future[ParsedTriples] = {
      ???
    }
  } 
  
  case class LanguageMapFileBasedSourceInfo[T <: AppLanguage]
  (
    fileDescription: FileDescription,
    associatedLanguageMapName: String,
    associatedLanguage: T,
    ec: ExecutionContext
  )

  def forEvaLanguageMapFiles(directoryNames: Set[EvaDirectorySource])(implicit ec: ExecutionContext): LanguageMapCollectionSource = {
    val files: Set[LanguageMapInputSource] = directoryNames.flatMap { source =>
      source.availableFileSuffixes.flatMap { suffix =>
        languageByEvaFileSuffix.get(suffix).flatMap(language => buildEvaReader[AppLanguage](source, language, suffix, ec))
      }
    }
    LanguageMapCollectionSource(files, ec)
  }

  case class EvaDirectorySource(languageMapName: String, dirFileDescription: FileDescription,
                                availableFileSuffixes: Set[String])

  private lazy val languageByEvaFileSuffix: Map[String, AppLanguage] = Map(
    "en" -> English, "de" -> German, "fr" -> French, "ua" -> Ukrainian,
    "ru" -> Russian, "tr" -> Turkish, "dk" -> Danish, "es" -> Spanish,
    "universal" -> UniversalLanguage
  )

  private def buildEvaReader[T <: AppLanguage](source: EvaDirectorySource, language: T, languageSuffix: String, ec: ExecutionContext): Option[LanguageMapSourceFileBased[T]] = {
    val infoOp = source.dirFileDescription.getChildrenFile(s"map-${languageSuffix}.json").map(LanguageMapFileBasedSourceInfo[T](_, source.languageMapName, language, ec))
    val res = infoOp.flatMap(info => LanguageMapSourceFileBased.forEvaFile[T](info))
    res

  }


}
