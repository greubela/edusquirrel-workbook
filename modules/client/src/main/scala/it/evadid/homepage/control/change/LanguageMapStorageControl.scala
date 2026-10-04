package it.evadid.homepage.control.change

import it.evadid.core.datastructures.language.AppLanguage.{Danish, German, HumanLanguage}
import it.evadid.core.datastructures.language.control.LanguageMapStorage
import it.evadid.core.datastructures.language.serialization.LanguageMapInputSource.{EvaDirectorySource, LanguageMapFileBasedSourceInfo}
import it.evadid.core.datastructures.language.serialization.abstractions.ParsedTriples
import it.evadid.core.datastructures.language.serialization.{LanguageMapCollectionSource, LanguageMapInputSource, LanguageMapSourceFileBased}
import it.evadid.homepage.control.change.LanguageMapStorageControl.STARTUP_STRATEGY
import it.evadid.homepage.control.change.LanguageMapStorageControl.STARTUP_STRATEGY.CONTINUE_AFTER_LOCAL_CACHE_SUCCESS
import it.evadid.homepage.control.model.FullInfo
import it.evadid.homepage.workbook.syncDestination.LocalIndexedDbStorageSync
import it.evadid.util.logging.Logger
import it.evadid.util.logging.LoggingLevel.WARN
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

  private lazy val localCache: SyncDestinationForType[LanguageMapStorage] = {
    LocalIndexedDbStorageSync.instanceForCaching.getSyncDestinationForType(syncLogger, "languageMapStore", LanguageMapStorage.serializerMain)
  }


  def ensureStartup(startupStrategy: STARTUP_STRATEGY): Future[?] = {

    val promise = Promise[Unit]()

    def readLocal: Future[LanguageMapStorage] = localCache.readElement

    def readRemote: Future[?] = ensureDefaultLanguageSourcesLoaded()

    readLocal.transformWith {
      case Success(store) => {
        if (store.parsedTriples.size > 0) {
          addTriplesAndStoreToCache(Set(), store.parsedTriples)
          if (startupStrategy == CONTINUE_AFTER_LOCAL_CACHE_SUCCESS) promise.success(())
        }
        readRemote
      }
      case Failure(err) => {
        syncLogger.logException("Ignored cached version of LanguageMapStorage!", err, Some(false), WARN)
        readRemote
      }
    }.onComplete {
      case Success(_) => {
        if(!promise.isCompleted) promise.success( () )
      }
      case Failure(err) => {
        syncLogger.logException("Could not read remote version of LanguageMapStorage!", err, Some(false), WARN)
        promise.failure(err)
      }
    }

    promise.future
  }

  private def addTriplesAndStoreToCache(loadedSources: Set[LanguageMapInputSource], loadedTriples: ParsedTriples): Unit = fullInfo.synchronized {
    fullInfo.homepageInfoState.update(curInfo => curInfo.copy(
      languageMapStore = {
        val newStorage = curInfo.languageMapStore.withLoadedTriples(contentControlLogger, loadedSources, loadedTriples)
        localCache.storeElement(newStorage)
        newStorage
      }
    ))
  }

  private def ensureDefaultLanguageSourcesLoaded(): Future[?] = {
    val loadLanguageMapDirs: Set[String] = Set(
      "basic", "login", "entitynames", "turtlestitch", "blockeditor", "embroideryworkbook", "testworkbook", "plantworkshop", "prompts", "compressionworkbook"
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
