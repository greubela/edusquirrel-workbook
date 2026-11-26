package it.evadid.vm.code.controlStructures

import it.evadid.core.datastructures.language.LanguageMap
import it.evadid.vm.code.abstractions.{BeControlStructure, BeExpression}
import it.evadid.vm.code.tree.{BeExpressionNode, BeExpressionReference}
import it.evadid.vm.code.usage.BeUseValue
import it.evadid.vm.controlflow.ControlFlowType.{ControlFlowUp, RepeatBranch, RepeatUnion}
import it.evadid.vm.io.BeSegmentedCodeElement.BeControlFlowLine
import it.evadid.vm.io.{BeExpressionStructureInfo, BeSegmentedCodeElement}
import it.evadid.vm.static.BeExpressionStaticInformation
import it.evadid.vm.types.*
import it.evadid.vm.types.BeScope.InSequenceScope

/** Repeats `body` `amount` times, where the amount is any numeric expression: Snap's `repeat (n)`,
  * Python's `for _ in range(n)` and a Java counting `for` whose body does not use the counter.
  */
case class BeRepeatNr(amount: BeExpression, body: BeSequence) extends BeControlStructure{

  /** The amount when it is a whole-number literal. */
  def fixedAmount: Option[Int] = amount match {
    case BeUseValue(BeDataValueLiteral(raw), _) => raw.trim.toIntOption
    case _ => None
  }

  override def allPossibleBodies: Seq[BeExpression] = List(body)

  override lazy val staticInformationExpression: BeExpressionStaticInformation = new BeExpressionStaticInformation() {
    override def syntaxErrors: Seq[BeInfo] = {
      if (fixedAmount.exists(_ < 0)) List(BeInfo(LanguageMap.universalMap("repeat count must be zero or positive"), BeInfo.SyntaxError.InvalidLiteralValue))
      else List()
    }
  }

  override lazy val structureInfo: BeExpressionStructureInfo[?] = new BeExpressionStructureInfo(this) {

    override def getChildrenAndExtension(myScope: BeScope): List[BeExpressionNode] =
      List(
        BeExpressionReference(BeChildInfo(BeChildRole.ExpressionInSequence(0), myScope), amount),
        BeExpressionReference(BeChildInfo(BeChildRole.BodySequence(0), InSequenceScope(body, myScope)), body))

    override def withReplacedChildren(newChildren: Map[BeChildRole, BeExpression]): BeRepeatNr = {
      val newAmount = newChildren.getOrElse(BeChildRole.ExpressionInSequence(0), amount)
      val newBody = newChildren.collectFirst {
        case (BeChildRole.BodySequence(0), seq: BeSequence) => seq
      }.getOrElse(body)
      copy(amount = newAmount, body = newBody)
    }

    override def toJavaStyleLines(myInfo: BeChildInfo): Seq[BeSegmentedCodeElement] = {
      List(
        BeControlFlowLine(RepeatBranch),
        getChildrenAsReference(myInfo.myScope).last.toSegment(Some(ControlFlowUp)),
        BeControlFlowLine(RepeatUnion)
      )
    }

  }

}

object BeRepeatNr {
  /** A repetition with a fixed whole-number count. */
  def times(count: Int, body: BeSequence): BeRepeatNr = BeRepeatNr(BeUseValue(BeDataValueLiteral(count.toString), None), body)
}
