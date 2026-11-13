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

import scala.concurrent.{ExecutionContext, Future}
import scala.util.*
import scala.util.control.NonFatal

trait LanguageMapSourceFileBased[T <: AppLanguage](
                                                    fileDescription: FileDescription,
                                                    val associatedLanguageMapName: String,
                                                    val associatedLanguage: T,
                                                    val ec: ExecutionContext
                                                  ) extends LanguageMapInputSource {

  given ExecutionContext = ec

  private def transformTriple(logger: Logger, key: String, value: String): Option[LanguageMapEntry[T]] = try {
    Some(LanguageMapEntry[T](LanguageMapContentId(associatedLanguageMapName, key), associatedLanguage, value))
  } catch case NonFatal(e) => {
    logger.logWarn(s"${logName} is ignoring triple: ${key} -> ${value} + (${e.getMessage})")
    None
  }

  private lazy val logName = s"LanguageMapSourceFileBased(${fileDescription.filenameWithExtension}: ${associatedLanguageMapName}/${associatedLanguage})"

  private def loadFile(logger: Logger): Future[Option[LoadedFile]] =
    Try(fileDescription.loadData()).fold(Future.failed, identity).map(file => Some(file)).recover {
      case NonFatal(error) =>
        logger.logExceptionInfo(s"Could not fetch $logName because of IO Error: ${error.getMessage}",
          "A language-map source does not need to exist", error)
        None
    }

  private def parseTriples(logger: Logger, file: LoadedFile): ParsedTriples = {
    val pairs = if file.fileDataAsUtf8String.trim.isEmpty then Map.empty[String, String] else parseKeyValuePairs(logger, file)
    val entries = pairs.toList.flatMap((key, value) => transformTriple(logger, key, value)).toSet
    logger.logInfo(s"Read ${pairs.size} entries from $logName (${entries.size} valid)")
    associatedLanguage match {
      case _: HumanLanguage => ParsedTriples(entries.map(_.asInstanceOf[LanguageMapEntry[HumanLanguage]]), Set.empty)
      case _: SpecialLanguage => ParsedTriples(Set.empty, entries.map(_.asInstanceOf[LanguageMapEntry[SpecialLanguage]]))
      case _ => throw IllegalStateException(s"Language-map source requires a human or special language: $associatedLanguage")
    }
  }

  def loadAllTriples(logger: Logger): Future[ParsedTriples] = {
    logger.logInfo(s"Started loading triples for $logName")
    loadFile(logger).map {
      case Some(file) => parseTriples(logger, file)
      case None => ParsedTriples(Set.empty, Set.empty)
    }.recover {
      case NonFatal(error) =>
        logger.logExceptionWarn(s"Could not parse language-map source $logName", error)
        ParsedTriples(Set.empty, Set.empty)
    }.map { triples =>
      logger.logInfo(s"Successfully read ${triples.regularTriples.size} regular and ${triples.universalTriples.size} universal triples")
      triples
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
    val extension = info.fileDescription.structure.extensionOrEmpty.toLowerCase
    extension match {
      case "csv" => Some(apply(info, parseCsv))
      case "json" => Some(apply(info, parseJson))
      case _ =>
        println(s"Unsupported language-map extension '$extension'; expected JSON or CSV")
        None
    }
  }

  def forSnapFile[T <: AppLanguage](info: LanguageMapFileBasedSourceInfo[T], mapKeys: String => String): Option[LanguageMapSourceFileBased[T]] = {
    Some(LanguageMapSourceFileBased(info, (logger, file) => parseSnapJS(logger, file, mapKeys)))
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
    val (valid, invalid) = lists.partition(_.size >= 2)
    if invalid.nonEmpty then
      logger.logWarn(s"LanguageMapSourceFileBased ignored ${invalid.size} entries from file ${loadedFile.description.filenameWithExtension} as they could not be parsed!")
    valid.map(row => row(0) -> row(1)).toMap
  }

  private def parseJson(logger: Logger, content: String): Map[String, String] = {
    DefaultSerializer.serializerJsonStringMap.deserialize(content)
  }

  private def parseJson(logger: Logger, loadedFile: LoadedFile): Map[String, String] = {
    DefaultSerializer.serializerJsonStringMap.deserialize(loadedFile.fileDataAsUtf8String)
  }

}
