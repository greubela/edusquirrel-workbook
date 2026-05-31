package it.evadid.workbook.model.interaction.sync

trait SyncDestination {

  def syncTo(key: String, value: String): Unit

  def syncAllFrom(): Map[String, String]

  def syncKeyFrom(key: String): Option[String]

}

