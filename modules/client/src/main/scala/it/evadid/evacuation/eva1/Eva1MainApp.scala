package it.evadid.evacuation.eva1

import it.evadid.evacuation.eva1.control.Eva1Control
import it.evadid.evacuation.eva1.control.modes.{ConfigurationMode, ScenarioEditorMode, ScenarioManagerMode, ScenarioPlayerMode}
import it.evadid.evacuation.eva1.model.ProgramState
import it.evadid.evacuation.html.EvaHtmlFactory
import org.scalajs.dom
import org.scalajs.dom.document

import java.util.Date
import scala.concurrent.ExecutionContextExecutor

object Eva1MainApp {

  val version = "2022-09-01"

  def main2(args: Array[String]): Unit = {

    println("Created Eva1 Page at: " + new Date() + ", compiled at: " + version)

    document.addEventListener("DOMContentLoaded", { (e: dom.Event) =>
      // setupCanvas()
      setupEva1Tabs()
      setupCSSVars()
    })

    ProgramState.instance.graph.addListener((oldVal, newVal) => {
      Eva1Control.redrawMainArea()
      //println("Changed in Graph. OldGraph: <" + oldVal + ">, new Graph: <" + newVal + ">")
    })

    // appendPar(document.body, "Eva1Web Version: <" + version + ">, finished building page at <" + new Date() + ">!")

  }

  private implicit val context: ExecutionContextExecutor = scala.concurrent.ExecutionContext.global


  private def setupCanvas(): Unit = {

    val animationPane = null

    val playButton = document.createElement("button")
    playButton.textContent = "jump"
    // playButton.addEventListener("click", { e: dom.MouseEvent => animationPane.incTime(100, true) })
    document.body.appendChild(playButton)
    //

  }

  def setupCSSVars(): Unit = {
    document.body.style = "--nrTabs: 5;"
  }

  def setupEva1Tabs(): Unit = {

    val container = document.createElement("div")
    container.setAttribute("id", "mainbutton-container")

    container.appendChild(EvaHtmlFactory.createControlTabElement("Scenario Editor", _ => Eva1Control.setNewControlMode(new ScenarioEditorMode())))
    container.appendChild(EvaHtmlFactory.createControlTabElement("Scenario Manager", _ => Eva1Control.setNewControlMode(new ScenarioManagerMode())))
    container.appendChild(EvaHtmlFactory.createControlTabElement("Configuration", _ => Eva1Control.setNewControlMode(new ConfigurationMode())))
    container.appendChild(EvaHtmlFactory.createControlTabElement("Scenario Player", _ => Eva1Control.setNewControlMode(new ScenarioPlayerMode())))

    container.appendChild(EvaHtmlFactory.createControlTabElement("About ", _ => ()))


    document.getElementById("centered_container").appendChild(container)

  }


  def appendPar(targetNode: dom.Node, text: String): Unit = {
    val parNode = document.createElement("p")
    parNode.textContent = text
    targetNode.appendChild(parNode)
  }


}
