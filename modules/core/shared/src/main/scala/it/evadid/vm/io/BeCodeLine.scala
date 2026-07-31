package it.evadid.vm.io


/*
sealed trait BeCodeLine extends BeCodeLinesElement {
  def lineNr: Int

  def controlFlowInfo: ControlFlowInfo

  override def allLines: Seq[BeCodeLine] = List(this)
  /*
  def nextLineFor(cfNextLine: ControlFlowType, expression: Option[BeExpressionReference], nextLineScope: BeScope): BeCodeLine = {
    val exprInfo = expression.map(expr => LineExpressionInfo(expr, Some(expr), nextLineScope))
    val staticInfo = expression.map((expr, role) => expr.staticInformationSubtree).getOrElse(BeExpressionStaticInformation.empty)
    nextLineFor(cfNextLine, exprInfo, staticInfo)
  }

  def nextLineFor(
                   cfNextLine: ControlFlowType,
                   expr: Option[LineExpressionInfo],
                   staticInfo: BeExpressionStaticInformation
                 ): BeCodeLine = {
    BeCodeLine(lineNr + 1, controlFlowInfo.createInfoForNextLine(cfNextLine), expr, staticInfo)
  }
*/
}

object BeCodeLine {

  case class BeControlFlowLine(
                                lineNr: Int,
                                controlFlowInfo: ControlFlowInfo,
                              ) extends BeCodeLine {
    def allExpressionLines(): Seq[BeExpressionCodeLine] = List()

    override def withUpdatedCodeLines(func: Int => Int): BeCodeLinesElement = this.copy(lineNr = func(lineNr))
  }

  def firstLine() = BeControlFlowLine(0, ControlFlowInfo(List(), ControlFlowType.ControlFlowStart))


  case class BeExpressionCodeLine
  (
    expressionReference: BeExpression,
    myControlFlowType: ControlFlowType
  ) extends BeCodeLine {


    def staticInfo: BeExpressionStaticInformation = associatedExpression.staticInformationSubtree

    override def allExpressionLines: Seq[BeExpressionCodeLine] = List(this)

    override def withUpdatedCodeLines(func: Int => Int): BeCodeLinesElement = this.copy(lineNr = func(lineNr))
  }
}

*/