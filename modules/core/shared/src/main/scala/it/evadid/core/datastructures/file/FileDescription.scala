package it.evadid.core.datastructures.file

import it.evadid.core.datastructures.file.CopyrightInfo.unknownCopyrightInfo
import it.evadid.core.datastructures.file.FileDescription.PathStructure

import scala.concurrent.Future

trait FileDescription {
  def copyrightInfo: CopyrightInfo

  lazy val isDirectory: Boolean = structure.extension.isEmpty

  lazy val filenameWithExtension: String = structure.filenameWithoutExtension + structure.extension.map("." + _).getOrElse("")

  def asUrlString: String

  lazy val structure: PathStructure = FileDescription.parseUrlStructure(asUrlString)

  def loadData(): Future[LoadedFile]

  def getChildrenFile(childName: String, cCopyrightInfo: CopyrightInfo = CopyrightInfo.unknownCopyrightInfo): Option[FileDescription]

}

object FileDescription {

  case class PathStructure(protocol: Option[String], domainElements: List[String], port: Option[Int], dirElements: List[String], filenameWithoutExtension: String, extension: Option[String]) {
    lazy val extensionOrEmpty: String = extension.getOrElse("")
  }

  def parseUrlStructure(fullPath: String): PathStructure = {
    if (fullPath.trim.isEmpty) PathStructure(None, List(), None, List(), "", None)
    else if (fullPath.trim == "/") PathStructure(None, List(), None, List(), "/", None)
    else {
      val (protocol: Option[String], remainder: String) = if (!fullPath.contains("://")) (None, fullPath) else {
        val parts = fullPath.split("://")
        if (parts.length != 2) throw new IllegalArgumentException("more than one protocol part??")
        else Some(parts(0)) -> parts(1)
      }
      val (beforeDirs, dirNames) = {
        val parts = remainder.split("/\\\\")
        parts.head -> parts.tail.filter(_.nonEmpty)
      }
      val domainList = beforeDirs.split(".").filter(_.nonEmpty).toList
      val (cleanedDomain: List[String], port: Option[Int]) = if (domainList.size > 0 && domainList.last.contains(":")) {
        val port = domainList.last.split(":").last
        val lastDomain = domainList.last.split(":").reverse.tail.reverse.mkString("", ":", "")
        val cleaned = domainList.reverse.tail.reverse ++ List(lastDomain)
        cleaned -> port.toIntOption
      } else domainList -> None


      val (onlyDirs, filename, extension) = if (dirNames.size > 0) (
        dirNames.reverse.tail.reverse.toList,
        dirNames.lastOption.map(_.split(".").toList.reverse.tail.reverse.mkString("", ".", "")).getOrElse(""),
        dirNames.lastOption.flatMap(_.split(".").lastOption)
      ) else (List(), "", None)

      PathStructure(protocol, cleanedDomain, port, onlyDirs, filename, extension)

    }


  }
  /*
    def domainAndDirNames(fullPath: String): (List[String], List[String]) = if (fullPath.trim.isEmpty) (List(), List()) else {
      //val withoutName: String = fullPath.substring(0, fullPath.length - filenameWithExtension.length - 1)
      val withoutProtocol: String = if (fullPath.contains("://")) fullPath.split("://").last else fullPath
      val splitted: Array[String] = withoutProtocol.split("/\\\\").filter(_.nonEmpty)
  
  
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
    }*/

  case class NamedDataFileDescription(url: String, override val data: Array[Byte], copyrightInfo: CopyrightInfo) extends FileDescription with LoadedFile {

    override val toString: String = "NamedDataFileDescription(" + asUrlString + ": " + data.length + " bytes)"

    def loadData(): Future[LoadedFile] = Future.successful(this)

    override val description: FileDescription = this

    override def getChildrenFile(childName: String, cCopyrightInfo: CopyrightInfo): Option[FileDescription] = None

    override def asUrlString: String = url
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
    NamedDataFileDescription(name + extension.map("." + _).getOrElse(""), data, copyrightInfo)
  }

  def apply(filename: String, data: Array[Byte], copyrightInfo: CopyrightInfo) = {
    val parts = FileDescription.parseUrlStructure(filename)
    NamedDataFileDescription(parts.filenameWithoutExtension, data, copyrightInfo)
  }


}