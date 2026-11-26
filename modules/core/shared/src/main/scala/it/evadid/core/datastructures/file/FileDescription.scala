package it.evadid.core.datastructures.file

import it.evadid.core.datastructures.file.FileDescription.PathStructure

import scala.concurrent.Future

trait FileDescription {
  def copyrightInfo: CopyrightInfo

  /** Media type declared by the location itself; only `data:` URLs carry one. */
  def mediaType: Option[String] = FileDescription.dataUrlMediaType(asUrlString)

  lazy val isDirectory: Boolean = structure.extension.isEmpty && mediaType.isEmpty

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

  private def isDataUrl(url: String): Boolean = url.trim.take(5).equalsIgnoreCase("data:")

  /** Media type (with parameters, without the base64 marker) of an RFC 2397 `data:[<media-type>][;base64],<data>` URL. */
  def dataUrlMediaType(url: String): Option[String] = Option.when(isDataUrl(url)) {
    val parameters = url.trim.drop(5).takeWhile(_ != ',').split(';').map(_.trim).filter(_.nonEmpty)
      .filterNot(_.equalsIgnoreCase("base64")).toList
    parameters match {
      case Nil => "text/plain;charset=US-ASCII"
      case mediaType :: _ if mediaType.contains('/') => parameters.mkString(";")
      case _ => ("text/plain" :: parameters).mkString(";")
    }
  }

  /** Data URLs are nameless: their payload is never parsed as a path. */
  def parsePathStructure(asUrl: String): PathStructure = if (isDataUrl(asUrl))
    PathStructure(LocationStructure(Some("data"), Nil, None, Nil), FilenameStructure("", None))
  else {
    val normalized = asUrl.trim.replace('\\', '/')
    val separator = normalized.indexOf("://")
    val protocol = if (separator >= 0) Some(normalized.take(separator)) else None
    val remainder = if (separator >= 0) normalized.drop(separator + 3) else normalized
    val cleanPath = if (protocol.nonEmpty) remainder.takeWhile(c => c != '?' && c != '#') else remainder
    val (authority, path) = if (protocol.nonEmpty) {
      val slash = cleanPath.indexOf('/')
      if (slash < 0) (cleanPath, "") else (cleanPath.take(slash), cleanPath.drop(slash + 1))
    } else ("", cleanPath)
    val components = path.split("/", -1).toList
    val filename = components.lastOption.getOrElse("")
    val dot = filename.lastIndexOf('.')
    val name = if (dot > 0 && dot < filename.length - 1)
      FilenameStructure(filename.take(dot), Some(filename.drop(dot + 1)))
    else FilenameStructure(filename, None)
    val colon = authority.lastIndexOf(':')
    val port = if (colon >= 0) authority.drop(colon + 1).toIntOption else None
    val host = if (port.nonEmpty) authority.take(colon) else authority
    val location = LocationStructure(protocol, host.split("\\.").filter(_.nonEmpty).toList,
      port, components.dropRight(1).filter(_.nonEmpty))
    PathStructure(location, name)
  }

}

/** A file that already holds its bytes, so it describes and loads itself (e.g. a file received over transport). */
case class NamedDataFileDescription(url: String, data: Array[Byte], copyrightInfo: CopyrightInfo, override val mediaType: Option[String])
  extends FileDescription with LoadedFile {

  override def toString: String = "NamedDataFileDescription(" + asUrlString + ": " + data.length + " bytes)"

  override def description: FileDescription = this

  override def loadData(): Future[LoadedFile] = Future.successful(this)

  override def getChildrenFile(childName: String, cCopyrightInfo: CopyrightInfo): Option[FileDescription] = None

  override def asUrlString: String = url
}

/*
  def domainAndDirNames(fullPath: String): (List[String], List[String]) = if (fullPath.trim.isEmpty) (List(), List()) else {
    //val withoutName: String = fullPath.substring(0, fullPath.length - filenameWithExtension.length - 1)
    val withoutProtocol: String = if (fullPath.contains("://")) fullPath.split("://").last else fullPath
    val splitted: Array[String] = withoutProtocol.split("/\\\\").filter(_.nonEmpty)


    (domain.toList, dirHierarchy.toList)
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
