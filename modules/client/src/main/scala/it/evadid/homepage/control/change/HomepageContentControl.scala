package it.evadid.homepage.control.change

import it.evadid.core.datastructures.file.*
import it.evadid.core.datastructures.file.CopyrightInfo.unknownCopyrightInfo
import it.evadid.homepage.control.change.HomepageContentControl.HomepageFileFactory
import it.evadid.homepage.control.model.FullInfo
import it.evadid.util.logging.Logger
import it.evadid.util.{DownloadToDisc, FetchFromRemote, FileFactory, PostToRemote}
import it.evadid.workbook.abstractions.TypeOfTextDisplay
import it.evadid.workbook.abstractions.TypeOfTextDisplay.URL_RELATIVE_TO_WORKBOOK_RESOURCES
import org.scalajs.dom
import org.scalajs.dom.URL

import scala.concurrent.*

case class HomepageContentControl(fullInfo: FullInfo, contentControlLogger: Logger, fileStorageLogger: Logger) {

  private given ec: ExecutionContext = ExecutionContext.global

  lazy val languageStorage: LanguageMapStorageControl = LanguageMapStorageControl(fullInfo, contentControlLogger, ec)

  lazy val downloadToDisc: DownloadToDisc = DownloadToDisc(contentControlLogger)
  lazy val postToRemote: PostToRemote = PostToRemote(contentControlLogger)
  lazy val fileFactory: HomepageFileFactory = HomepageFileFactory(fullInfo, fetchFromRemote)

  private[control] val fetchFromRemote: FetchFromRemote = FetchFromRemote(fileStorageLogger, ExecutionContext.global)

}

object HomepageContentControl {

  case class HomepageFileFactory(fullInfo: FullInfo, ffr: FetchFromRemote) extends FileFactory(ffr) {

    def relativeToArtifactsFolder(pathRelativeToResourceFolder: String, copyrightInfo: CopyrightInfo = unknownCopyrightInfo): FileDescription = {
      relativeToLocalDir("../../artifacts/", pathRelativeToResourceFolder, copyrightInfo)
    }

    def relativeToLocalDir(localDir: String, pathRelativeToIt: String, copyrightInfo: CopyrightInfo = unknownCopyrightInfo): FileDescription = {
      val str = if (pathRelativeToIt.startsWith("/")) pathRelativeToIt.substring(1) else pathRelativeToIt
      val url = new URL(localDir + str, dom.window.location.href)
      fromUrl(url, copyrightInfo)
    }

    def relativeToTechnicalResources(pathRelativeToResourceFolder: String, copyrightInfo: CopyrightInfo = unknownCopyrightInfo): FileDescription = {
      relativeToLocalDir("../../resources", pathRelativeToResourceFolder, copyrightInfo)
    }

    def asDirectoryRelativeToResources(pathRelativeToResourceFolder: String, copyrightInfo: CopyrightInfo = unknownCopyrightInfo): FileDescription = {
      val str = if (pathRelativeToResourceFolder.startsWith("/")) pathRelativeToResourceFolder.substring(1) else pathRelativeToResourceFolder
      val url = new URL(s"../../resources" + str, dom.window.location.href)
      url.pathname = url.pathname + "/" + pathRelativeToResourceFolder
      fromUrl(url, copyrightInfo)
    }

    def resolveFromTypeAndLanguageMapContent(howToResolveUrl: TypeOfTextDisplay, src: String): FileDescription = {
      howToResolveUrl.match {
        case TypeOfTextDisplay.URL_RELATIVE_TO_TECHNICAL_RESOURCES => relativeToTechnicalResources(src)
        case URL_RELATIVE_TO_WORKBOOK_RESOURCES => relativeToTechnicalResources("workbookresources/" + src)
        case TypeOfTextDisplay.URL_ABSOLUTE => fromUrl(URL(src))
        case _ => throw new IllegalArgumentException(s"URL cannot be resolved with type ${howToResolveUrl}")
      }
    }

    def onBackendServer(pathRelativeToBackendServer: String): FileDescription = {
      val toAdd = if (pathRelativeToBackendServer.startsWith("/")) pathRelativeToBackendServer else "/" + pathRelativeToBackendServer
      val urlStr = "https://" + fullInfo.defaults.defaultBackend.backendDomain + toAdd
      fromUrl(URL(urlStr))
    }
  }


}