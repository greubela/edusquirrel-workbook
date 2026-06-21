package it.evadid.evacuation.eva2.graphic.controllerHTML

import it.evadid.evacuation.eva2.control.modes.ScenarioPlayerMode
import it.evadid.evacuation.eva2.graphic.ImageConfigFactory
import it.evadid.evacuation.eva2.model.ProgramState
import it.evadid.evacuation.html.EvaHtmlFactory
import org.scalajs.dom.{Element, document}

object SimulationPlayerHtmlFactory {

  private def createStatusElement(ctrl: ScenarioPlayerMode, currentStateInfo: Element): Element = {

    val statusTable = document.createElement("div")
    statusTable.setAttribute("id", "eva-player-status-table")

    val content: List[String] = List(
      "Persons in Scenario", ctrl.evacuationSimulation.initialState.persons.size.toString,
      "Simulation Steps ", ctrl.evacuationSimulation.steps.size.toString,
      "Simulation MicroSteps ", ctrl.evacuationSimulation.states.size.toString,
      "Neighbourhood", ctrl.evacuationMetaData.neighbourhoodFunc,
      "Goal Strategy", ctrl.evacuationMetaData.strategyName,
      "Execution Time (CPU)", s"${ctrl.evacuationMetaData.executionTimeInMs / 10 * 1.0 / 100.0}s",
    )
    val elements = content.map(EvaHtmlFactory.createLabel)
    elements.foreach(statusTable.appendChild)

    statusTable
  }

  def createControlElement(ctrl: ScenarioPlayerMode, currentStateInfo: Element): Element = {
    val control = document.createElement("div")
    control.setAttribute("id", "simulation-player-control")

    control.appendChild(createNavigationControl(ctrl))
    control.appendChild(currentStateInfo)
    control.appendChild(createStatusElement(ctrl, currentStateInfo))

    //control.appendChild(EvaHtmlFactory.createRadioButtonForm(ProgramState.config.showMovementOption))

    if(ProgramState.instance.graphicConfig.spriteMapProperty.getValue.value.layout == "topdown"){
      control.appendChild(EvaHtmlFactory.createPropertyTickBox(ProgramState.config.showAnimations))
    }

    ctrl.updateCurrentStateInfo()
    control
  }

  private def createNavigationControl(ctrl: ScenarioPlayerMode): Element = {
    val eva = ctrl.evacuationSimulation

    val control = document.createElement("div")
    control.setAttribute("id", "simulation-navigation-control")

    control.appendChild(ImageConfigFactory.forSimulationNavigationButtonOuter("simulation-navigation-button-first", "first", _ => ctrl.changeStatus(_ => 0)).createImageInDiv(Map("class" -> "sim-nav-button")))
    control.appendChild(ImageConfigFactory.forSimulationNavigationButton("simulation-navigation-button-CL", "CArrowL", _ => ctrl.changeStatus(eva.previousStep)).createImage())
    control.appendChild(ImageConfigFactory.forSimulationNavigationButton("simulation-navigation-button-L", "ArrowL", _ => ctrl.changeStatus(eva.previousMicroStep)).createImage())
    control.appendChild(ImageConfigFactory.forSimulationNavigationButton("simulation-navigation-button-R", "ArrowR", _ => ctrl.changeStatus(eva.nextMicroStep)).createImage())
    control.appendChild(ImageConfigFactory.forSimulationNavigationButton("simulation-navigation-button-CR", "CArrowR", _ => ctrl.changeStatus(eva.nextStep)).createImage())
    control.appendChild(ImageConfigFactory.forSimulationNavigationButtonOuter("simulation-navigation-button-last", "last", _ => ctrl.changeStatus(_ => eva.states.size - 1)).createImageInDiv(Map("class" -> "sim-nav-button")))

    control
  }


}
