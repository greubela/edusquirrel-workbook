package it.evadid.core.datastructures.language.serialization

import fs2.data.csv.lowlevel
import fs2.{Fallible, Stream}
import it.evadid.core.datastructures.file.FileDescription
import it.evadid.core.datastructures.language.AppLanguage
import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.core.datastructures.language.serialization.abstractions.ParsedTriples
import it.evadid.util.logging.Logger

import scala.concurrent.{ExecutionContext, Future}

trait LanguageMapInputSource {

  def loadAllTriples(logger: Logger): Future[ParsedTriples]

}

object LanguageMapInputSource {

  case class LanguageMapFileBasedSourceInfo[T <: AppLanguage]
  (
    fileDescription: FileDescription,
    associatedLanguageMapName: String,
    associatedLanguage: T,
    ec: ExecutionContext
  )

  def forEvaLanguageMapFiles(directoryNames: Set[EvaDirectorySource])(implicit ec: ExecutionContext): LanguageMapCollectionSource = {
    lazy val evaLanguageFileInfo: Set[(EvaDirectorySource, (String, HumanLanguage))] = for {
      a <- directoryNames
      b <- languageByEvaFileSuffix.toList
    } yield (a, b)
    val evaLanguageFiles: Set[LanguageMapSourceFileBased[HumanLanguage]] = evaLanguageFileInfo.flatMap(tup => buildEvaReader[HumanLanguage](tup._1, tup._2._2, tup._2._1, ec))
    lazy val evaUniversalFiles: Set[LanguageMapSourceFileBased[SpecialLanguage]] = directoryNames.flatMap(curFile => buildEvaReader[SpecialLanguage](curFile, UniversalLanguage, "universal", ec))
    val res: Set[LanguageMapInputSource] = (evaLanguageFiles ++ evaUniversalFiles).toSet
    LanguageMapCollectionSource(res, ec)
  }


  case class EvaDirectorySource(languageMapName: String, dirFileDescription: FileDescription)

  private lazy val languageByEvaFileSuffix: Map[String, HumanLanguage] = Map(
    "en" -> English,
    "de" -> German,
    "fr" -> French,
    "ua" -> Ukrainian,
    "ru" -> Russian,
    "tr" -> Turkish,
    "dk" -> Danish,
    "es" -> Spanish
  )

  private def buildEvaReader[T <: AppLanguage](source: EvaDirectorySource, language: T, languageSuffix: String, ec: ExecutionContext): Option[LanguageMapSourceFileBased[T]] = {

    val infoOp = source.dirFileDescription.getChildrenFile(s"map-${languageSuffix}.json").map(LanguageMapFileBasedSourceInfo[T](_, source.languageMapName, language, ec))
    val res = infoOp.flatMap(info => LanguageMapSourceFileBased.forEvaFile[T](info))
    println("[UGLY LANGUAGEMAPINPUTSOURCE] " + source + ", " + language + ", " + languageSuffix + ", " + infoOp + " ---> res: " + res)
    res

  }


}
