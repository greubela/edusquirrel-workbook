package it.evadid.evacuation.eva2.model

import it.evadid.evacuation.core.datastructures.Direction
import it.evadid.evacuation.core.datastructures.matrix.{Matrix, MatrixDimension, MatrixPosition, PositionInMatrix}
import it.evadid.evacuation.core.graphic.sprites.traits.{FloorSprite, PersonSprite}
import it.evadid.evacuation.eva2.model.FloorMatrix.FloorMatrix
import it.evadid.evacuation.eva2.model.ProgramState.spriteMap

import scala.collection.immutable.HashSet
import scala.collection.mutable
import scala.collection.mutable.ListBuffer

case class EvaFloorMap(floorMatrix: Matrix[FloorSprite], persons: Set[Person]) {

  def getPositionOfPerson(id: Integer): Option[PositionInMatrix] = persons.find(_.id == id).map(_.pos)

  /*private val personPositions: immutable.Set[PositionInMatrix] = {
    persons.map(_.pos)
  }

  def isPositionFreeFromPerson(positionInMatrix: PositionInMatrix): Boolean = {
    !persons.exists(_.pos == positionInMatrix)
   // !personPositions.contains(positionInMatrix)
  }*/

  private def personIdNewPosMap(): Map[Integer, PositionInMatrix] = {
    val map: mutable.HashMap[Integer, PositionInMatrix] = new mutable.HashMap()
    persons.foreach(person => {
      map.put(person.id, person.pos)
    })
    map.toMap
  }

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

  def extendMatrix(top: Boolean, left: Boolean, bottom: Boolean, right: Boolean): EvaFloorMap = {
    var updated = floorMatrix
    if (top) updated = updated.addRow(_ => spriteMap.defaultEmpty, 0)
    if (left) updated = updated.addColumn(_ => spriteMap.defaultEmpty, 0)
    if (bottom) updated = updated.addRow(_ => spriteMap.defaultEmpty)
    if (right) updated = updated.addColumn(_ => spriteMap.defaultEmpty)

    var updatedPersons = persons
    if (top) updatedPersons = updatedPersons.map(person => Person(person.id, person.pos.cPos.inDirection(Direction.BOTTOM).in(updated.dim), person.sprite))
    if (left) updatedPersons = updatedPersons.map(person => Person(person.id, person.pos.cPos.inDirection(Direction.RIGHT).in(updated.dim), person.sprite))

    updatedPersons = updatedPersons.map(person => Person(person.id, person.pos.cPos.in(updated.dim), person.sprite))

    val res = EvaFloorMap(updated, updatedPersons)

    res
  }

  def shrinkMatrix(top: Boolean, left: Boolean, bottom: Boolean, right: Boolean): EvaFloorMap = {

    var updated = floorMatrix
    if (top && updated.dim.rows > 1) updated = updated.removeRow(0)
    if (left && updated.dim.cols > 1) updated = updated.removeColumn(0)
    if (bottom && updated.dim.rows > 1) updated = updated.removeRow()
    if (right && updated.dim.cols > 1) updated = updated.removeColumn()

    var updatedPersons = persons
    if (top && updated.dim.rows > 1) updatedPersons = updatedPersons.map(person => Person(person.id, person.pos.cPos.inDirection(Direction.TOP).in(updated.dim), person.sprite))
    if (left && updated.dim.cols > 1) updatedPersons = updatedPersons.map(person => Person(person.id, person.pos.cPos.inDirection(Direction.LEFT).in(updated.dim), person.sprite))

    updatedPersons = updatedPersons.map(person => Person(person.id, person.pos.cPos.in(updated.dim), person.sprite))

    updatedPersons = updatedPersons.filter(_.pos.isInRange)

    val res = EvaFloorMap(updated, updatedPersons)
    res

  }


  def replaceIntoMap(floorMapToInsert: EvaFloorMap, insertAtPosition: MatrixPosition = MatrixPosition(0, 0)): EvaFloorMap = {

    def fitToDimension(initMatrix: Matrix[FloorSprite]): Matrix[FloorSprite] = {
      val necessaryWidth = insertAtPosition.x + floorMapToInsert.floorMatrix.dim.cols
      val necessaryHeight = insertAtPosition.y + floorMapToInsert.floorMatrix.dim.rows

      val setToWidth = math.max(necessaryWidth, floorMatrix.dim.cols)
      val setToHeight = math.max(necessaryHeight, floorMatrix.dim.rows)
      val setToDim = MatrixDimension(setToWidth, setToHeight)

      floorMatrix.setToDimension(setToDim, _ => ProgramState.spriteMap.defaultEmpty)
    }

    def replaceTilesIntoNewMatrix(initMatrix: Matrix[FloorSprite]): Matrix[FloorSprite] = {
      val map = mutable.HashMap[MatrixPosition, FloorSprite]()
      floorMapToInsert.floorMatrix.elementsAtPosition.foreach(tup => {
        val (sprite: FloorSprite, pim: PositionInMatrix) = tup
        val posToInsert = pim.cPos.add(insertAtPosition)
        map.put(posToInsert, sprite)
      })

      def getElementAtPosition(pos: MatrixPosition): FloorSprite = {
        map.getOrElse(pos, initMatrix.get(pos).getOrElse(ProgramState.spriteMap.defaultEmpty))
      }

      Matrix[FloorSprite](initMatrix.dim, (pim: PositionInMatrix) => getElementAtPosition(pim.cPos))
    }


    def getCombinedPersonList(curMatrix: Matrix[FloorSprite]): List[Person] = {
      val removePersonsAt = floorMapToInsert.floorMatrix.dim.positions.map(_.cPos.add(insertAtPosition))
      val map = mutable.HashMap[MatrixPosition, PersonSprite]()

      persons.filterNot(person => removePersonsAt.contains(person.pos.cPos)).foreach(person => map.put(person.pos.cPos, person.sprite))
      floorMapToInsert.persons.foreach(person => map.put(person.pos.cPos.add(insertAtPosition), person.sprite))

      map.toList.zipWithIndex.map(tup => {
        val (((pos, sprite), index)): ((MatrixPosition, PersonSprite), Int) = tup
        Person(index, pos.in(curMatrix.dim), sprite)
      })
    }

    var newMatrix = floorMatrix
    newMatrix = fitToDimension(newMatrix)
    newMatrix = replaceTilesIntoNewMatrix(newMatrix)
    val newPersons: Set[Person] = new HashSet[Person]() ++ getCombinedPersonList(newMatrix)

    EvaFloorMap(newMatrix, newPersons)
  }

  def calcPersonIdMovementMap(otherState: EvaFloorMap): Map[Int, Direction] = {
    val res: mutable.HashMap[Int, Direction] = new mutable.HashMap()

    val idNewPosMap = otherState.personIdNewPosMap()
    persons.foreach(person => {
      val pos = person.pos
      val newPos = idNewPosMap.get(person.id)
      if (newPos.nonEmpty && pos.cPos != newPos.get.cPos)
        Direction.fromPosition(newPos.get.cPos.sub(pos.cPos)).foreach(res.put(person.id, _))
    })
    res.toMap

  }

  def calcMovement(otherState: EvaFloorMap): List[(Person, Direction)] = {
    val res: ListBuffer[(Person, Direction)] = new ListBuffer()

    val idNewPosMap = otherState.personIdNewPosMap()

    persons.foreach(person => {
      val pos = person.pos
      val newPos = idNewPosMap.get(person.id)
      if (newPos.nonEmpty) {
        if (pos.cPos != newPos.get.cPos) {
          val dir = Direction.fromPosition(newPos.get.cPos.sub(pos.cPos))
          if (dir.nonEmpty) {
            res += ((person, dir.get))
          }
        }
      }
    })

    res.toList
  }

}

