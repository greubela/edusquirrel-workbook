package interactionPlugins.blockEnvironment.programming.blocks.parents

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.Var
import contentmanagement.datastructures.tree.nodeImpl.NodeBasedTreePosition
import contentmanagement.model.vm.code.BeExpression
import contentmanagement.model.vm.code.controlStructures.BeSequence
import contentmanagement.model.vm.types.BeChildRole.{ExpressionInBody, RecentlyInsertedInto}
import contentmanagement.model.vm.types.BeScope.*
import contentmanagement.model.vm.types.*
import interactionPlugins.blockEnvironment.config.{BeControllerState, BeDisplayConfig, BeRenderingConfig}
import interactionPlugins.blockEnvironment.programming.BeProgram
import interactionPlugins.blockEnvironment.programming.blocks.other.BeBlockReference
import interactionPlugins.blockEnvironment.programming.blocks.other.BeBlockReference.*
import interactionPlugins.blockEnvironment.programming.blocks.variable.BeBlockPlaceholerOptionalValue
import interactionPlugins.blockEnvironment.programming.blocks.{BeBlock, BeBlockParent}
import interactionPlugins.blockEnvironment.programming.editor.elements.BeTreeControllerConfig
import interactionPlugins.blockEnvironment.programming.shapes.composite.{HBoxSameHeight, VBoxSameWidth}
import interactionPlugins.blockEnvironment.programming.shapes.controlflow.ControlFlowDown
import interactionPlugins.blockEnvironment.programming.shapes.{BeShape, BeShapeAmendFactory}

case class BeBlockSequence(expression: BeSequence, override val positionAsChild: BeChildPosition) extends BeBlockParent {

  override def getDisplayChildren(myPosition: NodeBasedTreePosition, treeControllerConfig: BeTreeControllerConfig, displayConfig: BeDisplayConfig, existingChildren: List[ReferenceExistingBlock]): List[BeBlockReference] = {
    if (treeControllerConfig.isEditable) {
      List(NewBlock(BeBlockPlaceholerOptionalValue(BeDataType.AnyType, BeChildPosition(myPosition, BeChildRole.ExpressionInBody(0), InSequenceScope(expression, positionAsChild.curScope)))))
        ++
        existingChildren.zipWithIndex.flatMap((curChild, curIndex) => {
          List(curChild, NewBlock(BeBlockPlaceholerOptionalValue(BeDataType.AnyType, BeChildPosition(myPosition, ExpressionInBody(curIndex + 1), InSequenceScope(expression, positionAsChild.curScope)))))
        })
    } else {
      existingChildren
    }
  }

  protected def render(inProgram: BeProgram, controllerStateVar: Var[BeControllerState], rendererConfig: BeRenderingConfig, renderedDisplayChildren: List[(BeBlockReference, BeShape)]): BeShape = {
    val factory = BeShapeAmendFactory(rendererConfig)
    val signalAmends = factory.muteOnTreeDragged(inProgram, controllerStateVar.signal, factory.defaultControlFlowBackgroundAmend)

    val controlFlowShape = ControlFlowDown.addSignalAmends(signalAmends)

    val childrenWithControlFlow = renderedDisplayChildren.map(tup => {
      HBoxSameHeight(List(controlFlowShape, tup._2), false)
    })
    VBoxSameWidth(childrenWithControlFlow, false)
  }

  override def changeRole(newRole: BeChildRole): BeBlock = this.copy(positionAsChild = positionAsChild.copy(roleInParent = newRole))

  override def calcAssociatedExpression(childrenWithExpression: List[(BeChildRole, BeExpression)]): BeExpression = {
    val expressions = childrenWithExpression.filter(_._1.isInstanceOf[ExpressionInBody]).map(_._2)

    val recentlyAdded: List[(RecentlyInsertedInto, BeExpression)] = childrenWithExpression
      .filter(_._1.isInstanceOf[RecentlyInsertedInto])
      .map(tup => (tup._1.asInstanceOf[RecentlyInsertedInto], tup._2))
      .filter(_._1.intoRole.isInstanceOf[ExpressionInBody])

    var resExpressions = expressions
    for ((role, expr) <- recentlyAdded) {
      val atIndex = role.intoRole.asInstanceOf[ExpressionInBody].nr
      resExpressions = resExpressions.slice(0, atIndex) ++ List(expr) ++ resExpressions.slice(atIndex, resExpressions.size)
    }
    BeSequence(expression.shouldEvaluateToUnit, resExpressions)

  }
}
