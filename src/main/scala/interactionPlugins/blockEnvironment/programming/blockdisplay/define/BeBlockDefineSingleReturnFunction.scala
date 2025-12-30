package interactionPlugins.blockEnvironment.programming.blockdisplay.define

import com.raquo.laminar.api.L
import contentmanagement.model.language.AppLanguage.{English, Python}
import contentmanagement.model.language.{HumanLanguage, LanguageMap}
import contentmanagement.model.vm.code.defining.*
import contentmanagement.model.vm.code.tree.BeExpressionNode
import contentmanagement.webElements.svg.shapes.{BeShape, ControlFlowShape, TextShape}
import contentmanagement.webElements.svg.shapes.composite.ShapeAroundShape
import contentmanagement.webElements.svg.shapes.controlflow.singleWidth.*
import contentmanagement.webElements.svg.shapes.datatypes.RectangleShape
import interactionPlugins.blockEnvironment.programming.*
import interactionPlugins.blockEnvironment.programming.blockdisplay.*
import interactionPlugins.blockEnvironment.rendering.NestedBlockRenderer

case class BeBlockDefineSingleReturnFunction(
                                              beDefineFunction: BeDefineFunction
                                            ) extends BeBlockSingleShape {


  override def renderShape(childrenShapes: List[(BeExpressionNode, BeShape)], renderingInformation: RenderingInformation): (ControlFlowShape, BeShape) = {

    val text = LanguageMap.universalMap[HumanLanguage](beDefineFunction.expressionIO.getInLanguage(Python, English).replaceAll("\n", ""))
    val textShape = TextShape(text).addAmends(renderingInformation.factory.defaultTextAmends)

    val res = ShapeAroundShape(RectangleShape, textShape)
    (ControlFlowEmpty(), res.addAmends(renderingInformation.factory.defaultControlColors))
  }
}
