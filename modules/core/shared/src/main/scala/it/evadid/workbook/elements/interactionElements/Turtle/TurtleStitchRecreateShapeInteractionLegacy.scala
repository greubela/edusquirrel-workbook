package it.evadid.workbook.elements.interactionElements.Turtle

import it.evadid.core.util.io.Serializer
import it.evadid.workbook.abstractions.{WorkbookElement, WorkbookInteractionElement}
import it.evadid.workbook.jsonFactory.WorkbookElementFactory.SimpleWorkbookElementFactory
import it.evadid.workbook.jsonFactory.WorkbookElementSerializable
import upickle.default.*

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
    private val header = "TURTLE_STITCH_STATE_V1\n"

    override def serialize(t: TurtleStitchProjectState): String = header + write(t)

    override def deserialize(s: String): TurtleStitchProjectState =
      if (s.startsWith(header)) read[TurtleStitchProjectState](s.drop(header.length))
      else if (s.isEmpty) TurtleStitchProjectState.empty()
      else TurtleStitchProjectState.parseFromStringOrEmpty(s)
  }

  override lazy val childrenOfThisElement: List[WorkbookElement] = List()

}
