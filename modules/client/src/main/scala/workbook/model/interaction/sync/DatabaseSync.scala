package workbook.model.interaction.sync

class DatabaseSync(
                    host: String,
                    ip: Int,
                    username: String,
                    password: String
                  ) extends SyncDestination {

  override def syncTo(key: String, value: String): Unit = ???

  override def syncAllFrom(): Map[String, String] = ???

  override def syncKeyFrom(key: String): Option[String] = ???


}
