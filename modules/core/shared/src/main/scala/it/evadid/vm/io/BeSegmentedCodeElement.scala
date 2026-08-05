package it.evadid.vm.io

import it.evadid.vm.code.abstractions.BeExpression
import it.evadid.vm.code.tree.BeExpressionReference
import it.evadid.vm.controlflow.{ControlFlowInfo, ControlFlowType}
import it.evadid.vm.io.BeSegmentedCodeElement.*
import it.evadid.vm.static.BeExpressionStaticInformation
import it.evadid.vm.types.BeChildInfo

trait BeSegmentedCodeElement {
  def allLines(): Seq[BeCodeLine]

  def allLinesWithExpressions: Seq[BeExpressionLine]
}

object BeSegmentedCodeElement {

  sealed trait BeCodeLine extends BeSegmentedCodeElement {
    override def allLines(): Seq[BeCodeLine] = List(this)

    def getExpression: Option[BeExpressionReference]
  }

  case class BeControlFlowLine(cfType: ControlFlowType) extends BeCodeLine {
    override def getExpression: Option[BeExpressionReference] = None

    override def allLinesWithExpressions: Seq[BeExpressionLine] = List()
  }

  case class BeExpressionLine(cfType: ControlFlowType, exprRef: BeExpressionReference) extends BeCodeLine {
    override def getExpression: Option[BeExpressionReference] = Some(exprRef)

    override def allLinesWithExpressions: Seq[BeExpressionLine] = List(this)
  }

  case class BeSegment(addToCfStack: Option[ControlFlowType], segmentInfo: BeChildInfo, myChildren: Seq[BeSegmentedCodeElement]) extends BeSegmentedCodeElement {
    override def allLines(): Seq[BeCodeLine] = myChildren.flatMap(_.allLines())

    override def allLinesWithExpressions: Seq[BeExpressionLine] = myChildren.flatMap(_.allLinesWithExpressions)
  }

  // todo based on commented-out code in the same package
  case class BeRenderingLine(
                              lineNr: Int,
                              controlFlowInfo: ControlFlowInfo,
                              associatedControlStructure: BeExpression,
                              associatedLineExpression: Option[BeExpression]
                            ) {

    def staticInfo: BeExpressionStaticInformation = associatedLineExpression.map(_.staticInformationSubtree).getOrElse(BeExpressionStaticInformation.empty)

  }

}


