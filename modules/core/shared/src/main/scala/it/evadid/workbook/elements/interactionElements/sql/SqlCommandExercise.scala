package it.evadid.workbook.elements.interactionElements.sql

import it.evadid.core.util.io.Serializer
import it.evadid.core.util.io.serializer.{InteractionFormat, DefaultSerializer}
import it.evadid.core.util.io.serializer.DefaultSerializer.given
import it.evadid.workbook.abstractions.{WorkbookElement, WorkbookInteractionElement}
import it.evadid.workbook.jsonFactory.{WorkbookElementFactory, WorkbookElementSerializable}
import upickle.default.*

/** A database name resolved against the backend's exercise database configuration.
 * Connection credentials deliberately never travel in workbook JSON.
 */
final case class SqlDatabaseConfig(databaseName: String)

final case class SqlCommandExercise(
                                     override val elementId: String,
                                     databaseConfig: SqlDatabaseConfig,
                                     initialSql: String = "SELECT * FROM table_name;"
                                   ) extends WorkbookInteractionElement[String] {

  override val associatedFactory = SqlCommandExercise.factory
  override val defaultValue: String = initialSql

  override lazy val childrenOfThisElement: List[WorkbookElement] = Nil
  override def format: InteractionFormat[String, Unit, Unit] = SqlCommandExercise.format
}

object SqlCommandExercise extends InteractionFormat[String, Unit, Unit] {
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

  override def stateSerializer: Serializer[String] = Serializer.fromImplicitRW
  override def historySerializer: Serializer[InteractionVariableHistory[String, Unit, Unit]] = Serializer.fromImplicitRW
  override def exerciseSerializer: Serializer[WorkbookInteractionElementFull[String, Unit, Unit]] = Serializer.fromImplicitRW
}
