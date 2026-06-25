package it.evadid.homepage.control

import it.evadid.homepage.control.*
import it.evadid.core.datastructures.file.{FileDescription, LoadedFile}
import it.evadid.distribution.clients.ExecutionClient
import it.evadid.homepage.control.info.control.TechnicalControl
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.homepage.webElements.basic.HtmlFullScreenContainerElement
import org.scalajs.dom
import it.evadid.core.datastructures.storage.AsyncDataCache

import scala.concurrent.{ExecutionContext, Future, Promise}

case class TechnicalHomepageElements(
                                      fullScreenContainer: HtmlFullScreenContainerElement,
                                      fileStore: AsyncDataCache[FileDescription, LoadedFile],
                                      backendServerExecutor: ExecutionClient,
                                      workerServerExecutor: ExecutionClient,
                                    ) extends TechnicalControl {
  


  def makeFullscreen(element: HtmlAppElement): Unit = {
    fullScreenContainer.setElementFullscreen(element)
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
