package it.evadid.homepage.control.change

import it.evadid.core.datastructures.language.AppLanguage.{Danish, German, HumanLanguage}
import it.evadid.core.datastructures.language.serialization.LanguageMapInputSource.{EvaDirectorySource, LanguageMapFileBasedSourceInfo}
import it.evadid.core.datastructures.language.serialization.abstractions.ParsedTriples
import it.evadid.core.datastructures.language.serialization.{LanguageMapCollectionSource, LanguageMapInputSource, LanguageMapSourceFileBased}
import it.evadid.homepage.control.change.LanguageMapStorageControl.STARTUP_STRATEGY
import it.evadid.homepage.control.model.FullInfo
import it.evadid.homepage.workbook.syncDestination.LocalIndexedDbStorageSync
import it.evadid.util.logging.Logger
import it.evadid.util.logging.derived.SyncLogger
import it.evadid.workbook.interaction.sync.destination.SyncDestination.SyncDestinationForType
import upickle.default.*

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success}

object LanguageMapStorageControl {

  enum STARTUP_STRATEGY derives ReadWriter {
    case CONTINUE_IMMEDIATELY
    case CONTINUE_AFTER_LOCAL_CACHE_SUCCESS
    case CONTINUE_AFTER_FULL_LOAD
  }

}

case class LanguageMapStorageControl(fullInfo: FullInfo, contentControlLogger: Logger, ec: ExecutionContext) {

  given ExecutionContext = ec

  val syncLogger: SyncLogger = fullInfo.loggerSystemInfo.syncControlLogger

  private lazy val localCache: SyncDestinationForType[ParsedTriples] = {
    LocalIndexedDbStorageSync.instanceForCaching.getSyncDestinationForType(syncLogger, "tripleCache", ParsedTriples.serializer)
  }


  def ensureStartup(startupStrategy: STARTUP_STRATEGY): Future[Unit] =
    LanguageCacheStartup.run(
      startupStrategy,
      () => localCache.readElement,
      triples => {
        syncLogger.logInfo(s"Restoring ${triples.size} language triples from IndexedDB")
        // Installing an existing cache must not serialize and write it again.
        addTriples(Set.empty, triples)
      },
      () => ensureDefaultLanguageSourcesLoaded(),
      work => { org.scalajs.dom.window.setTimeout(() => work(), 0); () },
      error => syncLogger.logExceptionWarn("Language cache/startup refresh unavailable", error))

  private def addTriples(loadedSources: Set[LanguageMapInputSource], loadedTriples: ParsedTriples): Unit =
    fullInfo.homepageInfoState.update(curInfo => curInfo.copy(
      languageMapStore = curInfo.languageMapStore.withLoadedTriples(contentControlLogger, loadedSources, loadedTriples)))

  private def addTriplesAndStoreToCache(loadedSources: Set[LanguageMapInputSource], loadedTriples: ParsedTriples): Unit = {
    addTriples(loadedSources, loadedTriples)
    if (loadedTriples.size > 0) {
      scala.util.Try(localCache.storeElement(fullInfo.homepageInfoNow().languageMapStore.parsedTriples))
        .fold(Future.failed, identity).recover { case scala.util.control.NonFatal(error) =>
          syncLogger.logExceptionWarn("Could not persist language cache", error)
          false
        }
    }
  }

  private def ensureDefaultLanguageSourcesLoaded(): Future[?] = {
    val loadLanguageMapDirs: Set[String] = Set(
      "basic", "login", "workbookSelection", "entitynames", "turtlestitch", "blockeditor", "embroideryworkbook", "testworkbook", "plantworkshop", "prompts", "compressionworkbook", "monksworkbook", "emailSimulator", "digitalWorkbooks", "blockchainworkbook"
    )

    val snapFiles: Set[LanguageMapInputSource] = Set(
      LanguageMapFileBasedSourceInfo[HumanLanguage](fullInfo.contentControl.fileFactory.relativeToTechnicalResources(s"programs/20260704Snap/locale/lang-de.js"), "originalSnap", German, ec),
      LanguageMapFileBasedSourceInfo[HumanLanguage](fullInfo.contentControl.fileFactory.relativeToTechnicalResources(s"programs/20260704Snap/locale/lang-dk.js"), "originalSnap", Danish, ec)
      //LanguageMapFileBasedSourceInfo[HumanLanguage](fileFactory.relativeToResourceFolder(s"programs/20260704Snap/locale/lang-en.js"), "originalSnap", English, ec),
    ).flatMap(LanguageMapSourceFileBased.forSnapFile(_, str => str))

    def evaLangDir(dirName: String): EvaDirectorySource = EvaDirectorySource(dirName, fullInfo.contentControl.fileFactory.relativeToTechnicalResources(s"/languageMaps/eva/${dirName}"))

    val defaultEvaFiles: Set[LanguageMapInputSource] = Set(
      LanguageMapInputSource.forEvaLanguageMapFiles(loadLanguageMapDirs.map(evaLangDir))
    )
    ensureLanguageSourcesLoaded(snapFiles ++ defaultEvaFiles)
  }

  def ensureLanguageSourceLoaded(source: LanguageMapInputSource): Future[?] = {
    ensureLanguageSourcesLoaded(List(source))
  }


  def ensureLanguageSourcesLoaded(newSources: IterableOnce[LanguageMapInputSource]): Future[?] = {
    val loadSources = newSources.iterator.toSet.diff(fullInfo.homepageInfoNow().languageMapStore.loadedSources)
    val resPromise: Promise[Unit] = Promise[Unit]()
    contentControlLogger.logInfo(s"Fetching ${loadSources} / ${newSources} (other did already exist)")
    LanguageMapCollectionSource(loadSources, ec).loadAllTriples(contentControlLogger).onComplete {
      case Success(triples) => {
        addTriplesAndStoreToCache(loadSources, triples)
        resPromise.success(())
      }
      case Failure(err) => {
        contentControlLogger.logExceptionWarn(s"ignoring all sources (${loadSources}) because of an uncatched error at loading", err)
        resPromise.failure(err)
      }
    }
    resPromise.future
  }


}
