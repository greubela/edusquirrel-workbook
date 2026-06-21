package it.evadid.evacuation.eva2.io

import it.evadid.evacuation.core.datastructures.matrix.{MatrixDimension, MatrixPosition}
import it.evadid.evacuation.core.graphic.spritemap.EvaSpriteMap
import it.evadid.evacuation.core.graphic.sprites.traits.PersonSprite
import it.evadid.evacuation.core.io.instances.basic.ByteFixedLengthIntIO
import it.evadid.evacuation.core.io.traits.encoder.IO
import it.evadid.evacuation.eva2.model.Person

import scala.collection.immutable.HashSet

case class SimplePersonListEncoder(spriteMap: EvaSpriteMap, dim: MatrixDimension) extends IO[Set[Person], Array[Byte]] {

  private def encode(in: Person): Array[Byte] = ByteFixedLengthIntIO.encode(in.pos.cPos.x) ++ ByteFixedLengthIntIO.encode(in.pos.cPos.y) ++ ByteFixedLengthIntIO.encode(in.sprite.id)

  override def encode(persons: Set[Person]): Array[Byte] = {
      persons.foldLeft(new Array[Byte](0))(_ ++ encode(_))

  }

  override def decode(out: Array[Byte]): Set[Person] = {
    if (out.length % 12 == 0) {
      new HashSet[Person]() ++ out.sliding(12, 12).zipWithIndex.flatMap(tup => {
        val arr = tup._1
        val x = ByteFixedLengthIntIO.decode(arr.slice(0, 4))
        val y = ByteFixedLengthIntIO.decode(arr.slice(4, 8))
        val id = ByteFixedLengthIntIO.decode(arr.slice(8, 12))

        val sprite = spriteMap.sprites.find(_.id == id).map(_.asInstanceOf[PersonSprite])
        val pos = MatrixPosition(x, y).in(dim)
        assert(sprite.nonEmpty, "Cannot find person sprite with id " + id)
        sprite.map(Person(tup._2, pos, _))
      })
    } else {
      println("A simple encoded person should have 12 bytes -> Ignoring Persons!!")
      new HashSet[Person]()
    }


  }
}
