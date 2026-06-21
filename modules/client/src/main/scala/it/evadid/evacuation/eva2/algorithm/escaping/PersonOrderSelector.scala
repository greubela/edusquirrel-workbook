package it.evadid.evacuation.eva2.algorithm.escaping

import it.evadid.evacuation.eva2.model.FloorMatrix.FloorMatrix
import it.evadid.evacuation.eva2.model.{EvaFloorMap, Person}

import scala.util.Random

trait PersonOrderSelector[I, J] {

  def setOrderOfMicroMovement(currentState: EvaFloorMap, notMovedYet: Seq[Person]): Seq[Person]
}

object PersonOrderSelector {

  private val universalRandom = new Random()

  def getRandomSelector[I, J]: PersonOrderSelector[I, J] = new PersonOrderSelector[I, J] {
    override def setOrderOfMicroMovement(currentState: EvaFloorMap, notMovedYet: Seq[Person]): Seq[Person] =
      universalRandom.shuffle(notMovedYet)
  }

  def getAirlineSelector[I, J]: PersonOrderSelector[I, J] = new PersonOrderSelector[I, J] {
    override def setOrderOfMicroMovement(currentState: EvaFloorMap, notMovedYet: Seq[Person]): Seq[Person] = {
      val floorMatrix = new FloorMatrix(currentState.floorMatrix)

      val airlineOrdering: Ordering[Person] = Ordering.by(person => {
        floorMatrix.savePositions.map(_.cPos.euclidianDistTo(person.pos)).min
      })

      notMovedYet.sorted(airlineOrdering)

    }
  }


}