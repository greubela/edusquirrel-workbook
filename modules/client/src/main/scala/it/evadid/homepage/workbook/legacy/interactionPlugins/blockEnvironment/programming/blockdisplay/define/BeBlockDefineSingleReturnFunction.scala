package it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.programming.blockdisplay.define

import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.programming.blockdisplay.*
import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.core.datastructures.language.LanguageMap
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.programming.blockdisplay.{BeBlockSingleShape, RenderingInformation}
import todomove.datastructures.core.vm.code.defining.BeDefineFunction
import todomove.datastructures.core.vm.code.tree.BeExpressionNode
import todomove.webElementsOld.webElements.svg.shapes.{BeShape, ControlFlowShape, TextShape}
import todomove.webElementsOld.webElements.svg.shapes.composite.ShapeAroundShape
import todomove.webElementsOld.webElements.svg.shapes.controlflow.singleWidth.ControlFlowEmpty
import todomove.webElementsOld.webElements.svg.shapes.datatypes.RectangleShape

case class BeBlockDefineSingleReturnFunction(
                                              beDefineFunction: BeDefineFunction
                                            ) extends BeBlockSingleShape {


  override def renderShape(childrenShapes: List[(BeExpressionNode, BeShape)], renderingInformation: RenderingInformation): (ControlFlowShape, BeShape) = {

    val text = LanguageMap.universalMap[HumanLanguage](beDefineFunction.expressionIO.toStringInLanguage(Python, English).replaceAll("\n", ""))
    val textShape = TextShape(text).addAmends(renderingInformation.factory.defaultTextAmends)

    val res = ShapeAroundShape(RectangleShape, textShape)
    (ControlFlowEmpty(), res.addAmends(renderingInformation.factory.defaultControlColors))
  }
}
