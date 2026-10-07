package it.evadid.workbook.elements.interactionElements.Turtle

import it.evadid.core.util.io.Serializer
import it.evadid.workbook.abstractions.{WorkbookElement, WorkbookInteractionElement}
import it.evadid.workbook.jsonFactory.WorkbookElementFactory.SimpleWorkbookElementFactory
import it.evadid.workbook.jsonFactory.WorkbookElementSerializable

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
                                               ) extends WorkbookInteractionElement[TurtleStitchProjectState] {
  override val associatedFactory = TurtleStitchRecreateShapeInteractionLegacy.factory

  override val defaultValue: TurtleStitchProjectState = TurtleStitchProjectState.empty()

  override val serializerInteractionContent: Serializer[TurtleStitchProjectState] = new Serializer[TurtleStitchProjectState] {
    override def serialize(t: TurtleStitchProjectState): String = t.asString

    override def deserialize(s: String): TurtleStitchProjectState = TurtleStitchProjectState.parseFromStringOrEmpty(s)
  }

  override lazy val childrenOfThisElement: List[WorkbookElement] = List()

}
