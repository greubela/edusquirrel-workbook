package interactionPlugins.automaton

import com.raquo.airstream.state.Var

import scala.collection.mutable

enum AutomatonLayoutAlgorithm(val label: String) {
  case ForceDirected extends AutomatonLayoutAlgorithm("Force-directed")
  case Circular extends AutomatonLayoutAlgorithm("Circular")
  case Grid extends AutomatonLayoutAlgorithm("Grid")
  case Layered extends AutomatonLayoutAlgorithm("Layered")
  case Linear extends AutomatonLayoutAlgorithm("Linear")
}

object AutomatonLayoutAlgorithm {
  def fromId(id: String): Option[AutomatonLayoutAlgorithm] = values.find(_.toString == id)
}

class AutomatonEditorStore(
  initialState: AutomatonEditorState,
  onStateChanged: AutomatonEditorState => Unit
) {

  private val nodeSize = 64.0
  private val padding = 24.0

  private var currentState: AutomatonEditorState = initialState
  private var nextNodeIndex: Int = initialState.nodes.flatMap(node => AutomatonEditorStore.extractIndex(node.id)).maxOption.getOrElse(-1) + 1

  val stateVar: Var[AutomatonEditorState] = Var(initialState)

  private def emit(newState: AutomatonEditorState): Unit = {
    currentState = newState
    stateVar.set(newState)
    onStateChanged(newState)
  }

  private def replaceState(newState: AutomatonEditorState): Unit = {
    nextNodeIndex = newState.nodes.flatMap(node => AutomatonEditorStore.extractIndex(node.id)).maxOption.getOrElse(-1) + 1
    emit(newState)
  }

  def state: AutomatonEditorState = currentState

  private def withUpdatedNodes(nodes: Vector[AutomatonNode]): AutomatonEditorState =
    currentState.copy(nodes = nodes)

  private def withUpdatedTransitions(transitions: Vector[AutomatonTransition]): AutomatonEditorState =
    currentState.copy(transitions = transitions)

  def setMode(mode: AutomatonMode): Unit = emit(currentState.copy(mode = mode))

  def addState(preferredX: Double, preferredY: Double): AutomatonNode = {
    val id = s"q$nextNodeIndex"
    nextNodeIndex += 1
    val hasStart = currentState.nodes.exists(_.isStart)
    val node = AutomatonNode(id, id, preferredX, preferredY, isStart = !hasStart, isAccepting = false)
    val updated = currentState.nodes :+ node
    emit(currentState.copy(nodes = updated))
    node
  }

  def moveState(nodeId: String, newX: Double, newY: Double): Unit = {
    val clampedX = math.max(0, newX)
    val clampedY = math.max(0, newY)
    val updatedNodes = currentState.nodes.map { node =>
      if (node.id == nodeId) node.moveTo(clampedX, clampedY) else node
    }
    emit(withUpdatedNodes(updatedNodes))
  }

  def removeState(nodeId: String): Unit = {
    val remainingNodes = currentState.nodes.filterNot(_.id == nodeId)
    val remainingTransitions = currentState.transitions.filterNot(t => t.fromStateId == nodeId || t.toStateId == nodeId)
    emit(currentState.copy(nodes = remainingNodes, transitions = remainingTransitions))
  }

  def toggleAccepting(nodeId: String): Unit = {
    val updatedNodes = currentState.nodes.map { node =>
      if (node.id == nodeId) node.withAccepting(!node.isAccepting) else node
    }
    emit(withUpdatedNodes(updatedNodes))
  }

  def setAsStart(nodeId: String): Unit = {
    val updatedNodes = currentState.nodes.map { node =>
      if (node.id == nodeId) node.withStart(true)
      else node.withStart(false)
    }
    emit(withUpdatedNodes(updatedNodes))
  }

  def addTransition(from: String, to: String, rawSymbols: String): Option[AutomatonTransition] = {
    val symbols = AutomatonTransition.parseSymbols(rawSymbols)
    if (symbols.isEmpty) return None
    val id = java.util.UUID.randomUUID().toString
    val transition = AutomatonTransition(id, from, to, symbols)
    emit(withUpdatedTransitions(currentState.transitions :+ transition))
    Some(transition)
  }

  def updateTransitionSymbols(transitionId: String, rawSymbols: String): Unit = {
    val symbols = AutomatonTransition.parseSymbols(rawSymbols)
    if (symbols.isEmpty) {
      removeTransition(transitionId)
    } else {
      val updatedTransitions = currentState.transitions.map { transition =>
        if (transition.id == transitionId) transition.withSymbols(symbols) else transition
      }
      emit(withUpdatedTransitions(updatedTransitions))
    }
  }

  def removeTransition(transitionId: String): Unit = {
    emit(withUpdatedTransitions(currentState.transitions.filterNot(_.id == transitionId)))
  }

  def applyLayout(algorithm: AutomatonLayoutAlgorithm, width: Double, height: Double): Unit = {
    if (currentState.nodes.isEmpty) return
    val layouted = layoutState(currentState, algorithm, width, height)
    emit(layouted)
  }

  def convertNfaToDfa(algorithm: AutomatonLayoutAlgorithm, width: Double, height: Double): Boolean = {
    AutomatonAlgorithms.convertNfaToDfa(currentState) match {
      case Some(converted) =>
        val positioned = layoutState(converted, algorithm, width, height)
        replaceState(positioned)
        true
      case None => false
    }
  }

  def minimizeDfa(algorithm: AutomatonLayoutAlgorithm, width: Double, height: Double): Boolean = {
    AutomatonAlgorithms.minimizeDfa(currentState) match {
      case Some(minimized) =>
        val positioned = layoutState(minimized, algorithm, width, height)
        replaceState(positioned)
        true
      case None => false
    }
  }

  def computeRegularExpression(): Option[String] =
    AutomatonAlgorithms.deriveRegularExpression(currentState)

  private def layoutState(
    state: AutomatonEditorState,
    algorithm: AutomatonLayoutAlgorithm,
    width: Double,
    height: Double
  ): AutomatonEditorState = {
    val nodes = algorithm match {
      case AutomatonLayoutAlgorithm.Circular      => computeCircularLayout(state.nodes, width, height)
      case AutomatonLayoutAlgorithm.ForceDirected => computeForceLayout(state, width, height)
      case AutomatonLayoutAlgorithm.Grid          => computeGridLayout(state.nodes, width, height)
      case AutomatonLayoutAlgorithm.Layered       => computeLayeredLayout(state, width, height)
      case AutomatonLayoutAlgorithm.Linear        => computeLinearLayout(state.nodes, width, height)
    }
    state.copy(nodes = nodes)
  }

  private def computeCircularLayout(nodes: Vector[AutomatonNode], width: Double, height: Double): Vector[AutomatonNode] = {
    if (nodes.isEmpty) return nodes
    val radius = math.max(math.min(width, height) / 2.5, nodeSize)
    val centerX = width / 2.0 - nodeSize / 2.0
    val centerY = height / 2.0 - nodeSize / 2.0
    nodes.zipWithIndex.map { case (node, index) =>
      val angle = 2 * math.Pi * index / nodes.length
      val x = centerX + radius * math.cos(angle)
      val y = centerY + radius * math.sin(angle)
      node.moveTo(clamp(x, padding, width - nodeSize - padding), clamp(y, padding, height - nodeSize - padding))
    }
  }

  private def computeForceLayout(state: AutomatonEditorState, width: Double, height: Double): Vector[AutomatonNode] = {
    val nodes = state.nodes
    if (nodes.length <= 1) return nodes

    val area = math.max(width * height, 1.0)
    val k = math.sqrt(area / nodes.length)
    val iterations = 80
    val positions = nodes.map(node => (node.x, node.y)).toArray
    val displacements = Array.fill(nodes.length)((0.0, 0.0))
    val indexById = nodes.zipWithIndex.map { case (node, idx) => node.id -> idx }.toMap

    for (_ <- 0 until iterations) {
      var idx = 0
      while (idx < displacements.length) {
        displacements(idx) = (0.0, 0.0)
        idx += 1
      }
      for (i <- nodes.indices) {
        var dispX = 0.0
        var dispY = 0.0
        val (xi, yi) = positions(i)
        for (j <- nodes.indices if j != i) {
          val (xj, yj) = positions(j)
          val dx = xi - xj
          val dy = yi - yj
          val distance = math.sqrt(dx * dx + dy * dy) + 0.01
          val force = k * k / distance
          dispX += dx / distance * force
          dispY += dy / distance * force
        }
        displacements(i) = (dispX, dispY)
      }

      for (transition <- state.transitions) {
        (indexById.get(transition.fromStateId), indexById.get(transition.toStateId)) match {
          case (Some(fromIdx), Some(toIdx)) =>
            val (xf, yf) = positions(fromIdx)
            val (xt, yt) = positions(toIdx)
            val dx = xf - xt
            val dy = yf - yt
            val distance = math.sqrt(dx * dx + dy * dy) + 0.01
            val force = distance * distance / k
            val fx = dx / distance * force
            val fy = dy / distance * force
            displacements(fromIdx) = (displacements(fromIdx)._1 - fx, displacements(fromIdx)._2 - fy)
            displacements(toIdx) = (displacements(toIdx)._1 + fx, displacements(toIdx)._2 + fy)
          case _ =>
        }
      }

      for (i <- nodes.indices) {
        val (dx, dy) = displacements(i)
        var (x, y) = positions(i)
        x = x + dx * 0.05
        y = y + dy * 0.05
        x = clamp(x, padding, width - nodeSize - padding)
        y = clamp(y, padding, height - nodeSize - padding)
        positions(i) = (x, y)
      }
    }

    nodes.zip(positions).map { case (node, (x, y)) => node.moveTo(x, y) }
  }

  private def computeGridLayout(nodes: Vector[AutomatonNode], width: Double, height: Double): Vector[AutomatonNode] = {
    if (nodes.isEmpty) return nodes
    val columns = math.max(1, math.ceil(math.sqrt(nodes.length.toDouble)).toInt)
    val rows = math.max(1, (nodes.length + columns - 1) / columns)
    val horizontalStep =
      if (columns <= 1) 0.0 else math.max(0.0, (width - 2 * padding - nodeSize) / (columns - 1))
    val verticalStep =
      if (rows <= 1) 0.0 else math.max(0.0, (height - 2 * padding - nodeSize) / (rows - 1))

    nodes.zipWithIndex.map { case (node, index) =>
      val row = index / columns
      val column = index % columns
      val x =
        if (columns <= 1) width / 2.0 - nodeSize / 2.0
        else clamp(padding + column * horizontalStep, padding, width - nodeSize - padding)
      val y =
        if (rows <= 1) height / 2.0 - nodeSize / 2.0
        else clamp(padding + row * verticalStep, padding, height - nodeSize - padding)
      node.moveTo(x, y)
    }
  }

  private def computeLayeredLayout(state: AutomatonEditorState, width: Double, height: Double): Vector[AutomatonNode] = {
    val nodes = state.nodes
    if (nodes.isEmpty) return nodes

    val adjacency = state.transitions.groupBy(_.fromStateId).view.mapValues(_.map(_.toStateId)).toMap
    val queue = mutable.Queue[(String, Int)]()
    val depthById = mutable.LinkedHashMap[String, Int]()
    val startIds = nodes.filter(_.isStart).map(_.id)

    if (startIds.nonEmpty) startIds.foreach(id => queue.enqueue((id, 0)))
    else nodes.foreach(node => queue.enqueue((node.id, 0)))

    while (queue.nonEmpty) {
      val (current, depth) = queue.dequeue()
      if (!depthById.contains(current)) {
        depthById += current -> depth
        adjacency.getOrElse(current, Vector.empty).foreach(next => queue.enqueue((next, depth + 1)))
      }
    }

    var nextDepth = if (depthById.isEmpty) 0 else depthById.values.max + 1
    nodes.foreach { node =>
      if (!depthById.contains(node.id)) {
        depthById += node.id -> nextDepth
        nextDepth += 1
      }
    }

    val nodesByDepth = mutable.LinkedHashMap[Int, Vector[String]]()
    depthById.foreach { case (nodeId, depth) =>
      val updated = nodesByDepth.getOrElse(depth, Vector.empty) :+ nodeId
      nodesByDepth.update(depth, updated)
    }

    val orderedLevels = nodesByDepth.toSeq.sortBy(_._1)
    val levelCount = orderedLevels.length
    val verticalStep =
      if (levelCount <= 1) 0.0 else math.max(0.0, (height - 2 * padding - nodeSize) / (levelCount - 1))

    val positionById = mutable.Map[String, (Double, Double)]()
    orderedLevels.zipWithIndex.foreach { case ((_, nodeIds), levelIndex) =>
      val count = nodeIds.length
      val horizontalStep =
        if (count <= 1) 0.0 else math.max(0.0, (width - 2 * padding - nodeSize) / (count - 1))
      nodeIds.zipWithIndex.foreach { case (nodeId, index) =>
        val x =
          if (count <= 1) width / 2.0 - nodeSize / 2.0
          else clamp(padding + index * horizontalStep, padding, width - nodeSize - padding)
        val y =
          if (levelCount <= 1) height / 2.0 - nodeSize / 2.0
          else clamp(padding + levelIndex * verticalStep, padding, height - nodeSize - padding)
        positionById.update(nodeId, (x, y))
      }
    }

    nodes.map { node =>
      positionById.get(node.id).map { case (x, y) => node.moveTo(x, y) }.getOrElse(node)
    }
  }

  private def computeLinearLayout(nodes: Vector[AutomatonNode], width: Double, height: Double): Vector[AutomatonNode] = {
    if (nodes.isEmpty) return nodes
    val baseY = clamp(height / 2.0 - nodeSize / 2.0, padding, height - nodeSize - padding)
    val horizontalStep =
      if (nodes.length <= 1) 0.0 else math.max(0.0, (width - 2 * padding - nodeSize) / (nodes.length - 1))

    nodes.zipWithIndex.map { case (node, index) =>
      val x =
        if (nodes.length <= 1) width / 2.0 - nodeSize / 2.0
        else clamp(padding + index * horizontalStep, padding, width - nodeSize - padding)
      node.moveTo(x, baseY)
    }
  }

  private def clamp(value: Double, min: Double, max: Double): Double = math.min(math.max(value, min), max)
}

object AutomatonEditorStore {
  private val IndexPattern = "(\\d+)$".r

  def extractIndex(id: String): Option[Int] = id match {
    case IndexPattern(number) => Some(number.toInt)
    case _                    => None
  }
}
