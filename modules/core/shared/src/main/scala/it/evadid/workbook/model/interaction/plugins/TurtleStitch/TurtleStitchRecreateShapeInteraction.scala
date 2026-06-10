package it.evadid.workbook.model.interaction.plugins.TurtleStitch

import it.evadid.core.datastructures.file.FileDescription
import it.evadid.core.util.io.Serializer
import it.evadid.workbook.model.interaction.WorkbookInteraction
import it.evadid.workbook.model.interaction.variable.InteractionVariable

case class TurtleStitchRecreateShapeInteraction(override val id: String, imageToRecreate: FileDescription) extends WorkbookInteraction[TurtleStitchProjectState] {

  override val defaultValue: TurtleStitchProjectState = TurtleStitchProjectState.empty()

  override val serializer: Serializer[TurtleStitchProjectState] = new Serializer[TurtleStitchProjectState] {
    override def serialize(t: TurtleStitchProjectState): String = t.asString

    override def deserialize(s: String): TurtleStitchProjectState = TurtleStitchProjectState.parseFromStringOrEmpty(s)
  }


}
