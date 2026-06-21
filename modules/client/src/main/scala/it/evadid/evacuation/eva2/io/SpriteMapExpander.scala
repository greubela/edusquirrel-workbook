package it.evadid.evacuation.eva2.io

import it.evadid.core.datastructures.matrix.{MatrixDimension, MatrixPosition}
import it.evadid.evacuation.core.graphic.spritemap.SpriteMapConfig
import it.evadid.evacuation.core.io.instances.eva.config.TopDownMetaConfig
import it.evadid.evacuation.core.io.util.LocalResourceReader

import java.awt.Color
import java.awt.image.BufferedImage
import java.io.{ByteArrayInputStream, File, InputStream}
import java.nio.file.Path
import javax.imageio.ImageIO
import scala.concurrent.ExecutionContext

object SpriteMapExpander {

  def expandInDirectory(imageStream: InputStream, formatLines: Seq[String], baseDir: Path, tileSize: Int, name: String, config: SpriteMapConfig): Unit = {
    try {

      val destDir = baseDir.resolve(name).resolve(tileSize.toString)
      println("Expand to: " + destDir.toAbsolutePath.toString)
      val spriteMapImage = ImageIO.read(imageStream)

      config.allSpriteLines.foreach(line => saveImg(spriteMapImage, tileSize, line, config, destDir))

      // overlays, 1: yellow:
      val yo = new BufferedImage(tileSize, tileSize, BufferedImage.TYPE_INT_RGB)
      MatrixDimension(tileSize, tileSize).positions.foreach(pos => yo.setRGB(pos.cPos.x, pos.cPos.y, Color.WHITE.getRGB))
      ImageIO.write(yo, "png", destDir.resolve("whiteOverlay.png").toFile.getAbsoluteFile)
      MatrixDimension(tileSize, tileSize).positions.foreach(pos => yo.setRGB(pos.cPos.x, pos.cPos.y, Color.YELLOW.getRGB))
      ImageIO.write(yo, "png", destDir.resolve("yellowOverlay.png").toFile.getAbsoluteFile)

    } catch {
      case e: Exception => e.printStackTrace()
    }
  }

  private def saveImg(spriteMapImage: BufferedImage, tileSize: Int, formatLine: String, config: SpriteMapConfig, destDir: Path): Unit = {

    destDir.toFile.mkdirs()

    val parts: Array[String] = formatLine.strip.split("\\s")
    val col = Integer.parseInt(parts(1))
    val row = Integer.parseInt(parts(2))
    //val name =  parts(parts.length - 1) + ".png"
    val name = config.allImages.find(_.id == config.config.positionToId(MatrixPosition(col, row))).get.frameData.filename + ".png"
    //println(col + "|" + row + ": " + name)
    val subImg = spriteMapImage.getSubimage(col * tileSize, row * tileSize, tileSize, tileSize)

    ImageIO.write(subImg, "png", destDir.resolve(name).toFile.getAbsoluteFile)

  }

  private implicit val context: ExecutionContext = ExecutionContext.global

  def main(args: Array[String]): Unit = {

    val tileSize = 24
    val name = "topdown"
    //val resourceName = "tilesets/Tiles" + tileSize + ".png"
    val resourceName = "tilesets/topdown24technical.png"
    val fImgStream = LocalResourceReader.getResourceBytes(resourceName).map(arr => new ByteArrayInputStream(arr))

    val fConfigLines = LocalResourceReader.getResourceLines("defs/2022-04-26-TopdownTilemap.txt")

    val baseDir = new File("C:\\Users\\musterhafter\\Pictures\\Pixelart\\export").toPath

    fImgStream.foreach(imgStream => {
      fConfigLines.foreach(confLines => {
        println("starting")
        //val config = SpriteMapConfig(confLines, DefaultMetaConfig)
        val config = SpriteMapConfig(confLines, TopDownMetaConfig)
        expandInDirectory(imgStream, confLines, baseDir, tileSize, name, config)
        println("finished")
      })
    })

    println("hai!")

    Thread.sleep(5000)

    println("fImgStream: " + fImgStream + ", confLines: " + fConfigLines)

  }


}
