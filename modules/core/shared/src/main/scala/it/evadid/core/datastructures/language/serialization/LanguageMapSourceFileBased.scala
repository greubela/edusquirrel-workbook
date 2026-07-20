package it.evadid.core.datastructures.language.serialization

import fs2.data.csv.lowlevel
import fs2.{Fallible, Stream}
import it.evadid.core.datastructures.file.{FileDescription, LoadedFile}
import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.core.datastructures.language.serialization.LanguageMapInputSource.LanguageMapFileBasedSourceInfo
import it.evadid.core.datastructures.language.serialization.abstractions.{LanguageMapEntry, ParsedTriples}
import it.evadid.core.datastructures.language.{AppLanguage, LanguageMapContentId}
import it.evadid.core.util.io.serializer.DefaultSerializer
import it.evadid.util.logging.Logger

import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}
import scala.util.*

trait LanguageMapSourceFileBased[T <: AppLanguage](
                                                    fileDescription: FileDescription,
                                                    val associatedLanguageMapName: String,
                                                    val associatedLanguage: T,
                                                    val ec: ExecutionContext
                                                  ) extends LanguageMapInputSource {

  given ExecutionContext = ec

  private def transformTriple(logger: Logger, key: String, value: String): Option[LanguageMapEntry[T]] = try {
    //logger.logInfo(s"${logName} is transforming triple: ${key} -> ${value}")
    Some(LanguageMapEntry[T](LanguageMapContentId(associatedLanguageMapName, key), associatedLanguage, value))
  } catch case (e: Throwable) => {
    logger.logWarn(s"${logName} is ignoring triple: ${key} -> ${value} + (${e.getMessage})")
    None
  }

  private lazy val logName = s"LanguageMapSourceFileBased(${fileDescription.filenameWithExtension}: ${associatedLanguageMapName}/${associatedLanguage}"

  def loadAllTriples(logger: Logger): Future[ParsedTriples] = {
    logger.logInfo(s"Started loading all triples for ${logName}")

    fileDescription.loadData().transform {
      case Success(loadedFile) =>
        logger.logInfo(s"Successfully loaded file for ${logName} ")
        if (loadedFile.fileDataAsUtf8String.trim.isEmpty) Success(Map[String, String]()) else Try {
          parseKeyValuePairs(logger, loadedFile)
        }
      case Failure(e: Exception) =>
        logger.logExceptionInfo(s"Could not fetch ${logName} because of IO Error: ${e.getMessage}", "A LanguageMapFileBasedSource does not need to exist ", e)
        Success(Map[String, String]())
    }.transform {
      case Success(contentAsMap) =>
        logger.logInfo(s"Arriving at Parsing Key Value Paris for ${logName}: ${contentAsMap}")
        val entries: Set[LanguageMapEntry[T]] = contentAsMap.toList.flatMap((key, value) => transformTriple(logger, key, value)).toSet
        logger.logInfo(s"successfully read ${contentAsMap.size} entries from ${associatedLanguageMapName}/${associatedLanguage} (and transformed them into ${entries.size} entries)")
        associatedLanguage.match {
          case t: HumanLanguage => Success(ParsedTriples(entries.map(_.asInstanceOf[LanguageMapEntry[HumanLanguage]]), Set()))
          case o: SpecialLanguage => Success(ParsedTriples(Set(), entries.map(_.asInstanceOf[LanguageMapEntry[SpecialLanguage]])))
          case _ => Failure(IllegalStateException(s"LanguageMapSourceFileBased:: associated language is neither Human or Special: ${associatedLanguage}"))
        }
      case Failure(err) =>
        logger.logExceptionWarn(s"[this should not happen #1 at ${logName}]: ${err.getMessage}", err)
        Success(ParsedTriples(Set(), Set()))
    }.transform {
      case Success(triples) => {
        logger.logInfo(s"Successfully read ${triples.regularTriples.size} regular and ${triples.universalTriples.size} universal triples!")
        Success(triples)
      }
      case Failure(err) => {
        logger.logExceptionWarn(s"[this should not happen #2 at ${logName}]: ${err.getMessage}", err)
        Success(ParsedTriples(Set(), Set()))
      }
    }
  }


  def parseKeyValuePairs(logger: Logger, file: LoadedFile): Map[String, String]

}

object LanguageMapSourceFileBased {


  def apply[T <: AppLanguage](info: LanguageMapFileBasedSourceInfo[T], parser: (Logger, LoadedFile) => Map[String, String]): LanguageMapSourceFileBased[T] = new LanguageMapSourceFileBased[T](
    info.fileDescription,
    info.associatedLanguageMapName,
    info.associatedLanguage,
    info.ec
  ) {
    override def parseKeyValuePairs(logger: Logger, loadedFile: LoadedFile): Map[String, String] = parser(logger, loadedFile)
  }

  def forEvaFile[T <: AppLanguage](info: LanguageMapFileBasedSourceInfo[T]): Option[LanguageMapSourceFileBased[T]] = {
    val e = info.fileDescription.structure.extensionOrEmpty.toLowerCase
    if (e == "csv") Some(apply(info, parseCsv))
    else if (e == "json") Some(apply(info, parseJson))
    else {
      println(s"[UGLY WARN LanguageMapSourceFileBased] file extension of an eva file should be json or csv (but was '${e}')")
      None
    }
  }

  def forSnapFile[T <: AppLanguage](info: LanguageMapFileBasedSourceInfo[T], mapKeys: String => String): Option[LanguageMapSourceFileBased[T]] = {
    Some(LanguageMapSourceFileBased(info, (a, b) => parseSnapJS(a, b, mapKeys)))
  }

  private def parseSnapJS(logger: Logger, loadedFile: LoadedFile, mapKeys: String => String): Map[String, String] = try {
    val linesNonEmpty = loadedFile.fileDataAsUtf8String.split("\n").filter(_.nonEmpty)
    val adjusted = linesNonEmpty.slice(1, linesNonEmpty.size - 1).mkString("{", "\n", "}")
    val parsed = parseJson(logger, adjusted)
    parsed.toList.map((key, value) => mapKeys(key) -> value).toMap
  } catch case (e: Exception) => {
    logger.logExceptionWarn("error while parsing snap js file", e)
    Map[String, String]()
  }

  private def parseCsvFs2(content: String): List[List[String]] = {
    val res: Either[Throwable, List[List[String]]] = Stream
      .emit(content)
      .covary[Fallible]
      .through(lowlevel.rows[Fallible, String](separator = ';'))
      .map(_.values.toList)
      .compile
      .toList

    res.getOrElse(List.empty[List[String]])
  }

  private def parseCsv(logger: Logger, loadedFile: LoadedFile): Map[String, String] = {
    val lists = parseCsvFs2(loadedFile.fileDataAsUtf8String)
    val failed = mutable.ListBuffer[List[String]]()
    val res = lists.flatMap(curElement => {
      if (curElement.size >= 2) Some(curElement(0) -> curElement(1))
      else {
        failed += curElement
        None
      }
    })
    logger.logWarn(s"LanguageMapSourceFileBased ignored ${failed.size} entries from file ${loadedFile.description.filenameWithExtension} as they could not be parsed!")
    res.toMap
  }

  private def parseJson(logger: Logger, content: String): Map[String, String] = {
    val res = DefaultSerializer.serializerJsonStringMap.deserialize(content)
    res
  }

  private def parseJson(logger: Logger, loadedFile: LoadedFile): Map[String, String] = {
    val res = DefaultSerializer.serializerJsonStringMap.deserialize(loadedFile.fileDataAsUtf8String)
    res
  }

  private def parseContent(logger: Logger, file: LoadedFile): Map[String, String] = try {
    val extension = file.description.structure.extension.getOrElse("").toLowerCase
    val content = file.fileDataAsUtf8String
    if (extension.toLowerCase == "json") parseJson(logger, file)
    else if (extension.toLowerCase == "csv") parseCsv(logger, file)
    else Map[String, String]()
  } catch case e: Throwable => {
    logger.logExceptionWarn(s"ignoring content of file ${file.description.asUrlString} after exception", e)
    Map[String, String]()
  }

}