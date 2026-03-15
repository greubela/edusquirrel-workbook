package contentmanagement.model.file

import contentmanagement.model.file.CopyrightInfo.unknownCopyrightInfo
import org.scalajs.dom
import org.scalajs.dom.{File, URL}

sealed trait FileDescription {
  def copyrightInfo: CopyrightInfo

  def location: Option[String]

  def nameWithoutExtension: String

  def extension: String

  lazy val filename: String = nameWithoutExtension + "." + extension
  lazy val fullPath: String = location.getOrElse("") + filename
}

object FileDescription {

  case class InternetResourceFileDescription(url: URL, serverLocationDir: String, nameWithoutExtension: String, extension: String, copyrightInfo: CopyrightInfo) extends FileDescription {
    val location: Option[String] = Some(serverLocationDir)
    override val toString: String = "InternetResourceFileDescription(" + fullPath + ")"
  }

  case class UploadedResourceFileDescription(file: File, nameWithoutExtension: String, extension: String, copyrightInfo: CopyrightInfo) extends FileDescription {
    val location: Option[String] = None
    override val toString: String = "UploadedResourceFileDescription(" + fullPath + ")"
  }

  case class NamedDataFileDescription(nameWithoutExtension: String, extension: String, data: Array[Byte], copyrightInfo: CopyrightInfo) extends FileDescription {
    val location: Option[String] = None
    override val toString: String = "NamedDataFileDescription(" + fullPath + ": " + data.length + " bytes)"
  }

  def relativeToResourceFolder(pathRelativeToResourceFolder: String, copyrightInfo: CopyrightInfo = unknownCopyrightInfo): FileDescription = {
    val str = if (pathRelativeToResourceFolder.startsWith("/")) pathRelativeToResourceFolder.substring(1) else pathRelativeToResourceFolder
    val url = new URL(s"../resources/$str", dom.window.location.href)
    FileDescription(url)
  }

  def apply(file: File): FileDescription = apply(file, unknownCopyrightInfo)

  def apply(url: URL): FileDescription = FileDescription(url, unknownCopyrightInfo)

  def apply(name: String, extension: String, data: Array[Byte]): FileDescription = FileDescription(name, extension, data, unknownCopyrightInfo)

  def apply(filename: String, data: Array[Byte]): FileDescription = FileDescription(filename, data, unknownCopyrightInfo)

  def apply(file: File, copyrightInfo: CopyrightInfo): FileDescription = {
    val parts = nameParts(file.name)
    UploadedResourceFileDescription(file, parts._2, parts._3, copyrightInfo)
  }

  def apply(url: URL, copyrightInfo: CopyrightInfo): FileDescription = {
    val parts = nameParts(url.href)
    InternetResourceFileDescription(url, parts._1, parts._2, parts._3, copyrightInfo)
  }

  def apply(name: String, extension: String, data: Array[Byte], copyrightInfo: CopyrightInfo) = {
    NamedDataFileDescription(name, extension, data, copyrightInfo)
  }

  def apply(filename: String, data: Array[Byte], copyrightInfo: CopyrightInfo) = {
    val parts = nameParts(filename)
    NamedDataFileDescription(parts._2, parts._3, data, copyrightInfo)
  }


  private def nameParts(fullPath: String): (String, String, String) = {
    val parts = fullPath.split("\\\\")
    val filename: String = parts.last.split("/").last.trim

    val filenameSplitted = filename.split("\\.")

    val extension: String = if (filenameSplitted.contains(".")) filenameSplitted.last.trim else ""

    val nameWithoutExtension = if (filenameSplitted.contains(".")) filename.substring(0, filename.length - extension.length - 1) else filename
    val filePath = fullPath.substring(0, fullPath.length - nameWithoutExtension.length - 1)

    (filePath, nameWithoutExtension, extension)
  }

}