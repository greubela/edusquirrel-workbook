package it.evadid.homepage.workbook.legacy.htmlElements

import com.raquo.laminar.api.L.{Element, StrictSignal}
import it.evadid.core.datastructures.file.*
import it.evadid.core.datastructures.state.{ObservableValue, State}
import it.evadid.distribution.clients.ExecutionClient
import it.evadid.homepage.workbook.legacy.model.info.control.TechnicalControl
import it.evadid.homepage.workbook.legacy.singletons.WorkbookLanguageInfo
import org.scalajs.dom
import WorkbookLanguageInfo.LabelLanguageMapStorage
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.homepage.workbook.legacy.htmlElements.container.HtmlFullScreenContainerElement
import todomove.datastructures.web.storage.AsyncDataCache

import scala.concurrent.ExecutionContext


case class TechnicalHomepageElements(
                                      fullScreenContainer: HtmlFullScreenContainerElement,
                                      fileStore: AsyncDataCache[FileDescription, LoadedFile],
                                      backendServerExecutor: ExecutionClient,
                                      workerServerExecutor: ExecutionClient,
                                    ) extends TechnicalControl {

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
