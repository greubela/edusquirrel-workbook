package workbook.model.info

import com.raquo.laminar.api.L.*
import datastructures.core.language.{AppLanguage, HumanLanguage, LanguageMap}
import datastructures.web.file.{FileDescription, LoadedFile}
import datastructures.web.storage.AsyncDataCache
import workbook.htmlElements.container.HtmlFullScreenContainerElement
import workbook.singletons.{FileDataStorage, WorkbookLanguageInfo}
import workbook.singletons.WorkbookLanguageInfo.LabelLanguageMapStorage
import workbook.user.User

import scala.concurrent.ExecutionContext

case class AllWorkbookInfo() {

  private val defaultInfo = WorkbookInfo(
    List[HumanLanguage](AppLanguage.English, AppLanguage.German, AppLanguage.Ukrainian, AppLanguage.Danish, AppLanguage.Turkish),
    WorkbookConfig(AppLanguage.German, None, User("TestUser", "dummy@test.de")),
    Map())
  
  
  val workbookInfoVar: Var[WorkbookInfo] = Var(defaultInfo)

  val technicalElements: TechnicalWorkbookElements = TechnicalWorkbookElements(
    HtmlFullScreenContainerElement(),
    FileDataStorage()
  )

  def updateConfig(func: WorkbookConfig => WorkbookConfig): Unit = {
    workbookInfoVar.update(curInfo => curInfo.copy(config = func(curInfo.config)))
  }

  @deprecated("Use AllWorkbookInfo::stringSignalFromLanguageMapId instead", "2026-04-01")
  def stringSignalFromLanguageMap(languageMap: LanguageMap[HumanLanguage]): Signal[String] = {
    println("[WARN] language Map that got not transferred to a proper file: " + languageMap + " at: \n" + new Exception().getStackTrace.map(_.getMethodName).mkString("Array(", ", ", ")") + "\n")

    val languageSignal: StrictSignal[HumanLanguage] = workbookInfoVar.signal.mapLazy(_.config.currentWorkbookLanguage)
    languageSignal.mapLazy(languageMap.getInLanguage)
  }

  def stringSignalFromLanguageMapId(languageMapId: String)(ec: ExecutionContext): Signal[String] = {
    val languageSignal: StrictSignal[HumanLanguage] = workbookInfoVar.signal.mapLazy(_.config.currentWorkbookLanguage)
    val mapSignal: StrictSignal[LanguageMap[HumanLanguage]] = technicalElements.languageMapStorage
      .loadIntoVariable(languageMapId)(ec).signal
      .mapLazy(_.getOrElse(WorkbookLanguageInfo.languageMapLoadingMap))

    Signal.combine(languageSignal, mapSignal).map(tup => {
      tup._2.getInLanguage(tup._1)
    })
  }


  case class TechnicalWorkbookElements(
                                        fullScreenContainer: HtmlFullScreenContainerElement,
                                        fileStore: AsyncDataCache[FileDescription, LoadedFile],
                                      ) {

    private[AllWorkbookInfo] val languageMapStorage: LabelLanguageMapStorage = LabelLanguageMapStorage(fileStore)

    // load as soon as possible
    WorkbookLanguageInfo.languageMapFiles.foreach(fileStore.loadIntoVariable(_)(ExecutionContext.global))

  }


}

object AllWorkbookInfo {

  val singleton = AllWorkbookInfo()

}
