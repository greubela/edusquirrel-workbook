package it.evadid.evacuation.eva1.graphic.drawer.traits

import it.evadid.evacuation.core.graphic.model.{EvaColor, EvaFont}
import it.evadid.evacuation.eva1.model.evagraph.Router
import it.evadid.evacuation.shared.traits.graphic.EvaCanvas

trait NodeDrawer {

  def drawNodes(nodes: Seq[Router]): Unit


}

object NodeDrawer{

  def drawLabel(canvas: EvaCanvas[_], node: Router, labelFill: EvaColor, labelStroke: EvaColor, textColor: EvaColor, font: EvaFont, text: String): Unit = {

    canvas.setFont(font)

    val textWidth = canvas.getTextWidth(text)
    val containerWidth = 10 + textWidth

    canvas.setColor(labelFill)
    canvas.fillRect(node.pos.x - containerWidth / 2, node.pos.y + 10, containerWidth, font.sizeInPx + 2)

    canvas.setColor(labelStroke)
    canvas.drawRect(node.pos.x - containerWidth / 2, node.pos.y + 10, containerWidth, font.sizeInPx + 2)

    canvas.setColor(textColor)
    canvas.drawStringCentered(node.pos.x, node.pos.y + 10 + font.sizeInPx, text)
  }
}