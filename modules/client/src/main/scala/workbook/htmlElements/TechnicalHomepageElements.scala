package workbook.htmlElements

import com.raquo.laminar.api.L.{Element, StrictSignal}
import contentmanagement.webElements.HtmlAppElement
import datastructures.web.file.{FileDescription, LoadedFile}
import datastructures.web.storage.AsyncDataCache
import it.evadid.core.datastructures.state.{ObservableValue, State}
import org.scalajs.dom
import workbook.htmlElements.container.HtmlFullScreenContainerElement
import workbook.model.info.control.TechnicalControl
import workbook.singletons.WorkbookLanguageInfo
import workbook.singletons.WorkbookLanguageInfo.LabelLanguageMapStorage

import scala.concurrent.ExecutionContext



case class TechnicalHomepageElements(
                                      fullScreenContainer: HtmlFullScreenContainerElement,
                                      fileStore: AsyncDataCache[FileDescription, LoadedFile],
                                    ) extends TechnicalControl{

  override val languageMapStorage: LabelLanguageMapStorage = LabelLanguageMapStorage(fileStore)

  // load as soon as possible
  WorkbookLanguageInfo.languageMapFiles.foreach(fileStore.loadIntoVariable(_)(using ExecutionContext.global))

  def makeFullscreen(element: HtmlAppElement): Unit = {
    fullScreenContainer.setElementFullscreen(element.getDomElement())
  }

  def addLanguageFile(file: FileDescription): Unit = fileStore.synchronized {
    addLanguageFiles(List(file))
  }

  def addLanguageFiles(files: List[FileDescription]): Unit = fileStore.synchronized {
    val uniqueFiles = files.distinct
    languageMapStorage.addLanguageFiles(uniqueFiles)
    uniqueFiles.foreach(curFile =>
      languageMapStorage.languageTriplesStorage.loadIntoVariable(curFile)(using ExecutionContext.global)
    )
    languageMapStorage.reloadAll()(using ExecutionContext.global)
  }

  def resetLocalStorage(): Unit = {
    val map = (0 until dom.window.localStorage.length)
      .flatMap { i =>
        Option(dom.window.localStorage.key(i)).flatMap { key =>
          Option(dom.window.localStorage.getItem(key)).map(value => key -> value)
        }
      }
      .toMap

    println("[WARN] resetting local storage in FullInfo! (CallStack: " + new Exception().getStackTrace.take(6).map(_.getMethodName).mkString(" -> ") + ")")

    map.keys.foreach(curKey => {
      println(curKey.toString + " -> " + map(curKey))
    })

    dom.window.localStorage.clear()
  }


}
