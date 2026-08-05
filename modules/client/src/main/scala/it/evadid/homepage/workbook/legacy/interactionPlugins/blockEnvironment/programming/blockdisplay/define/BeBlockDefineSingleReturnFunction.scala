package it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.programming.blockdisplay.define

import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.programming.blockdisplay.*
import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.core.datastructures.language.LanguageMap
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.programming.blockdisplay.{BeBlockSingleShape, RenderingInformation}
import it.evadid.vm.code.defining.BeDefineFunction
import it.evadid.vm.code.tree.BeExpressionNode
import todomove.webElementsOld.webElements.svg.shapes.{BeShape, ControlFlowShape, TextShape}
import todomove.webElementsOld.webElements.svg.shapes.composite.ShapeAroundShape
import todomove.webElementsOld.webElements.svg.shapes.controlflow.singleWidth.ControlFlowEmpty
import todomove.webElementsOld.webElements.svg.shapes.datatypes.RectangleShape

case class BeBlockDefineSingleReturnFunction(
                                              beDefineFunction: BeDefineFunction
                                            ) extends BeBlockSingleShape {


  override def renderShape(childrenShapes: List[(BeExpressionNode, BeShape)], renderingInformation: RenderingInformation): (ControlFlowShape, BeShape) = {

    val text = LanguageMap.universalMap[HumanLanguage](beDefineFunction.structureInfo.toStringInLanguage(Python, English).replaceAll("\n", ""))
    val textShape = TextShape(text).addAmends(renderingInformation.factory.defaultTextAmends)

    val res = ShapeAroundShape(RectangleShape, textShape)
    (ControlFlowEmpty(), res.addAmends(renderingInformation.factory.defaultControlColors))
  }
}
