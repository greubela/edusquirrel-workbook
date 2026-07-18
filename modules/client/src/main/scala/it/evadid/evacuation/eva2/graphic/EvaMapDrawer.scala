package it.evadid.evacuation.eva2.graphic

import it.evadid.core.datastructures.matrix.MatrixDimension
import it.evadid.evacuation.core.graphic.spritemap.FrameData
import it.evadid.evacuation.eva2.model.EvaFloorMap

import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Path
import javax.imageio.ImageIO
import scala.collection.mutable

case class EvaMapDrawer(tileDir: Path, tileSize: Int, overlayBackground: Color) {

  private val imgCache = new mutable.HashMap[String, BufferedImage]()

  def drawState(evaFloorMap: EvaFloorMap, dest: Path, animationFrame: Int = 0): Unit = {
    dest.getParent.toFile.mkdirs()
    val img = drawImage(evaFloorMap, animationFrame)
    ImageIO.write(img, "png", dest.toFile)
  }


  private def loadFirstSpriteImage(frameData: FrameData): BufferedImage = {
    val tileFileName = frameData.filename + ".png"
    if (!imgCache.contains(tileFileName)) {
      val tilePath: File = tileDir.resolve(tileFileName).toFile
      val tileImg: BufferedImage = ImageIO.read(tilePath)
      imgCache.put(tileFileName, tileImg)
    }
    imgCache(tileFileName)
  }


  private def drawImage(fM: EvaFloorMap, animationFrame: Int): BufferedImage = {

    val img = new BufferedImage(fM.floorMatrix.dim.cols * tileSize, fM.floorMatrix.dim.rows * tileSize, BufferedImage.TYPE_INT_RGB)

    def drawFrameData(offsetX: Int, offsetY: Int, frameData: FrameData, ignoreColor: Option[Color]): Unit = {
      MatrixDimension(tileSize, tileSize).positions.foreach(pxPosition => {
        val tileImg = loadFirstSpriteImage(frameData)
        val tileRGB = tileImg.getRGB(pxPosition.cPos.x, pxPosition.cPos.y)
        val tileCol = new Color(tileRGB)
        if (ignoreColor.isEmpty || tileCol.getRed != ignoreColor.get.getRed || tileCol.getBlue != ignoreColor.get.getBlue || tileCol.getGreen != ignoreColor.get.getGreen) {
          img.setRGB(offsetX + pxPosition.cPos.x, offsetY + pxPosition.cPos.y, tileRGB)
        }
      })
    }

    fM.floorMatrix.dim.positions.foreach(position => {
      val offsetX = position.cPos.x * tileSize
      val offsetY = position.cPos.y * tileSize
      drawFrameData(offsetX, offsetY, fM.floorMatrix.get(position).get.frameData, None)
    })

    fM.persons.foreach(person => {
      val offsetX = person.pos.cPos.x * tileSize
      val offsetY = person.pos.cPos.y * tileSize
      drawFrameData(offsetX, offsetY, person.sprite.frameData, Some(overlayBackground))
    })

    img

  }


}
