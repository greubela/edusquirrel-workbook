package it.evadid.vm.code.controlStructures

import it.evadid.vm.code.abstractions.{BeControlStructure, BeExpression}
import it.evadid.vm.code.tree.{BeExpressionNode, BeExpressionReference, BeExtensionPoint}
import it.evadid.vm.io.{BeExpressionStructureInfo, BeSegmentedCodeElement}
import it.evadid.vm.simulation.*
import it.evadid.vm.static.BeExpressionStaticInformation
import it.evadid.vm.types.*
import it.evadid.vm.types.BeChildRole.ExpressionInSequence
import it.evadid.vm.types.BeScope.InSequenceScope

case class BeSequenceInfo(mustEvaluateTo: Option[BeDataType], maxBodyElements: Option[Int] = None)

case class BeSequence(body: Seq[BeExpression], sequenceInfo: BeSequenceInfo) extends BeControlStructure {

  def allPossibleBodies: Seq[BeExpression] = body

  override lazy val staticInformationExpression: BeExpressionStaticInformation = new BeExpressionStaticInformation {
    override def staticType: BeDataType = sequenceInfo.mustEvaluateTo.getOrElse(body.lastOption.map(_.staticInformationExpression.staticType).getOrElse(BeDataType.Error))

    override def staticValue: Option[BeDataValue] = body.lastOption.flatMap(_.staticInformationExpression.staticValue)
  }


  override def expressionExecutor(simulatorConfig: BeSimulatorConfig, stateBeforeExecution: BeSimulatorState): BeExpressionExecutor = new BeExpressionExecutor(simulatorConfig, stateBeforeExecution, this) {
    override protected def childExpressionsToExecute(stateBeforeExecution: BeSimulatorState): Seq[BeExpression] = body

    override protected def applySideEffectsOfThisBlock(stateBeforeExecution: BeSimulatorState, childrenResults: Seq[(BeSimulatorState, BeDataValue)]): BeSimulatorState = stateBeforeExecution

    override protected def executeThisBlockInSimulatorAndGetValue(stateBeforeExecution: BeSimulatorState, childrenResults: Seq[(BeSimulatorState, BeDataValue)]): (BeSimulatorState, BeDataValue) =
      (stateBeforeExecution, BeDataValueUnit())
  }


  override lazy val structureInfo: BeExpressionStructureInfo[?] = new BeExpressionStructureInfo[BeSequence](this) {

    override def withReplacedChildren(newChildren: Map[BeChildRole, BeExpression]): BeSequence = {
      def lookupChild(pos: Int): Option[BeExpression] = {
        val role = ExpressionInSequence(pos)
        if (newChildren.contains(role)) newChildren.get(role)
        else if (body.size < pos) Some(body(pos))
        else None
      }

      val maxPos = (newChildren.keys.flatMap {
        case c@ExpressionInSequence(nr) => Some(c)
        case _ => None
      }.map(_.nr) ++ body.indices.toList).maxOption

      if (maxPos.isEmpty) BeSequence.this else {
        val newBody = 0.to(maxPos.get).toList.flatMap(lookupChild)
        BeSequence.this.copy(body = newBody)
      }
    }

    override def toJavaStyleLines(myInfo: BeChildInfo): Seq[BeSegmentedCodeElement] = {
      getChildrenAsReference(myInfo.myScope).map(_.toSegment(None))
    }

    override def getChildrenAndExtension(myScope: BeScope): Seq[BeExpressionNode] = {

      def getChildPosFor(nr: Int): BeChildInfo = BeChildInfo(BeChildRole.ExpressionInSequence(nr), InSequenceScope(BeSequence.this, myScope))

      if (sequenceInfo.maxBodyElements.nonEmpty && body.size >= sequenceInfo.maxBodyElements.get) {
        body.zipWithIndex.map((curExpr, curNr) => BeExpressionReference(getChildPosFor(curNr), curExpr))
      } else {
        val bodyWithExtensions: Seq[BeExpressionNode] = body.zipWithIndex.flatMap((curExpr, curNr) => List(
          BeExtensionPoint(false, getChildPosFor(curNr), BeDataType.Unit),
          BeExpressionReference(getChildPosFor(curNr), curExpr)
        ))

        def lastExtendAnyOption: Option[BeExtensionPoint] = {
          if (sequenceInfo.maxBodyElements.isEmpty || sequenceInfo.maxBodyElements.get > body.size)
            Some(BeExtensionPoint(false, getChildPosFor(bodyWithExtensions.size), BeDataType.Unit))
          else None
        }

        def lastExtendCorrectOption: Option[BeExtensionPoint] = {
          if (sequenceInfo.mustEvaluateTo.nonEmpty && !sequenceInfo.mustEvaluateTo.get.canTakeValuesFrom(body.last.staticInformationExpression.staticType).possibleWithoutSyntaxErrors)
            Some(BeExtensionPoint(false, getChildPosFor(bodyWithExtensions.size), BeDataType.Unit))
          else None
        }

        bodyWithExtensions ++ lastExtendAnyOption ++ lastExtendCorrectOption
      }

    }
  }

}

object BeSequence {

  def optionalBody(body: List[BeExpression]) = BeSequence(body, BeSequenceInfo(None, None))

  def conditionalBody(body: List[BeExpression]) = BeSequence(body, BeSequenceInfo(Some(BeDataType.Boolean), Some(1)))

}
