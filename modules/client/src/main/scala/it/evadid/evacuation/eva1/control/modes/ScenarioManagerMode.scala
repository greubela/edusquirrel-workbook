package it.evadid.evacuation.eva1.control.modes

import it.evadid.evacuation.core.io.instances.eva.eva1.{EvaFlowGraphBase64Converter, EvaGraphFlowJsonConverter}
import it.evadid.evacuation.core.io.traits.encoder.IO
import it.evadid.evacuation.eva1.control.BasicPaneControlMode
import it.evadid.evacuation.eva1.model.ProgramState
import it.evadid.evacuation.eva1.model.evagraph.EvaGraphModel
import it.evadid.evacuation.html.EvaHtmlFactory
import org.scalajs.dom.html.TextArea
import org.scalajs.dom.{Element, document}

class ScenarioManagerMode extends BasicPaneControlMode {

  override def onEnteringMode(): Unit = {
  }

  override def onLeavingMode(): Unit = {
  }

  def loadGraph(converter: IO[EvaGraphModel, String], textBox: TextArea): Unit = try {
    val graph = converter.decode(textBox.value)
    ProgramState.instance.graph.setValue(graph)
  } catch {
    case e: Exception => e.printStackTrace()
  }

  def storeGraph(converter: IO[EvaGraphModel, String], textBox: TextArea): Unit = try {
    val str = converter.encode(ProgramState.graph())
    textBox.value = str
  } catch {
    case e: Exception => e.printStackTrace()
  }

  override def getControlElement: Element = {

    val tile = document.createElement("div")
    tile.setAttribute("id", "map-insert-control")


    tile.appendChild(createLoadStoreElement(EvaGraphFlowJsonConverter, "Json"))
    tile.appendChild(createLoadStoreElement(EvaFlowGraphBase64Converter, "Bas64"))
    tile.appendChild(EvaHtmlFactory.boxElement("Load Standard Scenarios", createStandardScenariosElement))
    tile
  }

  private def createLoadStoreElement(converter: IO[EvaGraphModel, String], typeName: String): Element = {

    val textBox = EvaHtmlFactory.getStandardTextArea("", 1, "ls-textbox-" + typeName, _ => {})
    val root = document.createElement("div")
    root.appendChild(textBox)
    root.appendChild(EvaHtmlFactory.createButton("load-" + typeName, "Load " + typeName, e => loadGraph(converter, textBox)))
    root.appendChild(EvaHtmlFactory.createButton("store-" + typeName, "Store " + typeName, e => storeGraph(converter, textBox)))
    EvaHtmlFactory.boxElement("Load and Store " + typeName, root)
  }

  private def createStandardScenariosElement: Element = {
    val root = document.createElement("div")

    val buttonLoadSporthall = EvaHtmlFactory.createButton("load-sportshall", "SportHall (40)", e => ProgramState.instance.graph.setValue(EvaGraphModel.createSportsHall()))
    root.appendChild(buttonLoadSporthall)

    val buttonLoadTest = EvaHtmlFactory.createButton("quick test", "Sample Graph", e => ProgramState.instance.graph.setValue(EvaGraphModel.createQuickTest()))
    root.appendChild(buttonLoadTest)

    root

  }


}
