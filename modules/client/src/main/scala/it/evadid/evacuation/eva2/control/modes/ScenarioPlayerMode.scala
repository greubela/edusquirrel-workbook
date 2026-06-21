package it.evadid.evacuation.eva2.control.modes

import it.evadid.evacuation.core.datastructures.Direction
import it.evadid.evacuation.core.datastructures.matrix.PositionInMatrix
import it.evadid.evacuation.core.graphic.sprites.BasicOverlaySprite
import it.evadid.evacuation.core.graphic.sprites.traits.OverlaySprite
import it.evadid.evacuation.eva2.algorithm.escaping.{Evacuation, EvacuationMetaData, EvacuationStep}
import it.evadid.evacuation.eva2.configuration.ui.{PersonDrawingInformation, ShowMovementOption}
import it.evadid.evacuation.eva2.control.traits.TileMapController
import it.evadid.evacuation.eva2.control.{Eva2Control, Eva2ControlMode}
import it.evadid.evacuation.eva2.graphic.controllerHTML.SimulationPlayerHtmlFactory
import it.evadid.evacuation.eva2.model.{EvaFloorMap, ProgramState}
import it.evadid.evacuation.html.EvaHtmlFactory
import org.scalajs.dom.Element

import scala.collection.mutable
import scala.concurrent.ExecutionContextExecutor

case class ScenarioPlayerMode(enteringState: EvaFloorMap, evacuationSimulation: Evacuation, evacuationMetaData: EvacuationMetaData) extends Eva2ControlMode {

  private var currentStateInfo: Element = EvaHtmlFactory.createLabel("Empty")

  private var currentState: Int = 0
  private var drawingInformation = calcDrawingInformation()

  private var highlightWayOverlays: List[(PositionInMatrix, OverlaySprite)] = List()

  private def updateCurrentSimulationState(): Unit = {

    println("--- update current simulation state ---")
    val times = mutable.ListBuffer[Long]()

    times += System.currentTimeMillis()
    val nextState = evacuationSimulation.states(currentState)
    times += System.currentTimeMillis()
    ProgramState.instance.floorMap.setValue(nextState)
    times += System.currentTimeMillis()
    drawingInformation = calcDrawingInformation()
    times += System.currentTimeMillis()
    updateCurrentStateInfo()
    times += System.currentTimeMillis()

    printTimes(times.toSeq, Some("updateCurrentSimulationState"))

  }

  private def printTimes(seq: Seq[Long], msg: Option[String]): Unit = {
    print("Performance Statistics")
    msg.foreach(curmsg => print(" (for task: " + curmsg + ")"))
    print(": ")
    var prio = seq.head
    for (curTime <- seq.tail) {
      val diff = (curTime - prio) / 1000.0
      prio = curTime
      print(s"$diff, ")
    }
    val totalDiff = (seq.last - seq.head) / 1000.0
    println("summed times: " + totalDiff + "ms")
    println()
  }

  def updateCurrentStateInfo(): Unit = {
    val stateAtStepBegin = evacuationSimulation.stepBeginOfState(currentState) //evacuationSimulation.get.steps(evacuationSimulation.get.stepNr(currentState.get))
    val microStepInStep = (currentState - stateAtStepBegin)
    currentStateInfo.textContent = "Step " + (evacuationSimulation.stepNr(currentState) + 1) + ":" + (microStepInStep + 1) + " (Global MicroStep " + (currentState + 1) + ")"
  }

  override def onEnteringMode(): Unit = {

    if (evacuationMetaData == null || !evacuationMetaData.success) {
      println("Error: Evacuation did not finish successfully (time executed: " + evacuationMetaData.executionTimeInMs + "ms)")
      Eva2Control.setNewControlMode(ScenarioEditorMode())
    }

  }

  override def onLeavingMode(): Unit = ProgramState.instance.floorMap.setValue(enteringState)


  def getOverlays(): List[(PositionInMatrix, OverlaySprite)] = {
    (getOverlaysForMovement() ++ highlightWayOverlays)
  }

  def getOverlaysForMovement(): List[(PositionInMatrix, OverlaySprite)] = {
    if (ProgramState.instance.config.showMovementOption.getValue.value == ShowMovementOption.SHOW_MICRO_MOVEMENT && currentState < evacuationSimulation.nextStep(currentState)) {
      val curState = evacuationSimulation.states(currentState)
      val next = evacuationSimulation.states(evacuationSimulation.nextMicroStep(currentState))
      calcOverlay(curState, next)
    } else if (ProgramState.instance.config.showMovementOption.getValue.value == ShowMovementOption.SHOW_ALL_MOVEMENTS) {
      val curState = evacuationSimulation.states(currentState)
      val next = evacuationSimulation.states(evacuationSimulation.nextStep(currentState))
      calcOverlay(curState, next)
    } else {
      List()
    }
  }

  override def getDrawingInformation(): Map[Int, PersonDrawingInformation] = drawingInformation

  private def calcDrawingInformation(): Map[Int, PersonDrawingInformation] = if (ProgramState.config.showAnimations.getValue.value) {

    val startOfStep = evacuationSimulation.stepBeginOfState(currentState)
    val nextStep = evacuationSimulation.nextStep(currentState)

    val allMovesInCurStep = evacuationSimulation.states(startOfStep).calcPersonIdMovementMap(evacuationSimulation.states(nextStep))
    val remMovesInCurStep = evacuationSimulation.states(currentState).calcPersonIdMovementMap(evacuationSimulation.states(nextStep))


    val res: mutable.HashMap[Int, PersonDrawingInformation] = new mutable.HashMap()

    evacuationSimulation.states(currentState).persons.foreach(curPerson => {

      val doesMoveInStep = allMovesInCurStep.contains(curPerson.id)
      val toMoveInStep = remMovesInCurStep.contains(curPerson.id)

      val dirToMoveNext = evacuationSimulation.getNextDirectionOfPerson(currentState, curPerson.id)

      val drawingInformation = PersonDrawingInformation(dirToMoveNext.getOrElse(Direction.BOTTOM), doesMoveInStep, toMoveInStep)

      res.put(curPerson.id, drawingInformation)
    })

    res.toMap
  } else Map()

  private def calcOverlay(state1: EvaFloorMap, state2: EvaFloorMap): List[(PositionInMatrix, OverlaySprite)] = {
    /*val movement = state1.calcMovement(state2)

    movement.map(tup => {
      val person = tup._1
      val dir = tup._2
      val dirSprite = ProgramState.spriteMap.directionSprite(dir)
      if (dirSprite.isEmpty) {
        println("dirSprite empty for dir: " + dir)
      }
      assert(dirSprite.isDefined, "No Direction Sprite for '" + dir + "' found!")
      (person.pos, dirSprite.get)
    })*/

    //print("[WARN]: Deactivated Movement Arrows!")
    List()
  }

  def changeStatus(func: Int => Int): Unit = {
    currentState = func(currentState)
    updateCurrentSimulationState()
  }


  override def getControlElement: Element = {
    val element = SimulationPlayerHtmlFactory.createControlElement(this, currentStateInfo) //RadioButtonHandler.getRadioButtonElement)
    updateCurrentStateInfo()
    element
  }


  override def mainAreaTileMapController: TileMapController = new TileMapController() {

    override def onMouseEnteringTileMap(onTile: PositionInMatrix): Unit = {
    }

    override def onMouseSwitchingTile(oldTile: PositionInMatrix, newTile: PositionInMatrix): Unit = {

      val opPerson = evacuationSimulation.states(currentState).persons.find(_.pos.cPos == newTile.cPos)
      if (opPerson.isDefined) {
        val trace = evacuationSimulation.tracePerson(opPerson.get.id).distinct.filterNot(_.cPos == newTile.cPos)

        highlightWayOverlays = trace.zipWithIndex.map(tup => {
          val op = (tup._2 * 1.0 / trace.size)
          val sprite = BasicOverlaySprite.yellowOverlayPath(op)
          val res = (tup._1, sprite)
          res
        })
        Eva2Control.requestRedrawTiles()
      } else if (highlightWayOverlays.nonEmpty) {
        highlightWayOverlays = List()
        Eva2Control.requestRedrawTiles()
      }


    }

    override def onMouseLeavingTileMap(lastTile: PositionInMatrix): Unit = {
      highlightWayOverlays = List()
      Eva2Control.requestRedrawTiles()
    }

    override def onMouseClickingOnTile(onTile: PositionInMatrix): Unit = {
    }
    /*
        override def onTileDragStarted(dragEvent: DragEvent, onTile: PositionInMatrix): Unit = ???

        override def onTileDragOver(dragEvent: DragEvent, onTile: PositionInMatrix): Unit = ???

        override def onTileDragEnded(dragEvent: DragEvent): Unit = ???

        override def onTileDragDropped(dragEvent: DragEvent, onTile: PositionInMatrix): Unit = ???*/
  }


}

object ScenarioPlayerMode {

  private implicit val context: ExecutionContextExecutor = scala.concurrent.ExecutionContext.global

  def apply(): ScenarioPlayerMode = {

    val enteringState = ProgramState.instance.floorMap.currentValue

    val tup = EvacuationStep.calculateEvacuationAndMetadata()
    val evacuationSimulation = tup._1
    val evacuationMetaData = tup._2

    new ScenarioPlayerMode(enteringState, evacuationSimulation, evacuationMetaData)
  }


}

