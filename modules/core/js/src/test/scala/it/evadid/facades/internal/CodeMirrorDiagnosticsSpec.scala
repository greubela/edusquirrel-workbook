package it.evadid.facades.internal

import it.evadid.facades.CodeMirrorSuite
import it.evadid.facades.internal.CodeMirrorDiagnostics.*
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js

class CodeMirrorDiagnosticsSpec extends CodeMirrorSuite {
  private def diagnostic(fields: (String, js.Any)*): js.Object = js.Dynamic.literal(fields*)

  test("raw diagnostics are normalized to valid lines, clamped ranges and known severities") {
    val raw = js.Array[js.Any](
      diagnostic("line" -> 2, "fromCh" -> 1.7, "toCh" -> "4", "severity" -> "ERROR", "message" -> "bad"),
      diagnostic("line" -> "3", "endLine" -> 10, "severity" -> "info"),
      diagnostic("line" -> 1, "endLine" -> 0, "fromCh" -> -3, "severity" -> "soft", "message" -> 42),
      diagnostic("line" -> 1, "severity" -> "hint", "message" -> null),
      diagnostic("line" -> 0), diagnostic("line" -> 4), diagnostic("line" -> 1.5), diagnostic("line" -> "x"),
      null, js.undefined, "text")
    assertEquals(normalize(raw, 3).toList, List(
      Normalized(2, 2, Some(1.0), Some(4.0), "error", "bad"),
      Normalized(3, 3, None, None, "soft", ""),
      Normalized(1, 1, Some(0.0), None, "soft", "42"),
      Normalized(1, 1, None, None, "warning", "")))
    assertEquals(normalize(js.Dynamic.literal(line = 1), 3), Vector.empty)
    assertEquals(normalize(js.Array(diagnostic("line" -> 1)), 0), Vector.empty)
  }

  test("overlapping ranges on a line are split, the most severe label wins and messages are combined") {
    val error = Normalized(1, 1, Some(2), Some(6), "error", "type error")
    val warning = Normalized(1, 1, Some(0), Some(4), "warning", "unused")
    val duplicate = Normalized(1, 1, Some(0), Some(4), "soft", "unused")
    val whole = Normalized(1, 1, None, None, "soft", "")
    val multiLine = Normalized(1, 2, Some(0), Some(3), "warning", "block")
    val empty = Normalized(1, 1, Some(5), Some(5), "error", "empty")
    val plan = CodeMirrorDiagnostics.plan(Vector(warning, whole, error, duplicate, multiLine, empty), lineLength = 5).get
    assertEquals(plan.label, Label("error", "type error\nempty\nunused\nblock"))
    assertEquals(plan.marks.toList, List(
      Mark(0, 2, Label("warning", "unused")),
      Mark(2, 4, Label("error", "type error\nunused")),
      Mark(4, 5, Label("error", "type error"))))
    assertEquals(CodeMirrorDiagnostics.plan(Nil, 5), None)
    assertEquals(CodeMirrorDiagnostics.plan(Vector(whole), 5).get, LinePlan(Label("soft", ""), Vector.empty))
  }

  test("diagnostics effects decorate lines and ranges, and edits map the decorations") {
    withLibraries { libs =>
      val diagnostics = new CodeMirrorDiagnostics
      val state = libs.EditorState.create(js.Dynamic.literal(doc = "a = 1\nprint(b)\nok", extensions = diagnostics.field))
      val raw = js.Array[js.Object](
        diagnostic("line" -> 2, "fromCh" -> 6, "toCh" -> 7, "severity" -> "error", "message" -> "undefined b"),
        diagnostic("line" -> 1, "endLine" -> 3, "severity" -> "warning", "message" -> ""),
        diagnostic("line" -> 3, "severity" -> "info", "message" -> "note"))
      val decorated = state.update(js.Dynamic.literal(effects = diagnostics.effect.of(raw))).state
      def entries(current: js.Dynamic) = {
        val found = js.Array[(Int, Int, String, String, String)]()
        current.field(diagnostics.field).between(0, current.doc.length, ((from: Int, to: Int, value: js.Dynamic) => {
          val attributes = value.spec.attributes
          found.push((from, to, value.spec.selectDynamic("class").asInstanceOf[String],
            attributes.selectDynamic("data-diagnostic-severity").asInstanceOf[String],
            attributes.title.asInstanceOf[js.UndefOr[String]].getOrElse("-")))
          ()
        }): js.Function3[Int, Int, js.Dynamic, Unit])
        found.toList
      }
      val lineClass = "cm-edusquirrel-diagnostic"
      val markClass = "cm-edusquirrel-diagnostic-mark"
      assertEquals(entries(decorated), List(
        (0, 0, lineClass, "warning", "-"),
        (6, 6, s"$lineClass $lineClass-error", "error", "undefined b"),
        (12, 13, s"$markClass $markClass-error", "error", "undefined b"),
        (15, 15, lineClass, "warning", "note")))
      val shifted = decorated.update(js.Dynamic.literal(changes = js.Dynamic.literal(from = 5, insert = "\n# x"))).state
      assertEquals(entries(shifted).map(_._1), List(0, 10, 16, 19))
      val cleared = shifted.update(js.Dynamic.literal(effects = diagnostics.effect.of(js.Array()))).state
      assertEquals(entries(cleared), Nil)
      scala.concurrent.Future.unit
    }
  }
}
