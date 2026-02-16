package workbook.model.history.sync

trait ExerciseVariableSyncSource {

  def syncTo(key: String, value: String): Unit

  def syncAllFrom(): Map[String, String]

  def syncKeyFrom(key: String): Option[String]

}

