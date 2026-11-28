package it.evadid.workbook.history

import upickle.default.*
import it.evadid.core.util.io.serializer.DefaultSerializer.given

import it.evadid.core.datastructures.language.*
import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.core.util.io.Serializer
import it.evadid.core.util.io.serializer.DefaultSerializer
import it.evadid.workbook.sync.UpdateImportance
import UpdateImportance.*

import java.time.Instant

case class InteractionState[T](value: T, updateImportance: UpdateImportance, timestamp: Instant) derives ReadWriter {


}

object InteractionState {

  case class DesignatedInteractionState[T](value: T, timestamp: Instant) derives ReadWriter

  case class InteractionStateChange[T](lastState: InteractionState[T], newState: DesignatedInteractionState[T]) derives ReadWriter

}

