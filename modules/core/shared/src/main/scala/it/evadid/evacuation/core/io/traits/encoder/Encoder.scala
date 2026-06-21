package it.evadid.evacuation.core.io.traits.encoder

trait Encoder[I, O] {
  def encode(in: I): O
}
