package it.evadid.sql

import upickle.default.*


sealed trait DatabaseHistoryFormat derives ReadWriter {

  def hasKeyTable: Boolean

  def useTableForStorage: String

  def tableHasColumns: Seq[DatabaseTableColumn]

  // def readValuesForColumn[T, GA, SA](variable: InteractionVariable[T, GA, SA]): Map[DatabaseColumn, String]

}

object DatabaseHistoryFormat {

  case object ClassicKeylessJson extends DatabaseHistoryFormat {
    def hasKeyTable: Boolean = false

    def useTableForStorage: String = "events"

    override val tableHasColumns: Seq[DatabaseTableColumn] = Seq(
      DatabaseTableColumn("programId", "TEXT"),
      DatabaseTableColumn("serializeddata", "LONGTEXT"),
      DatabaseTableColumn("scenarioid", "TEXT"),
      DatabaseTableColumn("userId", "TEXT"),
      DatabaseTableColumn("timestamp", "DATETIME"),
      DatabaseTableColumn("eventid", "INT")
    )

    /*override def readValuesForColumn[T, GA, SA](variable: InteractionVariable[T, GA, SA]): Map[DatabaseColumn, String] = Map(
      columns(1) -> write(variable.currentHistory)(using InteractionVariableHistory.given_ReadWriter_InteractionVariableHistory[T, GA, SA])
    )*/
  }

  case object ClassicJsonWithKey extends DatabaseHistoryFormat {
    def hasKeyTable: Boolean = true

    override val tableHasColumns: Seq[DatabaseTableColumn] = Seq(
      DatabaseTableColumn("programId", "TEXT"),
      DatabaseTableColumn("serializeddata", "LONGTEXT"),
      DatabaseTableColumn("scenarioid", "TEXT"),
      DatabaseTableColumn("userId", "TEXT"),
      DatabaseTableColumn("timestamp", "DATETIME"),
      DatabaseTableColumn("eventid", "INT"),
      DatabaseTableColumn("eventkey", "VARCHAR(100)")
    )

    /*override def readValuesForColumn[T, GA, SA](variable: InteractionVariable[T, GA, SA]): Map[DatabaseColumn, String] = Map(
      columns(1) -> write(variable.currentHistory)(using InteractionVariableHistory.given_ReadWriter_InteractionVariableHistory[T, GA, SA])
    )*/

    override def useTableForStorage: String = "eventsWithKey"
  }

}