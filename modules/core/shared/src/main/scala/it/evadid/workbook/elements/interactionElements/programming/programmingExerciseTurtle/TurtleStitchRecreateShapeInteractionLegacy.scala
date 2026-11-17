package it.evadid.workbook.elements.interactionElements.programming.programmingExerciseTurtle

import it.evadid.workbook.abstractions.*
import it.evadid.workbook.abstractions.InteractionFormat.*
import it.evadid.core.util.io.Serializer
import it.evadid.workbook.abstractions.{WorkbookElement, WorkbookInteractionElement}
import it.evadid.workbook.jsonFactory.WorkbookElementFactory.SimpleWorkbookElementFactory
import it.evadid.workbook.jsonFactory.WorkbookElementSerializable
import upickle.default.*

import it.evadid.core.util.io.serializer.DefaultSerializer.given
object TurtleStitchRecreateShapeInteractionLegacy {
  val factory = new SimpleWorkbookElementFactory[TurtleStitchRecreateShapeInteractionLegacy]() {
    override protected val constructorFieldOrder = List("elementId", "filenameRelToResources")


    override def finishSerialization(baseElement: WorkbookElementSerializable, infoElement: TurtleStitchRecreateShapeInteractionLegacy): WorkbookElementSerializable = {
      baseElement.withElementAdded("filenameRelToResources", infoElement.filenameRelToResources)
    }

    override def finishDeserialization(serialized: WorkbookElementSerializable): TurtleStitchRecreateShapeInteractionLegacy = {
      TurtleStitchRecreateShapeInteractionLegacy(serialized.elementId, serialized.getElementAs("filenameRelToResources"))
    }
  }
}

case class TurtleStitchRecreateShapeInteractionLegacy(
                                                 override val elementId: String,
                                                 val filenameRelToResources: String
                                               ) extends WorkbookInteractionElement[TurtleProjectFormat.type] derives upickle.default.ReadWriter {
  override val associatedFactory = TurtleStitchRecreateShapeInteractionLegacy.factory

  override val defaultValue: TurtleStitchProjectState = TurtleStitchProjectState.empty()

  override lazy val childrenOfThisElement: List[WorkbookElement] = List()

}
