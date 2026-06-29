package it.evadid.homepage.control.info

import it.evadid.core.datastructures.language.AppLanguage
import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.core.datastructures.user.User
import it.evadid.homepage.control.model.*
import it.evadid.homepage.workbook.syncDestination.{DatabaseSyncViaBackendServer, LocalStorageSync}
import it.evadid.workbook.model.interaction.sync.*
import it.evadid.workbook.model.interaction.sync.SyncStrategy.SYNC_MAJOR

import scala.util.Random

case class HomepageDefaults() {
  lazy val availableLanguages: List[HumanLanguage] = List(AppLanguage.German, AppLanguage.English)

  lazy val defaultLanguage: HumanLanguage = AppLanguage.German

  lazy val defaultSyncLocation: List[SyncInformation] = List(
    SyncInformation(LocalStorageSync, SyncStrategy.SYNC_LAST, SyncFormatter.serializeHistory),
    SyncInformation(DatabaseSyncViaBackendServer("db_332371_12", true), SYNC_MAJOR, SyncFormatter.RichInteractionVariableFormatter()),
    SyncInformation(DatabaseSyncViaBackendServer("db_332371_12", false), SYNC_MAJOR, SyncFormatter.RichInteractionVariableFormatter()),
  )

  lazy val defaultUser: AllUserInfo = selectableUsers.head

  lazy val defaultUserConfig: UserConfig = UserConfig(
    defaultSyncLocation
  )

  lazy val rnd: List[Int] = 1.to(3).map(_ => Random().nextInt(10000) + 10000).toList

  lazy val selectableUsers: List[AllUserInfo] = {
    val default = List(
      User("André Greubel", "andre.greubel@hu-berlin.de"),
      User("Niels Pinkwart", "niels@testhomepage.de"),
      User("De Mo", "demo@website.com"),
    )
    val random = rnd.map(curNr => {
      User(s"Random ${curNr}", s"random-${curNr}@homepage.com")
    })
    default ++ random
  }.map(createDefaultUserInfo)

  def createDefaultUserInfo(user: User): AllUserInfo = AllUserInfo(user, defaultUserConfig)
}
