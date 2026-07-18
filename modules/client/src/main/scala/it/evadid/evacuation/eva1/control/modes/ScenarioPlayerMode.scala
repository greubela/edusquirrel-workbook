package it.evadid.evacuation.eva1.control.modes

import it.evadid.evacuation.core.utility.GeneralUtility
import it.evadid.evacuation.eva1.algorithm.routing.EvacuationFlowSimulation
import it.evadid.evacuation.eva1.control.Eva1ControlMode
import it.evadid.evacuation.eva1.graphic.{ControllableAnimationPane, GraphPane, GraphPaneBasic}
import it.evadid.evacuation.eva1.model.ProgramState
import it.evadid.evacuation.html.EvaHtmlFactory
import org.scalajs.dom.html.Canvas
import org.scalajs.dom.{Element, document, html, window}

class ScenarioPlayerMode extends Eva1ControlMode {

  private val evacuation: Option[EvacuationFlowSimulation] = try {
    val res = EvacuationFlowSimulation.simulateEvacuation(ProgramState.instance.graph.currentValue, ProgramState.instance.programConfig.evacuationStrategy.getValue.value)
    println("finished with " + res.getStates().size + " states, times: " + res.getStates().map(_.currenTimestamp).distinct + "!")
    Some(res)
  } catch {
    case e: Exception => e.printStackTrace()
      None
  }

  private val timeLabel = EvaHtmlFactory.createLabel("Animation Time: 0 s")

  private val animPane: Option[ControllableAnimationPane[html.Canvas]] = evacuation.map(ControllableAnimationPane(_)(using createNewCanvas))
  private val basicPane = GraphPaneBasic()(using createNewCanvas)

  private var handlerID = -1

  override def onEnteringMode(): Unit = {
    handlerID = window.setInterval(() => update(25), 25)
  }

  override def onLeavingMode(): Unit = {
    window.clearInterval(handlerID)
  }

  override def getControlElement: Element = {
    val root = document.createElement("div")

    evacuation.foreach(evaSim => {

      root.appendChild(EvaHtmlFactory.createNumberForm(ProgramState.instance.graphicConfig.animationSpeed, Some(25)))


      // Result Table
      val resTableElement = document.createElement("div")
      resTableElement.setAttribute("id", "eva-player-status-table")

      resTableElement.appendChild(EvaHtmlFactory.createLabel("Animation Time:"))
      resTableElement.appendChild(timeLabel)

      resTableElement.appendChild(EvaHtmlFactory.createLabel("Simulation Duration:"))
      resTableElement.appendChild(EvaHtmlFactory.createLabel(GeneralUtility.formatDuration(evaSim.getStates().last.currenTimestamp)))

      resTableElement.appendChild(EvaHtmlFactory.createLabel("Persons in Simulation:"))
      resTableElement.appendChild(EvaHtmlFactory.createLabel(""+evaSim.getStates().last.persons.size))

      resTableElement.appendChild(EvaHtmlFactory.createLabel("Movement Events:"))
      resTableElement.appendChild(EvaHtmlFactory.createLabel("" + evaSim.getStates().last.handledEvents.size))

      root.appendChild(resTableElement)


      root.appendChild(EvaHtmlFactory.createPropertyTickBox(ProgramState.instance.graphicConfig.restartAnimation))

    })


    root
  }


  private def update(interval: Long): Unit = {

    val graphicInc = (interval / 100.0 * ProgramState.instance.graphicConfig.animationSpeed.getValue.value).asInstanceOf[Long]

    animPane.foreach(pane => {
      pane.incTime(graphicInc, ProgramState.instance.graphicConfig.restartAnimation.getValue.value)
      timeLabel.textContent = "" + GeneralUtility.formatDuration(pane.getTime())
    })
  }

  override def getMainPane(): GraphPane[Canvas] = if (animPane.isDefined) animPane.get else basicPane
}
