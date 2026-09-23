package it.evadid.workbook.elements.interactionElements.TurtleStitch

import it.evadid.core.util.io.Serializer
import it.evadid.workbook.abstractions.{WorkbookElement, WorkbookInteractionElement}
import it.evadid.workbook.jsonFactory.WorkbookElementFactory

object TurtleStitchRecreateShapeInteraction {

  def fromFactory(factory: WorkbookElementFactory): TurtleStitchRecreateShapeInteraction = {
    TurtleStitchRecreateShapeInteraction(factory.elementId, factory.getElementAsString("filenameRelToResources"))
  }

}

case class TurtleStitchRecreateShapeInteraction(
                                                 override val elementId: String,
                                                 val filenameRelToResources: String
                                               ) extends WorkbookInteractionElement[TurtleStitchProjectState] {

  override val defaultValue: TurtleStitchProjectState = TurtleStitchProjectState.empty()

  override val serializerInteractionContent: Serializer[TurtleStitchProjectState] = new Serializer[TurtleStitchProjectState] {
    override def serialize(t: TurtleStitchProjectState): String = t.asString

    override def deserialize(s: String): TurtleStitchProjectState = TurtleStitchProjectState.parseFromStringOrEmpty(s)
  }

  override lazy val childrenOfThisElement: List[WorkbookElement] = List()

  override val toSerializableType: WorkbookElementFactory =
    toFactoryBase.withElementAdded("filenameRelToResources", filenameRelToResources)
}
