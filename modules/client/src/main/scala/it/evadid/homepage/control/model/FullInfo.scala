package it.evadid.homepage.control.model

import com.raquo.laminar.api.L.*
import it.evadid.distribution.clients.ExecutionClient
import it.evadid.homepage.control.change.*
import it.evadid.homepage.control.info.*
import it.evadid.homepage.control.singletons.{ HomepageDefaults, HtmlFullWorkbookApp}
import it.evadid.workbook.interaction.sync.SyncControl

case class FullInfo(
                     val defaults: HomepageDefaults,
                     private[control] val initInfo: HomepageInfo
                   ) {


  private[control] val homepageInfoState: Var[HomepageInfo] = Var(initInfo)

  /* INFO */

  //lazy val executorForParallelism: List[ExecutionClient] = List(backendExecutor)
  //ExecuteOnRemoteServer("http://localhost", 9000),
  //ExecuteOnWebWorker(FileFactory.relativeToArtifactsFolder("/newest/backend-worker.js").fullPath),:
  
  lazy val loggerSystemInfo: HomepageLoggerInfo = HomepageLoggerInfo.singleton

  lazy val signals: HomepageSignalInfo = HomepageSignalInfo(this)

  lazy val current: HomepageCurrentInfo = HomepageCurrentInfo(this)

  /* CONTROLS */

  lazy val usageControl: HomepageUsageControl = HomepageUsageControl(this)

  lazy val syncControl: SyncControl = RemoteInteractionCacheControl(this)

  lazy val displayControl: DisplayControl = DisplayControl(this)

  lazy val contentControl: HomepageContentControl = HomepageContentControl(this, loggerSystemInfo.contentStorageLogger, loggerSystemInfo.fileDataStorageLogger)

}
