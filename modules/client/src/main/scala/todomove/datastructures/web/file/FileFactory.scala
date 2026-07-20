package todomove.datastructures.web.file

import it.evadid.core.datastructures.file.CopyrightInfo.unknownCopyrightInfo
import it.evadid.core.datastructures.file.{CopyrightInfo, FileDescription, LoadedFile}
import it.evadid.homepage.control.singletons.FileStore
import it.evadid.workbook.abstractions.TypeOfTextDisplay
import it.evadid.workbook.abstractions.TypeOfTextDisplay.URL_TYPE
import org.scalajs.dom
import org.scalajs.dom.{File, URL}

import scala.concurrent.{ExecutionContext, Future}

object FileFactory {

  private case class InternetResourceFileDescription(url: URL, filenameWithoutExtension: String, extension: Option[String], copyrightInfo: CopyrightInfo) extends FileDescription {

    override val toString: String = "InternetResourceFileDescription(" + fullPath + ")"

    def loadData(): Future[LoadedFile] = FileStore.fetchUrl(url.href).map(data => LoadedFile(this, data))(using ExecutionContext.global)

    override def getChildrenFile(childName: String, cCopyrightInfo: CopyrightInfo): Option[FileDescription] = if (extension.nonEmpty) None else {
      val (cFilePath, cNameWithoutExtension, cExtension) = FileDescription.nameParts(childName)
      Some(InternetResourceFileDescription(URL(url.href + "/" + childName), cNameWithoutExtension, cExtension, cCopyrightInfo))
    }
  }

  private case class UploadedResourceFileDescription(file: File, filenameWithoutExtension: String, extension: Option[String], copyrightInfo: CopyrightInfo) extends FileDescription {
    val location: Option[String] = None
    override val toString: String = "UploadedResourceFileDescription(" + fullPath + ")"

    def loadData(): Future[LoadedFile] = FileStore.fetchFile(file).map(data => LoadedFile(this, data))(using ExecutionContext.global)

    override def getChildrenFile(childName: String, cCopyrightInfo: CopyrightInfo): Option[FileDescription] = None
  }


  def resolve(howToResolveUrl: URL_TYPE, src: String): FileDescription = {
    howToResolveUrl.match {
      case TypeOfTextDisplay.URL_RELATIVE_TO_GLOBAL_RESOURCES => relativeToResourceFolder(src)
      case TypeOfTextDisplay.URL_RELATIVE_TO_WORKBOOK_RESOURCES(workbookRoot) => fromUrl(URL(workbookRoot.fullPath + "/" + src))
    }
  }


  def fromUploadedFile(file: File): FileDescription = fromFile(file)

  def fromFile(file: File, copyrightInfo: CopyrightInfo = unknownCopyrightInfo): FileDescription = {
    println("file: " + file.name)
    val parts = FileDescription.nameParts(file.name)
    UploadedResourceFileDescription(file, parts._2, parts._3, copyrightInfo)
  }

  def fromUrl(url: URL, copyrightInfo: CopyrightInfo = unknownCopyrightInfo): FileDescription = {
    val (filePath, nameWithoutExtension, extension) = FileDescription.nameParts(url.href)
    InternetResourceFileDescription(url, nameWithoutExtension, extension, copyrightInfo)
  }

  def relativeToArtifactsFolder(pathRelativeToResourceFolder: String, copyrightInfo: CopyrightInfo = unknownCopyrightInfo): FileDescription = {
    val str = if (pathRelativeToResourceFolder.startsWith("/")) pathRelativeToResourceFolder.substring(1) else pathRelativeToResourceFolder
    val url = new URL(s"../../artifacts/" + str, dom.window.location.href)
    fromUrl(url, copyrightInfo)
  }

  def relativeToResourceFolder(pathRelativeToResourceFolder: String, copyrightInfo: CopyrightInfo = unknownCopyrightInfo): FileDescription = {
    val str = if (pathRelativeToResourceFolder.startsWith("/")) pathRelativeToResourceFolder.substring(1) else pathRelativeToResourceFolder
    val url = new URL(s"../../resources/" + str, dom.window.location.href)
    fromUrl(url, copyrightInfo)
  }

  def asDirectoryRelativeToResources(pathRelativeToResourceFolder: String, copyrightInfo: CopyrightInfo = unknownCopyrightInfo): FileDescription = {
    val str = if (pathRelativeToResourceFolder.startsWith("/")) pathRelativeToResourceFolder.substring(1) else pathRelativeToResourceFolder
    val url = new URL(s"../../resources" + str, dom.window.location.href)
    url.pathname = url.pathname + "/" + pathRelativeToResourceFolder
    fromUrl(url, copyrightInfo)
  }


}
