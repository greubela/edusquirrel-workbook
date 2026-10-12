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
  private case class StoredSegment(kind: String, payload: ujson.Value) derives upickle.default.ReadWriter

  given upickle.default.ReadWriter[BeSegmentedCodeElement] = upickle.default.readwriter[StoredSegment].bimap(
    element => element match {
      case value: BeControlFlowLine => StoredSegment("control-flow", upickle.default.writeJs(value))
      case value: BeExpressionLine => StoredSegment("expression", upickle.default.writeJs(value))
      case value: BeSegment => StoredSegment("segment", upickle.default.writeJs(value))
      case other => throw new IllegalArgumentException(s"Unsupported code segment: ${other.getClass.getName}")
    }, stored => stored.kind match {
      case "control-flow" => upickle.default.read[BeControlFlowLine](stored.payload)
      case "expression" => upickle.default.read[BeExpressionLine](stored.payload)
      case "segment" => upickle.default.read[BeSegment](stored.payload)
      case other => throw new IllegalArgumentException(s"Unknown code segment: $other")
    })

  sealed trait BeCodeLine extends BeSegmentedCodeElement derives upickle.default.ReadWriter {
    override def allLines(): Seq[BeCodeLine] = List(this)

    def getExpression: Option[BeExpressionReference]
  }

  case class BeControlFlowLine(cfType: ControlFlowType) extends BeCodeLine derives upickle.default.ReadWriter {
    override def getExpression: Option[BeExpressionReference] = None

    override def allLinesWithExpressions: Seq[BeExpressionLine] = List()
  }

  case class BeExpressionLine(cfType: ControlFlowType, exprRef: BeExpressionReference) extends BeCodeLine derives upickle.default.ReadWriter {
    override def getExpression: Option[BeExpressionReference] = Some(exprRef)

    override def allLinesWithExpressions: Seq[BeExpressionLine] = List(this)
  }

  case class BeSegment(addToCfStack: Option[ControlFlowType], segmentInfo: BeChildInfo, myChildren: Seq[BeSegmentedCodeElement]) extends BeSegmentedCodeElement derives upickle.default.ReadWriter {
    override def allLines(): Seq[BeCodeLine] = myChildren.flatMap(_.allLines())

    override def allLinesWithExpressions: Seq[BeExpressionLine] = myChildren.flatMap(_.allLinesWithExpressions)
  }

  // todo based on commented-out code in the same package
  case class BeRenderingLine(
                              lineNr: Int,
                              controlFlowInfo: ControlFlowInfo,
                              associatedControlStructure: BeExpression,
                              associatedLineExpression: Option[BeExpression]
                            ) derives upickle.default.ReadWriter {

    def staticInfo: BeExpressionStaticInformation = associatedLineExpression.map(_.staticInformationSubtree).getOrElse(BeExpressionStaticInformation.empty)

  }

}


