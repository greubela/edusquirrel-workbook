package it.evadid.evacuation.core.io.traits.encoder

import scala.concurrent.{ExecutionContext, Future}

trait AsyncEncoder[I, O] extends Encoder[I, Future[O]]{
  implicit val executionContext: ExecutionContext
}
