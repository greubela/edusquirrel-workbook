package it.evadid.workbook.model.qr

import munit.FunSuite
import it.evadid.workbook.elements.interactionElements.qr.{CreateQrCodeInteraction, QrCodeRequirements}
import it.evadid.workbook.jsonFactory.WorkbookElementFactory
import upickle.default.*

class QrCodeSpec extends FunSuite {
  private def checksum(matrix: Vector[Vector[Boolean]]): Int =
    matrix.flatten.foldLeft(0x811c9dc5)((value, dark) => (value ^ (if dark then 1 else 0)) * 16777619)

  test("all 40 versions and four correction levels match independent full-matrix fixtures") {
    QrCodeFixtures.symbols.foreach { (version, ecc, mask, expected) =>
      val bytes = Array.tabulate[Byte](version * 2 + 1)(i => (i * 149 + version).toByte)
      val q = QrCode(bytes, QrCodeConfig(version, QrErrorCorrection.values(ecc), mask))
      assertEquals(q.modules.size, 17 + 4 * version)
      assertEquals(checksum(q.modules), expected, s"version=$version ecc=$ecc mask=$mask")
    }
  }
  test("full-capacity symbols match independently generated matrices, including length-field boundaries") {
    QrCodeFixtures.boundaries.foreach { (version, ecc, capacity, expected) =>
      val bytes = Array.tabulate[Byte](capacity)(i => (i * 73 + 129).toByte)
      val q = QrCode(bytes, QrCodeConfig(version, QrErrorCorrection.values(ecc), 7))
      assertEquals(checksum(q.modules), expected, s"boundary version=$version ecc=$ecc")
    }
  }
  test("UTF-8 ECI and automatic version/mask selection match a second independent encoder") {
    QrCodeFixtures.automatic.foreach { (text, version, mask, hash) =>
      val code = QrCode.fromText(text)
      assertEquals(code.config.version, version, text)
      assertEquals(code.config.mask, mask, text)
      assertEquals(checksum(code.modules), hash, text)
      assertEquals(code.text, text)
    }
  }
  test("byte-mode headers, alternating padding and Reed–Solomon parity match a known codeword vector") {
    val expected = Vector(64, 148, 134, 86, 198, 198, 242, 5, 21, 34, 16, 236, 17, 236, 17, 236,
      160, 109, 40, 152, 42, 195, 21, 17, 212, 124)
    val content = "Hello QR!".getBytes(java.nio.charset.StandardCharsets.UTF_8)
    val config = QrCodeConfig(1, QrErrorCorrection.M, 0)
    assertEquals(QrCodeEncoder.codewords(content, config), expected)
    assertEquals(QrCodeEncoder.remainder(expected.take(16), 10), expected.drop(16))
  }
  test("mask changes preserve payload and select the lowest penalty with a deterministic tie-break") {
    val q = QrCode.encode(Array[Byte](0, -1, -128, 65))
    assertEquals(q.config.mask, q.maskPenalties.zipWithIndex.minBy(p => (p._1, p._2))._2)
    val symbols = (0 to 7).map(m => q.withMask(m).modules)
    assertEquals(symbols.distinct.size, 8)
    for mask <- 0 to 7 do {
      assertEquals(q.withMask(mask).content.toVector, q.content.toVector)
      // Finder, timing and fixed dark module are never XOR-masked.
      assert(q.withMask(mask).isDark(0, 0))
      assert(q.withMask(mask).isDark(6, 10))
      assert(q.withMask(mask).isDark(8, q.size - 8))
    }
  }
  test("byte capacity boundaries, explicit versions and oversized content") {
    assertEquals(QrCode.capacity(QrCodeConfig(1, QrErrorCorrection.L)), 17)
    assertEquals(QrCode.capacity(QrCodeConfig(1, QrErrorCorrection.M)), 14)
    for v <- 1 to 40; ecc <- QrErrorCorrection.values; utf8 <- List(false, true) do {
      val config = QrCodeConfig(v, ecc, 0, utf8)
      val capacity = QrCode.capacity(config)
      assertEquals(QrCode(Array.fill[Byte](capacity)(0), config).byteCount, capacity)
      intercept[IllegalArgumentException](QrCode(Array.fill[Byte](capacity + 1)(0), config))
    }
    assertEquals(QrCode.encode(Array.fill[Byte](18)(65), QrErrorCorrection.L).config.version, 2)
    intercept[IllegalArgumentException](QrCode.encode(Array.fill[Byte](3000)(0)))
    intercept[IllegalArgumentException](QrCode.encode(Array[Byte](1), version = Some(0)))
    intercept[IllegalArgumentException](QrCode.encode(Array[Byte](1), mask = Some(8)))
  }
  test("payload is defensively copied and equality survives serialization") {
    val bytes = Array[Byte](0, -1, -128, 127)
    val q = QrCode.encode(bytes)
    bytes(0) = 42
    q.content(1) = 42
    assertEquals(q.content.toVector, Vector[Byte](0, -1, -128, 127))
    val restored = read[QrCode](write(q))
    assertEquals(restored, q)
    assertEquals(restored.hashCode(), q.hashCode())
    assertEquals(restored.modules, q.modules)
    intercept[IllegalArgumentException](QrCodeConfig(version = 41))
    intercept[IllegalArgumentException](QrCodeConfig(mask = -1))
  }
  test("requirements grade bytes and metadata, including all correction thresholds") {
    val code = QrCode.fromText("ä🐿", QrErrorCorrection.Q, version = Some(2), mask = Some(3))
    assertEquals(code.byteCount, 6)
    val req = QrCodeRequirements(6, Some(6), Some(2), QrErrorCorrection.M, Some(3))
    assert(req.isSatisfiedBy(code))
    assert(!req.copy(minBytes = 7, maxBytes = None).isSatisfiedBy(code))
    assert(!req.copy(maxBytes = Some(5), minBytes = 0).isSatisfiedBy(code))
    assert(!req.copy(requiredVersion = Some(1)).isSatisfiedBy(code))
    assert(!req.copy(requiredMask = Some(0)).isSatisfiedBy(code))
    for level <- QrErrorCorrection.values do
      assertEquals(req.copy(minimumErrorCorrection = level).isSatisfiedBy(code), level.ordinal <= QrErrorCorrection.Q.ordinal)
    intercept[IllegalArgumentException](QrCodeRequirements(-1))
    intercept[IllegalArgumentException](QrCodeRequirements(2, Some(1)))
    intercept[IllegalArgumentException](QrCodeRequirements(requiredMask = Some(8)))
  }
  test("interaction factory and persisted interaction content round-trip") {
    val initial = QrCode.fromText("ä🐿")
    val element = CreateQrCodeInteraction("summary.qr", QrCodeRequirements(6, requiredMask = Some(initial.config.mask)), initial)
    for serializer <- List(WorkbookElementFactory.serializerRefBasedJson) do {
      val restored = serializer.deserialize(serializer.serialize(element)).asInstanceOf[CreateQrCodeInteraction]
      assertEquals(restored, element)
      assert(restored.isPassed)
      assertEquals(restored.defaultValue.modules, initial.modules)
    }
    assert(element.toStringConstructorLike.contains("requirements"))
    assert(element.toStringConstructorLike.contains("initialCode"))
    assertEquals(element.serializerInteractionContent.deserialize(element.serializerInteractionContent.serialize(initial)), initial)
    assert(!CreateQrCodeInteraction("empty").isPassed)
  }
}
