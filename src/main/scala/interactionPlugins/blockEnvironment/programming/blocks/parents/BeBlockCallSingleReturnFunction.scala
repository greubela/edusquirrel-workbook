package interactionPlugins.blockEnvironment.programming.blocks.parents

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.Var
import contentmanagement.model.vm.expressions.*
import contentmanagement.model.vm.types.*
import contentmanagement.model.vm.types.BeChildRole.FunctionParameter
import interactionPlugins.blockEnvironment.config.*
import interactionPlugins.blockEnvironment.programming.BeProgram
import interactionPlugins.blockEnvironment.programming.blocks.*
import interactionPlugins.blockEnvironment.programming.blocks.BeBlockReference.*
import interactionPlugins.blockEnvironment.programming.blocks.traits.*
import interactionPlugins.blockEnvironment.programming.blocks.variable.BeBlockPlaceholderMissingValue
import interactionPlugins.blockEnvironment.programming.shapes.*
import interactionPlugins.blockEnvironment.programming.shapes.composite.{HBoxSameHeight, ShapeAroundShape}

case class BeBlockCallSingleReturnFunction(
                                            function: BeFunctionCall,
                                            roleInParent: BeChildRole
                                          ) extends BeBlockParent with BeBlockStructureUsing {

  def getDisplayChildren(displayConfig: BeDisplayConfig, existingChildren: List[ReferenceExistingBlock]): List[BeBlockReference] = {

    val functionNameDisplay = NewBlock(BeBlockTextDisplay(function.funcDef.signature.name))

    val parameterValue = function.funcDef.signature.parameter.zipWithIndex.map((curPar, parNr) => {
      val parChildOp = existingChildren.find(_.nrInChildList == parNr)
      val alt = NewBlock(BeBlockPlaceholderMissingValue(BeUseNonExistingValue, FunctionParameter(parNr)))
      parChildOp.getOrElse(alt)
    })

    functionNameDisplay :: parameterValue
  }

  protected def render(inProgram: BeProgram, controllerStateVar: Var[BeControllerState], rendererConfig: BeRenderingConfig, renderedDisplayChildren: List[(BeBlockReference, BeShape)]): BeShape
  = {
    val childBox = HBoxSameHeight(renderedDisplayChildren.map(_._2))
    val shape = ShapeAroundShape(BeDataType.getShape(function.canEvaluateTo), childBox)
    val factory = BeShapeAmendFactory(rendererConfig)

    val signalAmends = factory.muteOnTreeDragged(controllerStateVar.signal, factory.defaultFunctionColorsAmend)
    shape.addSignalAmends(signalAmends)

  }

  override def changeRole(newRole: BeChildRole): BeBlock = this.copy(roleInParent = newRole)

  override def calcAssociatedExpression(childrenWithExpression: List[(BeChildRole, BeExpression)]): BeExpression = {

    val existingParameter: List[(FunctionParameter, BeUseValue)] = childrenWithExpression
      .filter(_._1.isInstanceOf[FunctionParameter])
      .filter(_._2.isInstanceOf[BeUseValue])
      .map(tup => (tup._1.asInstanceOf[FunctionParameter], tup._2.asInstanceOf[BeUseValue]))

    val missingParameter: List[(FunctionParameter, BeUseValue)] = function.funcDef.signature.parameter.zipWithIndex
      .filterNot((curPar, curIndex) => existingParameter.exists(_._1.nr == curIndex))
      .map((curPar, curIndex) => (FunctionParameter(curIndex), BeUseNonExistingValue))

    val allParameter: List[(FunctionParameter, BeUseValue)] = (existingParameter ++ missingParameter).toList.sortBy(_._1.nr)

    BeFunctionCall(function.funcDef, allParameter.map(_._2))

  }

}
