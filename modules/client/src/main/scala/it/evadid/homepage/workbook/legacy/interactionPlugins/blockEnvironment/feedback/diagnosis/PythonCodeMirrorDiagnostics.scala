package it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.feedback.diagnosis

import it.evadid.homepage.webElements.editor.code.CodeMirrorEditor
import it.evadid.vm.code.abstractions.BeExpression
import it.evadid.vm.code.errors.{BeExpressionUnparsable, BeExpressionUnsupported}
import it.evadid.vm.code.tree.BeExpressionReference
import it.evadid.vm.types.BeChildInfo
import it.evadid.vm.types.BeChildRole.NoRole
import it.evadid.vm.types.BeScope.GlobalScope

object PythonCodeMirrorDiagnostics:

  final case class SourceProblem(originalSource: String, message: String, severity: String)

  private val FramePattern = """^\s+File "([^"]+)", line (\d+)(?:, in .*)?$""".r
  private val ChainSeparators = Set(
    "During handling of the above exception, another exception occurred:",
    "The above exception was the direct cause of the following exception:"
  )

  def forProgram(program: BeExpression, rawPython: String): Seq[CodeMirrorEditor.Diagnostic] =
    val tree =
      program.recToTree(withExtensions = false, BeChildInfo(NoRole, GlobalScope()))

    val problems =
      tree.values.toSeq.collect {
        case BeExpressionReference(_, BeExpressionUnparsable(original, message)) =>
          SourceProblem(original, message, "warning")
        case BeExpressionReference(_, BeExpressionUnsupported(original)) =>
          SourceProblem(original, s"Unknown Python structure: $original", "soft")
      }

    deduplicate(problems.flatMap(problemToDiagnostic(_, rawPython)))

  def forRuntimeMessage(message: String): Option[CodeMirrorEditor.Diagnostic] =
    val lines = Option(message).getOrElse("").replace("\r\n", "\n").linesIterator.toVector
    val block = lines.drop(lines.lastIndexWhere(ChainSeparators.contains) + 1)
    val headlineIndex = block.indexWhere(isExceptionHeadline)
    if headlineIndex < 0 then None
    else
      block.take(headlineIndex).collect {
        case FramePattern("<student-source>", line) => line.toIntOption.filter(_ > 0)
      }.flatten.lastOption.map { lineNr =>
        CodeMirrorEditor.Diagnostic(line = lineNr, message = block(headlineIndex), severity = "error")
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

  private def isExceptionHeadline(line: String): Boolean =
    val name = line.takeWhile(ch => ch.isLetterOrDigit || ch == '_' || ch == '.')
    val suffix = line.drop(name.length)
    name.headOption.exists(ch => ch.isLetter || ch == '_') &&
      (suffix.isEmpty || suffix.startsWith(":"))
