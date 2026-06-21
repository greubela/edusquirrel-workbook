package it.evadid.evacuation.core.io.instances.eva

import it.evadid.evacuation.core.datastructures.matrix.{Matrix, MatrixDimension}
import it.evadid.evacuation.core.graphic.spritemap.SpriteMap
import it.evadid.evacuation.core.graphic.sprites.traits.FloorSprite
import it.evadid.evacuation.core.io.instances.basic.ByteFixedLengthIntIO
import it.evadid.evacuation.core.io.traits.encoder.IO

case class SimpleFloorMatrixIdStringConverter(spriteMap: SpriteMap) extends IO[Matrix[FloorSprite], Array[Byte]] {

  override def encode(in: Matrix[FloorSprite]): Array[Byte] =  {
    val colBytes: Array[Byte] = ByteFixedLengthIntIO.encode(in.dim.cols)
    val rowBytes: Array[Byte] = ByteFixedLengthIntIO.encode(in.dim.rows)
    val contentBytes: Array[Byte] = IndicesIO.encode(in.elements.map(_.id))
    colBytes ++ rowBytes ++ contentBytes
  }

  override def decode(bytes: Array[Byte]): Matrix[FloorSprite] =  {

    val colBytes = bytes.slice(0, 4)
    val rowBytes = bytes.slice(4, 8)
    val contentBytes = bytes.slice(8, bytes.length)

    val cols = ByteFixedLengthIntIO.decode(colBytes)
    val rows = ByteFixedLengthIntIO.decode(rowBytes)
    val dim = MatrixDimension(cols, rows)

    val ids = IndicesIO.decode(contentBytes)
    val tileOptions = ids.map(id => spriteMap.sprites.find(_.id == id))
    assert(!tileOptions.exists(_.isEmpty), "An id is invalid!")
    assert(!tileOptions.exists(_.get.isInstanceOf[FloorSprite] == false), "An id is not an FloorSprite!")
    val floorSprites = tileOptions.map(_.get.asInstanceOf[FloorSprite])

    assert(floorSprites.size == cols * rows, "Elements are missing from the map!")

    Matrix(dim, floorSprites)

  }

}
