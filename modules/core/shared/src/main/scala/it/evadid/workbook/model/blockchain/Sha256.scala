package it.evadid.workbook.model.blockchain

import java.nio.charset.StandardCharsets

/** SHA-256 as specified in FIPS 180-4, shared by JVM and Scala.js without a crypto dependency. */
object Sha256 {
  private val constants = Array(
    0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5, 0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5,
    0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174,
    0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
    0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7, 0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967,
    0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85,
    0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3, 0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
    0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
    0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208, 0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2)
  private def rotate(x: Int, n: Int): Int = (x >>> n) | (x << (32 - n))

  def digest(bytes: Array[Byte]): Array[Byte] = {
    val paddedLength = ((bytes.length.toLong + 9 + 63) / 64) * 64
    require(paddedLength <= Int.MaxValue, "Input too large for an in-memory SHA-256 calculation")
    val padded = new Array[Byte](paddedLength.toInt)
    Array.copy(bytes, 0, padded, 0, bytes.length)
    padded(bytes.length) = 0x80.toByte
    val bitLength = bytes.length.toLong * 8
    for (i <- 0 until 8) padded(padded.length - 1 - i) = (bitLength >>> (8 * i)).toByte
    val hash = Array(0x6a09e667, 0xbb67ae85, 0x3c6ef372, 0xa54ff53a, 0x510e527f, 0x9b05688c, 0x1f83d9ab, 0x5be0cd19)
    val words = new Array[Int](64)
    for (offset <- 0 until padded.length by 64) {
      for (i <- 0 until 16) {
        val j = offset + i * 4
        words(i) = ((padded(j) & 255) << 24) | ((padded(j + 1) & 255) << 16) |
          ((padded(j + 2) & 255) << 8) | (padded(j + 3) & 255)
      }
      for (i <- 16 until 64) {
        val x = words(i - 15)
        val y = words(i - 2)
        val s0 = rotate(x, 7) ^ rotate(x, 18) ^ (x >>> 3)
        val s1 = rotate(y, 17) ^ rotate(y, 19) ^ (y >>> 10)
        words(i) = words(i - 16) + s0 + words(i - 7) + s1
      }
      var a = hash(0); var b = hash(1); var c = hash(2); var d = hash(3)
      var e = hash(4); var f = hash(5); var g = hash(6); var h = hash(7)
      for (i <- 0 until 64) {
        val s1 = rotate(e, 6) ^ rotate(e, 11) ^ rotate(e, 25)
        val choose = (e & f) ^ (~e & g)
        val t1 = h + s1 + choose + constants(i) + words(i)
        val s0 = rotate(a, 2) ^ rotate(a, 13) ^ rotate(a, 22)
        val majority = (a & b) ^ (a & c) ^ (b & c)
        val t2 = s0 + majority
        h = g; g = f; f = e; e = d + t1
        d = c; c = b; b = a; a = t1 + t2
      }
      val working = Array(a, b, c, d, e, f, g, h)
      for (i <- hash.indices) hash(i) += working(i)
    }
    (for (word <- hash; shift <- Array(24, 16, 8, 0)) yield (word >>> shift).toByte)
  }

  def hex(bytes: Array[Byte]): String = {
    val digits = "0123456789abcdef"
    digest(bytes).iterator.map(b => s"${digits((b & 255) >>> 4)}${digits(b & 15)}").mkString
  }
  /** Text is hashed as UTF-8 without trimming or Unicode normalization. */
  def text(value: String): String = hex(value.getBytes(StandardCharsets.UTF_8))
  def differingBits(first: String, second: String): Int = {
    val a = digest(first.getBytes(StandardCharsets.UTF_8))
    val b = digest(second.getBytes(StandardCharsets.UTF_8))
    a.indices.map(i => Integer.bitCount((a(i) ^ b(i)) & 255)).sum
  }
}
