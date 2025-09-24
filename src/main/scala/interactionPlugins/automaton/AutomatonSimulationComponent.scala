package interactionPlugins.automaton

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.{*, given}
import workbook.model.display.InteractionComponent.InteractionComponentWithReactiveVars

class AutomatonSimulationComponent(controller: AutomatonSimulationController)
    extends InteractionComponentWithReactiveVars {

  private val domElement: HtmlElement = {
    val currentStepSignal = controller.currentStepSignal

    div(
      cls := "automaton-simulator",
      div(
        cls := "input-row",
        label(cls := "container-label", "Enter input string"),
        input(
          typ := "text",
          placeholder := "e.g. 0101",
          controlled(
            value <-- controller.inputVar.signal,
            onInput.mapToValue --> (value => controller.setInput(value))
          )
        )
      ),
      div(
        cls := "automaton-simulator-controls",
        button(cls := "turtle-action-button", "Reset", onClick --> (_ => controller.reset())),
        button(cls := "turtle-action-button", "Prev", onClick --> (_ => controller.stepBackward())),
        button(cls := "turtle-action-button", "Next", onClick --> (_ => controller.stepForward())),
        button(cls := "turtle-action-button", "Play", onClick --> (_ => controller.play())),
        button(cls := "turtle-action-button", "Pause", onClick --> (_ => controller.pause()))
      ),
      div(
        cls := "automaton-test-result-summary",
        child.text <-- currentStepSignal.map { step =>
          val active = if (step.activeStateIds.isEmpty) "∅" else step.activeStateIds.toList.sorted.mkString(", ")
          s"Active states: $active"
        }
      ),
      div(
        cls := "automaton-test-result-summary",
        child.text <-- controller.simulationVar.signal.combineWithFn(controller.stepIndexVar.signal) { (simulation, index) =>
          val status = if (index >= simulation.steps.length - 1 && simulation.isAccepted) "Accepted" else "In progress"
          s"Status: $status"
        }
      ),
      ul(
        cls := "automaton-step-list",
        children <-- controller.simulationVar.signal.combineWithFn(controller.stepIndexVar.signal) { (simulation, index) =>
          simulation.steps.map { step =>
            li(
              cls := (if (step.stepIndex == index) "active" else ""),
              span(s"Step ${step.stepIndex}: consumed '${step.consumed}'"),
              span(s" → remaining '${step.remaining}'"),
              span(s" | states ${if (step.activeStateIds.isEmpty) "∅" else step.activeStateIds.toList.sorted.mkString(", ")}"),
              span(if (step.isAccepting) " ✓" else "")
            )
          }
        }
      )
    )
  }

  override def getDomElement(): L.Element = domElement
}
