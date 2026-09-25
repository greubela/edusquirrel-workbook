package it.evadid.workbook.elements.interactionElements.TurtleStitch

import it.evadid.core.util.io.Serializer
import it.evadid.workbook.abstractions.{WorkbookElement, WorkbookInteractionElement}
import it.evadid.workbook.jsonFactory.WorkbookElementFactory.SimpleWorkbookElementFactory
import it.evadid.workbook.jsonFactory.WorkbookElementSerializable

object TurtleStitchRecreateShapeInteraction {
  val factory = new SimpleWorkbookElementFactory[TurtleStitchRecreateShapeInteraction]() {


    override def finishSerialization(baseElement: WorkbookElementSerializable, infoElement: TurtleStitchRecreateShapeInteraction): WorkbookElementSerializable = {
      baseElement.withElementAdded("filenameRelToResources", infoElement.filenameRelToResources)
    }

    override def finishDeserialization(serialized: WorkbookElementSerializable): TurtleStitchRecreateShapeInteraction = {
      TurtleStitchRecreateShapeInteraction(serialized.elementId, serialized.getElementAs("filenameRelToResources"))
    }
  }
}

case class TurtleStitchRecreateShapeInteraction(
                                                 override val elementId: String,
                                                 val filenameRelToResources: String
                                               ) extends WorkbookInteractionElement[TurtleStitchProjectState] {
  override val associatedFactory = TurtleStitchRecreateShapeInteraction.factory

  override val defaultValue: TurtleStitchProjectState = TurtleStitchProjectState.empty()

  override val serializerInteractionContent: Serializer[TurtleStitchProjectState] = new Serializer[TurtleStitchProjectState] {
    override def serialize(t: TurtleStitchProjectState): String = t.asString

    override def deserialize(s: String): TurtleStitchProjectState = TurtleStitchProjectState.parseFromStringOrEmpty(s)
  }

  override lazy val childrenOfThisElement: List[WorkbookElement] = List()

}
