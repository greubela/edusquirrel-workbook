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

  private[util] case class InternetResourceFileDescription(url: URL, copyrightInfo: CopyrightInfo, downloader: FetchFromRemote) extends FileDescription {

    override val toString: String = "InternetResourceFileDescription(" + url.href + ")"

    def loadData(): Future[LoadedFile] = {
      downloader.fetch(this)
      //HomepageFileStore.fetchUrl(url.href).map(data => LoadedFile(this, data))(using ExecutionContext.global)
    }

    override def getChildrenFile(childName: String, cCopyrightInfo: CopyrightInfo): Option[FileDescription] = if (structure.extension.nonEmpty) None else {
      Some(InternetResourceFileDescription(URL(url.href + "/" + childName), cCopyrightInfo, downloader))
    }

    override def asUrlString: String = url.href

  }

  private[util] case class UploadedResourceFileDescription(file: File, copyrightInfo: CopyrightInfo, downloader: FetchFromRemote) extends FileDescription {
    val location: Option[String] = None
    override val toString: String = "UploadedResourceFileDescription(" + file.name + ")"

    def loadData(): Future[LoadedFile] = downloader.fetch(this) //.fetchFile(file).map(data => LoadedFile(this, data))(using ExecutionContext.global)

    override def getChildrenFile(childName: String, cCopyrightInfo: CopyrightInfo): Option[FileDescription] = None
    override def asUrlString: String = file.name
  }

}

class FileFactory(downloader: FetchFromRemote) {
  
  def fromFile(file: File, copyrightInfo: CopyrightInfo = unknownCopyrightInfo): FileDescription = {
    UploadedResourceFileDescription(file, copyrightInfo, downloader)
  }

  def fromUrl(url: URL, copyrightInfo: CopyrightInfo = unknownCopyrightInfo): FileDescription = {
    InternetResourceFileDescription(url, copyrightInfo, downloader)
  }

}
