package it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.matching

import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.homepage.webElements.UiDomSuite
import it.evadid.homepage.webElements.editor.code.EditorDomSuite
import it.evadid.homepage.workbook.htmlRenderer.HtmlRenderFactory
import it.evadid.workbook.elements.interactionElements.matching.MatchingInteraction
import org.scalajs.dom
import scala.scalajs.js

/** Selecting and connecting items of a matching task with clicks, drags and the keyboard. */
class HtmlMatchingInteractionRendererSpec extends UiDomSuite {
  private def ids(prefix: String, count: Int) = (1 to count).map(index => LanguageMapContentId(s"ui/$prefix$index"))
  private def task = MatchingInteraction("matching", None, ids("left", 2), ids("right", 2), Some(Seq(0, 1)))

  private def pointer(target: dom.EventTarget, kind: String, x: Double): Unit =
    target.dispatchEvent(js.Dynamic.newInstance(EditorDomSuite.window.MouseEvent)(kind,
      js.Dynamic.literal(bubbles = true, button = 0, clientX = x, clientY = 0)).asInstanceOf[dom.Event])

  private def key(target: dom.EventTarget, name: String): Unit =
    target.dispatchEvent(js.Dynamic.newInstance(EditorDomSuite.window.KeyboardEvent)("keydown",
      js.Dynamic.literal(bubbles = true, key = name)).asInstanceOf[dom.Event])

  private def rendered[T](body: dom.Element => T): T = {
    val interaction = task
    inOpenWorkbook(interaction)(mounted(HtmlRenderFactory.render(interaction).getDomElement())(body))
  }

  private def left(root: dom.Element, index: Int) = one(root, s".matching-item--left[data-index='$index']")
  private def isSelected(item: dom.Element) = item.getAttribute("aria-pressed") == "true"

  test("clicking a left item selects it and clicking it again deselects it") {
    rendered { root =>
      val first = left(root, 0)
      click(first)
      assert(isSelected(first))
      click(first)
      assert(!isSelected(first))
    }
  }

  test("a drag draws a pending line, and the click ending it on its own item does not select the item") {
    rendered { root =>
      val first = left(root, 0)
      val board = one(root, ".matching-board")
      pointer(first, "pointerdown", 0)
      pointer(dom.document, "pointermove", 20)
      assert(board.classList.contains("matching-board--dragging"))
      assertEquals(all(root, ".matching-line__stroke--pending").size, 1)
      pointer(dom.document, "pointerup", 20)
      assert(!board.classList.contains("matching-board--dragging"))
      assertEquals(all(root, ".matching-line__stroke--pending").size, 0)
      click(first)
      assert(!isSelected(first), "the click that ends the drag only finishes it")
      click(first)
      assert(isSelected(first), "the next click selects again")
    }
  }

  test("after a drag that ended elsewhere a keyboard click still selects the item") {
    rendered { root =>
      val first = left(root, 0)
      pointer(first, "pointerdown", 0)
      pointer(dom.document, "pointermove", 20)
      pointer(dom.document, "pointerup", 20)
      key(first, "Enter")
      click(first)
      assert(isSelected(first))
      key(first, "Escape")
      assert(!isSelected(first), "Escape cancels the selection")
    }
  }

  test("clicking a right item and then a left item connects them, as the other way round") {
    val interaction = task
    inOpenWorkbook(interaction)(mounted(HtmlRenderFactory.render(interaction).getDomElement()) { root =>
      val right = one(root, ".matching-item--right[data-index='1']")
      click(right)
      assert(isSelected(right))
      assert(left(root, 0).classList.contains("matching-item--awaiting"))
      click(left(root, 0))
      assert(!isSelected(right))
      assertEquals(interaction.interactionVariable.currentValue.selectedRightIndicesByLeftIndex, Seq(Some(1), None))
      click(left(root, 1))
      click(one(root, ".matching-item--right[data-index='0']"))
      assertEquals(interaction.interactionVariable.currentValue.selectedRightIndicesByLeftIndex, Seq(Some(1), Some(0)))
    })
  }

  test("a press without movement is an ordinary click") {
    rendered { root =>
      val first = left(root, 0)
      pointer(first, "pointerdown", 0)
      pointer(dom.document, "pointermove", 3)
      pointer(dom.document, "pointerup", 3)
      click(first)
      assert(isSelected(first))
    }
  }
}
