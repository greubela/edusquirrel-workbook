package it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.matching

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.datastructures.state.StateHelper.StateBasedVar
import it.evadid.homepage.workbook.htmlRenderer.HtmlRenderFactory.LineBasedRenderingFactory
import it.evadid.homepage.workbook.htmlRenderer.InlineMarkdown
import it.evadid.homepage.workbook.htmlRenderer.atomarLineRenderings.AtomarLineRendering
import it.evadid.workbook.elements.interactionElements.matching.MatchingInteraction
import it.evadid.workbook.interaction.sync.UpdateImportance
import org.scalajs.dom

import scala.scalajs.js

/** Two columns of items; a connection line joins a left item with its chosen right item.
 *
 * Connections are made by dragging from a left item to a right item, or by clicking (or pressing Enter on)
 * a left item and then a right item, which also works with touch and keyboard. Clicking a line or the
 * remove button of a left item deletes its connection. Lines are drawn in an SVG overlay whose coordinates
 * are measured from the item buttons and re-measured whenever the board or an item changes size.
 */
object HtmlMatchingInteractionRenderer extends LineBasedRenderingFactory[MatchingInteraction] {

  private case class Point(x: Double, y: Double)

  private case class Drag(left: Int, start: Point, pointer: Point, moved: Boolean)

  private case class Connection(left: Int, right: Int, from: Point, to: Point)

  private val DragThreshold = 6.0

  private def basicLabel(key: String): LanguageMapContentId = LanguageMapContentId("basic/" + key)

  private def curve(from: Point, to: Point): String = {
    val dx = math.max(24.0, (to.x - from.x) / 2)
    s"M ${from.x} ${from.y} C ${from.x + dx} ${from.y}, ${to.x - dx} ${to.y}, ${to.x} ${to.y}"
  }

  /** Connection point of an item relative to the board: right edge for left items, left edge for right items. */
  private def anchor(board: dom.html.Element, side: String, index: Int): Option[Point] =
    Option(board.querySelector(s".matching-item--$side[data-index='$index']")).map { node =>
      val item = node.getBoundingClientRect()
      val origin = board.getBoundingClientRect()
      val x = if side == "left" then item.right - origin.left else item.left - origin.left
      Point(x, item.top - origin.top + item.height / 2)
    }

  private def relative(board: dom.html.Element, event: dom.PointerEvent): Point = {
    val origin = board.getBoundingClientRect()
    Point(event.clientX - origin.left, event.clientY - origin.top)
  }

  override protected def createRendering(e: MatchingInteraction): AtomarLineRendering = {
    val state = e.interactionVariable.createBoundStateWithUpdateImportance(UpdateImportance.MAJOR, fullInfo.workbookContextInformation).toAirstreamVar
    val locked = e.isDisabledState.toAirstreamVar
    val selected = Var(Option.empty[Int])
    val drag = Var(Option.empty[Drag])
    val dropTarget = Var(Option.empty[Int])
    val board = Var(Option.empty[dom.html.Element])
    val layoutTick = Var(0)
    var observer: Option[dom.ResizeObserver] = None
    // A drag that ends on its own left item would otherwise also count as a click on it.
    var suppressClick = false

    def connectionOf(leftIndex: Int): Signal[Option[Int]] =
      state.signal.map(_.selectedRightIndex(leftIndex, e.left.size, e.right.size)).distinct

    def connect(leftIndex: Int, rightIndex: Option[Int]): Unit =
      if !locked.now() then state.update(e.connect(_, leftIndex, rightIndex))

    def rightIndexAt(event: dom.PointerEvent): Option[Int] = for {
      root <- board.now()
      hit <- Option(dom.document.elementFromPoint(event.clientX, event.clientY))
      item <- Option(hit.closest(".matching-item--right"))
      if root.contains(item)
      index <- item.getAttribute("data-index").toIntOption
    } yield index

    val connections: Signal[Seq[Connection]] = state.signal.combineWith(board.signal, layoutTick.signal).map { (answer, root, _) =>
      root.toSeq.flatMap { node =>
        answer.sanitized(e.left.size, e.right.size).selectedRightIndicesByLeftIndex.zipWithIndex.flatMap { (target, leftIndex) =>
          for {
            rightIndex <- target
            from <- anchor(node, "left", leftIndex)
            to <- anchor(node, "right", rightIndex)
          } yield Connection(leftIndex, rightIndex, from, to)
        }
      }
    }

    val pendingLine: Signal[Option[(Point, Point)]] = drag.signal.combineWith(board.signal).map { (current, root) =>
      for {
        active <- current
        if active.moved
        node <- root
        from <- anchor(node, "left", active.left)
      } yield from -> active.pointer
    }

    val leftItems = e.left.zipWithIndex.map { (content, index) =>
      div(cls := "matching-row",
        // Placed before the item so the line leaving the item's right edge never crosses it. Always rendered,
        // so connecting does not change the item width and move the anchors.
        button(typ := "button", cls := "matching-remove",
          visibility <-- connectionOf(index).map(connection => if connection.isEmpty then "hidden" else "visible"),
          disabled <-- locked.signal.combineWith(connectionOf(index)).map((isLocked, connection) => isLocked || connection.isEmpty),
          aria.label <-- laminarHelper.plaintextStringSignal(basicLabel("matchingRemove")),
          title <-- laminarHelper.plaintextStringSignal(basicLabel("matchingRemove")),
          onClick --> (_ => connect(index, None)),
          "×"),
        button(typ := "button", cls := "matching-item matching-item--left", dataAttr("index") := index.toString,
          cls("matching-item--selected") <-- selected.signal.map(_.contains(index)),
          cls("matching-item--connected") <-- connectionOf(index).map(_.nonEmpty),
          aria.pressed <-- selected.signal.map(_.contains(index).toString),
          disabled <-- locked.signal,
          onPointerDown.filter(_.button == 0) --> { event =>
            if !locked.now() then board.now().foreach { root =>
              val point = relative(root, event)
              drag.set(Some(Drag(index, point, point, moved = false)))
            }
          },
          onClick --> { _ =>
            if !locked.now() && !suppressClick then selected.update(current => if current.contains(index) then None else Some(index))
          },
          InlineMarkdown.element(laminarHelper.plaintextStringSignal(content)),
          // Screen readers hear the current connection; sighted users see the line.
          child.maybe <-- connectionOf(index).map(_.map { rightIndex =>
            span(cls := "matching-sr-only",
              text <-- laminarHelper.plaintextStringSignal(basicLabel("matchingConnectedTo"))
                .combineWith(laminarHelper.plaintextStringSignal(e.right(rightIndex)))
                .map((prefix, category) => s" ($prefix: $category)"))
          }),
          span(cls := "matching-dot", aria.hidden := true)))
    }

    val rightItems = e.right.zipWithIndex.map { (content, index) =>
      button(typ := "button", cls := "matching-item matching-item--right", dataAttr("index") := index.toString,
        cls("matching-item--target") <-- dropTarget.signal.map(_.contains(index)),
        cls("matching-item--awaiting") <-- selected.signal.map(_.nonEmpty),
        disabled <-- locked.signal,
        onClick --> { _ =>
          selected.now().foreach { leftIndex =>
            connect(leftIndex, Some(index))
            selected.set(None)
          }
        },
        span(cls := "matching-dot", aria.hidden := true),
        InlineMarkdown.element(laminarHelper.plaintextStringSignal(content)))
    }

    val lines = svg.svg(svg.cls := "matching-lines",
      children <-- connections.map(_.map { connection =>
        val path = curve(connection.from, connection.to)
        svg.g(svg.cls := "matching-line",
          svg.path(svg.cls := "matching-line__stroke", svg.d := path),
          // Wide invisible stroke on top makes the thin line easy to hit.
          svg.path(svg.cls := "matching-line__hit", svg.d := path,
            onClick --> (_ => connect(connection.left, None)),
            svg.titleTag(text <-- laminarHelper.plaintextStringSignal(basicLabel("matchingRemove")))))
      }),
      child.maybe <-- pendingLine.map(_.map((from, to) =>
        svg.path(svg.cls := "matching-line__stroke matching-line__stroke--pending", svg.d := curve(from, to)))))

    val content = div(cls := "workbook-interaction matching-interaction",
      e.instruction.map(id => div(cls := "matching-instruction", InlineMarkdown.element(laminarHelper.plaintextStringSignal(id)))),
      p(cls := "matching-hint", text <-- laminarHelper.plaintextStringSignal(basicLabel("matchingHint"))),
      div(cls := "matching-board",
        cls("matching-board--dragging") <-- drag.signal.map(_.exists(_.moved)),
        onMountUnmountCallback(
          context => {
            val node = context.thisNode.ref
            board.set(Some(node))
            val remeasure: js.Function0[Unit] = () => layoutTick.update(_ + 1)
            val created = js.Dynamic.newInstance(js.Dynamic.global.ResizeObserver)(remeasure).asInstanceOf[dom.ResizeObserver]
            created.observe(node)
            val items = node.querySelectorAll(".matching-item")
            (0 until items.length).foreach(i => created.observe(items(i)))
            observer = Some(created)
            dom.window.requestAnimationFrame(_ => layoutTick.update(_ + 1))
          },
          _ => {
            observer.foreach(_.disconnect())
            observer = None
            board.set(None)
          }),
        documentEvents(_.onPointerMove) --> { event =>
          for {
            active <- drag.now()
            root <- board.now()
          } {
            val point = relative(root, event)
            val moved = active.moved || math.hypot(point.x - active.start.x, point.y - active.start.y) > DragThreshold
            drag.set(Some(active.copy(pointer = point, moved = moved)))
            dropTarget.set(if moved then rightIndexAt(event) else None)
          }
        },
        documentEvents(_.onPointerUp) --> { event =>
          drag.now().filter(_.moved).foreach { active =>
            rightIndexAt(event).foreach(rightIndex => connect(active.left, Some(rightIndex)))
            selected.set(None)
            suppressClick = true
            dom.window.setTimeout(() => suppressClick = false, 0)
          }
          drag.set(None)
          dropTarget.set(None)
        },
        documentEvents(_.onPointerCancel) --> { _ =>
          drag.set(None)
          dropTarget.set(None)
        },
        documentEvents(_.onKeyDown).filter(_.key == "Escape") --> { _ =>
          selected.set(None)
          drag.set(None)
          dropTarget.set(None)
        },
        div(cls := "matching-column matching-column--left", leftItems),
        div(cls := "matching-gap", aria.hidden := true),
        div(cls := "matching-column matching-column--right", rightItems),
        lines))
    AtomarLineRendering.basicLine(e, content)
  }
}
