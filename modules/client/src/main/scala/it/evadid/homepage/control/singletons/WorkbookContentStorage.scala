package it.evadid.homepage.control.singletons

import it.evadid.core.datastructures.file.*
import it.evadid.core.datastructures.language.*
import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.core.datastructures.language.serialization.{LanguageMapCollectionSource, LanguageMapInputSource}
import it.evadid.core.datastructures.state.State
import it.evadid.core.datastructures.state.observable.ObservableValue
import it.evadid.core.datastructures.storage.AsyncDataCache
import it.evadid.homepage.control.model.FullInfo
import it.evadid.util.logging.Logger

import scala.concurrent.*
import scala.util.{Failure, Success}

case class WorkbookContentStorage(contentStorageLogger: Logger) {

  private given ExecutionContext = ExecutionContext.global

  private val languageStorage: State[WorkbookLanguageStorage] = State(WorkbookLanguageStorage.empty)

  def languageMapObservable(languageMapId: LanguageMapContentId): ObservableValue[Option[LanguageMap[HumanLanguage]]] = {
    languageStorage.observable.deriveValue(_.languageMaps.get(languageMapId))
  }

  def getLanguageMapIfLoaded(languageMapId: LanguageMapContentId): Option[LanguageMap[HumanLanguage]] = {
    languageStorage.now().languageMaps.get(languageMapId)
  }

  /*
    def withLoadedSources(logger: Logger, newSources: IterableOnce[LanguageMapInputSource]): WorkbookLanguageStorage = {
    val maybeAdd: Set[LanguageMapInputSource] = newSources.iterator.toSet
    logger.logInfo(s"Adding ${sourcesToLoad.size} from the proposed ${maybeAdd.size} to WorkbookLanguageStorage (others already loaded)!")
    val triples: ParsedTriples = WorkbookLanguageStorage.loadParsedTriples(logger, sourcesToLoad)
    withLoaded(logger, sourcesToLoad, triples)
  }

   */

  def ensureLoaded(source: LanguageMapInputSource): Future[?] = {
    // , reloadEvenIfPresent: Boolean = false
    ensureLoaded(List(source))

    /*val isContained = languageStorage.now().loadedSources.contains(sources)
    /*val futureTriples: Future[Set[ParsedTriples]] = Future.traverse(sources)(_.loadAllTriples.recover { case (e: Exception) =>
      contentStorageLogger.logExceptionWarn(s"ignoring triples from a source because it failed to load", e)
      ParsedTriples(Set(), Set())
    })*/
    if (isContained && !reloadEvenIfPresent) contentStorageLogger.logInfo(s"Skip loading ${source} again: already existing (force reloading deactivated)")
    else {
      if (isContained) contentStorageLogger.logInfo(s"Loading ${source} again: force reloading activated")
      else contentStorageLogger.logInfo(s"Loading ${source}: not loaded yet!")
      source.loadAllTriples*/
    }

    def ensureLoaded(newSources: IterableOnce[LanguageMapInputSource]): Future[?] = {
      val loadSources = languageStorage.now().loadedSources.diff(newSources.iterator.toSet)
      val resPromise: Promise[Unit] = Promise[Unit]()

      LanguageMapCollectionSource(loadSources).loadAllTriples(contentStorageLogger).onComplete {
        case Success(triples) => {
          languageStorage.update(_.withLoadedTriples(contentStorageLogger, loadSources, triples))
          resPromise.success(())
        }
        case Failure(err) => {
          contentStorageLogger.logExceptionWarn(s"ignoring all sources (${loadSources}) because of an uncatched error at loading", err)
          resPromise.failure(err)
        }
      }
      resPromise.future
    }


    def ensureDefaultLoaded(): Future[?] = ensureLoaded(HtmlFullWorkbookApp.fullInfo.defaults.defaultInputSources)

  }


  object WorkbookContentStorage {


  }