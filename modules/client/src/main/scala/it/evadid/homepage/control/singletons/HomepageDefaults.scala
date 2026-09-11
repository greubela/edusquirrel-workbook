package it.evadid.homepage.control.singletons

import it.evadid.core.util.io.Serializer
import it.evadid.distribution.clients.RemoteExecutionConfig
import it.evadid.homepage.workbook.syncDestination.{DatabaseSyncViaBackendServer, LocalStorageSync}
import it.evadid.workbook.interaction.sync.SyncStrategy.SYNC_MAJOR
import it.evadid.workbook.interaction.sync.{SyncFormatter, SyncInformation, SyncStrategy}

import scala.util.Random

object HomepageDefaults {

  def defaultSyncLocation: List[SyncInformation] = defaultSyncLocations.map(_._2)

  lazy val defaultSyncLocations: List[(String, SyncInformation)] = List(
    "local" -> SyncInformation(LocalStorageSync, SyncStrategy.SYNC_LAST, SyncFormatter.serializeHistory),
    "db1" -> SyncInformation(DatabaseSyncViaBackendServer("db_332371_12", true), SYNC_MAJOR, SyncFormatter.RichInteractionVariableFormatter()),
    "db2" -> SyncInformation(DatabaseSyncViaBackendServer("db_332371_12", false), SYNC_MAJOR, SyncFormatter.RichInteractionVariableFormatter())
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
}

case class HomepageDefaults() {

  /* LANGUAGE MAP INPUT SOURCES */

  /*


  lazy val availableLanguages: List[HumanLanguage] = List(AppLanguage.German, AppLanguage.English)

  lazy val defaultLanguage: HumanLanguage = AppLanguage.German




  lazy val defaultDisplay: AllDisplayInfo = AllDisplayInfo(false, None)

  lazy val defaultUser: AllUserInfo = selectableUsers.head
*/

  lazy val defaultBackend: RemoteExecutionConfig = RemoteExecutionConfig("ypcgzj23.trafficplex.cloud", 443)

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
