package it.evadid.evacuation.core.utility

object BinaryUtility {

  def getByteValue(number: Long, pos: Int): Long = {
    number >> (64 - pos * 8) & 255
  }

  def byteToUInt(byte: Byte): Integer = if (byte >= 0) byte.toInt else byte + 256


  def isBitSet(number: Integer, position: Integer): Boolean = {
    if (position < 0 || position > 32) None
    val mask = 1 << position
    (mask & number) > 0
  }


  def isBitSet(long: Long, pos: Int): Boolean =
    ((long >>> pos) & 1) % 2 == 1

  def setBit(long: Long, pos: Int): Long = long | (1 << pos)

  def flipBit(long: Long, pos: Int): Long = long ^ (1 << pos)



  def main(args: Array[String]): Unit = {
/*
    def testVals: List[Int] = List(0, 1, -1, 255, -255, 100, -100, 1000, 1024, 3333, -3333, 4444, 555555, 923423423, Integer.MAX_VALUE, Integer.MIN_VALUE)

    testVals.foreach(res => println(res + util.Arrays.toString(getUBytes(res))))

    println("255: " + getUBytes(255))
*/
  }


}
