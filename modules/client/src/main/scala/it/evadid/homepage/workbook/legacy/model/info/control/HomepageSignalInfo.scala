package it.evadid.homepage.workbook.legacy.model.info.control

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.core.datastructures.language.{LanguageMap, LanguageMapContentId}
import it.evadid.core.datastructures.state.StateHelper.*
import it.evadid.homepage.workbook.legacy.model.info.{AllWorkbookInfo, FullInfo, HomepageInfo}
import it.evadid.homepage.workbook.legacy.singletons.WorkbookLanguageInfo
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

  def languageMapOpFromId(languageMapId: String): StrictSignal[Option[LanguageMap[HumanLanguage]]] = {
    fullInfo.technical.languageMapStorage.loadIntoVariable(languageMapId)(using ExecutionContext.global).toAirstreamVar.signal
    //Var(None).signal
  }

  def languageMapFromId(languageMapId: String): Signal[LanguageMap[HumanLanguage]] = {
    languageMapOpFromId(languageMapId).map {
      case None => WorkbookLanguageInfo.languageMapLoadingMap
      case Some(map) => map
    }
  }

  def stringFromLanguageMapId(languageMapId: String): Signal[String] = {
    Signal.combine(currentLanguage, languageMapFromId(languageMapId)).map(tup => {
      tup._2.getInLanguage(tup._1)
    })
  }

  def stringFromLanguageMapId(languageMapId: LanguageMapContentId): Signal[String] = {
    stringFromLanguageMapId(languageMapId.languageMapIdentifier + "/" + languageMapId.languageMapKey)
  }


}
