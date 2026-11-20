package it.evadid.facades.internal

import it.evadid.facades.{CodeMirror, CodeMirrorSuite}
import it.evadid.facades.internal.CodeMirrorJavaLineEndings.*
import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js

class CodeMirrorJavaLineEndingsSpec extends CodeMirrorSuite {
  private val mixed = "a\r\nb\nc\r\nd"

  private def state(libs: js.Dynamic, source: String): js.Dynamic = libs.EditorState.create(js.Dynamic.literal(
    doc = text(source),
    extensions = js.Array(field.init(_ => parse(source)), historyEffects, libs.history())))

  private def sourceOf(value: js.Dynamic): String = source(value.asInstanceOf[CodeMirror.Api.State])
  private def metadata(value: js.Dynamic): Metadata = value.field(field).asInstanceOf[Metadata]
  private def delete(from: Int, to: Int) = js.Dynamic.literal(changes = js.Dynamic.literal(from = from, to = to))
  private def restoreEffects(libs: js.Dynamic, start: js.Dynamic, transaction: js.Dynamic): js.Array[js.Dynamic] =
    start.facet(libs.invertedEffects).asInstanceOf[js.Array[js.Function1[js.Dynamic, js.Array[js.Dynamic]]]](0)(transaction)

  test("the dominant line separator is the first one and other separators are recorded at their normalized position") {
    assertEquals(parse(mixed), Metadata("\r\n", Vector(Exception(3, "\n"))))
    assertEquals(parse("x\ry\nz\r"), Metadata("\r", Vector(Exception(3, "\n"))))
    assertEquals(parse("single line"), Metadata("\n", Vector.empty))
    withLibraries { libs =>
      val document = text("a\r\nb\rc\n")
      assertEquals(document.toString(), "a\nb\nc\n")
      assertEquals(document.lines, 4)
      assertEquals(sourceOf(state(libs, mixed)), mixed)
      assertEquals(sourceOf(state(libs, "")), "")
      val unrecorded = libs.EditorState.create(js.Dynamic.literal(doc = "a\nb", extensions = field))
      assertEquals(metadata(unrecorded), Metadata("\n", Vector.empty), "documents without recorded endings use LF")
      Future.unit
    }
  }

  test("edits keep recorded separators in place, and deleting one falls back to the dominant separator") {
    withLibraries { libs =>
      val start = state(libs, mixed)
      val inserted = start.update(js.Dynamic.literal(changes = js.Dynamic.literal(from = 0, insert = "x\n"))).state
      assertEquals(sourceOf(inserted), "x\r\n" + mixed)
      assertEquals(metadata(inserted).exceptions, Vector(Exception(5, "\n")))
      val removed = start.update(delete(2, 4)).state
      assertEquals(sourceOf(removed), "a\r\nc\r\nd")
      val replaced = removed.update(js.Dynamic.literal(effects = reset.of(Metadata("\n", Vector(Exception(1, "\r")))))).state
      assertEquals(sourceOf(replaced), "a\rc\nd")
      Future.unit
    }
  }

  test("undo restores deleted separators and redo removes them again") {
    withLibraries { libs =>
      val view = js.Dynamic.newInstance(libs.EditorView)(js.Dynamic.literal(state = state(libs, mixed), parent = host()))
      def current = sourceOf(view.state)
      try {
        view.dispatch(delete(2, 4))
        assertEquals(current, "a\r\nc\r\nd")
        assert(libs.undo(view).asInstanceOf[Boolean])
        assertEquals(current, mixed)
        assert(libs.redo(view).asInstanceOf[Boolean])
        assertEquals(current, "a\r\nc\r\nd")
        assert(libs.undo(view).asInstanceOf[Boolean])
        view.dispatch(js.Dynamic.literal(changes = js.Dynamic.literal(from = 0, insert = "XY"),
          annotations = libs.Transaction.addToHistory.of(false)))
        view.dispatch(delete(4, 6))
        view.dispatch(js.Dynamic.literal(changes = js.Dynamic.literal(from = 0, insert = "Z"),
          annotations = libs.Transaction.addToHistory.of(false)))
        assert(libs.undo(view).asInstanceOf[Boolean])
        assertEquals(current, "ZXY" + mixed, "restored separators follow later non-history edits")
      } finally view.destroy()
      Future.unit
    }
  }

  test("restore effects map through later changes and are dropped when their line breaks are deleted") {
    withLibraries { libs =>
      val start = state(libs, mixed)
      val deletion = start.update(delete(2, 4))
      val effects = restoreEffects(libs, start, deletion)
      assertEquals(effects.length, 1)
      assertEquals(effects(0).value.asInstanceOf[Vector[Exception]], Vector(Exception(3, "\n")))
      val length = start.doc.length
      val shifted = effects(0).map(libs.ChangeSet.of(js.Dynamic.literal(from = 0, insert = "XY"), length))
      assertEquals(shifted.value.asInstanceOf[Vector[Exception]], Vector(Exception(5, "\n")))
      assert(js.isUndefined(effects(0).map(libs.ChangeSet.of(js.Dynamic.literal(from = 2, to = 5), length))))
      assertEquals(restoreEffects(libs, start, start.update(js.Dynamic.literal(changes = js.Dynamic.literal(from = 0, insert = "x\n")))).length, 0,
        "insertions remove no separators")
      Future.unit
    }
  }

  test("restore effects only patch line breaks and never record the dominant separator") {
    withLibraries { libs =>
      val start = state(libs, "a\nb\r\nc")
      val restore = restoreEffects(libs, start, start.update(delete(2, 4)))(0)
      assertEquals(restore.value.asInstanceOf[Vector[Exception]], Vector(Exception(3, "\r\n")))
      val dominant = state(libs, "a\r\nb\r\nc").update(js.Dynamic.literal(effects = restore)).state
      assertEquals(metadata(dominant).exceptions, Vector.empty)
      val noBreak = state(libs, "abcdef").update(js.Dynamic.literal(effects = restore)).state
      assertEquals(metadata(noBreak).exceptions, Vector.empty)
      val reapplied = state(libs, "a\nb\nc").update(js.Dynamic.literal(effects = restore)).state
      assertEquals(sourceOf(reapplied), "a\nb\r\nc")
      Future.unit
    }
  }
}
