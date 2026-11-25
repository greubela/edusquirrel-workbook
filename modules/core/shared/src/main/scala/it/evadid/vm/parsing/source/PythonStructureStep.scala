package it.evadid.vm.parsing.source

import it.evadid.core.datastructures.language.AppLanguage.Python
import it.evadid.vm.conversion.dependencies.KnownDependencies
import it.evadid.vm.parsing.source.SourceTokens.*

/** Builds [[PythonSourceStructured]] from the lexer's tokens; it accepts any text. */
private[source] object PythonStructureStep {
  private final case class Line(start: Int, end: Int, indent: Int, tokens: Seq[Token])

  def apply(source: String, tokens: Seq[Token]): PythonSourceStructured = {
    val sourceLines = SourceLines(source)
    val breaks = tokens.collect { case Symbol(start, _, "\n") => start }
    val logical = (0 +: breaks.map(_ + 1)).zip(breaks :+ source.length).map { (start, end) =>
      Line(start, end, source.substring(start, end).takeWhile(c => c == ' ' || c == '\t').length,
        tokens.filter(token => token.start >= start && token.end <= end))
    }.toVector
    def code(line: Line): Seq[Token] = significant(line.tokens)
    val lines = logical.map { line =>
      val tokens = code(line)
      PythonLineStructured(sourceLines.line(line.start), line.indent,
        if tokens.isEmpty then "" else source.substring(tokens.head.start, tokens.last.end),
        tokens.lastOption.exists { case Symbol(_, _, ":") => true; case _ => false }, sourceLines.span(line.start, line.end))
    }

    /** The block of the header on logical line `index`: up to the next code line indented no deeper. */
    def blockEnd(index: Int): Int =
      logical.drop(index + 1).find(next => code(next).nonEmpty && next.indent <= logical(index).indent).map(_.start).getOrElse(source.length)
    def bodyStart(index: Int, rest: Seq[Token]): Int =
      rest.collect { case Symbol(_, colonEnd, ":") => colonEnd }.lastOption.getOrElse(logical(index).end)

    val classes = logical.indices.flatMap { index =>
      code(logical(index)) match {
        case Seq(Word(_, _, "class"), Word(_, _, name), rest*) =>
          val line = logical(index)
          val end = blockEnd(index)
          val after = bodyStart(index, rest)
          val bases = rest.collectFirst { case group: Group if group.open == '(' => source.substring(group.start + 1, group.end - (if group.closed then 1 else 0)) }
          Some(PythonClassStructured(name, bases.getOrElse("").trim, source.substring(line.start, after).trim,
            SourceText.dedent(source.substring(after, end)), SourceText.dedent(source.substring(line.start, end)), line.indent,
            sourceLines.span(line.start, end)))
        case _ => None
      }
    }.toVector

    val functions = logical.indices.flatMap { index =>
      code(logical(index)).dropWhile { case Word(_, _, "async") => true; case _ => false } match {
        case Seq(Word(_, _, "def"), Word(_, _, name), rest*) =>
          val line = logical(index)
          val decorators = logical.take(index).reverse.takeWhile(previous => code(previous).headOption.exists {
            case Symbol(_, _, "@") => previous.indent == line.indent
            case _ => false
          }).reverse
          val start = decorators.headOption.fold(line.start)(_.start)
          val end = blockEnd(index)
          val after = bodyStart(index, rest)
          val parameters = rest.collectFirst { case group: Group if group.open == '(' => group }
          val returnType = rest.dropWhile { case Symbol(_, _, "-") => false; case _ => true }.drop(2)
            .takeWhile { case Symbol(_, _, ":") => false; case _ => true }
          val owner = classes.filter(owner => owner.span.start < line.start && line.start < owner.span.end && owner.indent < line.indent)
            .maxByOption(_.indent).map(_.name)
          Some(PythonFunctionStructured(name, owner, decorators.map(decorator => source.substring(code(decorator).head.start, code(decorator).last.end)),
            parameters.toVector.flatMap(parametersOf(source, _)), parameters.fold("")(group =>
              source.substring(group.start + 1, group.end - (if group.closed then 1 else 0)).trim),
            Option.when(returnType.nonEmpty)(source.substring(returnType.head.start, returnType.last.end)),
            source.substring(line.start, after).trim, SourceText.dedent(source.substring(after, end)),
            SourceText.dedent(source.substring(start, end)), line.indent, sourceLines.line(line.start), sourceLines.span(start, end)))
        case _ => None
      }
    }.toVector

    PythonSourceStructured(source, lines, classes, functions, balanced(tokens), blank(source, libraryRanges(source)))
  }

  /** Parameters split at top-level commas: `name`, `name: annotation`, `name = default`, `*args`, `**kwargs`. */
  private def parametersOf(source: String, group: Group): Vector[PythonParameterStructured] = {
    val code = significant(group.children).toVector
    val parts = code.foldLeft(Vector(Vector.empty[Token])) { (done, token) => token match {
      case Symbol(_, _, ",") => done :+ Vector.empty
      case other => done.init :+ (done.last :+ other)
    } }.filter(_.nonEmpty)
    parts.flatMap { tokens =>
      val text = source.substring(tokens.head.start, tokens.last.end)
      tokens.collectFirst { case Word(_, _, name) => name }.map { name =>
        val equals = tokens.indexWhere { case Symbol(_, _, "=") => true; case _ => false }
        val colon = tokens.indexWhere { case Symbol(_, _, ":") => true; case _ => false }
        def part(from: Int, until: Int) = Option.when(from >= 0 && from + 1 < until)(source.substring(tokens(from + 1).start, tokens(until - 1).end).trim)
        PythonParameterStructured(name, part(colon, if equals >= 0 then equals else tokens.size), part(equals, tokens.size), text)
      }
    }
  }

  /** Each library definition's first run of whole lines, ignoring indentation and blank lines. */
  private def libraryRanges(source: String): Seq[(Int, Int)] = {
    val lines = (0 +: source.indices.filter(source(_) == '\n').map(_ + 1)).map { start =>
      val end = source.indexOf('\n', start)
      start -> (if end < 0 then source.length else end)
    }
    val code = lines.filter((start, end) => source.substring(start, end).trim.nonEmpty)
    KnownDependencies.all.flatMap(_.definition(Python)).flatMap { definition =>
      val wanted = definition.linesIterator.map(_.trim).filter(_.nonEmpty).toVector
      code.indices.find(first => code.slice(first, first + wanted.size).map((start, end) => source.substring(start, end).trim) == wanted)
        .map(first => code(first)._1 -> math.min(source.length, code(first + wanted.size - 1)._2 + 1))
    }
  }
}
