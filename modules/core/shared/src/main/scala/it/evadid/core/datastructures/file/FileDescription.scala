package it.evadid.core.datastructures.file

import it.evadid.core.datastructures.file.CopyrightInfo.unknownCopyrightInfo

import scala.concurrent.Future

trait FileDescription {
  def copyrightInfo: CopyrightInfo

  def location: Option[String]

  def filenameWithoutExtension: String

  def extensionOrEmpty: String = extension.getOrElse("")

  def extension: Option[String]

  lazy val dirNames: List[String] = {
    val withoutName: String = fullPath.substring(0, fullPath.length - filenameWithExtension.length - 1)
    val protocolSplit: String = if(withoutName.contains("://")) withoutName.split("://").last else withoutName
    val dirNames: List[String] = protocolSplit.split("[./\\\\]").toList.filter(_.nonEmpty)
    dirNames
  }

  lazy val isDirectory: Boolean = extension.isEmpty

  lazy val filenameWithExtension: String = filenameWithoutExtension + extension.map("." + _).getOrElse("")
  lazy val fullPath: String = location.map(_ + "/").getOrElse("") + filenameWithExtension

  def loadData(): Future[LoadedFile]
}

object FileDescription {

  def nameParts(fullPath: String): (String, String, Option[String]) = {
    val parts = fullPath.split("\\\\")
    val filenameWithExtension: String = parts.last.split("/").last.trim

    val (nameWithoutExtension, extension) =
      if (filenameWithExtension.contains(".")) {
        val filenameParts = filenameWithExtension.split("\\.")
        val extensionRaw: String = filenameParts.last.trim
        val nameWithoutExtension = filenameWithExtension.substring(0, filenameWithExtension.length - extensionRaw.length - 1)
        val extension = if (extensionRaw.isEmpty) None else Some(extensionRaw)
        (nameWithoutExtension, extension)
      } else {
        (filenameWithExtension, None)
      }

    val filePath =
      if (fullPath == filenameWithExtension) ""
      else fullPath.substring(0, fullPath.length - filenameWithExtension.length - 1)

    val res = (filePath, nameWithoutExtension, extension)
    res
  }

  case class NamedDataFileDescription(filenameWithoutExtension: String, extension: Option[String], override val data: Array[Byte], copyrightInfo: CopyrightInfo) extends FileDescription with LoadedFile {
    val location: Option[String] = None
    override val toString: String = "NamedDataFileDescription(" + fullPath + ": " + data.length + " bytes)"

    def loadData(): Future[LoadedFile] = Future.successful(this)

    override val description: FileDescription = this
  }

  def apply(name: String, extension: Option[String], data: Array[Byte]): FileDescription = FileDescription(name, extension, data, unknownCopyrightInfo)

  def apply(filename: String, data: Array[Byte]): FileDescription = FileDescription(filename, data, unknownCopyrightInfo)

  def apply(name: String, extension: Option[String], data: Array[Byte], copyrightInfo: CopyrightInfo) = {
    NamedDataFileDescription(name, extension, data, copyrightInfo)
  }

  def apply(filename: String, data: Array[Byte], copyrightInfo: CopyrightInfo) = {
    val parts = nameParts(filename)
    NamedDataFileDescription(parts._2, parts._3, data, copyrightInfo)
  }


}