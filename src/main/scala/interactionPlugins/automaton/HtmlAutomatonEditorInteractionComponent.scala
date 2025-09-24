package interactionPlugins.automaton

import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.{*, given}
import org.scalajs.dom
import workbook.model.display.InteractionComponent.InteractionComponentWithReactiveVars

class HtmlAutomatonEditorInteractionComponent(store: AutomatonEditorStore)
    extends InteractionComponentWithReactiveVars {

  private val addTransitionModeVar: Var[Boolean] = Var(false)
  private val pendingTransitionVar: Var[Option[String]] = Var(None)
  private val editorArea = new HtmlAutomatonEditorArea(store, addTransitionModeVar, pendingTransitionVar)
  private val layoutOptions = AutomatonLayoutAlgorithm.values.toList
  private val layoutSelectionVar: Var[AutomatonLayoutAlgorithm] = Var(AutomatonLayoutAlgorithm.ForceDirected)

  private def addState(): Unit = {
    val (x, y) = editorArea.defaultNodePosition
    store.addState(x, y, Some(editorArea.currentSize))
  }

  private def toggleTransitionMode(): Unit = {
    val next = !addTransitionModeVar.now()
    addTransitionModeVar.set(next)
    if (!next) pendingTransitionVar.set(None)
  }

  private def applyLayout(algorithm: AutomatonLayoutAlgorithm): Unit = {
    val (width, height) = editorArea.currentSize
    store.applyLayout(algorithm, width, height)
  }

  private def setLayoutSelection(algorithm: AutomatonLayoutAlgorithm): Unit = {
    layoutSelectionVar.set(algorithm)
    applyLayout(algorithm)
  }

  private def reapplySelectedLayout(): Unit = applyLayout(layoutSelectionVar.now())

  private def convertNfaToDfa(): Unit = {
    val (width, height) = editorArea.currentSize
    val success = store.convertNfaToDfa(layoutSelectionVar.now(), width, height)
    if (!success) {
      dom.window.alert("Conversion requires at least one start state.")
    }
  }

  private def minimizeDfa(): Unit = {
    val (width, height) = editorArea.currentSize
    val success = store.minimizeDfa(layoutSelectionVar.now(), width, height)
    if (!success) {
      dom.window.alert("Minimization requires a deterministic automaton with a start state.")
    }
  }

  private def showRegularExpression(): Unit = {
    store.computeRegularExpression() match {
      case Some(regex) => dom.window.alert(s"Equivalent regular expression:\n$regex")
      case None        => dom.window.alert("Provide a start state and at least one accepting state to derive a regular expression.")
    }
  }

  private val domElement =
    div(
      cls := "automaton-editor-component",
      div(
        cls := "automaton-toolbar",
        button("Add state", onClick --> (_ => addState())),
        button(
          cls.toggle("active") <-- addTransitionModeVar.signal,
          "Add transition",
          onClick --> (_ => toggleTransitionMode())
        ),
        div(
          cls := "automaton-layout-controls",
          span("Layout:"),
          select(
            layoutOptions.map { algorithm =>
              option(
                value := algorithm.toString,
                algorithm.label,
                selected <-- layoutSelectionVar.signal.map(_ == algorithm)
              )
            },
            onChange.mapToValue --> (value => AutomatonLayoutAlgorithm.fromId(value).foreach(setLayoutSelection))
          ),
          button("Apply", onClick --> (_ => reapplySelectedLayout()))
        ),
        div(
          cls := "automaton-transformation-group",
          button("Convert NFA to DFA", onClick --> (_ => convertNfaToDfa())),
          button("Minimize DFA", onClick --> (_ => minimizeDfa())),
          button("Show regex", onClick --> (_ => showRegularExpression()))
        ),
        div(
          cls := "automaton-mode-toggle",
          button(
            cls.toggle("active") <-- store.stateVar.signal.map(_.mode == AutomatonMode.Dfa),
            "DFA",
            onClick --> (_ => store.setMode(AutomatonMode.Dfa))
          ),
          button(
            cls.toggle("active") <-- store.stateVar.signal.map(_.mode == AutomatonMode.Nfa),
            "NFA",
            onClick --> (_ => store.setMode(AutomatonMode.Nfa))
          )
        )
      ),
      child <-- addTransitionModeVar.signal.map { active =>
        if (active)
          div(cls := "automaton-empty-placeholder", "Select a source state and then the target to place the transition.")
        else emptyNode
      },
      editorArea.getDomElement()
    )

  override def getDomElement(): L.Element = domElement
}
