package it.evadid.homepage.control.info.control

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.core.datastructures.language.{AppLanguage, LanguageMap, LanguageMapContentId, LanguageMapIdResolver}
import it.evadid.core.datastructures.state.StateHelper.*
import it.evadid.homepage.control.*
import it.evadid.homepage.control.info.{AllUserInfo, AllWorkbookInfo, FullInfo, HomepageInfo}
import it.evadid.workbook.model.elements.WorkbookSection
import it.evadid.core.datastructures.storage.AsyncData
import it.evadid.core.datastructures.storage.AsyncData.*

import scala.concurrent.*
import it.evadid.core.datastructures.state.StateHelper.*
case class HomepageSignalInfo(fullInfo: FullInfo) {

  lazy val contentStorage: WorkbookContentStorage = WorkbookContentStorage(fullInfo.technical.fileStore)
  
  lazy val langMapIdResolver: LanguageMapIdResolver = new LanguageMapIdResolver(fullInfo.signals.currentLanguage.toObservableValue) {
    override def resolveMap(id: LanguageMapContentId): Future[LanguageMap[AppLanguage.HumanLanguage]] = {
      contentStorage.asStorage.loadAsFuture(id)(using ExecutionContext.global)
    }
  }

  private lazy val baseSignal: StrictSignal[HomepageInfo] = {
    fullInfo.homepageInfoState.signal
  }

  lazy val activeSection: StrictSignal[Option[WorkbookSection]] = {
    baseSignal.mapLazy(_.workbookInfo.flatMap(_.config.activeSection))
  }

  lazy val workbook: StrictSignal[Option[AllWorkbookInfo]] = {
    baseSignal.mapLazy(_.workbookInfo)
    // Var(None).signal
  }

  lazy val currentUserInfo: StrictSignal[Option[AllUserInfo]] = {
    baseSignal.mapLazy(_.userInfo)
  }

  lazy val availableLanguages: StrictSignal[List[HumanLanguage]] = {
    val default = fullInfo.homepageInfoState.now().homepageDefaults.availableLanguages
    baseSignal.mapLazy(_.workbookInfo.map(_.loadedWorkbook.availableLanguages).getOrElse(default))
  }

  lazy val currentLanguage: StrictSignal[HumanLanguage] = {
    baseSignal.mapLazy(_.currentLanguage)
  }

  def getLanguageMapIfLoaded(languageMapId: LanguageMapContentId): Option[LanguageMap[HumanLanguage]] = {
    contentStorage.getSyncIfLoaded(languageMapId)
  }

  def signalWithEnsuredLanguageMap(languageMapId: LanguageMapContentId): Signal[LanguageMap[HumanLanguage]] = {
    val languageMapOpFromId: StrictSignal[AsyncData[LanguageMap[HumanLanguage]]] = contentStorage.asStorage.loadIntoVariable(languageMapId)(using ExecutionContext.global).toAirstreamVar.signal
    languageMapOpFromId.mapLazy {
      case AsyncDataLoading() => WorkbookContentStorage.languageMapLoadingMap
      case AsyncDataSuccess(map) => map
      case AsyncDataFailed(cause) => WorkbookContentStorage.languageMapError(languageMapId, cause)
    }
  }

  def stringFromLanguageMap(languageMap: LanguageMap[HumanLanguage]): Signal[String] = {
    currentLanguage.mapLazy(curLang => languageMap.getInLanguage(curLang))
  }

  def stringFromLanguageMapId(languageMapId: LanguageMapContentId): Signal[String] = {
    Signal.combine(currentLanguage, signalWithEnsuredLanguageMap(languageMapId)).map(tup => {
      tup._2.getInLanguage(tup._1)
    })
  }

}
