package it.evadid.evacuation.core.io.instances.binary

import it.evadid.evacuation.core.io.traits.converter.{Bijection, Converter}

case class BytePlaneConverter(stepSize: Int) extends Converter[Array[Byte]] {

  // Todo implement

  override def convert(in: Array[Byte]): Array[Byte] = {
    val bij = Bijection.planeList(stepSize, in.length)
    Bijection.fromShuffledIndexList(bij).convert(in.toList).toArray
  }

  override def reconstruct(out: Array[Byte]): Array[Byte] = {
    val bij = Bijection.planeList(stepSize, out.length)
    Bijection.fromShuffledIndexList(bij).reconstruct(out.toList).toArray
  }
}
