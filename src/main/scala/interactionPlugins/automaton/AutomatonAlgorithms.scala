package interactionPlugins.automaton

import util.IdHelper

import scala.collection.mutable

object AutomatonAlgorithms {

  private val EmptyRegex = "∅"
  private val EpsilonRegex = "ε"

  private val epsilonTokens: Set[String] = Set("", "ε", "epsilon", "eps", "λ")

  private[automaton] def isEpsilon(symbol: String): Boolean = epsilonTokens.contains(symbol.trim.toLowerCase)

  private def alphabet(state: AutomatonEditorState): Set[String] =
    state.transitions.flatMap(_.symbols).filterNot(isEpsilon).toSet

  private def epsilonClosure(
    start: Set[String],
    transitionsBySource: Map[String, Vector[AutomatonTransition]]
  ): Set[String] = {
    val visited = mutable.Set[String]()
    val pending = mutable.ArrayDeque[String]()
    pending.appendAll(start)
    while (pending.nonEmpty) {
      val stateId = pending.removeLast()
      if (!visited.contains(stateId)) {
        visited += stateId
        transitionsBySource
          .getOrElse(stateId, Vector.empty)
          .foreach { transition =>
            if (transition.symbols.exists(isEpsilon)) pending.append(transition.toStateId)
          }
      }
    }
    visited.toSet
  }

  def convertNfaToDfa(state: AutomatonEditorState): Option[AutomatonEditorState] = {
    if (state.nodes.isEmpty) return None
    val startStates = state.nodes.filter(_.isStart).map(_.id).toSet
    if (startStates.isEmpty) return None

    val nodeLookup = state.nodeMap
    val transitionsBySource = state.transitions.groupBy(_.fromStateId).view.mapValues(_.toVector).toMap
    val alphabetSymbols = alphabet(state).toList.sorted
    val startClosure = epsilonClosure(startStates, transitionsBySource)
    if (startClosure.isEmpty) return None

    val subsetQueue = mutable.Queue[Set[String]]()
    val subsetNodes = mutable.LinkedHashMap[Set[String], AutomatonNode]()
    val transitionLabels = mutable.Map[(String, String), mutable.Set[String]]()
    var nextIndex = 0

    def labelForSubset(subset: Set[String]): String = {
      if (subset.isEmpty) "∅"
      else if (subset.size == 1) {
        val representative = subset.head
        nodeLookup.get(representative).map(_.label).getOrElse(representative)
      } else {
        subset.toList.sorted.map(id => nodeLookup.get(id).map(_.label).getOrElse(id)).mkString("{", ", ", "}")
      }
    }

    def registerSubset(subset: Set[String]): AutomatonNode = {
      subsetNodes.getOrElseUpdate(subset, {
        val isStartSubset = subset == startClosure
        val isAccepting = subset.exists(id => nodeLookup.get(id).exists(_.isAccepting))
        val node = AutomatonNode(s"q$nextIndex", labelForSubset(subset), 0.0, 0.0, isStartSubset, isAccepting)
        nextIndex += 1
        subsetQueue.enqueue(subset)
        node
      })
    }

    registerSubset(startClosure)

    while (subsetQueue.nonEmpty) {
      val subset = subsetQueue.dequeue()
      val fromNode = subsetNodes(subset)
      alphabetSymbols.foreach { symbol =>
        val directTargets = subset.flatMap { stateId =>
          transitionsBySource
            .getOrElse(stateId, Vector.empty)
            .collect { case transition if transition.symbols.contains(symbol) => transition.toStateId }
        }
        val closure = epsilonClosure(directTargets.toSet, transitionsBySource)
        if (closure.nonEmpty) {
          val targetNode = registerSubset(closure)
          val labels = transitionLabels.getOrElseUpdate((fromNode.id, targetNode.id), mutable.Set.empty[String])
          labels += symbol
        }
      }
    }

    val nodes = subsetNodes.values.toVector
    val transitions = transitionLabels.toVector.map { case ((from, to), symbols) =>
      AutomatonTransition(IdHelper.getNextId(), from, to, symbols.toSet)
    }

    Some(AutomatonEditorState(AutomatonMode.Dfa, nodes, transitions))
  }

  private def isDeterministic(state: AutomatonEditorState): Boolean = {
    val hasSingleStart = state.nodes.count(_.isStart) <= 1
    val hasEpsilonTransitions = state.transitions.exists(_.symbols.exists(isEpsilon))
    val hasConflicts = state.transitions
      .flatMap(transition => transition.symbols.map(symbol => ((transition.fromStateId, symbol), transition.toStateId)))
      .groupBy(_._1)
      .exists { case (_, destinations) => destinations.map(_._2).toSet.size > 1 }
    hasSingleStart && !hasEpsilonTransitions && !hasConflicts
  }

  def minimizeDfa(state: AutomatonEditorState): Option[AutomatonEditorState] = {
    val deterministicStateOpt =
      if (isDeterministic(state)) Some(state.copy(mode = AutomatonMode.Dfa))
      else convertNfaToDfa(state)

    deterministicStateOpt.flatMap { deterministicState =>
      val startIdOpt = deterministicState.nodes.find(_.isStart).map(_.id)
      startIdOpt.flatMap { startId =>
        val reachable = computeReachable(deterministicState, startId)
        if (reachable.isEmpty) None
        else {
          val filteredNodes = deterministicState.nodes.filter(node => reachable.contains(node.id))
          if (filteredNodes.isEmpty) None
          else {
            val filteredTransitions = deterministicState.transitions.filter(t => reachable.contains(t.fromStateId) && reachable.contains(t.toStateId))
            val normalized = deterministicState.copy(nodes = filteredNodes, transitions = filteredTransitions, mode = AutomatonMode.Dfa)
            Some(minimizeDeterministic(normalized))
          }
        }
      }
    }
  }

  private def computeReachable(state: AutomatonEditorState, startId: String): Set[String] = {
    val adjacency = state.transitions.groupBy(_.fromStateId).view.mapValues(_.map(_.toStateId)).toMap
    val visited = mutable.Set[String](startId)
    val queue = mutable.Queue[String](startId)
    while (queue.nonEmpty) {
      val current = queue.dequeue()
      adjacency.getOrElse(current, Vector.empty).foreach { next =>
        if (!visited.contains(next)) {
          visited += next
          queue.enqueue(next)
        }
      }
    }
    visited.toSet
  }

  private def buildTransitionMap(state: AutomatonEditorState): Map[(String, String), String] = {
    state.transitions
      .flatMap(transition => transition.symbols.filterNot(isEpsilon).map(symbol => ((transition.fromStateId, symbol), transition.toStateId)))
      .groupBy(_._1)
      .view
      .mapValues(_.head._2)
      .toMap
  }

  private def minimizeDeterministic(state: AutomatonEditorState): AutomatonEditorState = {
    val alphabetSymbols = alphabet(state).toList.sorted
    val transitionMap = buildTransitionMap(state)
    val accepting = state.nodes.filter(_.isAccepting).map(_.id).toSet
    val nonAccepting = state.nodes.filterNot(_.isAccepting).map(_.id).toSet
    var partitions = List(accepting, nonAccepting).filter(_.nonEmpty)
    val startId = state.nodes.find(_.isStart).map(_.id).getOrElse(state.nodes.head.id)
    val nodeLookup = state.nodeMap

    var changed = true
    while (changed) {
      changed = false
      val stateToPartition = partitions.zipWithIndex.flatMap { case (block, idx) => block.map(_ -> idx) }.toMap
      val refined = partitions.flatMap { block =>
        val grouped = block.groupBy { stateId =>
          alphabetSymbols.map { symbol =>
            transitionMap.get((stateId, symbol)).flatMap(stateToPartition.get).getOrElse(-1)
          }
        }.values.map(_.toSet).toList
        if (grouped.size > 1) changed = true
        grouped
      }
      partitions = refined
    }

    val partitionNodes = partitions.zipWithIndex.map { case (block, idx) =>
      val label =
        if (block.size == 1) {
          val originalId = block.head
          nodeLookup.get(originalId).map(_.label).getOrElse(originalId)
        } else {
          block.toList.sorted.map(id => nodeLookup.get(id).map(_.label).getOrElse(id)).mkString("{", ", ", "}")
        }
      val isStart = block.contains(startId)
      val isAccepting = block.exists(id => nodeLookup.get(id).exists(_.isAccepting))
      AutomatonNode(s"q$idx", label, 0.0, 0.0, isStart, isAccepting)
    }

    val blockIndexByState = partitions.zipWithIndex.flatMap { case (block, idx) => block.map(_ -> idx) }.toMap
    val transitionLabels = mutable.Map[(String, String), mutable.Set[String]]()

    partitions.zipWithIndex.foreach { case (block, idx) =>
      val fromId = partitionNodes(idx).id
      alphabetSymbols.foreach { symbol =>
        val destinationPartitions = block.flatMap { stateId =>
          transitionMap.get((stateId, symbol)).flatMap(blockIndexByState.get)
        }.toSet
        destinationPartitions.foreach { targetIdx =>
          val toId = partitionNodes(targetIdx).id
          val labels = transitionLabels.getOrElseUpdate((fromId, toId), mutable.Set.empty[String])
          labels += symbol
        }
      }
    }

    val minimizedTransitions = transitionLabels.toVector.map { case ((from, to), symbols) =>
      AutomatonTransition(IdHelper.getNextId(), from, to, symbols.toSet)
    }

    AutomatonEditorState(AutomatonMode.Dfa, partitionNodes.toVector, minimizedTransitions)
  }

  def deriveRegularExpression(state: AutomatonEditorState): Option[String] = {
    if (state.nodes.isEmpty) return None
    val startStates = state.nodes.filter(_.isStart).map(_.id)
    if (startStates.isEmpty) return None
    val acceptingStates = state.nodes.filter(_.isAccepting).map(_.id)
    if (acceptingStates.isEmpty) return Some(EmptyRegex)

    val startId = "__start"
    val endId = "__end"
    val orderedStateIds = state.nodes.map(_.id)
    val stateSequence = mutable.ArrayBuffer[String]()
    stateSequence += startId
    stateSequence ++= orderedStateIds
    stateSequence += endId

    val regexMap = mutable.Map[(String, String), String]().withDefaultValue(EmptyRegex)

    def addEdge(from: String, to: String, expr: String): Unit = {
      if (!isEmptyRegex(expr)) {
        val combined = unionExpr(regexMap((from, to)), expr)
        regexMap.update((from, to), combined)
      }
    }

    state.transitions.foreach { transition =>
      val expr = symbolSetToRegex(transition.symbols)
      addEdge(transition.fromStateId, transition.toStateId, expr)
    }
    startStates.foreach(start => addEdge(startId, start, EpsilonRegex))
    acceptingStates.foreach(accept => addEdge(accept, endId, EpsilonRegex))

    orderedStateIds.foreach { eliminateId =>
      val currentStates = stateSequence.toList
      currentStates.foreach { from =>
        if (from != eliminateId) {
          val entry = regexMap((from, eliminateId))
          if (!isEmptyRegex(entry)) {
            currentStates.foreach { to =>
              if (to != eliminateId) {
                val exit = regexMap((eliminateId, to))
                if (!isEmptyRegex(exit)) {
                  val loop = regexMap((eliminateId, eliminateId))
                  val concatenated = concatExpr(List(entry, starExpr(loop), exit))
                  if (!isEmptyRegex(concatenated)) {
                    val combined = unionExpr(regexMap((from, to)), concatenated)
                    regexMap.update((from, to), combined)
                  }
                }
              }
            }
          }
        }
      }
      stateSequence -= eliminateId
      regexMap.keys.filter { case (from, to) => from == eliminateId || to == eliminateId }.toList.foreach(regexMap.remove)
    }

    val expression = regexMap((startId, endId))
    Some(if (isEmptyRegex(expression)) EmptyRegex else expression)
  }

  private def unionExpr(a: String, b: String): String = {
    (isEmptyRegex(a), isEmptyRegex(b)) match {
      case (true, true)   => EmptyRegex
      case (true, false)  => b
      case (false, true)  => a
      case (false, false) => if (a == b) a else s"$a + $b"
    }
  }

  private def concatExpr(parts: List[String]): String = {
    val filtered = parts.filterNot(isEpsilonRegex)
    if (filtered.exists(isEmptyRegex)) EmptyRegex
    else if (filtered.isEmpty) EpsilonRegex
    else filtered.map(part => if (needsParentheses(part)) s"($part)" else part).mkString("")
  }

  private def starExpr(expr: String): String = {
    if (isEmptyRegex(expr) || isEpsilonRegex(expr)) EpsilonRegex
    else if (expr.endsWith("*")) expr
    else {
      val inner = if (needsParentheses(expr)) s"($expr)" else expr
      s"$inner*"
    }
  }

  private def symbolSetToRegex(symbols: Set[String]): String = {
    if (symbols.isEmpty) return EmptyRegex
    val (epsilonSymbols, otherSymbols) = symbols.partition(isEpsilon)
    val ordered = mutable.LinkedHashSet[String]()
    otherSymbols.toList.map(_.trim).filter(_.nonEmpty).foreach(ordered.addOne)
    if (epsilonSymbols.nonEmpty) ordered.addOne(EpsilonRegex)
    ordered.toList match {
      case Nil          => EmptyRegex
      case single :: Nil => single
      case many         => many.mkString("(", " + ", ")")
    }
  }

  private def isEmptyRegex(expr: String): Boolean = expr == EmptyRegex || expr.trim.isEmpty

  private def isEpsilonRegex(expr: String): Boolean = expr == EpsilonRegex

  private def needsParentheses(expr: String): Boolean =
    expr.contains(" + ") || expr.contains("|") || (expr.contains(" ") && expr.length > 1)
}
