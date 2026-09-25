package it.evadid.workbook.elements.interactionElements.TurtleStitch

import it.evadid.core.util.io.Serializer
import it.evadid.workbook.abstractions.{WorkbookElement, WorkbookInteractionElement}
import it.evadid.workbook.jsonFactory.WorkbookElementSerializable
import it.evadid.workbook.jsonFactory.WorkbookElementFactory

object TurtleStitchRecreateShapeInteraction {
  val factory = WorkbookElementFactory.simple[TurtleStitchRecreateShapeInteraction](
    element => WorkbookElementSerializable(element.elementId, classOf[TurtleStitchRecreateShapeInteraction].getSimpleName, Map())
      .withElementAdded("filenameRelToResources", element.filenameRelToResources),
    serialized => TurtleStitchRecreateShapeInteraction(serialized.elementId, serialized.getElementAsString("filenameRelToResources"))
  )
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
