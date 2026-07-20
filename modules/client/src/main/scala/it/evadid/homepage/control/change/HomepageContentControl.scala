package it.evadid.homepage.control.change

import it.evadid.core.datastructures.file.*
import it.evadid.core.datastructures.file.CopyrightInfo.unknownCopyrightInfo
import it.evadid.core.datastructures.language.*
import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.core.datastructures.language.control.LanguageMapStorageControl
import it.evadid.core.datastructures.language.serialization.LanguageMapInputSource.EvaDirectorySource
import it.evadid.core.datastructures.language.serialization.{LanguageMapCollectionSource, LanguageMapInputSource}
import it.evadid.core.datastructures.state.State
import it.evadid.core.datastructures.state.observable.ObservableValue
import it.evadid.core.datastructures.storage.AsyncDataCache
import it.evadid.homepage.control.change.HomepageContentControl.HomepageFileFactory
import it.evadid.homepage.control.model.FullInfo
import it.evadid.homepage.control.singletons.{ HtmlFullWorkbookApp}
import it.evadid.util.FileFactory.{InternetResourceFileDescription, UploadedResourceFileDescription}
import it.evadid.util.logging.Logger
import it.evadid.util.{DownloadToDisc, FetchFromRemote, FileFactory, PostToRemote}
import it.evadid.workbook.abstractions.TypeOfTextDisplay
import it.evadid.workbook.abstractions.TypeOfTextDisplay.URL_TYPE
import org.scalajs.dom
import org.scalajs.dom.{File, URL}

import scala.concurrent.*
import scala.util.{Failure, Success}

case class HomepageContentControl(fullInfo: FullInfo, contentControlLogger: Logger, fileStorageLogger: Logger) {

  private given ec: ExecutionContext = ExecutionContext.global

  lazy val languageStorage: LanguageMapStorageControl = LanguageMapStorageControl(contentControlLogger, ec)

  lazy val downloadToDisc: DownloadToDisc = DownloadToDisc(contentControlLogger)
  lazy val postToRemote: PostToRemote = PostToRemote(contentControlLogger)
  lazy val fileFactory: HomepageFileFactory = HomepageFileFactory(fullInfo, fetchFromRemote)

  private[control] val fetchFromRemote: FetchFromRemote = FetchFromRemote(fileStorageLogger, ExecutionContext.global)

  def ensureDefaultLanguageSourcesLoaded(): Future[?] = {
    val snapFiles: Set[FileDescription] = Set(
      fileFactory.relativeToResourceFolder(s"programs/20260704Snap/locale/lang-de.js"),
      fileFactory.relativeToResourceFolder(s"programs/20260704Snap/locale/lang-en.js"),
    )

    def evaLangDir(dirName: String): EvaDirectorySource = EvaDirectorySource(dirName, fileFactory.relativeToResourceFolder(s"/languageMaps/eva/${dirName}"))

    val defaultEvaFiles: Set[LanguageMapInputSource] = Set(
      LanguageMapInputSource.forEvaLanguageMapFiles(fullInfo.defaults.loadLanguageMapDirs.map(evaLangDir))
    )
    languageStorage.ensureLanguageSourcesLoaded(defaultEvaFiles)
  }


}


object HomepageContentControl {

  case class HomepageFileFactory(fullInfo: FullInfo, ffr: FetchFromRemote) extends FileFactory(ffr) {
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

    def resolveFromTypeAndLanguageMapContent(howToResolveUrl: URL_TYPE, src: String): FileDescription = {
      howToResolveUrl.match {
        case TypeOfTextDisplay.URL_RELATIVE_TO_GLOBAL_RESOURCES => relativeToResourceFolder(src)
        case TypeOfTextDisplay.URL_RELATIVE_TO_WORKBOOK_RESOURCES(workbookRoot) => fromUrl(URL(workbookRoot.asUrlString + "/" + src))
      }
    }

    def onBackendServer(pathRelativeToBackendServer: String): FileDescription = {
      val toAdd = if(pathRelativeToBackendServer.startsWith("/")) pathRelativeToBackendServer else "/" + pathRelativeToBackendServer
      val urlStr = "https://" + fullInfo.defaults.defaultBackend.backendDomain + toAdd
      fromUrl(URL(urlStr))
    }

  }


}