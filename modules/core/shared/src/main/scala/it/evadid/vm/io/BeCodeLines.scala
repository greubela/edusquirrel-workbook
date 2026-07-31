package it.evadid.vm.io

/*

object BeCodeLines {

  trait BeCodeLinesElement {
    def allExpressionLines: Seq[BeExpressionCodeLine]

    def allLines: Seq[BeCodeLine]

    def withUpdatedCodeLines(func: Int => Int): BeCodeLinesElement
  }

  object BeCodeSegment {
    def tmpEmpty(ref: BeExpressionReference, parentStack: Seq[ControlFlowType]): BeCodeSegment = BeCodeSegment(myInfo, associatedExpression, parentStack)
  }


  def toSegmentOrLine: Either[BeCodeSegment, BeCodeLine] = {

  }


  case class BeCodeSegment(
                            ref: BeExpressionReference,
                            myChildren: Seq[BeCodeLinesElement] = List()
                          ) extends BeCodeLinesElement {
    lazy val nextLineNr: Int = myChildren.flatMap(_.allLines.map(_.lineNr)).maxOption.getOrElse(0) + 1

    private def typeToInfo(cfType: ControlFlowType): ControlFlowInfo = ControlFlowInfo(cfStack, cfType)

    override def allExpressionLines: Seq[BeExpressionCodeLine] = myChildren.flatMap(_.allExpressionLines)

    override def allLines: Seq[BeCodeLine] = myChildren.flatMap(_.allLines)

    def withAppendedNewLine(codeLine: BeCodeLine): BeCodeSegment = {
      this.copy(myChildren = myChildren ++ List(codeLine))
    }

    def withAppendedControlFlowLine(cfType: ControlFlowType): BeCodeSegment = {
      withAppendedNewLine(BeControlFlowLine(nextLineNr, typeToInfo(cfType)))
    }

    def withAppendedExpressionLine(expression: BeExpression, exprChildInfo: BeChildInfo, exprCfType: ControlFlowType): BeCodeSegment = {
      withAppendedNewLine(BeExpressionCodeLine(nextLineNr, typeToInfo(exprCfType), expression, myInfo))
    }

    def withSegmentAutoAdjust(segment: BeCodeSegment): BeCodeSegment = {
      val fixedLines = segment.withUpdatedCodeLines(_ - segment.nextLineNr + nextLineNr)
      val fixedStack = segment.
        this.copy(myChildren = myChildren ++ List(withFixedLineNrs))
    }

    def withUpdatedCodeLines(func: Int => Int): BeCodeLinesElement = {
      this.copy(myChildren = myChildren.map(_.withUpdatedCodeLines(func)))
    }

  }
}

/*
case class BeCodeLines(logger: Logger, segments: List[BeCodeLinesElement]) extends Iterable[BeCodeLine] {

  def appendWithBlock(newLines: BeExpressionCodeLines): BeExpressionCodeLines = {
    var res = ensureStartLine
    for (cur <- newLines) res = res.appendWithLast(_.asNextLine(cur))
    res
  }

  def appendWithLine(newLine: BeCodeLine): BeExpressionCodeLines = {
    ensureStartLine.appendWithLast(_.asNextLine(newLine))
  }

  def appendWithLast(func: BeCodeLine => BeCodeLine): BeExpressionCodeLines = {
    ensureStartLine.copy(lines = lines ++ List(func(lines.last)))
  }

  override def iterator: Iterator[BeCodeLine] = lines.iterator
}
*/
*/
