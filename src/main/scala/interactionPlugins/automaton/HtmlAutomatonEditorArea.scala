package interactionPlugins.automaton

import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.{*, given}
import com.raquo.laminar.api.L.svg
import org.scalajs.dom
import org.scalajs.dom.{Element, MouseEvent, PointerEvent, html}
import workbook.workbookHtmlElements.abstractions.HtmlWorkbookElement

class HtmlAutomatonEditorArea(
  store: AutomatonEditorStore,
  addTransitionModeVar: Var[Boolean],
  pendingTransitionVar: Var[Option[String]]
) extends HtmlWorkbookElement {

  private val nodeRadius = 32.0
  private val connectionActivationDelayMs = 280
  private val dragThreshold = 6.0
  private var containerElement: Option[html.Div] = None
  private var lastKnownSize: (Double, Double) = (640, 380)
  private val sizeVar: Var[(Double, Double)] = Var(lastKnownSize)

  private sealed trait PointerInteraction
  private case object IdleInteraction extends PointerInteraction
  private case class PendingInteraction(
    nodeId: String,
    pointerId: Double,
    offsetX: Double,
    offsetY: Double,
    startClientX: Double,
    startClientY: Double,
    centerX: Double,
    centerY: Double,
    timeoutHandle: Int
  ) extends PointerInteraction
  private case class DraggingInteraction(nodeId: String, offsetX: Double, offsetY: Double) extends PointerInteraction
  private case class ConnectingInteraction(nodeId: String, pointerId: Double, centerX: Double, centerY: Double)
      extends PointerInteraction

  private case class ConnectionPreview(startX: Double, startY: Double, currentX: Double, currentY: Double)

  private var pointerInteraction: PointerInteraction = IdleInteraction
  private val selectedNodeVar: Var[Option[String]] = Var(None)
  private val connectionPreviewVar: Var[Option[ConnectionPreview]] = Var(None)

  private case class ContextMenuState(nodeId: String, x: Double, y: Double)

  private val contextMenuVar: Var[Option[ContextMenuState]] = Var(None)

  private def updateSize(): Unit = containerElement.foreach { el =>
    val rect = el.getBoundingClientRect()
    lastKnownSize = (rect.width, rect.height)
    sizeVar.set(lastKnownSize)
  }

  def currentSize: (Double, Double) = lastKnownSize

  def defaultNodePosition: (Double, Double) = (lastKnownSize._1 / 2 - nodeRadius, lastKnownSize._2 / 2 - nodeRadius)

  private def startNodeInteraction(node: AutomatonNode, event: PointerEvent): Unit = {
    if (addTransitionModeVar.now()) return
    containerElement.foreach { container =>
      resetPointerInteraction()
      event.preventDefault()
      event.stopPropagation()
      val rect = container.getBoundingClientRect()
      val offsetX = event.clientX - rect.left - node.x
      val offsetY = event.clientY - rect.top - node.y
      val centerX = node.x + nodeRadius
      val centerY = node.y + nodeRadius
      val pending = PendingInteraction(
        node.id,
        event.pointerId,
        offsetX,
        offsetY,
        event.clientX,
        event.clientY,
        centerX,
        centerY,
        timeoutHandle = 0
      )
      val handle = dom.window.setTimeout(() => activateConnection(node.id, event.pointerId, centerX, centerY), connectionActivationDelayMs)
      pointerInteraction = pending.copy(timeoutHandle = handle)
      event.target.asInstanceOf[dom.Element].setPointerCapture(event.pointerId)
    }
  }

  private def activateConnection(nodeId: String, pointerId: Double, centerX: Double, centerY: Double): Unit = {
    pointerInteraction match {
      case PendingInteraction(id, pid, _, _, _, _, _, _, _) if id == nodeId && pid == pointerId =>
        pointerInteraction = ConnectingInteraction(nodeId, pointerId, centerX, centerY)
        connectionPreviewVar.set(Some(ConnectionPreview(centerX, centerY, centerX, centerY)))
      case _ =>
    }
  }

  private def cancelPendingInteraction(): Unit =
    pointerInteraction match {
      case PendingInteraction(_, _, _, _, _, _, _, _, handle) =>
        dom.window.clearTimeout(handle)
      case _ =>
    }

  private def updateConnectionPreview(event: PointerEvent): Unit = {
    pointerInteraction match {
      case ConnectingInteraction(_, _, startX, startY) =>
        containerElement.foreach { container =>
          val rect = container.getBoundingClientRect()
          val currentX = event.clientX - rect.left
          val currentY = event.clientY - rect.top
          connectionPreviewVar.set(Some(ConnectionPreview(startX, startY, currentX, currentY)))
        }
      case _ =>
    }
  }

  private def selectNode(nodeId: String, closeContextMenu: Boolean = true): Unit = {
    selectedNodeVar.set(Some(nodeId))
    if (closeContextMenu) contextMenuVar.set(None)
  }

  private def clearSelection(): Unit = {
    selectedNodeVar.set(None)
    contextMenuVar.set(None)
  }

  private def elementNodeId(element: Element): Option[String] = {
    if (element == null) None
    else if (element.hasAttribute("data-node-id")) Option(element.getAttribute("data-node-id"))
    else Option(element.parentElement).flatMap(elementNodeId)
  }

  private def findNodeIdAt(clientX: Double, clientY: Double): Option[String] =
    Option(dom.document.elementFromPoint(clientX, clientY)).flatMap(el => elementNodeId(el))

  private def promptForTransition(fromId: String, toId: String): Unit = {
    val response = dom.window.prompt("Transition symbols (comma separated):", "")
    if (response != null) {
      store.addTransition(fromId, toId, response)
    }
  }

  private def dragNode(nodeId: String, offsetX: Double, offsetY: Double, event: PointerEvent): Unit = {
    containerElement.foreach { container =>
      val rect = container.getBoundingClientRect()
      val newX = event.clientX - rect.left - offsetX
      val newY = event.clientY - rect.top - offsetY
      store.moveState(nodeId, newX, newY, Some(lastKnownSize), enforceCollision = false)
    }
  }

  private def resetPointerInteraction(): Unit = {
    cancelPendingInteraction()
    pointerInteraction = IdleInteraction
    connectionPreviewVar.set(None)
  }

  private def handlePointerUp(event: PointerEvent): Unit = {
    pointerInteraction match {
      case PendingInteraction(nodeId, _, _, _, _, _, _, _, _) =>
        cancelPendingInteraction()
        pointerInteraction = IdleInteraction
        selectNode(nodeId)
        connectionPreviewVar.set(None)
      case DraggingInteraction(nodeId, _, _) =>
        pointerInteraction = IdleInteraction
        store.state.nodeMap.get(nodeId).foreach { node =>
          store.moveState(nodeId, node.x, node.y, Some(lastKnownSize))
        }
        selectNode(nodeId)
        connectionPreviewVar.set(None)
      case ConnectingInteraction(fromId, _, _, _) =>
        pointerInteraction = IdleInteraction
        val maybeTarget = findNodeIdAt(event.clientX, event.clientY)
        maybeTarget.foreach(targetId => promptForTransition(fromId, targetId))
        selectNode(fromId)
        connectionPreviewVar.set(None)
      case IdleInteraction =>
    }
  }

  private def handleBackgroundDoubleClick(event: MouseEvent): Unit = {
    val target = event.target.asInstanceOf[Element]
    val isOverNode = elementNodeId(target).isDefined
    val isOverLabel = target.classList.contains("automaton-transition-label")
    if (isOverNode || isOverLabel) return
    containerElement.foreach { container =>
      val rect = container.getBoundingClientRect()
      val x = event.clientX - rect.left - nodeRadius
      val y = event.clientY - rect.top - nodeRadius
      val created = store.addState(x, y, Some(lastKnownSize))
      selectNode(created.id)
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
    selectNode(nodeId, closeContextMenu = false)
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
      styleAttr := s"left: ${state.x}px; top: ${state.y}px;",
      button("Set as start", onClick --> (_ => { store.setAsStart(state.nodeId); contextMenuVar.set(None) })),
      button("Toggle accepting", onClick --> (_ => { store.toggleAccepting(state.nodeId); contextMenuVar.set(None) })),
      button("Delete state", onClick --> (_ => { store.removeState(state.nodeId); clearSelection() }))
    )
  }

  private def renderSelectedNodeActions(node: AutomatonNode): HtmlElement = {
    div(
      cls := "automaton-node-actions",
      span(cls := "title", s"State ${node.label}"),
      div(
        cls := "actions",
        button(
          "Set as start",
          disabled := node.isStart,
          onClick --> (_ => store.setAsStart(node.id))
        ),
        button(
          if (node.isAccepting) "Mark as non-accepting" else "Mark as accepting",
          onClick --> (_ => store.toggleAccepting(node.id))
        ),
        button(
          cls := "danger",
          "Delete state",
          onClick --> (_ => { store.removeState(node.id); clearSelection() })
        )
      )
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
        svg.path(svg.d := pathData, svg.cls := "self-loop")
      } else {
        val (startX, startY, endX, endY) = edgeEndpoints(from, to)
        svg.path(svg.d := s"M $startX $startY L $endX $endY")
      }
    }
  }

  private def renderConnectionPreview(preview: ConnectionPreview): SvgElement = {
    val dx = preview.currentX - preview.startX
    val dy = preview.currentY - preview.startY
    val distance = math.hypot(dx, dy)
    val (startX, startY) =
      if (distance <= 0.0001) (preview.startX, preview.startY)
      else {
        val ratio = nodeRadius / distance
        (preview.startX + dx * ratio, preview.startY + dy * ratio)
      }
    svg.path(
      svg.cls := "connection-preview",
      svg.d := s"M $startX $startY L ${preview.currentX} ${preview.currentY}",
      svg.markerEnd := "url(#automaton-arrow)"
    )
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
        L.cls := "automaton-transition-label",
        L.styleAttr := s"left: ${x}px; top: ${y}px;",
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
    svg.defs(
      svg.marker(
        svg.idAttr := "automaton-arrow",
        svg.viewBox := "0 0 10 10",
        svg.refX := "10",
        svg.refY := "5",
        svg.markerWidth := "8",
        svg.markerHeight := "8",
        svg.orient := "auto-start-reverse",
        svg.path(svg.d := "M 0 0 L 10 5 L 0 10 z", svg.fill := "var(--color-text-secondary)")
      )
    )

  private val domElement: HtmlElement =
    div(
      cls := "automaton-editor-area",
      onMountCallback(ctx => {
        containerElement = Some(ctx.thisNode.ref.asInstanceOf[html.Div])
        updateSize()
      }),
      onResize --> (_ => updateSize()),
      onPointerMove --> (event =>
        pointerInteraction match {
          case PendingInteraction(nodeId, _, offsetX, offsetY, startX, startY, _, _, handle) =>
            val distance = math.hypot(event.clientX - startX, event.clientY - startY)
            if (distance > dragThreshold) {
              dom.window.clearTimeout(handle)
              pointerInteraction = DraggingInteraction(nodeId, offsetX, offsetY)
              dragNode(nodeId, offsetX, offsetY, event)
            }
          case DraggingInteraction(nodeId, offsetX, offsetY) =>
            dragNode(nodeId, offsetX, offsetY, event)
          case ConnectingInteraction(_, _, _, _) =>
            updateConnectionPreview(event)
          case IdleInteraction =>
        }
      ),
      onPointerUp --> (event => handlePointerUp(event)),
      onPointerCancel --> (_ => resetPointerInteraction()),
      onPointerLeave --> (_ => resetPointerInteraction()),
      onPointerDown --> ((event: PointerEvent) => {
        val target = event.target.asInstanceOf[Element]
        val overNode = elementNodeId(target).isDefined
        val overLabel = target.classList.contains("automaton-transition-label")
        val overActions = Option(target.closest(".automaton-node-actions")).isDefined
        val overMenu = Option(target.closest(".automaton-context-menu")).isDefined
        if (!overNode && !overLabel && !overActions && !overMenu) {
          clearSelection()
        }
      }),
      onDblClick --> (event => handleBackgroundDoubleClick(event)),
      svg.svg(
        svg.cls := "automaton-transition-layer",
        markerDefinition,
        svg.viewBox <-- sizeVar.signal.map { case (w, h) => s"0 0 $w $h" },
        svg.preserveAspectRatio := "none",
        children <-- store.stateVar.signal.combineWithFn(connectionPreviewVar.signal) { (state, previewOpt) =>
          val nodes = state.nodeMap
          val transitions = state.transitions.flatMap(transition => transitionPath(transition, nodes))
          previewOpt match {
            case Some(preview) => transitions :+ renderConnectionPreview(preview)
            case None           => transitions
          }
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
        children <-- store.stateVar.signal
          .combineWithFn(pendingTransitionVar.signal.combineWithFn(selectedNodeVar.signal)) { (state, combined) =>
            val (pendingFrom, selectedId) = combined
            state.nodes.map { node =>
              val baseClasses = List(
                "automaton-node",
                if (node.isAccepting) "accepting" else "",
                if (node.isStart) "start" else "",
                if (pendingFrom.contains(node.id)) "pending" else "",
                if (selectedId.contains(node.id)) "selected" else ""
              ).filterNot(_.isEmpty)
              div(
                cls := baseClasses.mkString(" "),
                dataAttr("node-id") := node.id,
                styleAttr := s"left: ${node.x}px; top: ${node.y}px;",
                node.label,
                onPointerDown --> ((event: PointerEvent) =>
                  if (addTransitionModeVar.now()) event.stopPropagation()
                  else startNodeInteraction(node, event)
                ),
                onClick --> (_ =>
                  if (addTransitionModeVar.now()) handleTransitionClick(node.id)
                  else selectNode(node.id)
                ),
                onContextMenu --> (event => showContextMenu(node.id, event))
              )
            }
          }
      ),
      child.maybe <-- store.stateVar.signal.combineWithFn(selectedNodeVar.signal) { (state, selectedId) =>
        selectedId.flatMap(state.nodeMap.get).map(renderSelectedNodeActions)
      },
      child.maybe <-- contextMenuVar.signal.map(_.map(renderContextMenu))
    )

  override def getDomElement(): L.Element = domElement
}
