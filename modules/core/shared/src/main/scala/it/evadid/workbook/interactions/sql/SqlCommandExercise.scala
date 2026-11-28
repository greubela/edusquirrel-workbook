package it.evadid.workbook.interactions.sql

import it.evadid.core.util.io.Serializer
import it.evadid.core.util.io.serializer.{DefaultSerializer}
import it.evadid.core.util.io.serializer.DefaultSerializer.given
import it.evadid.workbook.abstractions.InteractionFormat.TextInteractionFormat
import it.evadid.workbook.abstractions.{InteractionFormat, WorkbookElement, WorkbookInteractionElement, *}
import it.evadid.workbook.serialization.{WorkbookElementFactory, WorkbookElementSerializable}
import it.evadid.workbook.history.InteractionHistory
import upickle.default.*

import it.evadid.workbook.abstractions.*
import it.evadid.workbook.abstractions.InteractionFormat.*
/** A database name resolved against the backend's exercise database configuration.
 * Connection credentials deliberately never travel in workbook JSON.
 */
final case class SqlDatabaseConfig(databaseName: String) derives ReadWriter

final case class SqlCommandExercise(
                                     override val elementId: String,
                                     databaseConfig: SqlDatabaseConfig,
                                     initialSql: String = "SELECT * FROM table_name;"
                                   ) extends WorkbookInteractionElement[TextInteractionFormat.type] {

  override val associatedFactory = SqlCommandExercise.factory
  override val defaultValue: String = initialSql

  override lazy val childrenOfThisElement: Seq[WorkbookElement] = Nil

}

object SqlCommandExercise  {

  val factory: WorkbookElementFactory[SqlCommandExercise] = new WorkbookElementFactory[SqlCommandExercise] {
    override def idsRequiredForDeserialization(element: WorkbookElementSerializable): Set[String] = Set.empty

    override def fromSerializedElement(element: WorkbookElementSerializable, parsed: Map[String, WorkbookElement]): SqlCommandExercise =
      SqlCommandExercise(element.elementId, element.getElementAs[SqlDatabaseConfig]("databaseConfig"),
        element.getOptionalElementAs[String]("initialSql", "SELECT * FROM table_name;"))

    override def toSerializableElement(element: SqlCommandExercise): WorkbookElementSerializable =
      toFactoryBase(element).withElementAddedAs("databaseConfig", element.databaseConfig)
        .withElementAddedAs("initialSql", element.initialSql)

    override lazy val writerJsonRegularRefBased: Writer[SqlCommandExercise] = macroRW
  }
}
