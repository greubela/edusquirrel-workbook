package it.evadid.homepage.control.singletons

import it.evadid.core.datastructures.language.AppLanguage.{English, German, HumanLanguage}
import it.evadid.core.datastructures.user.UserConfig
import it.evadid.core.datastructures.user.UserTokenInfo.SignedToken
import it.evadid.core.util.io.Serializer
import it.evadid.distribution.clients.{ExecutionClient, RemoteExecutionConfig}
import it.evadid.homepage.control.model.FullInfo
import it.evadid.homepage.workbook.syncDestination.{DatabaseSyncViaBackendServer, LocalIndexedDbStorageSync, LocalStorageSync}
import it.evadid.workbook.interaction.sync.SyncStrategy.{SYNC_LAST, SYNC_LAST_AND_MAJOR, SYNC_MAJOR}
import it.evadid.workbook.interaction.sync.{SyncFormatter, SyncInformation}
import upickle.{ReadWriter, macroRW}

import scala.util.Random

object HomepageDefaults {

  private val localIndexStorage: SyncInformation = SyncInformation(LocalIndexedDbStorageSync.instance, SYNC_LAST_AND_MAJOR, SyncFormatter.RichInteractionVariableFormatter())

  val useDefaultLocalSyncLocations: List[SyncInformation] = List(localIndexStorage)

  def useDefaultOnlineSyncLocations: List[SyncInformation] = defaultSyncLocations.filter(_._1 != "localStorage").map(_._2)

  private lazy val defaultSyncLocations: List[(String, SyncInformation)] = List(
    "localStorage" -> SyncInformation(LocalStorageSync(50000), SYNC_LAST, SyncFormatter.serializeHistory),
    "db1" -> SyncInformation(DatabaseSyncViaBackendServer("db_332371_12", true), SYNC_MAJOR, SyncFormatter.RichInteractionVariableFormatter()),
    "db2" -> SyncInformation(DatabaseSyncViaBackendServer("db_332371_12", false), SYNC_MAJOR, SyncFormatter.RichInteractionVariableFormatter()),
    "localIndexedDb" -> localIndexStorage
  )

  lazy val defaultSyncLocationSerializer: Serializer[SyncInformation] = new Serializer[SyncInformation] {
    override def serialize(obj: SyncInformation): String = {
      defaultSyncLocations.find(_._2 == obj).map(_._1).getOrElse("unknown")
    }

    override def deserialize(str: String): SyncInformation = {
      val default = defaultSyncLocations.head._2
      defaultSyncLocations.find(_._1 == str).map(_._2).getOrElse(default)
    }
  }

  private given ReadWriter[SyncInformation] = defaultSyncLocationSerializer.uPickleReadWrite

  private given ucRW: ReadWriter[UserConfig] = macroRW

  lazy val defaultSerializerUserConfig: Serializer[UserConfig] = Serializer.fromUpickleJson(HomepageDefaults.ucRW)
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
