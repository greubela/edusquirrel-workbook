package it.evadid.homepage.control.info.control

import it.evadid.core.datastructures.file.*
import it.evadid.distribution.clients.ExecutionClient
import it.evadid.homepage.control.*
import it.evadid.homepage.webElements.HtmlAppElement
import todomove.datastructures.web.storage.AsyncDataCache

trait TechnicalControl {

  def fileStore: AsyncDataCache[FileDescription, LoadedFile]

  val contentStorage: WorkbookContentStorage = WorkbookContentStorage(fileStore)

  def makeFullscreen(element: HtmlAppElement): Unit

  def resetLocalStorage(): Unit

  def backendServerExecutor: ExecutionClient

  def workerServerExecutor: ExecutionClient


}
