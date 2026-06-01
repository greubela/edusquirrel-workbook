package it.evadid.homepage.control.info.control

import com.raquo.laminar.api.L.StrictSignal
import it.evadid.core.datastructures.file.*
import it.evadid.core.datastructures.state.ObservableValue
import it.evadid.distribution.clients.ExecutionClient
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.homepage.control.WorkbookLanguageInfo.LabelLanguageMapStorage
import it.evadid.workbook.model.interaction.WorkbookInteraction
import todomove.datastructures.web.storage.AsyncDataCache

trait TechnicalControl {

  def fileStore: AsyncDataCache[FileDescription, LoadedFile]

  def languageMapStorage: LabelLanguageMapStorage = LabelLanguageMapStorage(fileStore)

  def makeFullscreen(element: HtmlAppElement): Unit

  def addLanguageFile(file: FileDescription): Unit

  def addLanguageFiles(files: List[FileDescription]): Unit

  def resetLocalStorage(): Unit

  def backendServerExecutor: ExecutionClient

  def workerServerExecutor: ExecutionClient


}
