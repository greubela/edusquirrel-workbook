package it.evadid.vm.analysis

import it.evadid.vm.conversion.parsed.{ParsedExpression, ParsedProgram, ParsedStatement}
import it.evadid.vm.parsing.source.{SourceLines, SourceSpan}

/** Adapter diagnostics use the syntax occurrence carried through the neutral model. */
private[analysis] object ParsedSourceWarnings {
  def at(source: String, kind: SourceWarning.Kind, subject: String, span: SourceSpan): SourceWarning = {
    val lines = SourceLines(source)
    SourceWarning(kind, subject, lines.line(span.start), lines.column(span.start), lines.line(span.end), lines.column(span.end))
  }

  def of(source: String, program: ParsedProgram): Seq[SourceWarning] = {
    def visit(value: Any): Seq[SourceWarning] = value match {
      case problem: ParsedStatement.Unsupported => problem.origin.toSeq.map(span => at(source,
        if problem.unparsable then SourceWarning.Kind.Unreadable else SourceWarning.Kind.Unsupported,
        problem.source.trim.linesIterator.nextOption().getOrElse(""), span))
      case problem: ParsedExpression.Unsupported => problem.origin.toSeq.map(at(source, SourceWarning.Kind.Unsupported, problem.source, _))
      case Some(value) => visit(value)
      case values: Iterable[?] => values.toSeq.flatMap(visit)
      case _: SourceSpan => Nil
      case product: Product => product.productIterator.toSeq.flatMap(visit)
      case _ => Nil
    }
    visit(program).distinct
  }
}
