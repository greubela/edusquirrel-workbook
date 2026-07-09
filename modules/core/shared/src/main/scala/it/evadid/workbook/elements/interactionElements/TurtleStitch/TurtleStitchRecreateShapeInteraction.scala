package it.evadid.workbook.elements.interactionElements.TurtleStitch

import it.evadid.core.datastructures.file.FileDescription
import it.evadid.core.util.io.Serializer
import it.evadid.workbook.model.abstractions.{WorkbookElement, WorkbookInteractionElement}

case class TurtleStitchRecreateShapeInteraction(override val id: String, imageToRecreate: FileDescription) extends WorkbookInteractionElement[TurtleStitchProjectState] {

  override val defaultValue: TurtleStitchProjectState = TurtleStitchProjectState.empty()

  override val serializer: Serializer[TurtleStitchProjectState] = new Serializer[TurtleStitchProjectState] {
    override def serialize(t: TurtleStitchProjectState): String = t.asString

    override def deserialize(s: String): TurtleStitchProjectState = TurtleStitchProjectState.parseFromStringOrEmpty(s)
  }

  override lazy val childrenOfThisElement: List[WorkbookElement] = List()
}
