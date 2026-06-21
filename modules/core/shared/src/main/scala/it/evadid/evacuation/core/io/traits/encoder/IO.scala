package it.evadid.evacuation.core.io.traits.encoder

trait IO[I, O] extends Encoder[I, O] with Decoder[I, O]

object IO {

  type ByteIO[I] = IO[I, Array[Byte]]


}