package it.evadid.homepage.control.model

import com.raquo.laminar.api.L.*
import it.evadid.distribution.clients.ExecutionClient
import it.evadid.homepage.control.change.*
import it.evadid.homepage.control.info.*
import it.evadid.homepage.control.singletons.{BackendServerConfig, FileStore, HomepageDefaults}
import it.evadid.workbook.interaction.sync.SyncControl

case class FullInfo(
                     val defaults: HomepageDefaults,
                     private[control] val initInfo: HomepageInfo
                   ) {

  lazy val loggerSystemInfo: HomepageLoggerInfo = HomepageLoggerInfo.singleton

  lazy val fileStore: FileStore = FileStore(loggerSystemInfo.fileDataStorageLogger)

  lazy val backendExecutor: ExecutionClient = BackendServerConfig.executor

  lazy val executorForParallelism: List[ExecutionClient] = List(backendExecutor)
  //ExecuteOnRemoteServer("http://localhost", 9000),
  //ExecuteOnWebWorker(FileFactory.relativeToArtifactsFolder("/newest/backend-worker.js").fullPath),:

  private[control] val homepageInfoState: Var[HomepageInfo] = Var(initInfo)

  lazy val control: HomepageUsageControl = HomepageUsageControl(this)

  lazy val signals: HomepageSignalInfo = HomepageSignalInfo(this)

  lazy val current: HomepageCurrentInfo = HomepageCurrentInfo(this)

  lazy val syncControl: SyncControl = RemoteInteractionCacheControl(this)

  lazy val displayControl: DisplayControl = DisplayControl(this)


}

object FullInfo {


}
