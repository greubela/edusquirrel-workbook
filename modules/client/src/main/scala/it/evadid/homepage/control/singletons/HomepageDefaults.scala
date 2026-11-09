package it.evadid.homepage.control.singletons

import it.evadid.core.datastructures.language.AppLanguage.{English, German, HumanLanguage}
import it.evadid.core.datastructures.user.UserTokenInfo.SignedToken
import it.evadid.distribution.clients.{ExecutionClient, RemoteExecutionConfig, RoutedExecutionClient, WebWorkerExecutionClient}
import it.evadid.distribution.commandTypes.WorkerCommands
import it.evadid.facades.Workers
import it.evadid.homepage.control.model.FullInfo
import it.evadid.homepage.control.startup.ClientRuntimeConfig


case class HomepageDefaults(fullInfo: FullInfo) {

  val defaultDb: it.evadid.sql.DatabaseConfiguration.PartialDatabaseInformation = it.evadid.sql.DatabaseConfiguration.defaultDb

  lazy val defaultLanguagesAvailable: List[HumanLanguage] = List(German, English)


  private[control] lazy val defaultBackend: RemoteExecutionConfig = RemoteExecutionConfig("ypcgzj23.trafficplex.cloud", 443)


  /** Claim the Scala.js startup pool rather than starting a second worker. */
  lazy val workerExecuter: WebWorkerExecutionClient =
    WebWorkerExecutionClient.fromWorker(Workers.takeOrCreate(ClientRuntimeConfig.workerUrl))

  def scaffoldingExecutor: ExecutionClient = RoutedExecutionClient(command =>
    if WorkerCommands.supportedCommands.contains(command.name) then workerExecuter else backendExecutor)

  def backendExecutorWithCredentials(signedToken: Option[SignedToken]): ExecutionClient =
    it.evadid.distribution.clients.SessionLlmExecutionClient(defaultBackend.executor(signedToken),
      () => fullInfo.homepageInfoNow().userInfo.flatMap(_.credentials.llmApiConfig))

  def backendExecutor: ExecutionClient = {
    val fullInfoToken = fullInfo.homepageInfoNow().userInfo.flatMap(_.credentials.signedBackendToken)
    val useToken: Option[SignedToken] = fullInfoToken
    backendExecutorWithCredentials(useToken)
  }


}
