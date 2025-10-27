package interactionPlugins.blockEnvironment.programming.blocks.parents

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.Var
import contentmanagement.model.vm.expressions.{BeExpression, BeSequence}
import contentmanagement.model.vm.types.BeChildRole.{ExpressionInBody, RecentlyInsertedInto}
import contentmanagement.model.vm.types.BeDataType.Unit
import contentmanagement.model.vm.types.{BeChildRole, BeDataType}
import interactionPlugins.blockEnvironment.config.{BeControllerState, BeDisplayConfig, BeRenderingConfig}
import interactionPlugins.blockEnvironment.programming.BeProgram
import interactionPlugins.blockEnvironment.programming.blocks.BeBlockReference.NewBlock
import interactionPlugins.blockEnvironment.programming.blocks.variable.BeBlockPlaceholerOptionalValue
import interactionPlugins.blockEnvironment.programming.blocks.{BeBlock, BeBlockParent, BeBlockReference}
import interactionPlugins.blockEnvironment.programming.shapes.BeShape
import interactionPlugins.blockEnvironment.programming.shapes.composite.VBoxSameWidth


case class BeBlockSequence(expression: BeSequence, roleInParent: BeChildRole) extends BeBlockParent {

  override def getDisplayChildren(displayConfig: BeDisplayConfig, existingChildren: List[BeBlockReference.ReferenceExistingBlock]): List[BeBlockReference] = {

    List(NewBlock(BeBlockPlaceholerOptionalValue(Set(Unit), BeChildRole.ExpressionInBody(0))))
    ++
    existingChildren.zipWithIndex.flatMap((curChild, curIndex) => {
      List(curChild, NewBlock(BeBlockPlaceholerOptionalValue(Set(Unit), BeChildRole.ExpressionInBody(curIndex+1))))
    })
  }

  protected def render(inProgram: BeProgram, controllerStateVar: Var[BeControllerState], rendererConfig: BeRenderingConfig, renderedDisplayChildren: List[(BeBlockReference, BeShape)]): BeShape = {
    VBoxSameWidth(renderedDisplayChildren.map(_._2))
  }

  override def changeRole(newRole: BeChildRole): BeBlock = this.copy(roleInParent = newRole)

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
    BeSequence(resExpressions, expression.shouldEvaluateToUnit)

  }
}
