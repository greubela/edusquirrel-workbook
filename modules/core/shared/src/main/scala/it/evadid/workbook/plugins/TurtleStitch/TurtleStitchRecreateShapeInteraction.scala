package it.evadid.workbook.plugins.TurtleStitch

import it.evadid.core.datastructures.file.FileDescription
import it.evadid.core.util.io.Serializer
import it.evadid.workbook.model.interaction.WorkbookInteraction
import it.evadid.workbook.model.interaction.variable.InteractionVariable

case class TurtleStitchRecreateShapeInteraction(override val id: String, imageToRecreate: FileDescription) extends WorkbookInteraction[TurtleStitchProjectState] {


  def defaultValue: TurtleStitchProjectState = TurtleStitchProjectState()

  def serializer: Serializer[TurtleStitchProjectState] = new Serializer[TurtleStitchProjectState] {
    override def serialize(t: TurtleStitchProjectState): String = t.asString

    override def deserialize(s: String): TurtleStitchProjectState = TurtleStitchProjectState(Some(s))
  }

  def interactionVariable: InteractionVariable[TurtleStitchProjectState] = InteractionVariable[TurtleStitchProjectState](this, serializer)


}
