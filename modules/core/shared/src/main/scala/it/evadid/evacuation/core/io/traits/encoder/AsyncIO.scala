package it.evadid.evacuation.core.io.traits.encoder

import scala.concurrent.ExecutionContext

trait AsyncIO[I, O] extends AsyncDecoder[I, O] with AsyncEncoder[I, O]{
  implicit val executionContext: ExecutionContext = scala.concurrent.ExecutionContext.global
}
