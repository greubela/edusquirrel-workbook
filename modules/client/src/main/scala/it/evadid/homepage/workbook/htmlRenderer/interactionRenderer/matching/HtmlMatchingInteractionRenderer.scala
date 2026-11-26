package it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.matching

import com.raquo.airstream.ownership.Subscription
import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.datastructures.state.StateHelper.StateBasedVar
import it.evadid.homepage.workbook.htmlRenderer.HtmlRenderFactory.LineBasedRenderingFactory
import it.evadid.homepage.workbook.htmlRenderer.InlineMarkdown
import it.evadid.homepage.workbook.htmlRenderer.atomarLineRenderings.AtomarLineRendering
import it.evadid.workbook.elements.interactionElements.matching.MatchingInteraction
import it.evadid.workbook.interaction.sync.UpdateImportance
import org.scalajs.dom

/** Two columns of items; a connection line joins a left item with its chosen right item.
 *
 * Connections are made in either direction: by dragging from an item to an item of the other column, or by
 * clicking (or pressing Enter on) an item and then an item of the other column, which also works with touch and
 * keyboard. Connecting a left item replaces its previous connection. Clicking a line or the
 * remove button of a left item deletes its connection. Lines are drawn in an SVG overlay whose coordinates
 * are measured from the item buttons and re-measured whenever the board or an item changes size.
 */
object HtmlMatchingInteractionRenderer extends LineBasedRenderingFactory[MatchingInteraction] {

  private case class Point(x: Double, y: Double) derives upickle.default.ReadWriter

  private enum Side(val css: String) derives upickle.default.ReadWriter {
    case Left extends Side("left")
    case Right extends Side("right")
    def opposite: Side = if this == Left then Right else Left
  }

  /** An item of either column. */
  private case class End(side: Side, index: Int) derives upickle.default.ReadWriter

  /** A pointer press on an item; once it has moved beyond the threshold it draws a pending line.
    * A `dropped` drag has ended: its pointerup also produces a click when it ends on its own item, and that
    * click must not select the item. The next pointer press or key press replaces it.
    */
  private case class Drag(from: End, start: Point, pointer: Point, moved: Boolean, dropped: Boolean = false) derives upickle.default.ReadWriter {
    def drawing: Boolean = moved && !dropped
  }

  private case class Connection(left: Int, right: Int, from: Point, to: Point) derives upickle.default.ReadWriter

  private val DragThreshold = 6.0

  private def basicLabel(key: String): LanguageMapContentId = LanguageMapContentId("basic/" + key)

  private def curve(from: Point, to: Point): String = {
    val dx = math.max(24.0, (to.x - from.x) / 2)
    s"M ${from.x} ${from.y} C ${from.x + dx} ${from.y}, ${to.x - dx} ${to.y}, ${to.x} ${to.y}"
  }

  /** Connection point of an item relative to the board: right edge for left items, left edge for right items. */
  private def anchor(board: dom.html.Element, end: End): Option[Point] =
    Option(board.querySelector(s".matching-item--${end.side.css}[data-index='${end.index}']")).map { node =>
      val item = node.getBoundingClientRect()
      val origin = board.getBoundingClientRect()
      val x = if end.side == Side.Left then item.right - origin.left else item.left - origin.left
      Point(x, item.top - origin.top + item.height / 2)
    }

  private def relative(board: dom.html.Element, event: dom.PointerEvent): Point = {
    val origin = board.getBoundingClientRect()
    Point(event.clientX - origin.left, event.clientY - origin.top)
  }

  override protected def createRendering(e: MatchingInteraction): AtomarLineRendering = {
    val state = e.interactionVariable.createBoundStateWithUpdateImportance(UpdateImportance.MAJOR, fullInfo.workbookContextInformation).toAirstreamVar
    val locked = e.isDisabledState.toAirstreamVar
    val selected = Var(Option.empty[End])
    val drag = Var(Option.empty[Drag])
    val dropTarget = Var(Option.empty[End])
    val board = Var(Option.empty[dom.html.Element])
    val layoutTick = Var(0)
    // Statements that a failed check found wrongly connected are marked until the answer changes.
    val wrong = fullInfo.signals.reportersFor(e).grading.checks.map(_.wrongParts).distinct

    def connectionOf(leftIndex: Int): Signal[Option[Int]] =
      state.signal.map(_.selectedRightIndex(leftIndex, e.left.size, e.right.size)).distinct

    def connect(leftIndex: Int, rightIndex: Option[Int]): Unit =
      if !locked.now() then state.update(e.connect(_, leftIndex, rightIndex))

    /** Connects two items of different columns, given in either order. */
    def connectEnds(a: End, b: End): Unit = (a.side, b.side) match {
      case (Side.Left, Side.Right) => connect(a.index, Some(b.index))
      case (Side.Right, Side.Left) => connect(b.index, Some(a.index))
      case _ => ()
    }

    def itemAt(event: dom.PointerEvent, side: Side): Option[End] = for {
      root <- board.now()
      hit <- Option(dom.document.elementFromPoint(event.clientX, event.clientY))
      item <- Option(hit.closest(s".matching-item--${side.css}"))
      if root.contains(item)
      index <- item.getAttribute("data-index").toIntOption
    } yield End(side, index)

    val connections: Signal[Seq[Connection]] = state.signal.combineWith(board.signal, layoutTick.signal).map { (answer, root, _) =>
      root.toSeq.flatMap { node =>
        answer.sanitized(e.left.size, e.right.size).selectedRightIndicesByLeftIndex.zipWithIndex.flatMap { (target, leftIndex) =>
          for {
            rightIndex <- target
            from <- anchor(node, End(Side.Left, leftIndex))
            to <- anchor(node, End(Side.Right, rightIndex))
          } yield Connection(leftIndex, rightIndex, from, to)
        }
      }
    }

    val pendingLine: Signal[Option[(Point, Point)]] = drag.signal.combineWith(board.signal).map { (current, root) =>
      for {
        active <- current
        if active.drawing
        node <- root
        from <- anchor(node, active.from)
        // Curves run from left to right, also while dragging from a right item.
      } yield if active.from.side == Side.Left then from -> active.pointer else active.pointer -> from
    }

    /** Selection, dragging and connecting, shared by the items of both columns. */
    def itemControls(end: End): Seq[Modifier[HtmlElement]] = Seq(
      dataAttr("index") := end.index.toString,
      cls("matching-item--selected") <-- selected.signal.map(_.contains(end)),
      cls("matching-item--target") <-- dropTarget.signal.map(_.contains(end)),
      cls("matching-item--awaiting") <-- selected.signal.map(_.exists(_.side != end.side)),
      aria.pressed <-- selected.signal.map(_.contains(end).toString),
      disabled <-- locked.signal,
      onPointerDown.filter(_.button == 0) --> { event =>
        if !locked.now() then board.now().foreach { root =>
          val point = relative(root, event)
          drag.set(Some(Drag(end, point, point, moved = false)))
        }
      },
      onClick --> { _ =>
        if drag.now().exists(_.dropped) then drag.set(None)
        else if !locked.now() then selected.now() match {
          case Some(other) if other.side != end.side =>
            connectEnds(other, end)
            selected.set(None)
          case current => selected.set(if current.contains(end) then None else Some(end))
        }
      })

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
        button(typ := "button", cls := "matching-item matching-item--left",
          itemControls(End(Side.Left, index)),
          cls("matching-item--connected") <-- connectionOf(index).map(_.nonEmpty),
          cls("answer-part--wrong") <-- wrong.map(_.contains(index)),
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
      button(typ := "button", cls := "matching-item matching-item--right",
        itemControls(End(Side.Right, index)),
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
        cls("matching-board--dragging") <-- drag.signal.map(_.exists(_.drawing)),
        onMountCallback { context =>
          val node = context.thisNode.ref
          board.set(Some(node))
          // Reports once the board is laid out and whenever the board or an item changes size.
          val observer = new dom.ResizeObserver((_, _) => layoutTick.update(_ + 1))
          observer.observe(node)
          node.querySelectorAll(".matching-item").foreach(item => observer.observe(item))
          new Subscription(context.owner, () => {
            observer.disconnect()
            board.set(None)
          })
        },
        documentEvents(_.onPointerMove) --> { event =>
          for {
            active <- drag.now()
            if !active.dropped
            root <- board.now()
          } {
            val point = relative(root, event)
            val moved = active.moved || math.hypot(point.x - active.start.x, point.y - active.start.y) > DragThreshold
            drag.set(Some(active.copy(pointer = point, moved = moved)))
            dropTarget.set(if moved then itemAt(event, active.from.side.opposite) else None)
          }
        },
        documentEvents(_.onPointerUp) --> { event =>
          drag.set(drag.now().filter(_.drawing).map { active =>
            itemAt(event, active.from.side.opposite).foreach(connectEnds(active.from, _))
            selected.set(None)
            active.copy(dropped = true)
          })
          dropTarget.set(None)
        },
        documentEvents(_.onPointerCancel) --> { _ =>
          drag.set(None)
          dropTarget.set(None)
        },
        documentEvents(_.onKeyDown) --> { event =>
          if event.key == "Escape" then {
            selected.set(None)
            drag.set(None)
            dropTarget.set(None)
          }
          // A key press may start a keyboard click, which an ended drag must not swallow.
          else if drag.now().exists(_.dropped) then drag.set(None)
        },
        div(cls := "matching-column matching-column--left", leftItems),
        div(cls := "matching-gap", aria.hidden := true),
        div(cls := "matching-column matching-column--right", rightItems),
        lines))
    AtomarLineRendering.basicLine(e, content)
  }
}
