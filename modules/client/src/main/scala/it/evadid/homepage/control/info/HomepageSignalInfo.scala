package it.evadid.homepage.control.info

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.*
import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.core.datastructures.language.control.{LanguageMapIdResolver, LanguageMapStorage}
import it.evadid.core.datastructures.state.StateHelper.*
import it.evadid.core.datastructures.state.observable.ObservableValue
import it.evadid.core.datastructures.storage.remote.WorkbookSyncEnvironment
import it.evadid.core.datastructures.user.AllUserInfo
import it.evadid.homepage.control.change.RemoteInteractionCacheControl.*
import it.evadid.homepage.control.model.*
import it.evadid.homepage.workbook.syncDestination.{DatabaseSyncViaBackendServerHandler, LocalIndexedDbStorageSyncHandler, LocalStorageSyncHandler}
import it.evadid.workbook.elements.structureElements.WorkbookSection
import it.evadid.workbook.interaction.sync.SyncInformation.*
import it.evadid.workbook.interaction.sync.UsageContext
import it.evadid.workbook.interaction.sync.*
import it.evadid.workbook.interaction.sync.destination.{SyncDestinationDefinition, SyncDestinationHistoryHandler}
import it.evadid.workbook.interaction.sync.destination.SyncDestinationDefinition.{LocalIndexedDbSyncDefinition, LocalStorageSyncDefinition, SyncDestinationBasedOnHistoryDatabase}

import it.evadid.workbook.abstractions.{InteractionFormat, WorkbookInteractionElement}
import it.evadid.homepage.control.info.interaction.InteractionReporters
import scala.concurrent.*

case class HomepageSignalInfo(fullInfo: FullInfo) {

  /** Only replacing the loaded workbook replaces its reporter instances. */
  lazy val interactionReporters: StrictSignal[Map[WorkbookInteractionElement[? <: InteractionFormat], InteractionReporters[? <: InteractionFormat]]] =
    baseSignal.map(_.workbookInfo.map(_.loadedWorkbook))
      .distinctByFn((previous, next) => previous.zip(next).exists((a, b) => a eq b) || (previous.isEmpty && next.isEmpty))
      .map(_.toSeq.flatMap(_.allContainedInteractionsById.values).map { interaction =>
        interaction -> new InteractionReporters(interaction, fullInfo)
      }.toMap).observe(using unsafeWindowOwner)

  def reportersFor(interaction: WorkbookInteractionElement[? <: InteractionFormat]): InteractionReporters[? <: InteractionFormat] =
    interactionReporters.now().get(interaction).filter(entry => entry.interaction eq interaction).getOrElse(
      throw new IllegalArgumentException(s"Interaction ${interaction.elementId} does not belong to the loaded workbook"))




  lazy val syncEnvironmentSignal: StrictSignal[Option[WorkbookSyncEnvironment]] = {

    fullInfo.homepageInfoState.signal.distinctByFn((previous, next) =>
      previous.toContext == next.toContext &&
        previous.workbookInfo.map(_.loadedWorkbook).zip(next.workbookInfo.map(_.loadedWorkbook)).forall((a, b) => a eq b) &&
        previous.workbookInfo.isDefined == next.workbookInfo.isDefined &&
        previous.userInfo.map(user => (user.config.syncDestinations, user.credentials)) ==
          next.userInfo.map(user => (user.config.syncDestinations, user.credentials))
    ).map(curHomepageInfo => {
      if (curHomepageInfo.userInfo.isEmpty || curHomepageInfo.workbookInfo.isEmpty) None
      else Some(WorkbookSyncEnvironment(
        curHomepageInfo.toContext,
        curHomepageInfo.workbookInfo.toSet.flatMap(_.loadedWorkbook.allContainedInteractionsById.values),
        curHomepageInfo.syncDestinations(fullInfo).toSet
      ))
    }).observe(using unsafeWindowOwner)


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


  lazy val currentUsageContext: StrictSignal[UsageContext] = baseSignal.mapLazy(_.toContext)



  /*def currentSyncSources: Seq[SyncInformationWithContext] = fullInfo.synchronized {
    val curContext: UsageContext = fullInfo.homepageInfoState.now().toContext
    val syncInformation: Seq[SyncInformation] = userInfo.map(_.config.syncDestinations.toList).toList.flatten
    val syncWithContext: Seq[SyncInformationWithContext] = syncInformation.map(_.forContext(fullInfo.current.currentHomepageContext, resolveSyncDefinition))
    syncWithContext
  }*/



  lazy val user: StrictSignal[Option[AllUserInfo]] = {
    baseSignal.mapLazy(_.userInfo)
  }

  lazy val availableLanguages: StrictSignal[Seq[HumanLanguage]] = {
    baseSignal.mapLazy(_.workbookInfo.map(_.loadedWorkbook.metadata.availableLanguages).getOrElse(fullInfo.defaults.defaultLanguagesAvailable))
  }

  lazy val currentLanguage: StrictSignal[HumanLanguage] = {
    baseSignal.mapLazy(_.currentLanguage)
  }

  /*def getLanguageMapIfLoaded(languageMapId: LanguageMapContentId): Option[LanguageMap[HumanLanguage]] =
    fullInfo.contentControl.languageStorage.getLanguageMapIfLoaded(languageMapId)*/


  def ensuredLanguageMapSignal(languageMapId: LanguageMapContentId): StrictSignal[LanguageMap[HumanLanguage]] = {
    val source = ensuredLanguageMap(languageMapId)
    // A fullscreen editor may create a label after the language map has loaded.
    // Observers receive future changes, so also seed the signal from the current value.
    val res: Var[LanguageMap[HumanLanguage]] = Var(source.now().getOrElse(LanguageMapStorage.languageMapLoading(languageMapId)))
    source.addObserver(newValue => res.set(newValue))
    res.signal
  }


  def ensuredLanguageMap(languageMapId: LanguageMapContentId): ObservableValue[LanguageMap[HumanLanguage]] = {
    val signal = fullInfo.homepageInfoState.signal.map(_.languageMapStore.languageMaps.getOrElse(languageMapId, LanguageMapStorage.languageMapLoading(languageMapId)))
    signal.toObservableValue
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
