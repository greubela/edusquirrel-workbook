package it.evadid.evacuation.eva2.model

import it.evadid.core.datastructures.matrix.PositionInMatrix
import it.evadid.evacuation.core.graphic.sprites.traits.PersonSprite

case class Person(id: Int, pos: PositionInMatrix, sprite: PersonSprite){

  def moveToPosition(newPos: PositionInMatrix): Person = Person(id, newPos, sprite)

}
