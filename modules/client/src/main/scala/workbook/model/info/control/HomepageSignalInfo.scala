package workbook.model.info.control

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.core.datastructures.language.LanguageMap
import workbook.model.WorkbookSection
import workbook.model.info.*
import workbook.singletons.WorkbookLanguageInfo

import scala.concurrent.*
case class HomepageSignalInfo(fullInfo: FullInfo) {

  private lazy val baseSignal: StrictSignal[HomepageInfo] = {
    fullInfo.homepageInfoVar.signal
  }

  lazy val activeSection: StrictSignal[Option[WorkbookSection]] = {
    workbook.mapLazy(_.flatMap(_.config.activeSection))
  }

  lazy val workbook: StrictSignal[Option[AllWorkbookInfo]] = {
    baseSignal.mapLazy(_.workbookInfo)
   // Var(None).signal
  }

  lazy val availableLanguages: StrictSignal[List[HumanLanguage]] = {
    val default = fullInfo.homepageInfoVar.now().homepageDefaults.availableLanguages
    baseSignal.mapLazy(_.workbookInfo.map(_.loadedWorkbook.availableInLanguages).getOrElse(default))
  }

  lazy val currentLanguage: StrictSignal[HumanLanguage] = baseSignal.mapLazy(_.currentLanguage)

  def languageMapOpFromId(languageMapId: String): StrictSignal[Option[LanguageMap[HumanLanguage]]] = {
    fullInfo.technical.languageMapStorage.loadIntoVariable(languageMapId)(ExecutionContext.global).signal
    //Var(None).signal
  }

  def languageMapFromId(languageMapId: String): Signal[LanguageMap[HumanLanguage]] = {
    languageMapOpFromId(languageMapId).map{
      case None => WorkbookLanguageInfo.languageMapLoadingMap
      case Some(map) => map
    }
  }

  def stringFromLanguageMapId(languageMapId: String): Signal[String] = {
    Signal.combine(currentLanguage, languageMapFromId(languageMapId)).map(tup => {
      tup._2.getInLanguage(tup._1)
    })
  }


}
