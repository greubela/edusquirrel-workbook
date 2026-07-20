package it.evadid.core.datastructures.language.serialization

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

import fs2.{Fallible, Stream}
import fs2.data.csv.lowlevel

trait LanguageMapSourceFileBased[T <: AppLanguage](
                                                    fileDescription: FileDescription,
                                                    val associatedLanguageMapName: String,
                                                    val associatedLanguage: T,
                                                  ) extends LanguageMapInputSource {

  def loadAllTriples(logger: Logger): Future[ParsedTriples] = {
    loadKeyValuePairs(logger).map(contentAsMap =>
        logger.logInfo(s"successfully read ${contentAsMap.size} entries from ${associatedLanguageMapName}/${associatedLanguage} (now transforming them to ParsedTriples)")
        val entries: Set[LanguageMapEntry[T]] = contentAsMap.toList.map((key, value) => {
          LanguageMapEntry[T](LanguageMapContentId(associatedLanguageMapName, key), associatedLanguage, value)
        }).toSet
        associatedLanguage.match {
          case t: HumanLanguage => ParsedTriples(entries.map(_.asInstanceOf[LanguageMapEntry[HumanLanguage]]), Set())
          case o: SpecialLanguage => ParsedTriples(Set(), entries.map(_.asInstanceOf[LanguageMapEntry[SpecialLanguage]]))
          case _ => throw new IllegalStateException(s"LanguageMapSourceFileBased:: associated language is neither Human or Special: ${associatedLanguage}")
        }
      )(using ExecutionContext.global)
      .recover {
        case (e: Exception) => logger.logExceptionWarn(s"ignoring input source ${associatedLanguageMapName}/${associatedLanguage} (error while reading)", e)
          ParsedTriples(Set(), Set())

      }(using ExecutionContext.global)
  }

  def loadKeyValuePairs(logger: Logger): Future[Map[String, String]] = {
    fileDescription.loadData()
      .map(loadedFile => {
        tryParseKeyValuePairs(logger, loadedFile)
      })(ExecutionContext.global)
      .recover { case (e: Exception) =>
        logger.logExceptionInfo(s"Ignore content of '${fileDescription.filenameWithoutExtension}' as could not fetch file", "A LanguageMapFileBasedSource does not need to exist ", e)
        Map[String, String]()
      }(ExecutionContext.global)
  }

  private def tryParseKeyValuePairs(logger: Logger, loadedFile: LoadedFile): Map[String, String] = try {
    if (loadedFile.fileDataAsUtf8String.trim.nonEmpty) parseKeyValuePairs(logger, loadedFile)
    else Map[String, String]()
  } catch case (e: Exception) => {
    logger.logExceptionWarn(s"ignoring content of file ${loadedFile.description.filenameWithExtension} because of an error during parsing", e)
    Map[String, String]()
  }

  def parseKeyValuePairs(logger: Logger, file: LoadedFile): Map[String, String]

}

object LanguageMapSourceFileBased {


  def apply[T <: AppLanguage](info: LanguageMapFileBasedSourceInfo[T], parser: (Logger, LoadedFile) => Map[String, String]): LanguageMapSourceFileBased[T] = new LanguageMapSourceFileBased[T](
    info.fileDescription,
    info.associatedLanguageMapName,
    info.associatedLanguage
  ) {
    override def parseKeyValuePairs(logger: Logger, loadedFile: LoadedFile): Map[String, String] = parser(logger, loadedFile)
  }

  def forEvaFile[T <: AppLanguage](info: LanguageMapFileBasedSourceInfo[T]): Option[LanguageMapSourceFileBased[T]] = {
    val e = info.fileDescription.extensionOrEmpty.trim.toLowerCase
    if (e.isEmpty) None
    else if (e == "csv") Some(apply(info, parseCsv))
    else if (e == "json") Some(apply(info, parseJson))
    else {
      println(s"[UGLY WARN LanguageMapSourceFileBased] file extension of an eva file should be json or csv (but was '${e}')")
      None
    }
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

  private def parseJson(logger: Logger, loadedFile: LoadedFile): Map[String, String] = {
    val res = DefaultSerializer.serializerJsonStringMap.deserialize(loadedFile.fileDataAsUtf8String)
    res
  }

  private def parseContent(logger: Logger, file: LoadedFile): Map[String, String] = try {
    val extension = file.description.extensionOrEmpty.toLowerCase
    val content = file.fileDataAsUtf8String
    if (extension.toLowerCase == "json") parseJson(logger, file)
    else if (extension.toLowerCase == "csv") parseCsv(logger, file)
    else Map[String, String]()
  } catch case e: Throwable => {
    logger.logExceptionWarn(s"ignoring content of file ${file.description.fullPath} after exception", e)
    Map[String, String]()
  }

}