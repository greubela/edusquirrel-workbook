package it.evadid.homepage.control.info

import it.evadid.workbook.session.AllWorkbookInfo
import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.*
import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.core.datastructures.language.control.LanguageMapStorage
import it.evadid.core.datastructures.storage.remote.WorkbookSyncEnvironment
import it.evadid.core.datastructures.user.AllUserInfo
import it.evadid.homepage.control.model.*
import it.evadid.workbook.elements.structureElements.WorkbookSection
import it.evadid.workbook.abstractions.{InteractionFormat, WorkbookInteractionElement}
import it.evadid.homepage.control.info.interaction.InteractionReporters

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

  private def baseSignal: StrictSignal[HomepageInfo] = fullInfo.homepageInfoState.signal

  lazy val activeSection: StrictSignal[Option[WorkbookSection]] =
    baseSignal.mapLazy(_.workbookInfo.flatMap(_.config.activeSection))

  lazy val display: StrictSignal[AllDisplayInfo] = baseSignal.mapLazy(_.displayInfo)

  lazy val workbook: StrictSignal[Option[AllWorkbookInfo]] = baseSignal.mapLazy(_.workbookInfo)

  lazy val user: StrictSignal[Option[AllUserInfo]] = baseSignal.mapLazy(_.userInfo)

  lazy val availableLanguages: StrictSignal[Seq[HumanLanguage]] =
    baseSignal.mapLazy(_.workbookInfo.map(_.loadedWorkbook.metadata.availableLanguages).getOrElse(fullInfo.defaults.defaultLanguagesAvailable))

  lazy val currentLanguage: StrictSignal[HumanLanguage] = baseSignal.mapLazy(_.currentLanguage)

  /** The language map, or its loading placeholder; owned by whoever observes it, so mounts release it. */
  def ensuredLanguageMapSignal(languageMapId: LanguageMapContentId): Signal[LanguageMap[HumanLanguage]] =
    baseSignal.map(_.languageMapStore.languageMaps.getOrElse(languageMapId, LanguageMapStorage.languageMapLoading(languageMapId))).distinct

  /** `content` in `language`, else English, else any language it has. */
  def localizedText(content: LanguageMap[HumanLanguage], language: HumanLanguage): String =
    if content.availableLanguages.isEmpty then content.getInLanguage(language)
    else content.getWithLanguagePreference(Seq(language, English))

  def stringFromLanguageMap(languageMap: LanguageMap[HumanLanguage]): StrictSignal[String] = {
    currentLanguage.mapLazy(curLang => localizedText(languageMap, curLang))
  }

  def stringFromLanguageMapId(languageMapId: LanguageMapContentId): Signal[String] = {
    Signal.combine(currentLanguage, ensuredLanguageMapSignal(languageMapId)).map(tup => {
      localizedText(tup._2, tup._1)
    })
  }

  def stringFromMapWithFallback(base: Signal[Option[LanguageMap[HumanLanguage]]], fallback: Signal[LanguageMap[HumanLanguage]]): Signal[String] = {
    Signal.combine(base, fallback, currentLanguage).map(trip => {
      val baseOp = trip._1.map(myMap => localizedText(myMap, trip._3))
      val other = localizedText(trip._2, trip._3)
      baseOp.getOrElse(other)
    })
  }

}
