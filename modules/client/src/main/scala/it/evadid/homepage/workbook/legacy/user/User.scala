package it.evadid.homepage.workbook.legacy.user

import it.evadid.homepage.workbook.legacy.model.interaction.sync.LocalStorageSync
import it.evadid.workbook.model.interaction.sync.{SyncInformation, SyncStrategy}


case class User(name: String, mail: String) {

 def id: String = mail

}


