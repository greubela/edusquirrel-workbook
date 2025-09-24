package interactionPlugins.automaton

import workbook.model.states.InteractionState

enum AutomatonMode {
  case Dfa, Nfa
}

case class AutomatonNode(
  id: String,
  label: String,
  x: Double,
  y: Double,
  isStart: Boolean,
  isAccepting: Boolean
) {
  def moveTo(newX: Double, newY: Double): AutomatonNode = copy(x = newX, y = newY)

  def withStart(isStartNode: Boolean): AutomatonNode = copy(isStart = isStartNode)

  def withAccepting(isAcceptingNode: Boolean): AutomatonNode = copy(isAccepting = isAcceptingNode)
}

case class AutomatonTransition(
  id: String,
  fromStateId: String,
  toStateId: String,
  symbols: Set[String]
) {
  def hasSymbol(symbol: String): Boolean = symbols.contains(symbol)

  def withSymbols(newSymbols: Set[String]): AutomatonTransition = copy(symbols = newSymbols)

  def label: String = symbols.toList.sorted.mkString(", ")
}

object AutomatonTransition {

  def parseSymbols(raw: String): Set[String] =
    raw.split(",").map(_.trim).filterNot(_.isEmpty).toSet

  def fromInput(id: String, from: String, to: String, rawSymbols: String): AutomatonTransition =
    AutomatonTransition(id, from, to, parseSymbols(rawSymbols))
}

case class AutomatonEditorState(
  mode: AutomatonMode,
  nodes: Vector[AutomatonNode],
  transitions: Vector[AutomatonTransition]
) extends InteractionState {
  override def getStateAsString(): String = {
    val nodeLines = nodes.map { node =>
      val flags = List(
        if (node.isStart) Some("start") else None,
        if (node.isAccepting) Some("accept") else None
      ).flatten.mkString(",")
      s"${node.id}:${node.label}:${node.x.formatted("%.1f")}:${node.y.formatted("%.1f")}:${flags}"
    }
    val transitionLines = transitions.map(t => s"${t.fromStateId}->${t.toStateId}:${t.label}")
    (nodeLines ++ transitionLines).mkString("\n")
  }

  lazy val nodeMap: Map[String, AutomatonNode] = nodes.map(node => node.id -> node).toMap
}

case class AutomatonScaffoldingState(editorState: AutomatonEditorState) extends InteractionState {
  override def getStateAsString(): String = editorState.getStateAsString()
}

case class AutomatonGradingState(
  editorState: AutomatonEditorState,
  shouldAccept: List[String],
  shouldReject: List[String]
) extends InteractionState {
  override def getStateAsString(): String = editorState.getStateAsString()
}

case class AutomatonTestCase(word: String, expectedAccept: Boolean)

case class AutomatonTestResult(word: String, expectedAccept: Boolean, actualAccept: Boolean) {
  val isCorrect: Boolean = expectedAccept == actualAccept
}
