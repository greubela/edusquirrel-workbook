package workbook.model.info.control

import com.raquo.laminar.api.L.StrictSignal
import contentmanagement.webElements.HtmlAppElement
import datastructures.web.file.{FileDescription, LoadedFile}
import datastructures.web.storage.AsyncDataCache
import it.evadid.core.datastructures.state.ObservableValue
import it.evadid.distribution.clients.ExecutionClient
import workbook.singletons.WorkbookLanguageInfo.LabelLanguageMapStorage

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
