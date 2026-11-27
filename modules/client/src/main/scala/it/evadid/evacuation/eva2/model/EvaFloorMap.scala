package it.evadid.evacuation.eva2.model

import it.evadid.core.datastructures.matrix.*
import it.evadid.evacuation.core.graphic.sprites.traits.{FloorSprite, PersonSprite}

import scala.collection.immutable.HashSet
import scala.collection.mutable

case class EvaFloorMap(floorMatrix: Matrix[FloorSprite], persons: Set[Person]) {

  def getPositionOfPerson(id: Integer): Option[PositionInMatrix] = persons.find(_.id == id).map(_.pos)

  /*private val personPositions: immutable.Set[PositionInMatrix] = {
    persons.map(_.pos)
  }

  def isPositionFreeFromPerson(positionInMatrix: PositionInMatrix): Boolean = {
    !persons.exists(_.pos == positionInMatrix)
   // !personPositions.contains(positionInMatrix)
  }*/

  private def personIdNewPosMap(): Map[Integer, PositionInMatrix] =
    persons.toList.map(person => (person.id: Integer) -> person.pos).toMap

  /** Every person one position further in `direction`, placed in a matrix of `dimension`. */
  private def shifted(people: Set[Person], direction: Direction, dimension: MatrixDimension): Set[Person] =
    people.map(person => Person(person.id, person.pos.cPos.inDirection(direction).in(dimension), person.sprite))

  /** Every person at the same position, placed in a matrix of `dimension`. */
  private def placedIn(people: Set[Person], dimension: MatrixDimension): Set[Person] =
    people.map(person => Person(person.id, person.pos.cPos.in(dimension), person.sprite))

  def insertOrSetPersonAtPosition(pos: PositionInMatrix, sprite: PersonSprite): EvaFloorMap = {
    val allPersons = persons.filter(_.pos != pos) ++ List(Person(0, pos, sprite))
    val personsWithNewIds = allPersons.zipWithIndex.map(tup => Person(tup._2, tup._1.pos, tup._1.sprite))
    EvaFloorMap(floorMatrix, personsWithNewIds)
  }

  def removePersonAtSavePoints(): EvaFloorMap = {
    val matrix = new FloorMatrix(floorMatrix)
    val personsCleaned = persons.filterNot(person => matrix.savePositions.contains(person.pos))
    EvaFloorMap(floorMatrix, personsCleaned)
  }

  def move(person: Person, to: PositionInMatrix): EvaFloorMap = {
    val newPerson = Person(person.id, to, person.sprite)
    val personsNew = persons - person + newPerson;
    EvaFloorMap(floorMatrix, personsNew)
  }

  def calcMovementOptions(pos: PositionInMatrix, neighbourFunc: Seq[MatrixPosition]): Set[PositionInMatrix] = {
    val blockedPositions = persons.map(_.pos)
    val reachablePositions = new FloorMatrix(floorMatrix).getReachableNeighbourPositions(pos, neighbourFunc, false)
    val unblockedReachable = reachablePositions.diff(blockedPositions)

    unblockedReachable
  }

  def extendMatrix(top: Boolean, left: Boolean, bottom: Boolean, right: Boolean, empty: FloorSprite = ProgramState.instance.spriteMap.currentValue.defaultEmpty): EvaFloorMap = {
    val withTop = if (top) floorMatrix.addRow(_ => empty, 0) else floorMatrix
    val withLeft = if (left) withTop.addColumn(_ => empty, 0) else withTop
    val withBottom = if (bottom) withLeft.addRow(_ => empty) else withLeft
    val updated = if (right) withBottom.addColumn(_ => empty) else withBottom

    val shiftedDown = if (top) shifted(persons, Direction.BOTTOM, updated.dim) else persons
    val shiftedRight = if (left) shifted(shiftedDown, Direction.RIGHT, updated.dim) else shiftedDown

    EvaFloorMap(updated, placedIn(shiftedRight, updated.dim))
  }

  def shrinkMatrix(top: Boolean, left: Boolean, bottom: Boolean, right: Boolean): EvaFloorMap = {
    val removedTop = top && floorMatrix.dim.rows > 1
    val removedLeft = left && floorMatrix.dim.cols > 1
    val withoutTop = if (removedTop) floorMatrix.removeRow(0) else floorMatrix
    val withoutLeft = if (removedLeft) withoutTop.removeColumn(0) else withoutTop
    val withoutBottom = if (bottom && withoutLeft.dim.rows > 1) withoutLeft.removeRow() else withoutLeft
    val updated = if (right && withoutBottom.dim.cols > 1) withoutBottom.removeColumn() else withoutBottom

    val shiftedUp = if (removedTop) shifted(persons, Direction.TOP, updated.dim) else persons
    val shiftedLeft = if (removedLeft) shifted(shiftedUp, Direction.LEFT, updated.dim) else shiftedUp

    EvaFloorMap(updated, placedIn(shiftedLeft, updated.dim).filter(_.pos.isInRange))
  }


  def replaceIntoMap(floorMapToInsert: EvaFloorMap, insertAtPosition: MatrixPosition = MatrixPosition(0, 0)): EvaFloorMap = {

    def fitToDimension(initMatrix: Matrix[FloorSprite]): Matrix[FloorSprite] = {
      val necessaryWidth = insertAtPosition.x + floorMapToInsert.floorMatrix.dim.cols
      val necessaryHeight = insertAtPosition.y + floorMapToInsert.floorMatrix.dim.rows

      val setToWidth = math.max(necessaryWidth, floorMatrix.dim.cols)
      val setToHeight = math.max(necessaryHeight, floorMatrix.dim.rows)
      val setToDim = MatrixDimension(setToWidth, setToHeight)

      floorMatrix.setToDimension(setToDim, _ => ProgramState.instance.spriteMap.currentValue.defaultEmpty)
    }

    def replaceTilesIntoNewMatrix(initMatrix: Matrix[FloorSprite]): Matrix[FloorSprite] = {
      val map: Map[MatrixPosition, FloorSprite] = floorMapToInsert.floorMatrix.elementsAtPosition.map { (sprite, pim) =>
        pim.cPos.add(insertAtPosition) -> sprite
      }.toMap

      def getElementAtPosition(pos: MatrixPosition): FloorSprite = {
        map.getOrElse(pos, initMatrix.get(pos).getOrElse(ProgramState.instance.spriteMap.currentValue.defaultEmpty))
      }

      Matrix[FloorSprite](initMatrix.dim, (pim: PositionInMatrix) => getElementAtPosition(pim.cPos))
    }


    def getCombinedPersonList(curMatrix: Matrix[FloorSprite]): List[Person] = {
      val removePersonsAt = floorMapToInsert.floorMatrix.dim.positions.map(_.cPos.add(insertAtPosition))
      val map = mutable.HashMap[MatrixPosition, PersonSprite]()

      persons.filterNot(person => removePersonsAt.contains(person.pos.cPos)).foreach(person => map.put(person.pos.cPos, person.sprite))
      floorMapToInsert.persons.foreach(person => map.put(person.pos.cPos.add(insertAtPosition), person.sprite))

      map.toList.zipWithIndex.map(tup => {
        val (((pos, sprite), index)) = tup
        Person(index, pos.in(curMatrix.dim), sprite)
      })
    }

    val newMatrix = replaceTilesIntoNewMatrix(fitToDimension(floorMatrix))
    val newPersons: Set[Person] = new HashSet[Person]() ++ getCombinedPersonList(newMatrix)

    EvaFloorMap(newMatrix, newPersons)
  }

  /** Each person that changes its position in `otherState`, with the direction of its movement. */
  def calcPersonIdMovementMap(otherState: EvaFloorMap): Map[Int, Direction] =
    calcMovement(otherState).map((person, direction) => person.id -> direction).toMap

  def calcMovement(otherState: EvaFloorMap): List[(Person, Direction)] = {
    val idNewPosMap = otherState.personIdNewPosMap()
    persons.toList.flatMap(person => idNewPosMap.get(person.id)
      .filter(_.cPos != person.pos.cPos)
      .flatMap(newPos => Direction.fromPosition(newPos.cPos.sub(person.pos.cPos)))
      .map(direction => (person, direction)))
  }

}
