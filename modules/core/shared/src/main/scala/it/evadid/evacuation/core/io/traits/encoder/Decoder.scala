package it.evadid.evacuation.core.io.traits.encoder

trait Decoder[I, O]{
  def decode(out: O): I
}
