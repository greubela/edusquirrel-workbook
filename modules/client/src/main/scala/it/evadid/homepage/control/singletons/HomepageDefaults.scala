package it.evadid.homepage.control.singletons

import it.evadid.core.datastructures.language.AppLanguage.{English, German, HumanLanguage}
import it.evadid.core.datastructures.user.UserConfig
import it.evadid.core.datastructures.user.UserTokenInfo.SignedToken
import it.evadid.core.util.io.Serializer
import it.evadid.distribution.clients.{ExecutionClient, RemoteExecutionConfig, RoutedExecutionClient, WebWorkerExecutionClient}
import it.evadid.distribution.commandTypes.WorkerCommands
import it.evadid.facades.Workers
import it.evadid.homepage.control.model.FullInfo
import it.evadid.homepage.control.startup.ClientRuntimeConfig
import it.evadid.homepage.workbook.syncDestination.LocalIndexedDbStorageSyncHandler
import it.evadid.sql.DatabaseConfig.DatabaseConfigPartialInformation
import it.evadid.sql.DatabaseHistoryFormat.{ClassicJsonWithKey, ClassicKeylessJson}
import it.evadid.workbook.interaction.sync.SyncInformation
import it.evadid.workbook.interaction.sync.SyncStrategy.{SYNC_LAST, SYNC_LAST_AND_MAJOR, SYNC_MAJOR}
import it.evadid.workbook.interaction.sync.destination.SyncDestinationDefinition.SyncDestinationBasedOnHistoryDatabase
import upickle.{ReadWriter, macroRW}


object HomepageDefaults {

  lazy val defaultSyncLocations: List[SyncInformation] = List(
    SyncInformation(LocalIndexedDbStorageSyncHandler.instanceForHistory, SYNC_LAST_AND_MAJOR),
    SyncInformation(SyncDestinationBasedOnHistoryDatabase(DatabaseConfigPartialInformation(databaseOp = Some("db_332371_12")), ClassicJsonWithKey), SYNC_MAJOR),
    SyncInformation(SyncDestinationBasedOnHistoryDatabase(DatabaseConfigPartialInformation(databaseOp = Some("db_332371_12")), ClassicKeylessJson), SYNC_MAJOR),
  )

}

case class HomepageDefaults(fullInfo: FullInfo) {

  lazy val defaultLanguagesAvailable: List[HumanLanguage] = List(German, English)


  private[control] lazy val defaultBackend: RemoteExecutionConfig = RemoteExecutionConfig("ypcgzj23.trafficplex.cloud", 443)


  /** Claim the Scala.js startup pool rather than starting a second worker. */
  lazy val workerExecuter: WebWorkerExecutionClient =
    WebWorkerExecutionClient.fromWorker(Workers.takeOrCreate(ClientRuntimeConfig.workerUrl))

  def scaffoldingExecutor: ExecutionClient = RoutedExecutionClient(command =>
    if WorkerCommands.supportedCommands.contains(command.name) then workerExecuter else backendExecutor)

  def backendExecutorWithCredentials(signedToken: Option[SignedToken]): ExecutionClient = defaultBackend.executor(signedToken)

  def backendExecutor: ExecutionClient = {
    val fullInfoToken = fullInfo.homepageInfoNow().userInfo.flatMap(_.token)
    val localStorageToken = fullInfo.usageControl.tryParsingExistingUser().flatMap(_.token)
    val useToken: Option[SignedToken] = (fullInfoToken ++ localStorageToken).headOption
    backendExecutorWithCredentials(useToken)
  }


}
