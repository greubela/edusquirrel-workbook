package it.evadid.evacuation.eva1.control.modes

import it.evadid.evacuation.config.property.ConfigProperty
import it.evadid.evacuation.config.property.ObservableConfigProperty.PropertyListener
import it.evadid.evacuation.config.property.discrete.{BasicDiscreteConfigProperty, BasicDiscreteObservableConfigProperty, ObservableDiscreteConfigProperty}
import it.evadid.evacuation.config.value.ConfigValue
import it.evadid.evacuation.core.graphic.model.{EvaColor, EvaFileInformation, EvaImage}
import it.evadid.evacuation.core.utility.GeneralUtility
import it.evadid.evacuation.eva1.control.traits.evamouselistener.{GraphObjectSelector, GraphObjectsSelector, GraphSpaceSelector}
import it.evadid.evacuation.eva1.control.traits.evamousevisualizer.GenericGraphSelectorVisualizer
import it.evadid.evacuation.eva1.control.{Eva1Control, Eva1ControlMode}
import it.evadid.evacuation.eva1.graphic.{GraphPane, GraphPaneWithVisualizedListener}
import it.evadid.evacuation.eva1.model.ProgramState
import it.evadid.evacuation.eva1.model.evagraph.EvaGraphTypes.{EvaEdge, EvaGraph, RouterOrEdge, edgeOptionToEitherOption}
import it.evadid.evacuation.eva1.model.evagraph.{ConnectionInfo, EvaGraphModel, EvaGraphTypes, Router}
import it.evadid.evacuation.html.EvaHtmlFactory
import it.evadid.evacuation.html.elements.ObservableInputElement.HtmlInputChangeListener
import org.scalajs.dom.html.Canvas
import org.scalajs.dom.{Element, document, html}

class ScenarioEditorMode extends Eva1ControlMode {

  private val graphPane: GraphPaneWithVisualizedListener[html.Canvas] = GraphPaneWithVisualizedListener[Canvas]()(createNewCanvas)

  private var showConfigObject: Option[RouterOrEdge] = None

  override def onEnteringMode(): Unit = {
  }

  override def onLeavingMode(): Unit = {
  }


  override def getControlElement: Element = {
    val root = document.createElement("div")

    root.appendChild(EvaHtmlFactory.boxElement("Mouse Mode", createModeButtons()))

    val buttonLoadEmpty = EvaHtmlFactory.createButton("load-empty", "Delete Everything", e => ProgramState.instance.graph.setValue(EvaGraphModel.emptyGraph()))
    root.appendChild(buttonLoadEmpty)

    showConfigObject.foreach(roe => root.appendChild(createConfigElement(roe)))

    root
  }


  private def createModeButtons(): Element = {
    val root = document.createElement("div")

    val buttonAddNode = EvaHtmlFactory.createButton("add-node-mode", "Add Nodes", e => graphPane.setMouseListener(ScenarioEditorMode.addNodeListener, GenericGraphSelectorVisualizer.getSpaceSelectorVisualizer))
    root.appendChild(buttonAddNode)

    val buttonAddEdge = EvaHtmlFactory.createButton("add-edge-mode", "Connect Nodes", e => graphPane.setMouseListener(ScenarioEditorMode.addEdgeListener, GenericGraphSelectorVisualizer.getAddEdgeVisualizer()))
    root.appendChild(buttonAddEdge)

    val buttonRemoveNode = EvaHtmlFactory.createButton("remove-mode", "Remove Object", e => graphPane.setMouseListener(ScenarioEditorMode.removeObjectSelector, GenericGraphSelectorVisualizer.getObjectSelectorVisualizer(EvaColor.red)))
    root.appendChild(buttonRemoveNode)

    val buttonChangeConfig = EvaHtmlFactory.createButton("change-config-mode", "Change Object", e => graphPane.setMouseListener(ScenarioEditorMode.changeObjectSelector(this), GenericGraphSelectorVisualizer.getObjectSelectorVisualizer()))
    root.appendChild(buttonChangeConfig)

    root
  }

  override def getMainPane(): GraphPane[Canvas] = graphPane

  def createConfigElement(routerOrEdge: RouterOrEdge): Element = routerOrEdge.getEither() match {
    case Left(router) => createConfigElement(router)
    case Right(edge) => createConfigElement(edge)
  }


  def createConfigElement(edge: EvaEdge): Element = {
    val root = document.createElement("div")

    def updateEdge(newInfo: ConnectionInfo): Unit = {
      val newGraph = ProgramState.graph().deleteEdgesBetween(edge.start, edge.dest).addEdge(edge.start, edge.dest, newInfo)
      val newEdge = newGraph.allEdgesBetween(edge.start, edge.dest)
      ProgramState.instance.graph.setValue(newGraph)
      changeConfigObject(edgeOptionToEitherOption(newEdge.headOption))
    }

    val parField = EvaHtmlFactory.createNumberField("Parallelism: ", "change-edge-par", edge.content.maxParallelism, e => updateEdge(edge.content.changeParallelism(e.newValue)), Some(1), Some(1), None)
    root.appendChild(parField)

    //val delField = EvaHtmlFactory.createNumberField("Delay (ms): ", "change-edge-del", edge.content.delayInMs, e => updateEdge(edge.content.changeDelay(e.newValue)), Some(100), Some(0), None)
    //root.appendChild(delField)

    val speedField = EvaHtmlFactory.createNumberField("Speed (px/s): ", "change-edge-speed", edge.content.pxSpeedPerSecond(edge.pxDist).asInstanceOf[Int], e => updateEdge(edge.content.changeSpeed(edge.pxDist, e.newValue)), Some(1), Some(1), None)
    root.appendChild(speedField)

    root.appendChild(EvaHtmlFactory.createLabel("Delay: " + GeneralUtility.formatDuration(edge.content.delayInMs, false)))
    root.appendChild(EvaHtmlFactory.createLabel("Capacity: " + f"${edge.content.capacityPerSecond}%.3f" + " persons/s"))

    val res = EvaHtmlFactory.boxElement("Change Edge Config", root)
    res.setAttribute("id", "change-config-element")
    res
  }

  def changeConfigObject(routerOrEdge: Option[RouterOrEdge]): Unit = {
    showConfigObject = routerOrEdge
    Eva1Control.reloadControl()
    graphPane.repaint()
  }

  private def createExitTickBox(router: Router): Element = {

    val isExitProperty: ObservableDiscreteConfigProperty[Boolean] = BasicDiscreteObservableConfigProperty(BasicDiscreteConfigProperty.createBooleanProperty("is_router_exit", "Is Exit Node?"))
    isExitProperty.setValue(router.isExit)
    val listener = new PropertyListener[Boolean] {
      override def onPropertyChange(property: ConfigProperty[Boolean], oldValue: ConfigValue[Boolean], newValue: ConfigValue[Boolean]): Unit = {
        var newRouter = router.setExit(newValue.value)
        if(newValue.value) newRouter = newRouter.changeInit(0)
        updateRouter(router, newRouter)
      }
    }
    isExitProperty.listener += listener
    val sField = EvaHtmlFactory.createPropertyTickBox(isExitProperty)
    sField
  }

  private def updateRouter(oldRouter: Router, newRouter: Router): Unit = {
    ProgramState.instance.graph.setValue(ProgramState.graph().replaceNode(oldRouter, newRouter))
    changeConfigObject(Some(EvaGraphTypes.routerToEither(newRouter)))
  }

  def createConfigElement(router: Router): Element = {
    val root = document.createElement("div")


    val xField = EvaHtmlFactory.createNumberField("X Position: ", "change-config-x", router.pos.x, e => updateRouter(router, router.changeX(e.newValue)), Some(10), Some(0), Some(ProgramState.instance.graphicConfig.widthDimension.getValue.value))
    root.appendChild(xField)
    val yField = EvaHtmlFactory.createNumberField("Y Position: ", "change-config-y", router.pos.y, e => updateRouter(router, router.changeY(e.newValue)), Some(10), Some(0), Some(ProgramState.instance.graphicConfig.heightDimension.getValue.value))
    root.appendChild(yField)
    val pField = EvaHtmlFactory.createNumberField("Persons: ", "change-config-persons", router.initCapacity, e => updateRouter(router, router.changeInit(e.newValue)), Some(1), Some(0), Some(router.maxCapacity))
    root.appendChild(pField)

    root.appendChild(createExitTickBox(router))

    val res = EvaHtmlFactory.boxElement("Change Router Config", root)
    res.setAttribute("id", "change-config-element")
    res
  }



}

object ScenarioEditorMode {


  val addEdgeListener: GraphObjectsSelector[Router] = new GraphObjectsSelector[Router]() {
    override def getSelectableObjects: Seq[Router] = graph.nodes

    override def onObjectSelected(obj: Router): Unit = {}

    override def onObjectDeselected(obj: Router): Unit = {}

    override def onSelectionFinished(objects: Seq[Router]): Unit = {
      assert(objects.size == 2, "must select exactly two routers for edge!")
      val newGraph = ProgramState.graph() += (objects(0), objects(1), 1)
      ProgramState.instance.graph.setValue(newGraph)
    }

    override def graph: EvaGraph = ProgramState.graph()

    override def getMaxSelectionDistance(): Option[Int] = Some(50)

    override def getMaxObjectsToSelect: Integer = 2

    override def automaticDeselect(): Boolean = true
  }


  val addNodeListener: GraphSpaceSelector = new GraphSpaceSelector() {
    override def minDistToEdges(): Option[Integer] = Some(10)

    override def minDistToNodes(): Option[Integer] = Some(20)

    override def onSpaceSelected(x: Double, y: Double, primaryButton: Boolean): Unit = {
      val newGraph = ProgramState.graph().addNode(Router(x, y))
      ProgramState.instance.graph.setValue(newGraph)
    }

    override def graph: EvaGraph = ProgramState.graph()
  }


  val removeObjectSelector: GraphObjectSelector[RouterOrEdge] = new GraphObjectSelector[RouterOrEdge] {
    override def getSelectableObjects: Seq[RouterOrEdge] = graph.nodes.map(EvaGraphTypes.routerToEither) ++ graph.edges.map(EvaGraphTypes.edgeToEither)

    override def onObjectSelected(obj: RouterOrEdge): Unit = {
      val newGraph = obj.getEither() match {
        case Left(router) => ProgramState.graph().deleteNodeAndEdges(router)
        case Right(edge) => ProgramState.graph().deleteEdgesBetween(edge.start, edge.dest)
      }
      ProgramState.instance.graph.setValue(newGraph)
    }

    override def onObjectDeselected(obj: RouterOrEdge): Unit = {}

    override def graph: EvaGraph = ProgramState.graph()

    override def getMaxSelectionDistance(): Option[Int] = Some(50)

    override def automaticDeselect(): Boolean = true
  }

  def changeObjectSelector(editorMode: ScenarioEditorMode): GraphObjectSelector[RouterOrEdge] = new GraphObjectSelector[RouterOrEdge] {
    override def getSelectableObjects: Seq[RouterOrEdge] = graph.nodes.map(EvaGraphTypes.routerToEither) ++ graph.edges.map(EvaGraphTypes.edgeToEither)

    override def onObjectSelected(obj: RouterOrEdge): Unit =
      editorMode.changeConfigObject(Some(obj))

    override def onObjectDeselected(obj: RouterOrEdge): Unit = editorMode.changeConfigObject(None)

    override def graph: EvaGraph = ProgramState.graph()

    override def getMaxSelectionDistance(): Option[Int] = Some(50)

    override def automaticDeselect(): Boolean = false
  }


}

