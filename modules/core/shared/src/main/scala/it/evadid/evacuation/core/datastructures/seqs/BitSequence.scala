package it.evadid.evacuation.core.datastructures.seqs


import it.evadid.evacuation.core.utility.BinaryUtility

import scala.collection.mutable.ListBuffer

case class BitSequence(seq: List[Boolean]) {


  def head(headSize: Int): BitSequence = if (headSize < size) this else BitSequence(seq.slice(0, headSize))

  def tail(from: Int): BitSequence = {
    assert(from < size, "BitSeq to short (" + size + ") for tail from " + from + "!")
    BitSequence(seq.slice(from, seq.size))
  }

  def headInt: Int = {
    head(32).toInt
  }

  def tailInt: BitSequence = {
    tail(32)
  }

  def padToInt(): BitSequence = ensureSize(32)

  def hasPrefix(bitSequence: BitSequence): Boolean =
    if (bitSequence.size > size) false
    else bitSequence.seq == seq.slice(0, bitSequence.size)

  def hasPostfix(bitSequence: BitSequence): Boolean =
    if (bitSequence.size > size) false
    else bitSequence.seq == seq.slice(size - bitSequence.size, size)

  def isBitSet(pos: Int, outOfRangeDefault: Boolean = false): Boolean = if (pos >= size || pos < 0) outOfRangeDefault else seq(size - pos - 1)

  def removeLeadingZeros(minRemainingSize: Int = 1): BitSequence = {
    val res: ListBuffer[Boolean] = ListBuffer()
    res.addAll(seq)

    while (res.size > minRemainingSize && !res.head) {
      res.remove(0)
    }
    new BitSequence(res.toList)
  }

  def ensureSize(toSize: Int, fillWith: Boolean = false): BitSequence = {
    if (size < toSize) padLeftTo(toSize, fillWith)
    if (size == toSize) this
    else BitSequence(seq.takeRight(toSize))
  }

  def padLeftTo(toSize: Int, value: Boolean): BitSequence = BitSequence(seq.reverse.padTo(toSize, value).reverse)

  def padTo(toSize: Int, value: Boolean): BitSequence = BitSequence(seq.padTo(toSize, value))

  def append(bool: Boolean): BitSequence = BitSequence(seq.appended(bool))

  def append(bitSequence: BitSequence): BitSequence = BitSequence(seq ++ bitSequence.seq)

  def size: Int = seq.size

  def toLong: Long = {
    checkLength(64, "a long")

    var long = 0L
    seq.zipWithIndex.foreach(tup => {
      if (tup._1) {
        long = BinaryUtility.setBit(long, size - 1 - tup._2)
      }
    })
    long
  }

  def toInt: Int = {
    checkLength(32, "an int")
    toLong.asInstanceOf[Int]
  }

  def toByte: Byte = {
    checkLength(8, "a byte")
    toLong.asInstanceOf[Byte]
  }

  def toUByte: Int = {
    checkLength(8, "an unsigned byte")
    val byteVal = toByte
    if (byteVal < 0) byteVal + 256 else byteVal
  }

  private def checkLength(maxBits: Int, name: String): Unit = {
    assert(size > 0, "Byte Seq of size " + size + " has no number representation!")
    assert(size <= maxBits, "Byte Seq of size " + size + "does not fit into " + name + "!")
  }

  override def toString: String =
    if (size == 0) "BitSeq[]"
    else seq.map(if (_) "1" else "0").mkString("BitSeq[", "", "]")


}


object BitSequence {

  val empty = new BitSequence(List())

  def main(args: Array[String]): Unit = {
    val arr = Array(0, 1, 2, 3, 4, 5).map(_.asInstanceOf[Byte])

    arr.foreach(nr => println(s"$nr: ${apply(nr)}"))
    println(BitSequence(arr))
    println(getBitplane(arr.map(BitSequence(_)).toIndexedSeq, 1))
  }

  def fullInt(int: Int): BitSequence = BitSequence(int).ensureSize(32)

  def apply(arr: Array[Byte]): BitSequence = {
    arr.map(apply).foldLeft(BitSequence(List()))(_.append(_))
  }

  def getBitplane(arr: Seq[BitSequence], bitNr: Int): BitSequence =
    new BitSequence(arr.map(_.isBitSet(bitNr)).toList)

  def paddedNumber(nr: Long, length: Int): BitSequence = BitSequence(nr).ensureSize(length).padLeftTo(8, false)

  def apply(int: Int): BitSequence = apply(int.asInstanceOf[Long], 32)

  def apply(short: Short): BitSequence = apply(short.asInstanceOf[Long], 16)

  def apply(byte: Byte): BitSequence = apply(byte.asInstanceOf[Long], 8)

  def apply(long: Long): BitSequence = apply(long, 64)

  def apply(long: Long, startFromBit: Int): BitSequence = {

    var seq: ListBuffer[Boolean] = ListBuffer()

    (startFromBit - 1).to(0, -1).foreach(bitNr => {
      val shifted = long >>> bitNr
      val isActive = (shifted & 1) % 2 == 1
      seq += isActive
    })

    new BitSequence(seq.toList).removeLeadingZeros()
  }
}