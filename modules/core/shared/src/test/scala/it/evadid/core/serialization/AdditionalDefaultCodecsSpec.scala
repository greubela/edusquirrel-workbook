package it.evadid.core.serialization

import munit.FunSuite
import upickle.default.*

class AdditionalDefaultCodecsSpec extends FunSuite {
  // A data-only destination identity. Reader/writer behavior is reconstructed, never serialized.
  private case class ReadOnlyDestination(name: String)
      extends it.evadid.core.datastructures.storage.RemoteCacheCollection.CacheKey[String, Int] derives ReadWriter {
    import it.evadid.core.datastructures.storage.RemoteSyncDataCache.*
    import it.evadid.workbook.interaction.sync.SyncSuccess
    import it.evadid.util.logging.derived.SyncLogger
    import scala.concurrent.Future
    private val time = java.time.LocalDateTime.of(2025, 1, 1, 0, 0)
    override def reader: RemoteDataReader[String, Int] = new RemoteDataReader[String, Int] {
      override def fetchAll(logger: SyncLogger): Future[FetchResponse[String, Int]] =
        Future.successful(FetchResponse.fromMap(time, Map.empty, _ => Some(time)))
      override def fetchByKey(logger: SyncLogger, key: String): Future[FetchResponse[String, Int]] = fetchAll(logger)
    }
    override def writer: RemoteDataWriter[String, Int] = new RemoteDataWriter[String, Int] {
      override def writeAll(logger: SyncLogger, values: Map[String, Int]): Future[SyncSuccess] =
        Future.failed(new UnsupportedOperationException("Read-only destination"))
      override def writeForKey(logger: SyncLogger, key: String, value: Int): Future[SyncSuccess] = writeAll(logger, Map(key -> value))
    }
  }
  private def restore[T: ReadWriter](value: T): T = {
    val restored = read[T](write(value))
    assertEquals(restored, value)
    assertEquals(readBinary[T](writeBinary(value)), value)
    restored
  }
  test("font descriptions preserve CSS behavior and defaults") {
    import it.evadid.core.datastructures.font.AppFont
    val font = AppFont("Anonymous Pro", 13.5, italic = true, bold = true, variant = "small-caps")
    assertEquals(restore(font).toCssString, font.toCssString)
    assertEquals(read[AppFont]("{\"name\":\"Arial\",\"sizeInPx\":12}"), AppFont.defaultFont)
  }
  test("bit and compression configuration codecs reconstruct executable algorithms") {
    import it.evadid.evacuation.core.datastructures.seqs.BitSequence
    import it.evadid.evacuation.core.io.instances.bits.HuffmanIO
    import it.evadid.evacuation.core.io.instances.binary.{BytePlaneConverter, SizeContentIO}
    restore(BitSequence.empty); restore(BitSequence(Long.MinValue))
    val huffman = restore(HuffmanIO(HuffmanIO.createEncodingMap(Map('a' -> 10, 'b' -> 1))))
    assertEquals(huffman.decode(huffman.encode("abba".toList)).mkString, "abba")
    val sizes = restore(SizeContentIO(8))
    val bits = List(BitSequence(1), BitSequence.empty)
    assertEquals(sizes.decode(sizes.encode(bits)), bits)
    val planes = restore(BytePlaneConverter(3))
    val bytes = Array[Byte](-1, 0, 42, 2, 3)
    assertEquals(planes.reconstruct(planes.convert(bytes)).toList, bytes.toList)
    intercept[upickle.core.TraceVisitor.TraceException](read[SizeContentIO]("{\"bitsForSize\":0}"))
  }
  test("routing node, option and algorithm information models have default codecs") {
    import it.evadid.evacuation.core.algorithm.routing.*
    import it.evadid.evacuation.core.algorithm.routing.model.*
    restore(Dijkstra.DijkstraInformation(1.5)); restore(BFS.BFSInformation(3))
    restore(AStar.AStarInformation(2.5, 10.5))
    restore(SearchNode("exit", Some("door"), Dijkstra.DijkstraInformation(4)))
    restore(SearchNode("start", None, BFS.BFSInformation(0)))
    restore(RoutingOption("door", Some("exit"), "exit", 1.5))
  }
  test("worksheet arithmetic results retain precision and derived values") {
    import it.evadid.workbook.model.blockchain.*
    restore(LedgerTransfer("Ada", "Bob", BigInt("12345678901234567890")))
    LedgerError.values.foreach(restore(_))
    val hash = SquareMiddleHash.calculate(BigInt("12345678901234567890"))
    assertEquals(restore(hash).highlightedSquare, hash.highlightedSquare)
    val energy = MiningEnergyEstimate(BigDecimal("0.12345678901234567890123456789"), 3000, 600)
    assertEquals(restore(energy).allocatedKilowattHoursPerTransaction, energy.allocatedKilowattHoursPerTransaction)
  }
  test("Unicode characters and all QR region kinds have default codecs") {
    import it.evadid.workbook.model.text.UnicodeText
    import it.evadid.workbook.model.qr.QrCodeRegion
    UnicodeText.characters("aé😀" + 0xd800.toChar + "x" + 0xdc00.toChar).foreach(c => assertEquals(restore(c).hexadecimal, c.hexadecimal))
    QrCodeRegion.values.foreach(restore(_))
    intercept[upickle.core.TraceVisitor.TraceException](read[it.evadid.workbook.model.text.UnicodeCharacter](
      "{\"position\":1,\"glyphCodeUnits\":[65536],\"codePoint\":65,\"wellFormed\":true}"))
  }
  test("Snap layout and reporter descriptors preserve defaults and every kind") {
    import it.evadid.workbook.elements.interactionElements.programming.state.snap.*
    restore(SnapCanvasLayout.empty)
    restore(SnapCanvasLayout(List(SnapCanvasScript(-5, 20, 3), SnapCanvasScript(9, 11, 1, false))))
    restore(SnapCanvasScript(0, 1, 4))
    SnapControlFlow.SnapReporterKind.values.foreach(kind => restore(SnapControlFlow.SnapReporter("selector", kind)))
    SnapTurtleCatalog.PaletteTab.values.foreach(restore(_))
    SnapTurtleCatalog.SnapInputKind.values.foreach(restore(_))
    assertEquals(read[SnapCanvasLayout]("{}"), SnapCanvasLayout.empty)
  }
  test("Snap parsed XML and custom blocks retain exact source slices and slot behavior") {
    import it.evadid.workbook.elements.interactionElements.programming.state.snap.*
    val xml = "<block-definition s=\"draw %steps\" type=\"command\" category=\"motion\"><inputs><input type=\"%n\">20</input></inputs><script><block s=\"forward\"><l>20</l></block></script></block-definition>"
    val element = SnapXmlParser.elements(xml, "block-definition").head
    assertEquals(restore(element).outer, xml)
    val block = SnapCustomBlockRules.definition(element)
    assertEquals(restore(block).blockSpec, "draw %n")
    block.slots.foreach(restore(_))
    val plan = SnapCustomBlockMerge.CustomBlockPlan("draw", "draw %steps", List("steps"), List("%n"),
      "command", "motion", "", "<comment>ä</comment>", "", "", "", "", "<inputs/>", "", Map("length" -> "steps"))
    val restoredPlan = restore(plan)
    assertEquals(restoredPlan.callSpec, "draw %n")
    assertEquals(restoredPlan.toXml("<block s=\"forward\"/>"), plan.toXml("<block s=\"forward\"/>"))
    restore(SnapCustomBlockRules.ObsoleteCall("missing", "unknown definition"))
  }
  test("sealed type-assignment outcomes preserve both concrete and polymorphic codecs") {
    import it.evadid.vm.types.*
    val same = AssigningPossibleWithSameType(BeDataType.Int)
    val cast = AssigningPossibleWithImplicitCast(BeDataType.Numeric)
    val no = AssigningNotPossible()
    restore(same); restore(cast); restore(no)
    List[BeDataTypeAssigningPossible](same, cast, no).foreach { outcome =>
      assertEquals(restore(outcome).possibleWithoutSyntaxErrors, outcome.possibleWithoutSyntaxErrors)
    }
  }
  test("diagnostic messages retain translated content and every diagnostic category") {
    import it.evadid.vm.types.BeInfo
    import it.evadid.core.datastructures.language.{LanguageMap, AppLanguage}
    val kinds: List[BeInfo.InfoType] = BeInfo.SyntaxError.values.toList ++ BeInfo.RuntimeError.values.toList ++ BeInfo.WarningType.values.toList
    BeInfo.SyntaxError.values.foreach(restore(_))
    BeInfo.RuntimeError.values.foreach(restore(_))
    BeInfo.WarningType.values.foreach(restore(_))
    intercept[Exception](read[BeInfo.InfoType]("[\"unknown\",\"Invalid\"]"))
    kinds.foreach { kind =>
      restore(kind)
      val info = BeInfo(LanguageMap.universalMap[AppLanguage.HumanLanguage]("message: ä\n😀"), kind)
      restore(info)
    }
  }
  test("sync records preserve nanosecond timestamps and every sealed variant") {
    import it.evadid.core.datastructures.storage.RemoteSyncDataCache.*
    val time = java.time.LocalDateTime.of(2025, 3, 4, 5, 6, 7, 123456789)
    val fetched = DataEntryReadFromServer("key", List("ä", "😀"), time)
    val writing = DataEntryToWriteToServer("key", List("answer"), time.plusNanos(1))
    restore(fetched); restore(writing)
    List[DataEntryToSync[String, List[String]]](fetched, writing).foreach(restore(_))
    val status = SyncStatus(Some(time), "key", Some(fetched))
    val restored = restore(status)
    assert(!restored.isSubmittedTimeNewerThanLastRequest(time))
    assert(restored.isSubmittedTimeNewerThanLastChangedTimestamp(time.plusNanos(1)))
    restore(SyncStatus[String, Int](None, "empty", None))
  }
  test("fetched workbook histories and response records have default codecs") {
    import it.evadid.workbook.interaction.sync.*
    import it.evadid.workbook.interaction.sync.SyncInformation.*
    import it.evadid.workbook.interaction.variable.*
    val time = java.time.LocalDateTime.of(2025, 1, 1, 0, 0)
    restore(SyncFetchedHistory(InteractionVariableHistory.empty[Int], time, InteractionVariableHistorySerialized.empty))
    restore(InteractionVariableFetchResponse(time, Set.empty))
  }
  test("cache reports derive a codec when the concrete destination identity has one") {
    import it.evadid.core.datastructures.storage.RemoteCacheCollection.CacheCollectionReport
    import it.evadid.core.datastructures.storage.RemoteSyncDataCache.*
    val destination = ReadOnlyDestination("local")
    val status = SyncStatus[String, Int](None, "answer", None)
    restore(CacheCollectionReport("answer", Map(destination -> status), List(destination)))
  }
}
