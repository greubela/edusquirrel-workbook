package it.evadid.evacuation.eva1.model

import it.evadid.evacuation.core.datastructures.utility.ObservableVar
import it.evadid.evacuation.core.graphic.model.EvaImage
import it.evadid.evacuation.eva1.control.Eva1Control
import it.evadid.evacuation.eva1.control.configuration.{Eva1Config, Eva1GraphicConfig}
import it.evadid.evacuation.eva1.model.evagraph.EvaGraphModel

//Todo: Change mainPane to ConfigEntry with Template (Creation Function, Dimension)
// Todo: Simulate empty graph
case class ProgramState(graph: ObservableVar[EvaGraphModel], backgroundImage: ObservableVar[Option[EvaImage]], programConfig: Eva1Config, graphicConfig: Eva1GraphicConfig) {

}

object ProgramState {

  private var webInstance = initSingleton()

  val instance: ProgramState = webInstance

  private def initSingleton(): ProgramState = {
    val graph = EvaGraphModel.createQuickTest()
    val ps = ProgramState(new ObservableVar(graph), new ObservableVar(None), new Eva1Config(), new Eva1GraphicConfig())
    ps.graph.addListener((oldVal, newVal) => {
      Eva1Control.redrawMainArea()
      println("Selected new Graph: " + newVal)
    })
    ps.backgroundImage.addListener((oldVal, newVal) => {
      Eva1Control.redrawMainArea()
      println("Selected new Image: " + newVal)
    })

    ps
  }

  def graph(): EvaGraphModel = instance.graph.currentValue


}
