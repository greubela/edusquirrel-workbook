package it.evadid.evacuation.eva2.control.modes

import it.evadid.core.datastructures.matrix.{Matrix, MatrixPosition, PositionInMatrix}
import it.evadid.evacuation.core.graphic.sprites.BasicOverlaySprite
import it.evadid.evacuation.core.graphic.sprites.traits.{FloorSprite, OverlaySprite}
import it.evadid.evacuation.eva2.control.traits.TileMapController
import it.evadid.evacuation.eva2.control.{Eva2Control, Eva2ControlMode}
import it.evadid.evacuation.eva2.io.FloorMapIO
import it.evadid.evacuation.eva2.model.{DefaultFloors, EvaFloorMap, Person, ProgramState}
import it.evadid.evacuation.html.EvaHtmlFactory
import it.evadid.evacuation.html.elements.TabPaneModel
import it.evadid.evacuation.html.elements.TabPaneModel.TabProperty
import org.scalajs.dom.html.{Input, TextArea}
import org.scalajs.dom.{Element, document}

import java.util.Date
import scala.collection.{immutable, mutable}
import scala.concurrent.ExecutionContext.Implicits.global

case class ScenarioManagerMode() extends Eva2ControlMode {

  var inputBox: TextArea = EvaHtmlFactory.getStandardTextArea("Scenario as String", 5, "scenario-textfield", e => inputBoxChanged())
  val statusElement: Element = EvaHtmlFactory.createLabel("Scenario in textbox is invalid")

  private val managerModeTabPane = new ManagerModeTabPane()

  private val insertControl: InsertControl = new InsertControl()

  val widthField: Input = EvaHtmlFactory.createTextField("10")
  val heightField: Input = EvaHtmlFactory.createTextField("10")

  override def onEnteringMode(): Unit = {
    inputBoxChanged()
  }

  override def onLeavingMode(): Unit = {
  }

  override def getOverlays(): List[(PositionInMatrix, OverlaySprite)] = {
    if (managerModeTabPane.getCurrentTab == managerModeTabPane.tabInsert && insertControl.floorMapToInsert.isDefined && ProgramState.config.previewInsertion.getValue.value) {
      println("show inserts overlay!")
      insertControl.getInsertOverlays
    } else if (managerModeTabPane.getCurrentTab == managerModeTabPane.tabInsert && insertControl.floorMapToInsert.isDefined) {
      System.out.println("highlight pos!")
      List((insertControl.insertAtPosition in ProgramState.floorMatrix.dim, BasicOverlaySprite.yellowOverlay(225)))
    } else {
      println("show preview: " + ProgramState.config.previewInsertion.getValue.value + " current tab: " + managerModeTabPane.getCurrentTab + " defined?: " + insertControl.floorMapToInsert.isDefined)
      List()
    }
  }

  def inputBoxChanged(): Unit = {
    if (inputBox.value == null || inputBox.value.trim().isEmpty) {
      insertControl.floorMapToInsert = None
      statusElement.textContent = "Scenario in textbox is empty!"
    } else {
      statusElement.textContent = "Verifying Scenario in Textbox!"

      val (sid, spriteMapF, floorMapF) = ProgramState.instance.cache.loadScenario(inputBox.value)

      spriteMapF.onComplete(sm => floorMapF.onComplete(fm => {
        if (sm.isSuccess && fm.isSuccess) {
          statusElement.textContent = "Scenario: " + fm.get.floorMatrix.dim.rows + "x" + fm.get.floorMatrix.dim.cols + ", " + fm.get.persons.size + " persons (" + sm.get.name + ")"
          insertControl.floorMapToInsert = Some(fm.get)
        } else {
          statusElement.textContent = "Invalid Scenario. SpriteMap: " + sm.isSuccess + ", FloorMap: " + fm.isSuccess
        }
      }))

    }

  }


  private def storeScenario(): Unit = {
    val encoded = FloorMapIO(ProgramState.spriteMap).encode(ProgramState.instance.floorMap.currentValue)
    println("Storing Scenario at " + new Date() + " (" + encoded.length + " chars): " + encoded)
    setInputBoxText(encoded)
  }

  private def loadScenario(): Unit = try {
    ProgramState.instance.setScenario(inputBox.value)
  } catch {
    case e: Exception => e.printStackTrace()
  }


  private def setInputBoxText(str: String): Unit = {
    inputBox.value = str
    inputBoxChanged()
  }


  override def getControlElement: Element = {

    val tile = document.createElement("div")
    tile.setAttribute("id", "map-insert-control")

    // TextArea and StatusText

    val scenarioBox = document.createElement("div")
    scenarioBox.appendChild(inputBox)
    scenarioBox.appendChild(statusElement)

    tile.appendChild(EvaHtmlFactory.boxElement("Scenario as String", scenarioBox))

    // Tabs Load/Store/Insert

    tile.appendChild(managerModeTabPane.tabsPaneElement)

    // Standard Scenarios

    tile.appendChild(EvaHtmlFactory.createLabel("Load Scenarios Into Textbox:"))


    val buttonLoadDefault = EvaHtmlFactory.createButton("load-default", "Default Scenario", e => setInputBoxText(DefaultFloors.default))
    tile.appendChild(buttonLoadDefault)

    val buttonLoadSporthalls = EvaHtmlFactory.createButton("load-sportshall", "SportHall (TD40)", e => setInputBoxText(DefaultFloors.tdSportsHall40))
    tile.appendChild(buttonLoadSporthalls)

    val buttonLoadBigSporthall = EvaHtmlFactory.createButton("load-sportshall-big", "SportHall (DF120)", e => setInputBoxText(DefaultFloors.dfSportsHall120))
    tile.appendChild(buttonLoadBigSporthall)

    val buttonLoadCSTop = EvaHtmlFactory.createButton("load-cs-top", "Computer Science Building (top)", e => setInputBoxText(DefaultFloors.dfCSTop))
    tile.appendChild(buttonLoadCSTop)

    //val buttonLoadCSDown = EvaHtmlFactory.createButton("load-cs-down", "Computer Science Building (bottom)", e => setInputBoxText(DefaultFloors.dfCSBot))
    //tile.appendChild(buttonLoadCSDown)

    val buttonDataCenter = EvaHtmlFactory.createButton("load-ds-empty", "Data Center Building (empty)", e => setInputBoxText(DefaultFloors.dfDataCenterEmpty))
    tile.appendChild(buttonDataCenter)

    tile
  }

  def getLoadElement: Element = {
    val buttonLoad = EvaHtmlFactory.createButton("load-button", "Load Scenario from Textbox", e => loadScenario())
    buttonLoad
  }

  def getStoreElement: Element = {
    val buttonStore = EvaHtmlFactory.createButton("store-button", "Store Scenario into Textbox", e => storeScenario())
    buttonStore
  }

  def getControlElementOld: Element = {

    val tile: Element = null


    //insert
    tile.appendChild(EvaHtmlFactory.createLabel("Click on the TileMap (left) or insert:"))

    //insert
    val elementInsert = insertControl.getInsertElement
    tile.appendChild(elementInsert)


    // empty
    tile.appendChild(EvaHtmlFactory.createLabel("Create new empty field:"))
    tile.appendChild(createNewEmptyFieldElement())


    tile
  }

  def createNewEmptyFieldElement(): Element = {

    val pane = document.createElement("div")

    val div = document.createElement("div")
    div.setAttribute("style", "display: grid; grid-template-columns: repeat(5,auto);")

    div.appendChild(EvaHtmlFactory.createLabel("Size: "))
    div.appendChild(widthField)
    div.appendChild(EvaHtmlFactory.createLabel("x"))
    div.appendChild(heightField)

    val buttonNew = EvaHtmlFactory.createButton("create-new-empty", "Create empty Scenario", e => {
      try {
        val w = Integer.parseInt(widthField.value)
        val h = Integer.parseInt(heightField.value)

        if (w > 0 && h > 0) {
          val matrix: Matrix[FloorSprite] = Matrix(w, h, pos => ProgramState.spriteMap.defaultEmpty)
          ProgramState.instance.floorMap.setValue(EvaFloorMap(matrix, immutable.HashSet[Person]()))
        }

      } catch {
        case e: Exception => println("error at insertion: " + e.getMessage)
      }
    })

    pane.appendChild(div)
    pane.appendChild(buttonNew)

    pane
  }


  private class ManagerModeTabPane {

    val tabLoad: TabProperty = TabProperty("load", "Load")
    val tabInsert: TabProperty = TabProperty("insert", "Insert")
    val tabStore: TabProperty = TabProperty("story", "Store")
    val tabNew: TabProperty = TabProperty("new", "New")

    val tabPaneModel: TabPaneModel = TabPaneModel(List(tabLoad, tabInsert, tabStore, tabNew))

    def getCurrentTab: TabProperty = tabPaneModel.currentTab.currentValue

    val tabsPaneElement: Element = EvaHtmlFactory.getStandardTabPane(tabPaneModel, Map(
      tabLoad -> (() => getLoadElement),
      tabInsert -> (() => insertControl.getInsertElement),
      tabStore -> (() => getStoreElement),
      tabNew -> (() => createNewEmptyFieldElement())
    ))

  }

  private class InsertControl {

    var insertAtPosition: MatrixPosition = MatrixPosition(0, 0)
    var floorMapToInsert: Option[EvaFloorMap] = None

    val xField: Input = EvaHtmlFactory.createTextField("0")
    val yField: Input = EvaHtmlFactory.createTextField("0")

    def getInsertElement: Element = {
      val pane = document.createElement("div")
      pane.setAttribute("id", "manager-insert-element")

      pane.appendChild(createPositionRow())

      pane.appendChild(EvaHtmlFactory.createPropertyTickBox(ProgramState.config.previewInsertion))

      pane
    }

    def createPositionRow(): Element = {

      val curMaxX = ProgramState.floorMatrix.dim.cols
      val curMaxY = ProgramState.floorMatrix.dim.rows

      val div = document.createElement("div")
      div.setAttribute("id", "position-pane")

      div.appendChild(EvaHtmlFactory.createLabel("at:"))
      div.appendChild(xField)
      div.appendChild(EvaHtmlFactory.createLabel("|"))
      div.appendChild(yField)
      div.appendChild(EvaHtmlFactory.createLabel(" (max pos: " + curMaxX + "|" + curMaxY + ")"))

      div.appendChild(buttonInsert)

      div
    }


    def insertAtPosition(pim: PositionInMatrix): Unit = if (floorMapToInsert.isDefined) {
      val inserted = ProgramState.instance.floorMap.currentValue.replaceIntoMap(floorMapToInsert.get, pim)
      ProgramState.instance.floorMap.setValue(inserted)
    }

    val buttonInsert: Element = EvaHtmlFactory.createButton("insert-at-pos", "Insert at Position", e => {

      println("reading pos: " + xField.value + "|" + yField.value)
      try {
        val x = Integer.parseInt(xField.value)
        val y = Integer.parseInt(yField.value)

        val pim = MatrixPosition(x, y) in ProgramState.floorMatrix.dim
        if (pim.isInRange) {
          println("inserting at: " + pim)
          insertAtPosition(pim)
        }
      } catch {
        case e: Exception => println("error at insertion: " + e.getMessage)
      }
    })

    def getInsertOverlays: List[(PositionInMatrix, OverlaySprite)] = {

      val currentMap = ProgramState.instance.floorMap.currentValue

      val buf: mutable.ListBuffer[(PositionInMatrix, OverlaySprite)] = mutable.ListBuffer()

      insertControl.floorMapToInsert.get.floorMatrix.elementsAtPosition.map(
        tup => (tup._1, tup._2.cPos.add(insertControl.insertAtPosition) in currentMap.floorMatrix.dim)
      ).filter(_._2.isInRange
      ).foreach(tup => {
        buf.append((tup._2, BasicOverlaySprite.whiteOverlay))
        buf.append((tup._2, tup._1.asInstanceOf[OverlaySprite]))
      })

      insertControl.floorMapToInsert.get.persons.foreach(person => {
        val pim = person.pos.cPos.add(insertControl.insertAtPosition) in currentMap.floorMatrix.dim
        if (pim.isInRange) buf.append((pim, OverlaySprite.fromSprite(person.sprite)))
      })

      buf.toList
    }

    def tmc: TileMapController = new TileMapController {

      var oldInsert: MatrixPosition = MatrixPosition(0, 0)

      override def onMouseEnteringTileMap(onTile: PositionInMatrix): Unit = {
        oldInsert = insertAtPosition
        insertAtPosition = onTile.cPos
      }

      override def onMouseSwitchingTile(oldTile: PositionInMatrix, newTile: PositionInMatrix): Unit = {
        val start = System.nanoTime()
        insertAtPosition = newTile.cPos
        Eva2Control.requestRedrawTiles()
        val end = System.nanoTime()
        println("diff: " + ((end - start) / (1000.0 * 1000)) + "ms")
      }

      override def onMouseLeavingTileMap(lastTile: PositionInMatrix): Unit = {
        insertAtPosition = oldInsert
      }

      override def onMouseClickingOnTile(onTile: PositionInMatrix): Unit = {
        insertAtPosition = onTile.cPos
        oldInsert = onTile.cPos
        insertAtPosition(onTile)
        inputBoxChanged()
      }
      /*
            override def onTileDragStarted(dragEvent: DragEvent, onTile: PositionInMatrix): Unit = ???

            override def onTileDragOver(dragEvent: DragEvent, onTile: PositionInMatrix): Unit = ???

            override def onTileDragEnded(dragEvent: DragEvent): Unit = ???

            override def onTileDragDropped(dragEvent: DragEvent, onTile: PositionInMatrix): Unit = ???*/
    }

  }

  override def mainAreaTileMapController: TileMapController = insertControl.tmc
}
