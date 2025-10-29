package interactionPlugins.blockEnvironment.programming.blocks.parents

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.Var
import contentmanagement.datastructures.tree.nodeImpl.NodeBasedTreePosition
import contentmanagement.model.vm.code.*
import contentmanagement.model.vm.code.usage.{BeFunctionCall, BeUseNonExistingValue, BeUseValue}
import contentmanagement.model.vm.types.*
import contentmanagement.model.vm.types.BeChildRole.{FunctionParameter, NoRole}
import interactionPlugins.blockEnvironment.config.*
import interactionPlugins.blockEnvironment.programming.BeProgram
import interactionPlugins.blockEnvironment.programming.blocks.*
import interactionPlugins.blockEnvironment.programming.blocks.other.*
import interactionPlugins.blockEnvironment.programming.blocks.other.BeBlockReference.*
import interactionPlugins.blockEnvironment.programming.blocks.variable.BeBlockPlaceholderMissingValue
import interactionPlugins.blockEnvironment.programming.editor.elements.BeTreeControllerConfig
import interactionPlugins.blockEnvironment.programming.shapes.*
import interactionPlugins.blockEnvironment.programming.shapes.composite.{HBoxSameHeight, ShapeAroundShape}

case class BeBlockCallSingleReturnFunction(
                                            function: BeFunctionCall,
                                            override val positionAsChild: BeChildPosition
                                          ) extends BeBlockParent {

  def getDisplayChildren(myPosition: NodeBasedTreePosition, treeControllerConfig: BeTreeControllerConfig, displayConfig: BeDisplayConfig, existingChildren: List[ReferenceExistingBlock]): List[BeBlockReference] = {

    val functionNameDisplay = NewBlock(BeBlockTextDisplay(function.funcDef.functionTypeInfo.displayName, myPosition))

    val parameterValue = function.funcDef.inputs.zipWithIndex.map((curPar, parNr) => {
      val parChildOp = existingChildren.find(_.nrInChildList == parNr)
      val alt = NewBlock(BeBlockPlaceholderMissingValue(BeUseNonExistingValue, BeChildPosition(myPosition, FunctionParameter(parNr), positionAsChild.curScope)))
      parChildOp.getOrElse(alt)
    })

    val namePos = function.funcDef.functionTypeInfo.displayNamePosition
    parameterValue.slice(0, namePos) ++ List(functionNameDisplay) ++ parameterValue.slice(namePos, parameterValue.size)

  }

  protected def render(inProgram: BeProgram, controllerStateVar: Var[BeControllerState], rendererConfig: BeRenderingConfig, renderedDisplayChildren: List[(BeBlockReference, BeShape)]): BeShape = {
    val childBox = HBoxSameHeight(renderedDisplayChildren.map(_._2))
    println("function: " + function.canEvaluateTo + ", outputs: " + function.funcDef.outputs.map(_.canEvaluateTo) )
    val shape = ShapeAroundShape(BeDataType.getShape(function.canEvaluateTo), childBox)
    val factory = BeShapeAmendFactory(rendererConfig)

    val signalAmends = factory.muteOnTreeDragged(inProgram, controllerStateVar.signal, factory.defaultFunctionColorsAmend)
    shape.addSignalAmends(signalAmends)
  }

  override def changeRole(newRole: BeChildRole): BeBlock = this.copy(positionAsChild = positionAsChild.copy(roleInParent = newRole))

  override def calcAssociatedExpression(childrenWithExpression: List[(BeChildRole, BeExpression)]): BeExpression = {

    val existingParameter: List[(FunctionParameter, BeUseValue)] = childrenWithExpression
      .filter(_._1.isInstanceOf[FunctionParameter])
      .filter(_._2.isInstanceOf[BeUseValue])
      .map(tup => (tup._1.asInstanceOf[FunctionParameter], tup._2.asInstanceOf[BeUseValue]))

    val missingParameter: List[(FunctionParameter, BeUseValue)] = function.funcDef.inputs.zipWithIndex
      .filterNot((curPar, curIndex) => existingParameter.exists(_._1.nr == curIndex))
      .map((curPar, curIndex) => (FunctionParameter(curIndex), BeUseNonExistingValue))

    val allParameter: List[(FunctionParameter, BeUseValue)] = (existingParameter ++ missingParameter).toList.sortBy(_._1.nr)

    BeFunctionCall(function.funcDef, allParameter.map(_._2))

  }

}
