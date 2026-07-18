package it.evadid.homepage.control.change

import it.evadid.core.datastructures.file.{FileDescription, LoadedFile}
import it.evadid.core.datastructures.storage.AsyncDataCache
import it.evadid.distribution.clients.ExecutionClient

trait TechnicalControl {

  def fileStore: AsyncDataCache[FileDescription, LoadedFile]

  def backendServerExecutor: ExecutionClient


  // def workerServerExecutor: ExecutionClient

}
