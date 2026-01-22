package contentmanagement.webElements.svg.builder.controlFlow

import contentmanagement.webElements.svg.shapes.BeShape
import contentmanagement.webElements.svg.shapes.BeShape.BeShapeContainerable
import interactionPlugins.blockEnvironment.config.BeRenderingConfig


trait ControlFlowPart {
  //case class ControlFlowPathElement(pathType: PathType) extends ControlFlowElement
  //case class ControlFlowText(content: LanguageMap[HumanLanguage]) extends ControlFlowElement

  def toShape(renderingConfig: BeRenderingConfig): BeShape

}

case class ControlFlowBackgroundPart(background: BeShapeContainerable) extends ControlFlowBackground {
  override def toShape(renderingConfig: BeRenderingConfig): BeShape = {
    background.addAmends(renderingConfig.amendFactory.defaultControlFlowBackgroundAmend)
  }
}

abstract class ControlFlowBackground() extends ControlFlowPart

abstract class ControlFlowOverlayElement() extends ControlFlowPart
