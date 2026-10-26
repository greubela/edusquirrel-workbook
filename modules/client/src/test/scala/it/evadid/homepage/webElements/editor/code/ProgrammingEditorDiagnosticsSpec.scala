package it.evadid.homepage.webElements.editor.code
import munit.FunSuite
import com.raquo.airstream.state.Var
import com.raquo.airstream.ownership.ManualOwner
import it.evadid.core.datastructures.language.AppLanguage
import it.evadid.core.datastructures.vectorShapes.svg.TurtlePathBuilder.TurtleCommand
import it.evadid.homepage.webElements.editor.code.EvaEditor.extensions.turtle.*
import it.evadid.workbook.elements.interactionElements.programming.state.*
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseRegular.*
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseTurtle.*
import scala.concurrent.{Future, Promise}
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import it.evadid.homepage.webElements.editor.code.codemirror.CodeMirrorEditor
import it.evadid.facades.internal.CodeMirrorDiagnostics

class ProgrammingEditorDiagnosticsSpec extends FunSuite {
  test("ProgrammingState converts itself at the Snap editor boundary") {
    val snap = ProgrammingStatePythonString("forward(10)").toSnapXml
    assert(snap.snapXml.contains("<project"), clue = snap.snapXml.take(120))
    assert(snap.snapXml.contains("forward"), clue = snap.snapXml)
  }

  test("CodeMirror maps Java and Python independently") {
    assertEquals(CodeMirrorEditor.languageToJs(AppLanguage.Java), "java")
    assertEquals(CodeMirrorEditor.languageToJs(AppLanguage.Python), "python")
    assertEquals(CodeMirrorEditor.languageToJs(AppLanguage.SQL), "sql")
    assertEquals(CodeMirrorEditor.languageToJs(AppLanguage.C), "c")
    assertEquals(CodeMirrorEditor.languageToJs(AppLanguage.Cpp), "cpp")
  }

  test("Java source metadata preserves mixed separators at UTF-16 positions") {
    import it.evadid.facades.internal.CodeMirrorJavaLineEndings as Endings
    for (source, separator, exceptions) <- List(
      ("", "\n", Vector.empty[Endings.Exception]),
      ("\tclass Drawing {}", "\n", Vector.empty[Endings.Exception]),
      ("a\r\n", "\r\n", Vector.empty[Endings.Exception]),
      ("a\rb\n", "\r", Vector(Endings.Exception(3, "\n"))),
      ("a\r\nb\nc\rd\r\n", "\r\n", Vector(Endings.Exception(3, "\n"), Endings.Exception(5, "\r"))),
      ("😀\r\nx\ny\r", "\r\n", Vector(Endings.Exception(4, "\n"), Endings.Exception(6, "\r")))
    ) do assertEquals(Endings.parse(source), Endings.Metadata(separator, exceptions))
  }

  test("CodeMirror diagnostic normalization rejects invalid lines without clamping them") {
    val raw = js.Array[js.Any](null, js.undefined, js.Dynamic.literal(),
      js.Dynamic.literal(line = 0), js.Dynamic.literal(line = -1), js.Dynamic.literal(line = 4),
      js.Dynamic.literal(line = 1.5), js.Dynamic.literal(line = Double.NaN),
      js.Dynamic.literal(line = Double.PositiveInfinity), js.Dynamic.literal(line = "2"))
    assertEquals(CodeMirrorDiagnostics.normalize(raw, 3).map(_.line), Vector(2))
    Seq[js.Any](null, js.undefined, js.Dynamic.literal(), "diagnostic", 4).foreach { value =>
      assertEquals(CodeMirrorDiagnostics.normalize(value, 3), Vector.empty)
    }
    assertEquals(CodeMirrorDiagnostics.normalize(js.Array(js.Dynamic.literal(line = 1)), 0), Vector.empty)
  }

  test("CodeMirror diagnostics preserve numeric coercion, safe columns and severity defaults") {
    val raw = js.Array(
      js.Dynamic.literal(line = 1, endLine = -1, fromCh = null, toCh = "2.9", severity = "ERROR", message = 7),
      js.Dynamic.literal(line = 2, endLine = 1e12, fromCh = -7, toCh = Double.PositiveInfinity, severity = "INFO"),
      js.Dynamic.literal(line = 3, endLine = Double.NaN, fromCh = js.undefined, severity = " Error ", message = null))
    val entries = CodeMirrorDiagnostics.normalize(raw, 3)
    assertEquals(entries.map(item => (item.line, item.endLine)), Vector((1, 1), (2, 3), (3, 3)))
    assertEquals(entries.map(_.fromCh), Vector(Some(0.0), Some(0.0), None))
    assertEquals(entries.map(_.toCh), Vector(Some(2.0), None, None))
    assertEquals(entries.map(_.severity), Vector("error", "soft", "warning"))
    assertEquals(entries.map(_.message), Vector("7", "", ""))
  }

  test("CodeMirror diagnostic overlap has stable priority, distinct messages and unmarked gaps") {
    import CodeMirrorDiagnostics.*
    val info = Normalized(1, 1, Some(0), Some(2), "soft", "Info")
    val error = Normalized(1, 1, Some(1), Some(3), "error", "Error")
    val expected = LinePlan(Label("error", "Error\nInfo"), Vector(
      Mark(0, 1, Label("soft", "Info")), Mark(1, 2, Label("error", "Error\nInfo")),
      Mark(2, 3, Label("error", "Error"))))
    Seq(Vector(info, error), Vector(error, info)).foreach { items =>
      assertEquals(plan(items, 3), Some(expected))
    }
    assertEquals(plan(Vector(info.copy(toCh = Some(1)), error.copy(fromCh = Some(2))), 3).map(_.marks),
      Some(Vector(Mark(0, 1, Label("soft", "Info")), Mark(2, 3, Label("error", "Error")))))
    assertEquals(plan(Vector(error, error), 3).map(_.label), Some(Label("error", "Error")))
    assertEquals(plan(Vector.empty, 3), None)
  }

  test("CodeMirror diagnostic spans clamp before integer conversion and retain UTF-16 offsets") {
    import CodeMirrorDiagnostics.*
    val entry = Normalized(1, 1, Some(2), Some(1e100), "error", "<img src=x onerror=alert(1)>")
    val planned = plan(Vector(entry), "😀x".length).get
    assertEquals(planned.marks, Vector(Mark(2, 3, Label("error", entry.message))))
    assertEquals("😀x".substring(planned.marks.head.from, planned.marks.head.to), "x")
    Seq(entry.copy(fromCh = Some(99)), entry.copy(toCh = Some(1)), entry.copy(toCh = Some(2)),
      entry.copy(toCh = None), entry.copy(endLine = 2)).foreach { noSpan =>
      assertEquals(plan(Vector(noSpan), 3).map(_.marks), Some(Vector.empty))
    }
    assertEquals(plan(Vector(entry.copy(fromCh = Some(0))), 0).map(_.marks), Some(Vector.empty))
  }

  test("CodeMirror diagnostic overlap agrees with the strongest diagnostic at every character") {
    import CodeMirrorDiagnostics.*
    val random = new scala.util.Random(7249)
    val severities = Vector("soft", "warning", "error")
    (0 until 500).foreach { sample =>
      val entries = Vector.fill(1 + random.nextInt(12)) {
        Normalized(1, 1, Some(random.nextInt(14) - 2), Some(random.nextInt(14) - 2),
          severities(random.nextInt(3)), s"message-${random.nextInt(4)}")
      }
      val marks = plan(entries, 10).get.marks
      (0 until 10).foreach { position =>
        val active = entries.filter(item => item.fromCh.exists(from => math.max(0, from) <= position) && item.toCh.exists(_ > position))
        val actual = marks.filter(mark => mark.from <= position && mark.to > position)
        if active.isEmpty then assertEquals(actual.size, 0, clue = s"$sample/$position")
        else {
          assertEquals(actual.size, 1, clue = s"$sample/$position")
          assertEquals(actual.head.label.severity, severities(active.map(item => severities.indexOf(item.severity)).max))
          val messages = actual.head.label.message.split("\n").toVector
          assertEquals(messages.distinct, messages)
        }
      }
    }
  }

}
