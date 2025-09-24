package interactionPlugins.automaton

import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.{*, given}
import com.raquo.laminar.api.L.svg
import com.raquo.laminar.api.L.svg.*
import org.scalajs.dom
import org.scalajs.dom.{MouseEvent, PointerEvent, html}
import workbook.workbookHtmlElements.abstractions.HtmlWorkbookElement

class HtmlAutomatonEditorArea(
  store: AutomatonEditorStore,
  addTransitionModeVar: Var[Boolean],
  pendingTransitionVar: Var[Option[String]]
) extends HtmlWorkbookElement {

  private val nodeRadius = 32.0
  private var containerElement: Option[html.Div] = None
  private var lastKnownSize: (Double, Double) = (640, 380)
  private val sizeVar: Var[(Double, Double)] = Var(lastKnownSize)
  private val draggingVar: Var[Option[(String, Double, Double)]] = Var(None)

  private case class ContextMenuState(nodeId: String, x: Double, y: Double)

  private val contextMenuVar: Var[Option[ContextMenuState]] = Var(None)

  private def updateSize(): Unit = containerElement.foreach { el =>
    val rect = el.getBoundingClientRect()
    lastKnownSize = (rect.width, rect.height)
    sizeVar.set(lastKnownSize)
  }

  def currentSize: (Double, Double) = lastKnownSize

  def defaultNodePosition: (Double, Double) = (lastKnownSize._1 / 2 - nodeRadius, lastKnownSize._2 / 2 - nodeRadius)

  private def startDrag(node: AutomatonNode, event: PointerEvent): Unit = {
    if (addTransitionModeVar.now()) return
    containerElement.foreach { container =>
      val rect = container.getBoundingClientRect()
      val offsetX = event.clientX - rect.left - node.x
      val offsetY = event.clientY - rect.top - node.y
      draggingVar.set(Some((node.id, offsetX, offsetY)))
    }
  }

  private def handleTransitionClick(nodeId: String): Unit = {
    if (!addTransitionModeVar.now()) return
    pendingTransitionVar.now() match {
      case None => pendingTransitionVar.set(Some(nodeId))
      case Some(fromId) =>
        val response = dom.window.prompt("Transition symbols (comma separated):", "")
        if (response != null) {
          store.addTransition(fromId, nodeId, response)
        }
        pendingTransitionVar.set(None)
        addTransitionModeVar.set(false)
    }
  }

  private def showContextMenu(nodeId: String, event: MouseEvent): Unit = {
    event.preventDefault()
    containerElement.foreach { container =>
      val rect = container.getBoundingClientRect()
      val localX = event.clientX - rect.left
      val localY = event.clientY - rect.top
      contextMenuVar.set(Some(ContextMenuState(nodeId, localX, localY)))
    }
  }

  private def renderContextMenu(state: ContextMenuState): HtmlElement = {
    div(
      cls := "automaton-context-menu",
      style := s"left: ${state.x}px; top: ${state.y}px;",
      button("Set as start", onClick --> (_ => { store.setAsStart(state.nodeId); contextMenuVar.set(None) })),
      button("Toggle accepting", onClick --> (_ => { store.toggleAccepting(state.nodeId); contextMenuVar.set(None) })),
      button("Delete state", onClick --> (_ => { store.removeState(state.nodeId); contextMenuVar.set(None) }))
    )
  }

  private def transitionPath(transition: AutomatonTransition, nodes: Map[String, AutomatonNode]): Option[SvgElement] = {
    for {
      from <- nodes.get(transition.fromStateId)
      to <- nodes.get(transition.toStateId)
    } yield {
      if (from.id == to.id) {
        val centerX = from.x + nodeRadius
        val topY = from.y
        val controlOffset = 60.0
        val pathData =
          s"M ${centerX} ${topY} C ${centerX - controlOffset} ${topY - controlOffset}, ${centerX + controlOffset} ${topY - controlOffset}, ${centerX} ${topY}"
        path(d := pathData, cls := "self-loop")
      } else {
        val (startX, startY, endX, endY) = edgeEndpoints(from, to)
        path(d := s"M $startX $startY L $endX $endY")
      }
    }
  }

  private def edgeEndpoints(from: AutomatonNode, to: AutomatonNode): (Double, Double, Double, Double) = {
    val startX = from.x + nodeRadius
    val startY = from.y + nodeRadius
    val endX = to.x + nodeRadius
    val endY = to.y + nodeRadius
    val dx = endX - startX
    val dy = endY - startY
    val distance = math.sqrt(dx * dx + dy * dy)
    if (distance == 0) return (startX, startY, endX, endY)
    val offsetX = dx / distance * nodeRadius
    val offsetY = dy / distance * nodeRadius
    (startX + offsetX, startY + offsetY, endX - offsetX, endY - offsetY)
  }

  private def transitionLabel(transition: AutomatonTransition, nodes: Map[String, AutomatonNode]): Option[HtmlElement] = {
    for {
      from <- nodes.get(transition.fromStateId)
      to <- nodes.get(transition.toStateId)
    } yield {
      val (x, y) =
        if (from.id == to.id) {
          (from.x + nodeRadius, from.y - 36.0)
        } else {
          val (startX, startY, endX, endY) = edgeEndpoints(from, to)
          ((startX + endX) / 2.0, (startY + endY) / 2.0 - 12.0)
        }
      div(
        cls := "automaton-transition-label",
        style := s"left: ${x}px; top: ${y}px;",
        transition.label,
        onDblClick --> (_ => {
          val response = dom.window.prompt("Update transition symbols", transition.label)
          if (response != null) {
            store.updateTransitionSymbols(transition.id, response)
          }
        }),
        onContextMenu.preventDefault --> (_ => store.removeTransition(transition.id))
      )
    }
  }

  private val markerDefinition: SvgElement =
    defs(
      marker(
        idAttr := "automaton-arrow",
        viewBox := "0 0 10 10",
        refX := "10",
        refY := "5",
        markerWidth := "8",
        markerHeight := "8",
        orient := "auto-start-reverse",
        path(d := "M 0 0 L 10 5 L 0 10 z", fill := "var(--color-text-secondary)")
      )
    )

  private val domElement: HtmlElement =
    div(
      cls := "automaton-editor-area",
      onMountCallback(ctx => {
        containerElement = Some(ctx.thisNode.ref.asInstanceOf[html.Div])
        updateSize()
      }),
      windowEvents.onResize --> (_ => updateSize()),
      documentEvents.onPointerMove --> (event =>
        draggingVar.now() match {
          case Some((nodeId, offsetX, offsetY)) =>
            containerElement.foreach { container =>
              val rect = container.getBoundingClientRect()
              val newX = event.clientX - rect.left - offsetX
              val newY = event.clientY - rect.top - offsetY
              store.moveState(nodeId, newX, newY)
            }
          case None =>
        }
      ),
      documentEvents.onPointerUp --> (_ => draggingVar.set(None)),
      documentEvents.onPointerDown --> (_ => contextMenuVar.set(None)),
      svg.svg(
        cls := "automaton-transition-layer",
        markerDefinition,
        viewBox <-- sizeVar.signal.map { case (w, h) => s"0 0 $w $h" },
        preserveAspectRatio := "none",
        children <-- store.stateVar.signal.map { state =>
          val nodes = state.nodeMap
          state.transitions.flatMap(transition => transitionPath(transition, nodes))
        }
      ),
      div(
        cls := "automaton-label-layer",
        children <-- store.stateVar.signal.map { state =>
          val nodes = state.nodeMap
          state.transitions.flatMap(transition => transitionLabel(transition, nodes))
        }
      ),
      div(
        cls := "automaton-nodes-layer",
        children <-- store.stateVar.signal.combineWithFn(pendingTransitionVar.signal) { (state, pendingFrom) =>
          state.nodes.map { node =>
            val baseClasses = List(
              "automaton-node",
              if (node.isAccepting) "accepting" else "",
              if (node.isStart) "start" else "",
              if (pendingFrom.contains(node.id)) "pending" else ""
            ).filterNot(_.isEmpty)
            div(
              cls := baseClasses.mkString(" "),
              style := s"left: ${node.x}px; top: ${node.y}px;",
              node.label,
              onPointerDown --> ((event: PointerEvent) => startDrag(node, event)),
              onClick --> (_ => handleTransitionClick(node.id)),
              onContextMenu --> (event => showContextMenu(node.id, event))
            )
          }
        }
      ),
      child.maybe <-- contextMenuVar.signal.map(_.map(renderContextMenu))
    )

  override def getDomElement(): L.Element = domElement
}
