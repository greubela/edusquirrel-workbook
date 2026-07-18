package it.evadid.evacuation.core.io.traits.converter

import scala.collection.mutable.ListBuffer
import scala.util.Random

trait Bijection[T] extends Converter[List[T]] { self =>

  // Bijection Converter?

  def encodeIndex(indexIn: Int): Int

  def reconstructIndex(indexOut: Int): Int

  override def convert(in: List[T]): List[T] = in.indices.map(encodeIndex).map(in).toList

  override def reconstruct(out: List[T]): List[T] = out.indices.map(reconstructIndex).map(out).toList

  def combineWith[T](bij: Bijection[T]): Bijection[T] = new Bijection[T] {

    override def encodeIndex(indexIn: Int): Int = bij.encodeIndex(self.encodeIndex(indexIn))

    override def reconstructIndex(indexOut: Int): Int = self.reconstructIndex(bij.reconstructIndex(indexOut))
  }
}

object Bijection {

  private def identityList(size: Int): List[Int] = 1.to(size).toList

  def identity[T](): Bijection[T] = new Bijection[T] {
    override def encodeIndex(indexIn: Int): Int = indexIn

    override def reconstructIndex(indexOut: Int): Int = indexOut
  }

  def planeList(stepSize: Int, maxSize: Int): List[Int] = {
    val buf: ListBuffer[Int] = ListBuffer()
    0.until(stepSize).foreach(curModStep => {
      0.until(maxSize / stepSize).foreach(curPosition => {
        buf += curPosition * stepSize + curModStep
      })
    })
    while (buf.length < maxSize) {
      buf += buf.length
    }
    buf.toList
  }

  def fromShuffledIndexList[T](shuffled: List[Int]): Bijection[T] = new Bijection[T] {
    override def encodeIndex(indexIn: Int): Int = {
      assert(indexIn < shuffled.size, "Cannot convert index: shuffled out of range!")
      shuffled(indexIn)
    }

    override def reconstructIndex(indexOut: Int): Int = {
      assert(shuffled.contains(indexOut), "Cannot reconstruct index: not in shuffled!")
      shuffled.indexOf(indexOut)
    }
  }

  def randomShuffle[T](seed: Long, size: Int): Bijection[T] =
    fromShuffledIndexList(new Random(seed).shuffle(identityList(size)))

}