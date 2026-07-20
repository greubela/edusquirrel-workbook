package it.evadid.homepage.control.info

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.*
import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.core.datastructures.state.StateHelper.*
import it.evadid.core.datastructures.state.observable.ObservableValue
import it.evadid.homepage.control.model.*
import it.evadid.homepage.control.singletons.{WorkbookContentStorage, WorkbookLanguageStorage}
import it.evadid.workbook.elements.structureElements.WorkbookSection
import it.evadid.workbook.interaction.sync.SyncInformation.SyncInformationWithContext
import it.evadid.workbook.interaction.sync.UsageContext

import scala.concurrent.*

case class HomepageSignalInfo(fullInfo: FullInfo) {

  lazy val contentStorage: WorkbookContentStorage = WorkbookContentStorage(fullInfo.loggerSystemInfo.contentStorageLogger)

  lazy val langMapIdResolver: LanguageMapIdResolver = new LanguageMapIdResolver(fullInfo.signals.currentLanguage.toObservableValue) {
    override def resolveMap(id: LanguageMapContentId): Future[LanguageMap[AppLanguage.HumanLanguage]] = {
      val res: Promise[LanguageMap[AppLanguage.HumanLanguage]] = Promise()
      contentStorage.languageMapObservable(id).addObserver((onNextValue: Option[LanguageMap[HumanLanguage]]) => if (onNextValue.isDefined) res.success(onNextValue.get))
      res.future
    }
  }

  private lazy val baseSignal: StrictSignal[HomepageInfo] = {
    fullInfo.homepageInfoState.signal
  }

  lazy val currentDisplayInfo: StrictSignal[AllDisplayInfo] = baseSignal.mapLazy(_.displayInfo)

  lazy val activeSection: StrictSignal[Option[WorkbookSection]] = {
    baseSignal.mapLazy(_.workbookInfo.flatMap(_.config.activeSection))
  }

  lazy val display: StrictSignal[AllDisplayInfo] = baseSignal.mapLazy(_.displayInfo)

  lazy val workbook: StrictSignal[Option[AllWorkbookInfo]] = {
    baseSignal.mapLazy(_.workbookInfo)
    // Var(None).signal
  }

  lazy val currentSyncDestinationObservable: ObservableValue[List[SyncInformationWithContext]] = {
    currentSyncDestinations.toObservableValue
  }

  lazy val currentUsageContext: StrictSignal[UsageContext] = baseSignal.mapLazy(_.toContext)

  lazy val currentSyncDestinations: StrictSignal[List[SyncInformationWithContext]] = {
    baseSignal.mapLazy(homepageInfo => {
      homepageInfo.userInfo.map(_.config.syncDestinations.map(_.forContext(homepageInfo.toContext))).getOrElse(List())
    })
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

  def getLanguageMapIfLoaded(languageMapId: LanguageMapContentId): Option[LanguageMap[HumanLanguage]] = contentStorage.getLanguageMapIfLoaded(languageMapId)


  def ensuredLanguageMapSignal(languageMapId: LanguageMapContentId): StrictSignal[LanguageMap[HumanLanguage]] = {
    val res: Var[LanguageMap[HumanLanguage]] = Var(WorkbookLanguageStorage.languageMapLoading(languageMapId))
    ensuredLanguageMap(languageMapId).addObserver(newValue => res.set(newValue))
    res.signal
  }


  def ensuredLanguageMap(languageMapId: LanguageMapContentId): ObservableValue[LanguageMap[HumanLanguage]] = {

    contentStorage.languageMapObservable(languageMapId).deriveValue(_.getOrElse(WorkbookLanguageStorage.languageMapLoading(languageMapId)))

    /*val languageMapOpFromId: AsyncData[Nothing, LanguageMap[HumanLanguage]] = contentStorage.asStorage.loadIntoVariable(languageMapId)(using ExecutionContext.global)
    languageMapOpFromId.toStateSignal.mapLazy {
      case AsyncDataLoading() => .languageMapLoading(languageMapId)
      case AsyncDataSuccess(map) => map
      case AsyncDataFailed(cause, data) => WorkbookContentStorage.languageMapError(languageMapId, cause)
    }*/
  }

  def stringFromLanguageMap(languageMap: LanguageMap[HumanLanguage]): StrictSignal[String] = {
    currentLanguage.mapLazy(curLang => languageMap.getInLanguage(curLang))
  }

  def stringFromLanguageMapId(languageMapId: LanguageMapContentId): Signal[String] = {
    Signal.combine(currentLanguage, ensuredLanguageMapSignal(languageMapId)).map(tup => {
      tup._2.getInLanguage(tup._1)
    })
  }

  def stringFromMapWithFallback(base: Signal[Option[LanguageMap[HumanLanguage]]], fallback: Signal[LanguageMap[HumanLanguage]]): Signal[String] = {
    Signal.combine(base, fallback, currentLanguage).map(trip => {
      val baseOp = trip._1.map(myMap => myMap.getInLanguage(trip._3))
      val other = trip._2.getInLanguage(trip._3)
      baseOp.getOrElse(other)
    })
  }

}
