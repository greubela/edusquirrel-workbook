package it.evadid.homepage.control.model

import it.evadid.core.datastructures.file.{FileDescription, LoadedFile}
import it.evadid.core.datastructures.storage.AsyncDataCache
import it.evadid.distribution.clients.ExecutionClient
import it.evadid.homepage.webElements.HtmlAppElement

trait TechnicalControl {

  def fileStore: AsyncDataCache[FileDescription, LoadedFile]

  def makeFullscreen(element: HtmlAppElement): Unit

  def backendServerExecutor: ExecutionClient


  // def workerServerExecutor: ExecutionClient

}
