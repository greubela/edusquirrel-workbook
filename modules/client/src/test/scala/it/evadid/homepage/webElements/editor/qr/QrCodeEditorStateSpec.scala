package it.evadid.homepage.webElements.editor.qr

import com.raquo.airstream.state.Var
import it.evadid.workbook.model.qr.{QrCode, QrErrorCorrection}
import munit.FunSuite

class QrCodeEditorStateSpec extends FunSuite {
  test("live text editing stores UTF-8 bytes and recomputes version and mask") {
    val saved = Var(QrCode.fromText(""))
    val editor = new QrCodeEditorState(saved)
    editor.setText("Grüße 🐿")
    assertEquals(saved.now(), QrCode.fromText("Grüße 🐿"))
    assert(!editor.error.now())
    editor.setText("x" * 200)
    assert(saved.now().config.version > 1)
    assertEquals(saved.now().byteCount, 200)
  }
  test("oversized drafts preserve saved state and recover after automatic version selection") {
    val saved = Var(QrCode.fromText("valid"))
    val editor = new QrCodeEditorState(saved)
    editor.setVersion(Some(1))
    val previous = saved.now()
    editor.setText("x" * 200)
    assert(editor.error.now())
    assertEquals(saved.now(), previous)
    assertEquals(editor.draft.now(), "x" * 200)
    editor.setVersion(None)
    assert(!editor.error.now())
    assertEquals(saved.now().text, "x" * 200)
    editor.setText("")
    assertEquals(saved.now().byteCount, 0)
  }
  test("explicit mask and correction selections update persisted metadata") {
    val saved = Var(QrCode.fromText("hello"))
    val editor = new QrCodeEditorState(saved)
    editor.setMask(Some(7))
    editor.setCorrection(QrErrorCorrection.H)
    assertEquals(saved.now().config.mask, 7)
    assertEquals(saved.now().config.errorCorrection, QrErrorCorrection.H)
    assertEquals(saved.now().text, "hello")
    editor.setMask(None)
    assertEquals(saved.now(), QrCode.fromText("hello", QrErrorCorrection.H))
    assertEquals(new QrCodeEditorState(saved).draft.now(), "hello")
  }
}
