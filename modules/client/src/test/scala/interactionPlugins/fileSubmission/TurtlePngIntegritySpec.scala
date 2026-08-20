package interactionPlugins.fileSubmission

import it.evadid.homepage.workbook.legacy.interactionPlugins.fileSubmission.turtleLogic.TurtleRenderer
import it.evadid.homepage.workbook.legacy.interactionPlugins.turtleStitchPlugin.TurtleStitchWorkerFacade
import munit.FunSuite

import java.nio.charset.StandardCharsets
import java.util.Base64

class TurtlePngIntegritySpec extends FunSuite {

  private val Prefix = "data:image/png;base64,"
  private val PngSignature: Array[Byte] = Array[Byte](0x89.toByte, 0x50.toByte, 0x4e.toByte, 0x47.toByte, 0x0d.toByte, 0x0a.toByte, 0x1a.toByte, 0x0a.toByte)
  private val Crc32Table: Array[Int] = {
    // Pure Scala CRC32 so this test works in both JVM and Scala.js.
    // Polynomial: 0xEDB88320 (reflected 0x04C11DB7)
    val table = Array.ofDim[Int](256)
    var n = 0
    while (n < 256) {
      var c = n
      var k = 0
      while (k < 8) {
        c = if ((c & 1) != 0) then (0xEDB88320 ^ (c >>> 1)) else (c >>> 1)
        k += 1
      }
      table(n) = c
      n += 1
    }
    table
  }

  test("TurtleStitchWorkerFacade emptyPngDataUrl has valid PNG CRCs") {
    assertValidPngDataUrl(TurtleStitchWorkerFacade.emptyPngDataUrl)
  }

  test("TurtleRenderer transparentPngDataUrl has valid PNG CRCs") {
    assertValidPngDataUrl(TurtleRenderer.transparentPngDataUrl)
  }

  private def assertValidPngDataUrl(dataUrl: String): Unit = {
    assert(dataUrl.startsWith(Prefix), s"Expected $Prefix..., got prefix='${dataUrl.take(30)}'")

    val base64 = dataUrl.stripPrefix(Prefix)
    val bytes = Base64.getDecoder.decode(base64)
    assert(bytes.length >= PngSignature.length, "PNG bytes too short")
    assert(PngSignature.indices.forall(i => bytes(i) == PngSignature(i)), "PNG signature mismatch")

    var offset = 8
    var seenIend = false

    while (!seenIend) {
      assert(offset + 8 <= bytes.length, s"Truncated chunk header at offset=$offset, total=${bytes.length}")

      val chunkLength = readUInt32(bytes, offset)
      offset += 4
      assert(chunkLength <= Int.MaxValue, s"Chunk too large: $chunkLength")
      val len = chunkLength.toInt

      assert(offset + 4 + len + 4 <= bytes.length, s"Truncated chunk at offset=$offset, len=$len")

      val chunkTypeBytes = bytes.slice(offset, offset + 4)
      offset += 4

      val chunkData = bytes.slice(offset, offset + len)
      offset += len

      val crcRead = readUInt32(bytes, offset)
      offset += 4

      val crcCalc = crc32Chunk(chunkTypeBytes, chunkData)

      val chunkType = new String(chunkTypeBytes, StandardCharsets.US_ASCII)
      assertEquals(crcRead, crcCalc, s"PNG CRC mismatch for chunk=$chunkType")

      if (chunkType == "IEND") {
        seenIend = true
      }
    }

    assertEquals(offset, bytes.length, s"Unexpected extra bytes after IEND (offset=$offset, total=${bytes.length})")
  }

  private def readUInt32(bytes: Array[Byte], offset: Int): Long =
    ((bytes(offset) & 0xffL) << 24) |
      ((bytes(offset + 1) & 0xffL) << 16) |
      ((bytes(offset + 2) & 0xffL) << 8) |
      (bytes(offset + 3) & 0xffL)

  private def crc32Chunk(chunkTypeBytes: Array[Byte], chunkData: Array[Byte]): Long = {
    var c = 0xFFFFFFFF

    var i = 0
    while (i < chunkTypeBytes.length) {
      val b = chunkTypeBytes(i) & 0xFF
      c = Crc32Table((c ^ b) & 0xFF) ^ (c >>> 8)
      i += 1
    }

    i = 0
    while (i < chunkData.length) {
      val b = chunkData(i) & 0xFF
      c = Crc32Table((c ^ b) & 0xFF) ^ (c >>> 8)
      i += 1
    }

    // Unsigned 32-bit -> Long
    (c ^ 0xFFFFFFFFL).toLong & 0xFFFFFFFFL
  }
}

