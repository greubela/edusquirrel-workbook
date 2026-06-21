package it.evadid.evacuation.core.io.instances.eva.eva1

import it.evadid.evacuation.core.datastructures.graphs.Position
import it.evadid.evacuation.core.io.instances.basic.{ByteBooleanIO, ByteFixedLengthShortIO}
import it.evadid.evacuation.core.io.traits.encoder.IO.ByteIO
import it.evadid.evacuation.eva1.model.evagraph.Router

import scala.collection.mutable.ListBuffer

object RouterByteConverter extends ByteIO[Router] {


  val BYTES_PER_NODE = 9

  override def decode(out: Array[Byte]): Router = {

    assert(out.length <= BYTES_PER_NODE, "insufficient information to reconstruct router (" + BYTES_PER_NODE + " needed, " + out.length + " existing)")

    val x = ByteFixedLengthShortIO.decode(out.slice(0, 2))
    val y = ByteFixedLengthShortIO.decode(out.slice(2, 4))
    val init = ByteFixedLengthShortIO.decode(out.slice(4, 6))
    val max = ByteFixedLengthShortIO.decode(out.slice(6, 8))
    val exit = ByteBooleanIO.decode(Array(out(8)))

    Router(Position(x, y), init, max, exit)
  }

  override def encode(node: Router): Array[Byte] = {
    assert(node.pos.x <= Short.MaxValue && node.pos.y <= Short.MaxValue, "Position needs to be in short range for conversion!")
    assert(node.initCapacity <= Short.MaxValue && node.initCapacity <= Short.MaxValue, "Capacity needs to be in short range for conversion!")

    val buf = new ListBuffer[Byte]()

    buf addAll ByteFixedLengthShortIO.encode(node.pos.x.toShort)
    buf addAll ByteFixedLengthShortIO.encode(node.pos.y.toShort)
    buf addAll ByteFixedLengthShortIO.encode(node.initCapacity.toShort)
    buf addAll ByteFixedLengthShortIO.encode(node.maxCapacity.toShort)
    buf addAll ByteBooleanIO.encode(node.isExit)

    buf.toArray
  }

  def main(args: Array[String]): Unit = {
    val router = Router(Short.MinValue, -1)
    val bytes = encode(router)

    println("encoded: " + bytes.mkString(","))

    val convBytes = encode(decode(bytes))
    println(bytes.mkString + " --> " + convBytes.mkString)

    val convRouter = decode(encode(router))

    println(String.valueOf(router) + " --> " + convRouter)
  }
}
