package it.evadid.evacuation.eva2

import it.evadid.evacuation.eva2.control.Eva2Control
import it.evadid.evacuation.eva2.control.modes._
import it.evadid.evacuation.eva2.io.ServerResourceReader
import it.evadid.evacuation.eva2.model.{DefaultFloors, ProgramState}
import it.evadid.evacuation.html.EvaHtmlFactory
import org.scalajs.dom
import org.scalajs.dom.{document, window}

import java.util.Date
import scala.concurrent.ExecutionContextExecutor
import scala.scalajs.js.annotation.JSExportTopLevel

object Eva2MainApp {

  val version = "2022-10-28"

  def main(args: Array[String]): Unit = {


    window.addEventListener("drop", (e: dom.DragEvent) => {
      e.preventDefault()
    }, true)

    // println("Created Eva2 Page at: " + new Date() + ", compiled at: " + version)
    setupTabs()

    document.addEventListener("DOMContentLoaded", { (e: dom.Event) =>
      setupEva()
      window.setInterval(() => {
        ProgramState.graphicConfig.vwWidth.setValue(window.innerWidth.toInt)
        ProgramState.graphicConfig.vwHeight.setValue(window.innerHeight.toInt)
        ProgramState.graphicConfig.updateCSSVariables()

        ProgramState.graphicConfig.animationCounter.setValue(ProgramState.graphicConfig.animationCounter.getValue.value + 1)
        if (ProgramState.config.showAnimations.getValue.value) Eva2Control.requestRedrawTiles()

      }, 250)
    })

    println("finished main of eva2web version <" + version + ">")
    // appendPar(document.body, "Version: <" + version + ">, finished building page at " + new Date())
  }

  private val resourceReader: ServerResourceReader.type = ServerResourceReader
  private implicit val context: ExecutionContextExecutor = scala.concurrent.ExecutionContext.global

  def setupTabs(): Unit = {

    val container = document.createElement("div")
    container.setAttribute("id", "mainbutton-container")
    container.setAttribute("class", "grid-item")

    container.appendChild(EvaHtmlFactory.createControlTabElement("Scenario Editor", _ => Eva2Control.setNewControlMode(ScenarioEditorMode())))
    container.appendChild(EvaHtmlFactory.createControlTabElement("Scenario Manager", _ => Eva2Control.setNewControlMode(ScenarioManagerMode())))
    container.appendChild(EvaHtmlFactory.createControlTabElement("Configuration", _ => Eva2Control.setNewControlMode(ChangeConfigurationMode())))
    container.appendChild(EvaHtmlFactory.createControlTabElement("Simulation Player", _ => Eva2Control.setNewControlMode(ScenarioPlayerMode())))
    //container.appendChild(HTMLFactory.createControlTabElement("Material and More", _ => ()))
    container.appendChild(EvaHtmlFactory.createControlTabElement("About the Project", _ => ()))
    /*container.appendChild(HTMLFactory.createControlTabElement("Adjust Tile Size", _ => {
      EvaControl.adjustSpriteSize()
      EvaControl.reload()
    }))*/

    document.getElementById("centered_container").appendChild(container)
  }


  def setupEva(): Unit = {
    ProgramState.instance.setScenario(DefaultFloors.default)

    registerAllListener()
  }

  def registerAllListener(): Unit = {
    // Redraw on Changed floor
    ProgramState.instance.floorMap.addListener((oldVal, newVal) => {
      if (oldVal.floorMatrix.dim == newVal.floorMatrix.dim) {
        Eva2Control.redrawMainArea()
      } else {
        Eva2Control.reload()
      }
    })
    ProgramState.instance.spriteMap.addListener((oldVal, newVal) => Eva2Control.reload())

  }


  def setupTest(): Unit = {
    val button = document.createElement("button")

    button.textContent = "Click me!"
    button.addEventListener("click", {
      (e: dom.MouseEvent) =>
        addClickedMessage()
    })
    document.body.appendChild(button)

  }

  def appendPar(targetNode: dom.Node, text: String): Unit = {
    val parNode = document.createElement("p")
    parNode.textContent = text
    targetNode.appendChild(parNode)
  }

  def addClickedMessage(): Unit = {
    appendPar(document.body, "You clicked the button!")
    println("You totally clicked the button!")

    val asdfButton = document.getElementById("asdf")
    asdfButton.textContent = "Clicked at: " + new Date()
  }

  @JSExportTopLevel("addClickedMessage")
  def addClickedMessage(str: String): Unit = {
    println("Hai, you clicked and said: " + str)
  }

}
