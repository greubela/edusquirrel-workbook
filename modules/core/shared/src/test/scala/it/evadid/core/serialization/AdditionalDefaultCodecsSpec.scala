package it.evadid.core.serialization

import it.evadid.vm.parsing.source.SourceParsing
import it.evadid.vm.parsing.source.SourceParsing.SourceLanguage
import it.evadid.vm.conversion.expressionsnap.SnapCustomBlockMerge
import it.evadid.vm.conversion.snapexpression.SnapControlFlow
import it.evadid.vm.conversion.snapexpression.SnapCustomBlockRules
import it.evadid.vm.conversion.snapmodelxml.SnapModelToXml.toXml
import it.evadid.workbook.interaction.sync.model.*
import munit.FunSuite
import upickle.default.*
import it.evadid.workbook.abstractions.InteractionFormat.*

class AdditionalDefaultCodecsSpec extends FunSuite {
  private def DataEntryReadFromServer[K, D](key: K, value: D, time: java.time.LocalDateTime): DataEntryToSync[K, D] =
    DataEntryToSync(key, value, SyncDirection.SERVER_TO_CLIENT)
  private def DataEntryToWriteToServer[K, D](key: K, value: D, time: java.time.LocalDateTime): DataEntryToSync[K, D] =
    DataEntryToSync(key, value, SyncDirection.CLIENT_TO_SERVER)

  private def restore[T: ReadWriter](value: T): T = {
    val restored = read[T](write(value))
    assertEquals(restored, value)
    assertEquals(readBinary[T](writeBinary(value)), value)
    restored
  }
}
