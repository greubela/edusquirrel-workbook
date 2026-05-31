package it.evadid.core.datastructures.file

import it.evadid.core.datastructures.file.CopyrightInfo.unknownCopyrightInfo
import it.evadid.core.datastructures.language.*
import it.evadid.core.datastructures.language.AppLanguage.*

import scala.concurrent.Future

trait FileDescription {
  def copyrightInfo: CopyrightInfo

  def location: Option[String]

  def filenameWithoutExtension: String

  def extension: String

  lazy val filenameWithExtension: String = filenameWithoutExtension + "." + extension
  lazy val fullPath: String = location.map(_ + "/").getOrElse("") + filenameWithExtension
  
  def loadData(): Future[LoadedFile]
}

object FileDescription {

  def nameParts(fullPath: String): (String, String, String) = {
    val parts = fullPath.split("\\\\")
    val filenameWithExtension: String = parts.last.split("/").last.trim

    val (nameWithoutExtension, extension) =
      if (filenameWithExtension.contains(".")) {
        val filenameParts = filenameWithExtension.split("\\.")
        val extension: String = filenameParts.last.trim
        val nameWithoutExtension = filenameWithExtension.substring(0, filenameWithExtension.length - extension.length - 1)
        (nameWithoutExtension, extension)
      } else {
        (filenameWithExtension, "")
      }

    val filePath =
      if (fullPath == filenameWithExtension) ""
      else fullPath.substring(0, fullPath.length - filenameWithExtension.length - 1)

    val res = (filePath, nameWithoutExtension, extension)
    res
  }

  case class NamedDataFileDescription(filenameWithoutExtension: String, extension: String, override val data: Array[Byte], copyrightInfo: CopyrightInfo) extends FileDescription with LoadedFile {
    val location: Option[String] = None
    override val toString: String = "NamedDataFileDescription(" + fullPath + ": " + data.length + " bytes)"
    
    def loadData(): Future[LoadedFile] = Future.successful(this)
    override val description: FileDescription = this 
  }

  def apply(name: String, extension: String, data: Array[Byte]): FileDescription = FileDescription(name, extension, data, unknownCopyrightInfo)

  def apply(filename: String, data: Array[Byte]): FileDescription = FileDescription(filename, data, unknownCopyrightInfo)


  def apply(name: String, extension: String, data: Array[Byte], copyrightInfo: CopyrightInfo) = {
    NamedDataFileDescription(name, extension, data, copyrightInfo)
  }

  def apply(filename: String, data: Array[Byte], copyrightInfo: CopyrightInfo) = {
    val parts = nameParts(filename)
    NamedDataFileDescription(parts._2, parts._3, data, copyrightInfo)
  }


}