package it.evadid.core.datastructures.user

import it.evadid.workbook.interaction.sync.SyncInformation
import upickle.{ReadWriter, macroRW}

case class UserConfig(syncDestinations: List[SyncInformation]) {

}

object UserConfig {



}

