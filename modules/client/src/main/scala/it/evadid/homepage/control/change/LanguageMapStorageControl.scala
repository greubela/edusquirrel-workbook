package it.evadid.homepage.control.change

import it.evadid.core.datastructures.language.AppLanguage.{Danish, German, HumanLanguage}
import it.evadid.core.datastructures.language.control.LanguageMapIdResolver
import it.evadid.core.datastructures.language.serialization.LanguageMapInputSource.{EvaDirectorySource, LanguageMapFileBasedSourceInfo}
import it.evadid.core.datastructures.language.serialization.abstractions.ParsedTriples
import it.evadid.core.datastructures.language.serialization.{LanguageMapCollectionSource, LanguageMapInputSource, LanguageMapSourceFileBased}
import it.evadid.core.datastructures.language.{AppLanguage, LanguageMap, LanguageMapContentId}
import it.evadid.core.datastructures.state.observable.ObservableValue
import it.evadid.homepage.control.model.FullInfo
import it.evadid.util.logging.Logger

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success}

case class LanguageMapStorageControl(fullInfo: FullInfo, contentControlLogger: Logger, ec: ExecutionContext) {

  given ExecutionContext = ec


  /*
  loadedTriples


  def langMapIdResolver(forLanguageObservable: ObservableValue[HumanLanguage]): LanguageMapIdResolver = new LanguageMapIdResolver(forLanguageObservable) {
    override def resolveMap(id: LanguageMapContentId): Future[LanguageMap[AppLanguage.HumanLanguage]] = {
      val res: Promise[LanguageMap[AppLanguage.HumanLanguage]] = Promise()
      languageMapObservable(id).addObserver((onNextValue: Option[LanguageMap[HumanLanguage]]) => if (onNextValue.isDefined) res.success(onNextValue.get))
      res.future
    }
  }*/

  def ensureDefaultLanguageSourcesLoaded(): Future[?] = {
    val loadLanguageMapDirs: Set[String] = Set(
      "basic", "entitynames", "turtlestitch", "blockeditor", "embroideryworkbook", "testworkbook", "plantworkshop", "prompts", "compressionworkbook"
    )

    val snapFiles: Set[LanguageMapInputSource] = Set(
      LanguageMapFileBasedSourceInfo[HumanLanguage](fullInfo.contentControl.fileFactory.relativeToResourceFolder(s"programs/20260704Snap/locale/lang-de.js"), "originalSnap", German, ec),
      LanguageMapFileBasedSourceInfo[HumanLanguage](fullInfo.contentControl.fileFactory.relativeToResourceFolder(s"programs/20260704Snap/locale/lang-dk.js"), "originalSnap", Danish, ec)
      //LanguageMapFileBasedSourceInfo[HumanLanguage](fileFactory.relativeToResourceFolder(s"programs/20260704Snap/locale/lang-en.js"), "originalSnap", English, ec),
    ).flatMap(LanguageMapSourceFileBased.forSnapFile(_, str => str))

    def evaLangDir(dirName: String): EvaDirectorySource = EvaDirectorySource(dirName, fullInfo.contentControl.fileFactory.relativeToResourceFolder(s"/languageMaps/eva/${dirName}"))

    val defaultEvaFiles: Set[LanguageMapInputSource] = Set(
      LanguageMapInputSource.forEvaLanguageMapFiles(loadLanguageMapDirs.map(evaLangDir))
    )
    ensureLanguageSourcesLoaded(snapFiles ++ defaultEvaFiles)
  }

  def ensureLanguageSourceLoaded(source: LanguageMapInputSource): Future[?] = {
    ensureLanguageSourcesLoaded(List(source))
  }

  def addTriples(loadedSources: Set[LanguageMapInputSource], loadedTriples: ParsedTriples): Unit = fullInfo.synchronized {
    fullInfo.homepageInfoState.update(curInfo => curInfo.copy(
      languageMapStore = curInfo.languageMapStore.withLoadedTriples(contentControlLogger, loadedSources, loadedTriples)
    ))
  }

  def ensureLanguageSourcesLoaded(newSources: IterableOnce[LanguageMapInputSource]): Future[?] = {
    val loadSources = newSources.iterator.toSet.diff(fullInfo.homepageInfoNow().languageMapStore.loadedSources)
    val resPromise: Promise[Unit] = Promise[Unit]()
    contentControlLogger.logInfo(s"Fetching ${loadSources} / ${newSources} (other did already exist)")
    LanguageMapCollectionSource(loadSources, ec).loadAllTriples(contentControlLogger).onComplete {
      case Success(triples) => {
        addTriples(loadSources, triples)
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
