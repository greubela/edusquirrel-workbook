package it.evadid.evacuation.eva2.io

import it.evadid.evacuation.core.graphic.spritemap.EvaSpriteMap
import it.evadid.evacuation.core.io.instances.binary.MinimalEncoder
import it.evadid.evacuation.core.io.instances.eva.SimpleFloorMatrixIdStringConverter
import it.evadid.evacuation.core.io.traits.encoder.IO
import it.evadid.evacuation.eva2.model
import it.evadid.evacuation.eva2.model.{EvaFloorMap, Person}

import scala.collection.immutable.HashSet
import scala.concurrent.ExecutionContextExecutor

case class FloorMapIO(spriteMap: EvaSpriteMap) extends IO[EvaFloorMap, String] {

  override def encode(floorMap: EvaFloorMap): String = {

    val floorMatrixBytes = SimpleFloorMatrixIdStringConverter(spriteMap).encode(floorMap.floorMatrix)
    val personBytes = SimplePersonListEncoder(spriteMap, floorMap.floorMatrix.dim).encode(floorMap.persons)

    val floorMatCompressed = new MinimalEncoder().convert(floorMatrixBytes)
    val personsCompressed = new MinimalEncoder().convert(personBytes)

    val floorMatrixString = java.util.Base64.getEncoder.encodeToString(floorMatCompressed)
    val personString = if (personsCompressed.length > 0) java.util.Base64.getEncoder.encodeToString(personsCompressed) else "null"

    val res = spriteMap.id.layout + spriteMap.id.size + ":" + floorMatrixString + ":" + personString
    res
  }

  override def decode(out: String): EvaFloorMap = {

    val parts = out.trim().split(":")

    assert(parts.length == 3, "String '" + out + "' is not formatted correctly, should be spriteMap:tiles:persons (but has " + parts.length + " parts)")
    //assert("default".equalsIgnoreCase(parts(0)), "Must be default spritemap!")

    val floorMatrixBytesCompressed = java.util.Base64.getDecoder.decode(parts(1))
    val floorMatrixBytes: Array[Byte] = new MinimalEncoder().reconstruct(floorMatrixBytesCompressed)

    val floorMatrix = SimpleFloorMatrixIdStringConverter(spriteMap).decode(floorMatrixBytes)
    val persons =
      if ("null".equalsIgnoreCase(parts(2))) {
        HashSet[Person]()
      } else {
        val personsBytesCompressed = java.util.Base64.getDecoder.decode(parts(2))
        val personBytes: Array[Byte] = new MinimalEncoder().reconstruct(personsBytesCompressed)
        SimplePersonListEncoder(spriteMap, floorMatrix.dim).decode(personBytes)
      }
    model.EvaFloorMap(floorMatrix, persons)
  }


}


object FloorMapIO {

  implicit val context: ExecutionContextExecutor = scala.concurrent.ExecutionContext.global

  def actualMain(spriteMap: EvaSpriteMap, floorMap: EvaFloorMap): Unit = {
    println("called actual main :-)")

    val io = new FloorMapIO(spriteMap)

    val res = io.encode(floorMap)
    println("res (" + res.length + "): " + res)

    val rec = io.decode(res)
    val simple = SimpleFloorMapIO(spriteMap).encode(rec)
    println(" simple_rec(" + simple.length + "): " + simple)

  }


}
