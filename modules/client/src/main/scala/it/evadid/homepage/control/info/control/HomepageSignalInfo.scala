package it.evadid.homepage.control.info.control

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.core.datastructures.language.{LanguageMap, LanguageMapContentId}
import it.evadid.core.datastructures.state.StateHelper.*
import it.evadid.homepage.control.*
import it.evadid.homepage.control.info.{AllWorkbookInfo, FullInfo, HomepageInfo}
import it.evadid.workbook.model.elements.WorkbookSection

import scala.concurrent.*

case class HomepageSignalInfo(fullInfo: FullInfo) {

  private lazy val baseSignal: StrictSignal[HomepageInfo] = {
    fullInfo.homepageInfoState.signal
  }

  lazy val activeSection: StrictSignal[Option[WorkbookSection]] = {
    workbook.mapLazy(_.flatMap(_.config.activeSection))
  }

  lazy val workbook: StrictSignal[Option[AllWorkbookInfo]] = {
    baseSignal.mapLazy(_.workbookInfo)
    // Var(None).signal
  }

  lazy val availableLanguages: StrictSignal[List[HumanLanguage]] = {
    val default = fullInfo.homepageInfoState.now().homepageDefaults.availableLanguages
    baseSignal.mapLazy(_.workbookInfo.map(_.loadedWorkbook.availableLanguages).getOrElse(default))
  }

  lazy val currentLanguage: StrictSignal[HumanLanguage] = baseSignal.mapLazy(_.currentLanguage)

  def languageMapOpFromId(languageMapId: LanguageMapContentId): StrictSignal[Option[LanguageMap[HumanLanguage]]] = {
    fullInfo.technical.contentStorage.asStorage.loadIntoVariable(languageMapId)(using ExecutionContext.global).toAirstreamVar.signal
  }

  def languageMapFromId(languageMapId: LanguageMapContentId): Signal[LanguageMap[HumanLanguage]] = {
    languageMapOpFromId(languageMapId).map {
      case None => WorkbookContentStorage.languageMapLoadingMap
      case Some(map) => map
    }
  }

  def stringFromLanguageMapId(languageMapId: LanguageMapContentId): Signal[String] = {
    Signal.combine(currentLanguage, languageMapFromId(languageMapId)).map(tup => {
      tup._2.getInLanguage(tup._1)
    })
  }

  /*def stringFromLanguageMapId(languageMapId: LanguageMapContentId): Signal[String] = {
    stringFromLanguageMapId(languageMapId. + "/" + languageMapId.languageMapKey)
  }*/


}
