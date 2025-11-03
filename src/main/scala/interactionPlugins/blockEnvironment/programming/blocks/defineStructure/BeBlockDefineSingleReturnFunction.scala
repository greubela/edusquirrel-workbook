package interactionPlugins.blockEnvironment.programming.blocks.defineStructure

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.Var
import contentmanagement.datastructures.tree.nodeImpl.NodeBasedTreePosition
import contentmanagement.model.language.AppLanguage.{English, Python}
import contentmanagement.model.language.{HumanLanguage, LanguageMap}
import contentmanagement.model.vm.code.BeExpression
import contentmanagement.model.vm.code.defining.*
import contentmanagement.model.vm.code.tree.BeExpressionNode
import contentmanagement.model.vm.types.*
import contentmanagement.webElements.svg.shapes.{BeShape, TextShape}
import contentmanagement.webElements.svg.shapes.composite.ShapeAroundShape
import contentmanagement.webElements.svg.shapes.datatypes.RectangleShape
import interactionPlugins.blockEnvironment.config.{BeControllerState, BeDisplayConfig, BeRenderingConfig}
import interactionPlugins.blockEnvironment.programming.*
import interactionPlugins.blockEnvironment.programming.blocks.*
import interactionPlugins.blockEnvironment.programming.editor.elements.BeTreeControllerConfig

case class BeBlockDefineSingleReturnFunction(
                                            beDefineFunction: BeDefineFunction
                                            )  extends BeBlock  {


  def render(renderedChildren: List[(BeExpressionNode, BeBlock, NestedBlockRenderer)], renderingInfo: RenderingInformation): NestedBlockRenderer = {

    val text = LanguageMap.universalMap[HumanLanguage](beDefineFunction.getInLanguage(Python, English).replaceAll("\n", ""))
    val textShape = TextShape(text).addAmends(renderingInfo.factory.defaultTextAmends)

    val res = ShapeAroundShape(RectangleShape, textShape)
    NestedBlockRenderer.fromShape(res.addAmends(renderingInfo.factory.defaultControlColors))

  }
}
