package it.evadid.core.datastructures.file

import it.evadid.core.datastructures.file.CopyrightInfo.unknownCopyrightInfo

import scala.concurrent.Future

trait FileDescription {
  def copyrightInfo: CopyrightInfo

  def filenameWithoutExtension: String

  lazy val extensionWithPointOrEmpty: String = extension.map("." + _).getOrElse("")

  lazy val extensionOrEmpty: String = extension.getOrElse("")

  def extension: Option[String]

  lazy val locationStr: String = if(locatedInDir.nonEmpty) locatedInDir.mkString("", "/", "/") else ""
  lazy val (domainNames: List[String], locatedInDir: List[String]) = FileDescription.domainAndDirNames(fullPath)

  lazy val isDirectory: Boolean = extension.isEmpty

  lazy val filenameWithExtension: String = filenameWithoutExtension + extensionWithPointOrEmpty

  lazy val fullPath: String = locationStr + filenameWithExtension

  def loadData(): Future[LoadedFile]

  def getChildrenFile(childName: String, cCopyrightInfo: CopyrightInfo = CopyrightInfo.unknownCopyrightInfo): Option[FileDescription]

}

object FileDescription {


  def domainAndDirNames(fullPath: String): (List[String], List[String]) = if (fullPath.trim.isEmpty) (List(), List()) else {
    //val withoutName: String = fullPath.substring(0, fullPath.length - filenameWithExtension.length - 1)
    val withoutProtocol: String = if (fullPath.contains("://")) fullPath.split("://").last else fullPath
    val splitted: Array[String] = withoutProtocol.split("/\\\\").filter(_.nonEmpty)
    val domain = splitted.head.split(".").filter(_.nonEmpty)
    val dirHierarchy = splitted.tail.map(_.split(".").head)
    (domain.toList, dirHierarchy.toList)
  }


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

    override def getChildrenFile(childName: String, cCopyrightInfo: CopyrightInfo): Option[FileDescription] = None
  }

  /*case class DerivedChildFileDescription(parent: FileDescription, childName: String, childExtension: Option[String], override val copyrightInfo: CopyrightInfo) extends FileDescription {

    if (!parent.isDirectory) println("[UGLY WARN, FileDescription] Children of non-directory parent! Treating file-extension as non-existent??")

    def extension: Option[String] = childExtension

    def location: Option[String] = parent.location.map(_ + s"/${childName}${extensionWithPointOrEmpty}")

    def filenameWithoutExtension: String = parent.filenameWithoutExtension + "/" + childName

    def loadData(): Future[LoadedFile] = ???

  }*/

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