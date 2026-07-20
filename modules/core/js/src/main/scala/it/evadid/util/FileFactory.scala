package it.evadid.util

import it.evadid.core.datastructures.file.CopyrightInfo.unknownCopyrightInfo
import it.evadid.core.datastructures.file.{CopyrightInfo, FileDescription, LoadedFile}
import it.evadid.util.FileFactory.{InternetResourceFileDescription, UploadedResourceFileDescription}
import it.evadid.workbook.abstractions.TypeOfTextDisplay
import it.evadid.workbook.abstractions.TypeOfTextDisplay.URL_TYPE
import org.scalajs.dom
import org.scalajs.dom.{File, URL}

import scala.concurrent.Future

object FileFactory {

  lazy val singleton: FileFactory = FileFactory(FetchFromRemote.singleton)

  private[util] case class InternetResourceFileDescription(url: URL, filenameWithoutExtension: String, extension: Option[String], copyrightInfo: CopyrightInfo, downloader: FetchFromRemote) extends FileDescription {

    override val toString: String = "InternetResourceFileDescription(" + fullPath + ")"

    def loadData(): Future[LoadedFile] = {
      downloader.fetch(this)
      //HomepageFileStore.fetchUrl(url.href).map(data => LoadedFile(this, data))(using ExecutionContext.global)
    }

    override def getChildrenFile(childName: String, cCopyrightInfo: CopyrightInfo): Option[FileDescription] = if (extension.nonEmpty) None else {
      val (cFilePath, cNameWithoutExtension, cExtension) = FileDescription.nameParts(childName)
      Some(InternetResourceFileDescription(URL(url.href + "/" + childName), cNameWithoutExtension, cExtension, cCopyrightInfo, downloader))
    }
  }

  private[util] case class UploadedResourceFileDescription(file: File, filenameWithoutExtension: String, extension: Option[String], copyrightInfo: CopyrightInfo, downloader: FetchFromRemote) extends FileDescription {
    val location: Option[String] = None
    override val toString: String = "UploadedResourceFileDescription(" + fullPath + ")"

    def loadData(): Future[LoadedFile] = downloader.fetch(this) //.fetchFile(file).map(data => LoadedFile(this, data))(using ExecutionContext.global)

    override def getChildrenFile(childName: String, cCopyrightInfo: CopyrightInfo): Option[FileDescription] = None
  }

}

class FileFactory(downloader: FetchFromRemote) {
  
  def fromFile(file: File, copyrightInfo: CopyrightInfo = unknownCopyrightInfo): FileDescription = {
    println("file: " + file.name)
    val parts = FileDescription.nameParts(file.name)
    UploadedResourceFileDescription(file, parts._2, parts._3, copyrightInfo, downloader)
  }

  def fromUrl(url: URL, copyrightInfo: CopyrightInfo = unknownCopyrightInfo): FileDescription = {
    val (filePath, nameWithoutExtension, extension) = FileDescription.nameParts(url.href)
    InternetResourceFileDescription(url, nameWithoutExtension, extension, copyrightInfo, downloader)
  }

}
