package it.evadid.homepage.webElements.editor.qr

import com.raquo.laminar.api.L.*
import it.evadid.homepage.webElements.UiDomSuite
import it.evadid.workbook.interactions.qr.QrCodeRequirements
import it.evadid.workbook.interactions.qr.{QrCode, QrErrorCorrection}
import org.scalajs.dom

class QrCodeEditorSpec extends UiDomSuite {
  private val requirements = QrCodeRequirements(minBytes = 3, maxBytes = Some(20), requiredVersion = Some(2),
    minimumErrorCorrection = QrErrorCorrection.Q, requiredMask = Some(3))
  private def selects(root: dom.Element) = all(root, "select")
  private def passed(root: dom.Element) = all(root, ".qr-requirement").map(_.classList.contains("is-passed"))
  private def caption(root: dom.Element) = one(root, ".qr-preview > p").textContent

  test("text, correction level, version and mask edits produce the saved symbol and update the requirement checklist") {
    val state = Var(QrCode.fromText("hi"))
    val editor = QrCodeEditor(pageInfo, state, requirements)
    assert(!editor.dismissOnOutsideClick)
    mounted(editor.getDomElement()) { root =>
      assertEquals(passed(root).take(4), List(false, false, true, false), "too short, too weak, short enough, wrong version")
      assert(all(root, ".qr-legend li").nonEmpty, "the legend names the regions that occur")
      typeInto(one(root, "textarea"), "hello")
      assertEquals(state.now().text, "hello")
      val List(correction, version, mask) = selects(root): @unchecked
      choose(correction, "Q")
      choose(version, "2")
      choose(mask, "3")
      assertEquals((state.now().config.errorCorrection, state.now().config.version, state.now().config.mask), (QrErrorCorrection.Q, 2, 3))
      assertEquals(passed(root), List(true, true, true, true, true))
      assertEquals(caption(root), "5 bytes · v2 · Q · mask 3")
      assert(one(root, "svg.qr-symbol title").textContent.contains("v2 · Q · mask 3"))
      assert(all(root, "svg.qr-symbol path").exists(_.getAttribute("d").nonEmpty))
      choose(version, "")
      assertEquals(state.now().config.version, 1, "automatic picks the smallest version")
    }
  }

  test("text that does not fit the chosen version is flagged and keeps the last valid symbol") {
    val state = Var(QrCode.fromText("ok"))
    mounted(QrCodeEditor(pageInfo, state, QrCodeRequirements()).getDomElement()) { root =>
      choose(selects(root)(1), "1")
      choose(selects(root).head, "H")
      val saved = state.now()
      typeInto(one(root, "textarea"), "x" * 200)
      assert(one(root, ".qr-editor__error").textContent.contains(loadingText("basic/qrTooLarge")))
      assert(one(root, ".qr-editor__invalid").textContent.contains(loadingText("basic/qrInvalidPreview")))
      assertEquals(state.now(), saved)
      assertEquals(valueOf(one(root, "textarea")), "x" * 200, "the draft stays visible")
      state.set(QrCode.fromText("restored", QrErrorCorrection.L))
      assertEquals(valueOf(one(root, "textarea")), "restored", "a restored answer replaces the draft and clears the error")
      assertEquals(one(root, ".qr-editor__error").textContent, "")
      assertEquals(valueOf(selects(root).head), "L")
    }
  }
}
