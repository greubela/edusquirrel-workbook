package interactionPlugins.blockEnvironment.programming.blockdisplay.define

import contentmanagement.webElements.svg.shapes.composite.ShapeAroundShape
import contentmanagement.webElements.svg.shapes.controlflow.singleWidth.*
import contentmanagement.webElements.svg.shapes.datatypes.RectangleShape
import contentmanagement.webElements.svg.shapes.{BeShape, ControlFlowShape, TextShape}
import datastructures.core.vm.code.defining.BeDefineFunction
import datastructures.core.vm.code.tree.BeExpressionNode
import interactionPlugins.blockEnvironment.programming.blockdisplay.*
import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.core.datastructures.language.LanguageMap
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
