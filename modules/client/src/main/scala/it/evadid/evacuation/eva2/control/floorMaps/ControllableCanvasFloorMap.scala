package it.evadid.evacuation.eva2.control.floorMaps

import it.evadid.core.datastructures.matrix.{Matrix, MatrixPosition, PositionInMatrix}
import it.evadid.evacuation.core.graphic.model.EvaImage
import it.evadid.evacuation.core.graphic.spritemap.SpriteMap
import it.evadid.evacuation.core.graphic.sprites.traits.{OverlaySprite, Sprite}
import it.evadid.evacuation.eva2.configuration.ui.PersonDrawingInformation
import it.evadid.evacuation.eva2.control.traits.TileMapController
import it.evadid.evacuation.eva2.model.{Person, ProgramState}
import it.evadid.evacuation.html.elements.EvaWebCanvas
import it.evadid.evacuation.shared.traits.graphic.EvaMouseListener
import org.scalajs.dom.Element

case class ControllableCanvasFloorMap(spriteMap: SpriteMap, matrix: Matrix[Sprite], tileMapController: TileMapController) extends FloorMap {

  private val canvas = new EvaWebCanvas(spriteMap.spriteSize * matrix.dim.cols, spriteMap.spriteSize * matrix.dim.rows)

  private def pixToPos(xPixelPos: Double, yPixelPos: Double): PositionInMatrix = {

    val xPos = (xPixelPos / spriteMap.spriteSize).toInt
    val yPos = (yPixelPos / spriteMap.spriteSize).toInt
    MatrixPosition(xPos, yPos) in matrix
  }

  canvas.addMouseListener(new EvaMouseListener {

    private var lastTile: Option[PositionInMatrix] = None

    override def onMouseEntered(x: Double, y: Double): Unit = tileMapController.onMouseEnteringTileMap(pixToPos(x, y))

    override def onMouseExited(x: Double, y: Double): Unit = tileMapController.onMouseLeavingTileMap(pixToPos(x, y))

    override def onMouseClicked(x: Double, y: Double, primaryButton: Boolean): Unit = tileMapController.onMouseClickingOnTile(pixToPos(x, y))

    override def onMouseMoved(x: Double, y: Double): Unit = {
      val curTile = pixToPos(x, y)
      if (lastTile.isEmpty) {
        lastTile = Some(curTile)
      }
      if (lastTile.get != curTile) {
        tileMapController.onMouseSwitchingTile(lastTile.get, curTile)
        lastTile = Some(curTile)
      }
    }
  })


  override def floorMapElement: Element = {
    /* Todo: Fix size changes
        val table = document.createElement("div")
        table.setAttribute("id", "content-table")
        table.appendChild(canvas.getCanvasElement)
        table*/
    canvas.getCanvasElement
  }

  override def redraw(overlays: List[(PositionInMatrix, OverlaySprite)], persons: Set[Person], personInformation: Map[Int, PersonDrawingInformation]): Unit = {

    canvas.clear()

    matrix.elementsAtPosition.foreach(tup => {
      val (sprite, pos) = tup
      //   println("drawn: " + sprite.filename)
      canvas.drawImage(pos.cPos.x * spriteMap.spriteSize, pos.cPos.y * spriteMap.spriteSize, EvaImage.fromPath(spriteMap.getFullSpritePath(sprite.frameData)))
    })

    overlays.foreach(tup => {
      val (pos, sprite) = tup
      canvas.drawImageWithAlpha(pos.cPos.x * spriteMap.spriteSize, pos.cPos.y * spriteMap.spriteSize, sprite.opacityUpTo255, EvaImage.fromPath(spriteMap.getFullSpritePath(sprite.frameData)))
    })

    if (ProgramState.config.showAnimations.getValue.value) {
      // println("[WARN] CCFP doesnot implement draw animated persons!")
      persons.foreach(person => {
        if (personInformation.contains(person.id)) {
          val info = personInformation(person.id)

          val op = if (info.doesMoveInCurrentStep) 255 else 100
          val animCount = ProgramState.graphicConfig.animationCounter.getValue.value
          val frameData = if (info.toMoveInStep) person.sprite.getFrame(animCount, info.directionToMove) else person.sprite.getFrame(0, info.directionToMove)
          val path = EvaImage.fromPath(spriteMap.getFullSpritePath(frameData))

          //println("###person: " + person.id + ", info: "+ info + ", animCount: " + animCount + ", path: " + path.fullFilePath + ", sprite: " + person.sprite.getClass )
          canvas.drawImageWithAlpha(person.pos.cPos.x * spriteMap.spriteSize, person.pos.cPos.y * spriteMap.spriteSize, op, path)

        } else
          canvas.drawImage(person.pos.cPos.x * spriteMap.spriteSize, person.pos.cPos.y * spriteMap.spriteSize, EvaImage.fromPath(spriteMap.getFullSpritePath(person.sprite.frameData)))
      }
      )
    }
    else {
      persons.foreach(person => {
        canvas.drawImage(person.pos.cPos.x * spriteMap.spriteSize, person.pos.cPos.y * spriteMap.spriteSize, EvaImage.fromPath(spriteMap.getFullSpritePath(person.sprite.frameData)))
      })

    }

  }
}
