package it.evadid.homepage.control.info.control

import it.evadid.core.datastructures.file.*
import it.evadid.core.datastructures.language.{AppLanguage, LanguageMap, LanguageMapContentId, LanguageMapIdResolver}
import it.evadid.distribution.clients.ExecutionClient
import it.evadid.homepage.control.*
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.core.datastructures.storage.AsyncDataCache

import scala.concurrent.{ExecutionContext, Future}

trait TechnicalControl {
  
  def fileStore: AsyncDataCache[FileDescription, LoadedFile]
  
  def makeFullscreen(element: HtmlAppElement): Unit

  def resetLocalStorage(): Unit

  def backendServerExecutor: ExecutionClient

  def workerServerExecutor: ExecutionClient

}
