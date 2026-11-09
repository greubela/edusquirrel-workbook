package it.evadid.homepage.control.model

import it.evadid.llm.LlmConfiguration
import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.AppLanguage.English
import it.evadid.core.datastructures.language.control.LanguageMapIdResolver
import it.evadid.core.datastructures.language.{AppLanguage, LanguageMap, LanguageMapContentId}
import it.evadid.core.datastructures.state.StateHelper.RichSignal
import it.evadid.distribution.clients.ExecutionClient
import it.evadid.homepage.control.change.*
import it.evadid.homepage.control.info.*
import it.evadid.homepage.control.singletons.HomepageDefaults
import it.evadid.homepage.control.singletons.HtmlFullWorkbookApp.fullInfo
import it.evadid.workbook.interaction.sync.SyncControl
import it.evadid.workbook.meta.ExecutionContextInformation

import scala.concurrent.{ExecutionContext, Future}


case class FullInfo(
                     // val defaults: HomepageDefaults,
                     private[control] val defaultInfo: HomepageInfo
                   ) {


  val defaults: HomepageDefaults = HomepageDefaults(this)

  lazy val executionContextInformation: ExecutionContextInformation = new ExecutionContextInformation() {
    private val currentLanguageObservable = signals.currentLanguage.toObservableValue

    override def llmConfiguration: Option[LlmConfiguration] =
      homepageInfoNow().userInfo.flatMap(_.credentials.llmApiConfig)

    override def availableExecutionClients: List[ExecutionClient] = List(defaults.workerExecuter, defaults.backendExecutor)

    override def getCurrentLanguage: AppLanguage.HumanLanguage = currentLanguageObservable.now().getOrElse(English)

    override lazy val languageMapIdResolver: Option[LanguageMapIdResolver] = Some(new LanguageMapIdResolver(currentLanguageObservable) {
      override def resolveMap(id: LanguageMapContentId): Future[LanguageMap[AppLanguage.HumanLanguage]] = Future {
        homepageInfoState.now().languageMapStore.languageMaps(id)
      }(using ExecutionContext.global)
    })

    override def futureExecutionContext: ExecutionContext = ExecutionContext.global

    override def syncControl: Option[SyncControl] = Some(FullInfo.this.syncControl)
  }


  private[control] val homepageInfoState: Var[HomepageInfo] = Var(defaultInfo)

  def homepageInfoNow(): HomepageInfo = homepageInfoState.now()

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
