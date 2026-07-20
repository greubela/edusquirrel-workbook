package it.evadid.core.datastructures.file

import it.evadid.core.datastructures.file.FileDescription.PathStructure

import scala.concurrent.Future

trait FileDescription {
  def copyrightInfo: CopyrightInfo

  lazy val isDirectory: Boolean = structure.extension.isEmpty

  lazy val filenameWithExtension: String = structure.filenameWithoutExtension + structure.extension.map("." + _).getOrElse("")

  def asUrlString: String

  lazy val structure: PathStructure = FileDescription.parsePathStructure(asUrlString)

  def loadData(): Future[LoadedFile]

  def getChildrenFile(childName: String, cCopyrightInfo: CopyrightInfo = CopyrightInfo.unknownCopyrightInfo): Option[FileDescription]

}

object FileDescription {

  case class FilenameStructure(filenameWithoutExtension: String, extension: Option[String]) {
    lazy val extensionOrEmpty: String = extension.getOrElse("")
    lazy val extensionWithPointOrEmpty: String = extension.map("." + _).getOrElse("")
    lazy val filenameWithExtension: String = filenameWithoutExtension + extensionWithPointOrEmpty
  }

  case class LocationStructure(protocol: Option[String], domainElements: List[String], port: Option[Int], dirElements: List[String]) {

  }

  case class PathStructure(locationStructure: LocationStructure, nameStructure: FilenameStructure) {
    def filenameWithoutExtension: String = nameStructure.filenameWithoutExtension

    def extension: Option[String] = nameStructure.extension

    def extensionOrEmpty: String = nameStructure.extensionOrEmpty

    def extensionWithPointOrEmpty: String = nameStructure.extensionWithPointOrEmpty

    def filenameWithExtension: String = nameStructure.filenameWithExtension

    def protocol: Option[String] = locationStructure.protocol

    def domainElements: List[String] = locationStructure.domainElements

    def port: Option[Int] = locationStructure.port

    def dirElements: List[String] = locationStructure.dirElements
  }

  private def parseNameStructure(asUrl: String): FilenameStructure = {
    val filenameWithExtension: String = asUrl.split("\\\\").last.split("/").last.trim
    if (filenameWithExtension.isEmpty) FilenameStructure("", None)
    else if (!filenameWithExtension.contains(".")) FilenameStructure(filenameWithExtension, None)
    else {
      val filenameParts = filenameWithExtension.split("\\.")
      val extensionRaw: String = filenameParts.last.trim
      val nameWithoutExtension = filenameWithExtension.substring(0, filenameWithExtension.length - extensionRaw.length - 1)
      val extension = if (extensionRaw.isEmpty) None else Some(extensionRaw)
      FilenameStructure(nameWithoutExtension, extension)
    }
  }

  def parsePathStructure(asUrl: String): PathStructure = {
    val nameStructure = parseNameStructure(asUrl)
    val locationStructure = parseLocationStructure(asUrl, nameStructure)
    PathStructure(locationStructure, nameStructure)
  }

  private def parseLocationStructure(asUrl: String, filenameStructure: FilenameStructure): LocationStructure = {
    if (asUrl.trim.isEmpty || asUrl.trim.size <= filenameStructure.filenameWithExtension.size) {
      LocationStructure(None, List(), None, List())
    } else {
      val withoutFileName = asUrl.trim.substring(0, asUrl.length - filenameStructure.filenameWithExtension.length)
      val (protocol: Option[String], remainder: String) = if (!withoutFileName.contains("://")) (None, withoutFileName) else {
        val parts = withoutFileName.split("://")
        if (parts.length != 2) throw new IllegalArgumentException("more than one protocol part??")
        else Some(parts(0)) -> parts(1)
      }

      val (beforeDirs, dirNameList) = {
        val parts = remainder.split("\\\\").flatMap(_.split("/"))
        parts.head -> parts.tail.filter(_.nonEmpty)
      }

      val domainList = beforeDirs.split("\\.").filter(_.nonEmpty).toList
      val (cleanedDomainList: List[String], port: Option[Int]) = if (domainList.nonEmpty && domainList.last.contains(":")) {
        val port = domainList.last.split(":").last
        val lastDomain = domainList.last.split(":").reverse.tail.reverse.mkString("", ":", "")
        val cleaned = domainList.reverse.tail.reverse ++ List(lastDomain)
        cleaned -> port.toIntOption
      } else domainList -> None

      val onlyDirs = if (dirNameList.nonEmpty) dirNameList.reverse.tail.reverse.toList else List()
      LocationStructure(protocol, cleanedDomainList, port, onlyDirs)
    }
  }


}

/*
  def domainAndDirNames(fullPath: String): (List[String], List[String]) = if (fullPath.trim.isEmpty) (List(), List()) else {
    //val withoutName: String = fullPath.substring(0, fullPath.length - filenameWithExtension.length - 1)
    val withoutProtocol: String = if (fullPath.contains("://")) fullPath.split("://").last else fullPath
    val splitted: Array[String] = withoutProtocol.split("/\\\\").filter(_.nonEmpty)


    (domain.toList, dirHierarchy.toList)
  }




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


}*/