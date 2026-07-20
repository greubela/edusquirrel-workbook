package it.evadid.core.datastructures.language.control

import it.evadid.core.datastructures.language.AppLanguage.HumanLanguage
import it.evadid.core.datastructures.language.serialization.{LanguageMapCollectionSource, LanguageMapInputSource}
import it.evadid.core.datastructures.language.{AppLanguage, LanguageMap, LanguageMapContentId}
import it.evadid.core.datastructures.state.State
import it.evadid.core.datastructures.state.observable.ObservableValue
import it.evadid.util.logging.Logger

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success}

case class LanguageMapStorageControl(contentControlLogger: Logger, ec: ExecutionContext) {

  given ExecutionContext = ec

  def langMapIdResolver(forLanguageObservable: ObservableValue[HumanLanguage]): LanguageMapIdResolver = new LanguageMapIdResolver(forLanguageObservable) {
    override def resolveMap(id: LanguageMapContentId): Future[LanguageMap[AppLanguage.HumanLanguage]] = {
      val res: Promise[LanguageMap[AppLanguage.HumanLanguage]] = Promise()
      languageMapObservable(id).addObserver((onNextValue: Option[LanguageMap[HumanLanguage]]) => if (onNextValue.isDefined) res.success(onNextValue.get))
      res.future
    }
  }

  val languageStorage: State[LanguageMapStorage] = State(LanguageMapStorage.empty)

  def ensureLanguageSourceLoaded(source: LanguageMapInputSource): Future[?] = {
    ensureLanguageSourcesLoaded(List(source))
  }

  def ensureLanguageSourcesLoaded(newSources: IterableOnce[LanguageMapInputSource]): Future[?] = {
    val loadSources = languageStorage.now().loadedSources.diff(newSources.iterator.toSet)
    val resPromise: Promise[Unit] = Promise[Unit]()

    LanguageMapCollectionSource(loadSources, ec).loadAllTriples(contentControlLogger).onComplete {
      case Success(triples) => {
        languageStorage.update(_.withLoadedTriples(contentControlLogger, loadSources, triples))
        resPromise.success(())
      }
      case Failure(err) => {
        contentControlLogger.logExceptionWarn(s"ignoring all sources (${loadSources}) because of an uncatched error at loading", err)
        resPromise.failure(err)
      }
    }
    resPromise.future
  }


  def languageMapObservable(languageMapId: LanguageMapContentId): ObservableValue[Option[LanguageMap[HumanLanguage]]] = {
    languageStorage.observable.deriveValue(_.languageMaps.get(languageMapId))
  }

  def getLanguageMapIfLoaded(languageMapId: LanguageMapContentId): Option[LanguageMap[HumanLanguage]] = {
    languageStorage.now().languageMaps.get(languageMapId)
  }


}
