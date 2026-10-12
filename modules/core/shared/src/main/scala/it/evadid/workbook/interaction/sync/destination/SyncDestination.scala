package it.evadid.workbook.interaction.sync.destination

import it.evadid.core.util.io.serializer.ConstructorLikeSerializable
import it.evadid.util.logging.Logger
import it.evadid.util.logging.derived.SyncLogger

import scala.concurrent.Future

object SyncDestination {

  trait SyncDestinationForType[T] {

    def storeElement(obj: T): Future[Boolean]

    def readElement: Future[T]

  }


}

trait SyncDestination extends ConstructorLikeSerializable[SyncDestination] {


}