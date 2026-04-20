package workbook.model.info

import com.raquo.laminar.api.L.Element
import datastructures.web.file.{FileDescription, LoadedFile}
import datastructures.web.storage.AsyncDataCache
import workbook.htmlElements.container.HtmlFullScreenContainerElement
import workbook.singletons.WorkbookLanguageInfo
import workbook.singletons.WorkbookLanguageInfo.LabelLanguageMapStorage

import scala.concurrent.ExecutionContext

case class TechnicalHomepageElements(
                                      private[info] fullScreenContainer: HtmlFullScreenContainerElement,
                                      fileStore: AsyncDataCache[FileDescription, LoadedFile],
                                    ) {
  private[info] val languageMapStorage: LabelLanguageMapStorage = LabelLanguageMapStorage(fileStore)

  // load as soon as possible
  WorkbookLanguageInfo.languageMapFiles.foreach(fileStore.loadIntoVariable(_)(ExecutionContext.global))

  def makeFullscreen(element: Element): Unit = {
    fullScreenContainer.setElementFullscreen(element)
  }

  def addLanguageFile(file: FileDescription): Unit = fileStore.synchronized {
    addLanguageFiles(List(file))
  }
  
  def addLanguageFiles(files: List[FileDescription]): Unit = fileStore.synchronized{
    val uniqueFiles = files.distinct
    languageMapStorage.addLanguageFiles(uniqueFiles)
    uniqueFiles.foreach(curFile =>
      languageMapStorage.languageTriplesStorage.loadIntoVariable(curFile)(ExecutionContext.global)
    )
    languageMapStorage.reloadAll()(ExecutionContext.global)
  }
  
  
}
