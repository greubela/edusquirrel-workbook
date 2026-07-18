package it.evadid.evacuation.core.io.traits.converter

trait Converter[T] {

  def convert(in: T): T

  def reconstruct(out: T): T


}

object Converter {

  def identity[T](): Converter[T] = new Converter[T] {
    override def convert(in: T): T = in

    override def reconstruct(out: T): T = out
  }

}
