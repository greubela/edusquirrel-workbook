package todomove.datastructures.web.file

import it.evadid.core.datastructures.file.CopyrightInfo.unknownCopyrightInfo
import it.evadid.core.datastructures.file.{CopyrightInfo, FileDescription, LoadedFile}
import it.evadid.homepage.util.web.DownloadHelper
import org.scalajs.dom
import org.scalajs.dom.{File, URL}

import scala.concurrent.{ExecutionContext, Future}

object FileFactory {

  private case class InternetResourceFileDescription(url: URL, serverLocationDir: String, filenameWithoutExtension: String, extension: String, copyrightInfo: CopyrightInfo) extends FileDescription {
    val location: Option[String] = Some(serverLocationDir)
    override val toString: String = "InternetResourceFileDescription(" + fullPath + ")"

    def loadData(): Future[LoadedFile] = DownloadHelper.fetchUrl(url.href).map(data => LoadedFile(this, data))(using ExecutionContext.global)
  }

  private case class UploadedResourceFileDescription(file: File, filenameWithoutExtension: String, extension: String, copyrightInfo: CopyrightInfo) extends FileDescription {
    val location: Option[String] = None
    override val toString: String = "UploadedResourceFileDescription(" + fullPath + ")"

    def loadData(): Future[LoadedFile] = DownloadHelper.fetchFile(file).map(data => LoadedFile(this, data))(using ExecutionContext.global)
    
  }


  def fromUploadedFile(file: File): FileDescription = fromFile(file)

  def fromFile(file: File, copyrightInfo: CopyrightInfo = unknownCopyrightInfo): FileDescription = {
    println("file: " + file.name)
    val parts = FileDescription.nameParts(file.name)
    UploadedResourceFileDescription(file, parts._2, parts._3, copyrightInfo)
  }

  def fromUrl(url: URL, copyrightInfo: CopyrightInfo = unknownCopyrightInfo): FileDescription = {
    val parts = FileDescription.nameParts(url.href)
    InternetResourceFileDescription(url, parts._1, parts._2, parts._3, copyrightInfo)
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
