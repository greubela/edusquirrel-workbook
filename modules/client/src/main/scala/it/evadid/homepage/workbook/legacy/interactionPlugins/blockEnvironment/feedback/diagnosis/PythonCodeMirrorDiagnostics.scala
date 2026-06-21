package it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.feedback.diagnosis

import it.evadid.homepage.webElements.editor.CodeMirrorEditor
import it.evadid.workbook.vm.code.BeExpression
import it.evadid.workbook.vm.code.errors.{BeExpressionUnparsable, BeExpressionUnsupported}
import it.evadid.workbook.vm.code.tree.BeExpressionReference
import it.evadid.workbook.vm.types.BeChildPosition
import it.evadid.workbook.vm.types.BeChildRole.NoRole
import it.evadid.workbook.vm.types.BeScope.GlobalScope

object PythonCodeMirrorDiagnostics:

  final case class SourceProblem(originalSource: String, message: String, severity: String)

  private val FramePattern = """(?i)File\s+"([^"]+)",\s+line\s+(\d+)""".r
  private val LinePattern = """(?i)\bline\s+(\d+)\b""".r

  def forProgram(program: BeExpression, rawPython: String): Seq[CodeMirrorEditor.Diagnostic] =
    val tree =
      program.recToTree(withExtensions = false, BeChildPosition(NoRole, GlobalScope()))

    val problems =
      tree.values.toSeq.collect {
        case BeExpressionReference(_, BeExpressionUnparsable(original, message)) =>
          SourceProblem(original, message, "warning")
        case BeExpressionReference(_, BeExpressionUnsupported(original)) =>
          SourceProblem(original, s"Unknown Python structure: $original", "soft")
      }

    deduplicate(problems.flatMap(problemToDiagnostic(_, rawPython)))

  def forRuntimeMessage(message: String): Option[CodeMirrorEditor.Diagnostic] =
    val normalized = Option(message).getOrElse("").replace("\r\n", "\n")
    val frames =
      FramePattern
        .findAllMatchIn(normalized)
        .flatMap(m => m.group(2).toIntOption.map(lineNr => m.group(1) -> lineNr))
        .toSeq

    val studentSourceFrameLine =
      frames
        .collect {
          case ("<student-source>", lineNr) => lineNr
        }
        .lastOption

    val dynamicStringFrameLine =
      frames
        .collect {
          case ("<string>", lineNr) => lineNr
        }
        .lastOption

    val fallbackLine =
      if frames.nonEmpty then None
      else
        LinePattern
          .findFirstMatchIn(normalized)
          .flatMap(m => m.group(1).toIntOption)

    studentSourceFrameLine
      .orElse(dynamicStringFrameLine)
      .orElse(fallbackLine)
      .map { lineNr =>
        CodeMirrorEditor.Diagnostic(
          line = math.max(1, lineNr),
          message = tracebackHeadline(normalized),
          severity = "error"
        )
      }

  def deduplicate(diagnostics: Seq[CodeMirrorEditor.Diagnostic]): Seq[CodeMirrorEditor.Diagnostic] =
    diagnostics.distinctBy(d => (d.line, d.endLine, d.fromCh, d.toCh, d.message))

  private def problemToDiagnostic(problem: SourceProblem, rawPython: String): Option[CodeMirrorEditor.Diagnostic] =
    val source = Option(problem.originalSource).getOrElse("").replace("\r\n", "\n").trim
    if source.isEmpty then None
    else
      val lines = Option(rawPython).getOrElse("").replace("\r\n", "\n").split("\n", -1).toIndexedSeq
      val sourceLines = source.split("\n", -1).map(_.trim).filter(_.nonEmpty).toIndexedSeq
      if sourceLines.isEmpty then None
      else
        val first = sourceLines.head
        val exactLine = lines.zipWithIndex.collectFirst {
          case (line, idx) if line.trim == first => line -> idx
        }
        val containingLine = exactLine.orElse {
          lines.zipWithIndex.collectFirst {
            case (line, idx) if line.contains(first) => line -> idx
          }
        }

        containingLine.map { case (line, idx) =>
          val fromCh = line.indexOf(first) match
            case pos if pos >= 0 => Some(pos)
            case _               => None
          val toCh = fromCh.map(_ + first.length)
          CodeMirrorEditor.Diagnostic(
            line = idx + 1,
            endLine = Some(idx + sourceLines.size),
            fromCh = if sourceLines.size == 1 then fromCh else None,
            toCh = if sourceLines.size == 1 then toCh else None,
            message = problem.message,
            severity = problem.severity
          )
        }

  private def tracebackHeadline(normalized: String): String =
    val lines =
      normalized
        .linesIterator
        .map(_.trim)
        .filter(_.nonEmpty)
        .toSeq

    lines
      .find(line => line.contains("SyntaxError") || line.contains("IndentationError"))
      .orElse(lines.reverse.find(isExceptionHeadline))
      .orElse(lines.reverse.find(isUsefulTracebackLine))
      .getOrElse("Python could not execute this line.")

  private def isExceptionHeadline(line: String): Boolean =
    val name = line.takeWhile(ch => ch.isLetterOrDigit || ch == '_' || ch == '.')
    val suffix = line.drop(name.length)
    val simpleName = name.split("\\.").lastOption.getOrElse(name)
    name.nonEmpty &&
      (suffix.isEmpty || suffix.startsWith(":")) &&
      (
        simpleName.endsWith("Error") ||
          simpleName.endsWith("Exception") ||
          simpleName.endsWith("Warning") ||
          simpleName == "KeyboardInterrupt" ||
          simpleName == "SystemExit" ||
          simpleName == "StopIteration" ||
          simpleName == "StopAsyncIteration" ||
          simpleName == "GeneratorExit"
      )

  private def isUsefulTracebackLine(line: String): Boolean =
    !line.startsWith("Traceback") &&
      !line.startsWith("File ") &&
      !line.startsWith("During handling of the above exception") &&
      !line.startsWith("The above exception was the direct cause") &&
      !line.matches("""\^+""")
