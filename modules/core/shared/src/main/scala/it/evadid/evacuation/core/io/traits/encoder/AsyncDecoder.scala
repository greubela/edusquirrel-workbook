package it.evadid.evacuation.core.io.traits.encoder

import scala.concurrent.{ExecutionContext, Future}

trait AsyncDecoder[I, O] extends Decoder[Future[I], O]{
  implicit val executionContext: ExecutionContext
}
