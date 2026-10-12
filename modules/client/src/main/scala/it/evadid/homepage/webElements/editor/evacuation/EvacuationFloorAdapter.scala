package it.evadid.homepage.webElements.editor.evacuation

import it.evadid.core.datastructures.matrix.*
import it.evadid.evacuation.core.graphic.sprites.{BasicFloorSprite, BasicPersonSprite}
import it.evadid.evacuation.core.graphic.sprites.traits.FloorSprite
import it.evadid.evacuation.core.graphic.spritemap.*
import it.evadid.evacuation.core.io.instances.eva.config.DefaultMetaConfig
import it.evadid.evacuation.eva2.model.{EvaFloorMap, Person}
import it.evadid.workbook.model.evacuation.*

/** A minimal construction palette using EVA2's actual movement properties, not a second simulator. */
object EvacuationFloorAdapter {
  val floor = BasicFloorSprite(0, "empty", FrameData("empty"), FloorSpriteProperties.open, false)
  val wall = BasicFloorSprite(1, "wall", FrameData("wall"), FloorSpriteProperties.closed, false)
  val exit = BasicFloorSprite(2, "exit", FrameData("exit"), FloorSpriteProperties.open, true)
  val person = BasicPersonSprite(3, "person", FrameData("person"))
  val sprites = EvaSpriteMap(SpriteMapResourceIdentifier("workbook", "default", 32, "Workbook construction palette"),
    List(floor, wall, exit, person), floor, DefaultMetaConfig, MatrixDimension(4, 1))
  def decode(plan: EvacuationFloorPlan): EvaFloorMap = {
    val dim = MatrixDimension(plan.cols, plan.rows)
    val tiles: List[FloorSprite] = plan.tiles.map {
      case EvacuationTile.Floor => floor
      case EvacuationTile.Wall => wall
      case EvacuationTile.Exit => exit
    }
    EvaFloorMap(Matrix(dim, tiles), plan.people.toList.sorted.zipWithIndex.map((index, id) =>
      Person(id, dim.positions(index), person)).toSet)
  }
  def encode(map: EvaFloorMap): EvacuationFloorPlan = {
    require(!map.floorMatrix.dim.wrapAround, "Workbook floors do not wrap around")
    val tiles = map.floorMatrix.elements.map {
      case s if s == floor => EvacuationTile.Floor
      case s if s == wall => EvacuationTile.Wall
      case s if s == exit => EvacuationTile.Exit
      case _ => throw new IllegalArgumentException("Unsupported workbook construction tile")
    }
    require(map.persons.forall(p => p.pos.dim == map.floorMatrix.dim && p.pos.isInRange && p.sprite == person),
      "People must belong to this floor and construction palette")
    // Painting a wall removes its occupant; clicking a person again removes it in ScenarioEditorMode.
    val people = map.persons.flatMap(_.pos.asIndex).filter(i => tiles(i) != EvacuationTile.Wall)
    EvacuationFloorPlan(map.floorMatrix.dim.cols, map.floorMatrix.dim.rows, tiles, people)
  }
}
