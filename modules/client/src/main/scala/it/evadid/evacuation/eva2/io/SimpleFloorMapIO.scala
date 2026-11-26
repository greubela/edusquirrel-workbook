package it.evadid.evacuation.eva2.io

import it.evadid.evacuation.core.graphic.spritemap.EvaSpriteMap
import it.evadid.evacuation.core.io.instances.eva.SimpleFloorMatrixIdStringConverter
import it.evadid.evacuation.core.io.traits.encoder.IO
import it.evadid.evacuation.eva2.model
import it.evadid.evacuation.eva2.model.{EvaFloorMap, Person}

case class SimpleFloorMapIO(spriteMap: EvaSpriteMap) extends IO[EvaFloorMap, String] {


  assert("default".equalsIgnoreCase(spriteMap.name), "FloorMapIO Can only handle default Sprite Map")


  override def encode(floorMap: EvaFloorMap): String = {
    val floorMatrixBytes = SimpleFloorMatrixIdStringConverter(spriteMap).encode(floorMap.floorMatrix)
    val floorMatrixString = java.util.Base64.getEncoder.encodeToString(floorMatrixBytes)

    val personBytes = SimplePersonListEncoder(spriteMap, floorMap.floorMatrix.dim).encode(floorMap.persons)
    val personString = if (personBytes.length > 0) java.util.Base64.getEncoder.encodeToString(personBytes) else "null"

    "default:" + floorMatrixString + ":" + personString
  }

  override def decode(out: String): EvaFloorMap = {

    val parts = out.trim().split(":")

    assert(parts.length == 3, "String '" + out + "' is not formatted correctly, should be spriteMap:tiles:persons (but has " + parts.length + " parts)")
    assert("default".equalsIgnoreCase(parts(0)), "Must be default spritemap!")

    val floorMatrixBytes = java.util.Base64.getDecoder.decode(parts(1))
    val floorMatrix = SimpleFloorMatrixIdStringConverter(spriteMap).decode(floorMatrixBytes)
    val persons =
      if ("null".equalsIgnoreCase(parts(2))) {
        Set[Person]()
      } else {
        val personsBytes = java.util.Base64.getDecoder.decode(parts(2))
        SimplePersonListEncoder(spriteMap, floorMatrix.dim).decode(personsBytes)
      }

    model.EvaFloorMap(floorMatrix, persons)
  }

  /*

  override def decode(out: String): Future[ProgramState] = {

    val spriteMapF = loadSpriteMap(out)

    val sprites: String = out.split(":")(1)
    val indices: Seq[Int] = IndicesIO.decode(Base64.getDecoder.decode(sprites))

    val sprites: Future[Sprites] = spriteMapF.map(spriteMap => {
      val sprites = indices.toList.flatMap(curId => spriteMap.sprites.find(_.id == curId))
      assert(indices.size == sprites.size, "Error at loading sprites: Not all indices were valid!")
    })

    // config.
    null
  }
*/
}


