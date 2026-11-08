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

import scala.util.Random

object HomepageDefaults {

  lazy val defaultSyncLocations: List[SyncInformation] = List(
    SyncInformation(LocalIndexedDbStorageSyncHandler.instanceForHistory, SYNC_LAST_AND_MAJOR),
    SyncInformation(SyncDestinationBasedOnHistoryDatabase(DatabaseConfigPartialInformation(databaseOp = Some("db_332371_12")), ClassicJsonWithKey), SYNC_MAJOR),
    SyncInformation(SyncDestinationBasedOnHistoryDatabase(DatabaseConfigPartialInformation(databaseOp = Some("db_332371_12")), ClassicKeylessJson), SYNC_MAJOR),
  )

}

case class HomepageDefaults(fullInfo: FullInfo) {

  /* LANGUAGE MAP INPUT SOURCES */

  /*


  lazy val availableLanguages: List[HumanLanguage] = List(AppLanguage.German, AppLanguage.English)

  lazy val defaultLanguage: HumanLanguage = AppLanguage.German




  lazy val defaultDisplay: AllDisplayInfo = AllDisplayInfo(false, None)

  lazy val defaultUser: AllUserInfo = selectableUsers.head
*/


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

  private lazy val rnd: List[Int] = 1.to(3).map(_ => Random().nextInt(10000) + 10000).toList
  /*
    lazy val selectableUsers: List[AllUserInfo] = {
      val default = List(
        User("André Greubel", "andre.greubel@hu-berlin.de", "ag1"),
        User("Niels Pinkwart", "niels@testhomepage.de", "np1"),
        User("De Mo", "demo@website.com", "demo"),
      )
      val random = rnd.map(curNr => {
        User(s"Random ${curNr}", s"random-${curNr}@homepage.com", "random-"+curNr)
      })
      default ++ random
    }.map(createDefaultUserInfo)


    lazy val defaultUserConfig: UserConfig = UserConfig(
      defaultSyncLocation, None
    )

    def createDefaultUserInfo(user: User): AllUserInfo = AllUserInfo(user, defaultUserConfig)

  */
}
