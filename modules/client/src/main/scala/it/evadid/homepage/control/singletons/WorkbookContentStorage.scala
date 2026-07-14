package it.evadid.homepage.control.singletons

import it.evadid.core.datastructures.file.*
import it.evadid.core.datastructures.language.*
import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.core.datastructures.state.State
import it.evadid.core.datastructures.state.observable.ObservableValue
import it.evadid.core.datastructures.storage.AsyncDataCache
import it.evadid.util.logging.Logger

import scala.concurrent.*
import scala.util.{Failure, Success}

case class WorkbookContentStorage(contentStorageLogger: Logger, fileStore: AsyncDataCache[FileDescription, LoadedFile]) {

  private given ExecutionContext = ExecutionContext.global

  private val languageStorage: State[WorkbookLanguageStorage] = State(WorkbookLanguageStorage.empty)

  def languageMapObservable(languageMapId: LanguageMapContentId): ObservableValue[Option[LanguageMap[HumanLanguage]]] = {
    languageStorage.observable.deriveValue(_.languageMaps.get(languageMapId))
  }

  def getLanguageMapIfLoaded(languageMapId: LanguageMapContentId): Option[LanguageMap[HumanLanguage]] = {
    languageStorage.now().languageMaps.get(languageMapId)
  }

  def ensureLoaded(newFiles: IterableOnce[FileDescription]): Future[?] = {
    fileStore.loadAllAsFuture(newFiles).transformWith {
      case Success(loadedFiles) =>
        languageStorage.update(_.withLoadedFiles(contentStorageLogger, loadedFiles.values.flatMap(_.toOption).toSet))
        Future.successful(())
      case Failure(err) =>
        contentStorageLogger.logExceptionWarn("WorkbookContentStorage::ensureLoaded failed, now ignoring values", err)
        Future.failed(err)
    }
  }

  def ensureDefaultLoaded(): Future[?] = ensureLoaded(WorkbookLanguageStorage.potentialLanguageMapFiles)

}


object WorkbookContentStorage {


}