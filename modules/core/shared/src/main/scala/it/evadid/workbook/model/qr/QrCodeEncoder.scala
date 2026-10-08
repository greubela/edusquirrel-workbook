package it.evadid.workbook.model.qr

import scala.collection.mutable.ArrayBuffer

/** ISO/IEC 18004: byte segment, RS blocks over GF(256), placement, BCH information and masking. */
private[qr] object QrCodeEncoder {
  private def blockGroups(version: Int, ecc: QrErrorCorrection): Vector[(Int, Int, Int)] =
    QrCodeTables.blocks((version - 1) * 4 + ecc.ordinal).grouped(3)
      .map(g => (g(0), g(1), g(2))).toVector
  def dataCapacity(version: Int, ecc: QrErrorCorrection): Int =
    blockGroups(version, ecc).map((count, _, data) => count * data).sum

  private def multiply(a: Int, b: Int): Int = {
    var x = a
    var y = b
    var result = 0
    while y != 0 do {
      if (y & 1) != 0 then result ^= x
      y >>>= 1
      x <<= 1
      if (x & 0x100) != 0 then x ^= 0x11d
    }
    result
  }
  private[qr] def remainder(data: Vector[Int], degree: Int): Vector[Int] = {
    var generator = Vector(1)
    var root = 1
    for _ <- 0 until degree do {
      val next = Array.fill(generator.size + 1)(0)
      generator.indices.foreach(i => { next(i) ^= generator(i); next(i + 1) ^= multiply(generator(i), root) })
      generator = next.toVector
      root = multiply(root, 2)
    }
    val result = Array.fill(degree)(0)
    data.foreach { byte =>
      val factor = byte ^ result(0)
      for i <- 0 until degree - 1 do result(i) = result(i + 1)
      result(degree - 1) = 0
      for i <- 0 until degree do result(i) ^= multiply(generator(i + 1), factor)
    }
    result.toVector
  }
  private[qr] def codewords(content: Array[Byte], config: QrCodeConfig): Vector[Int] = {
    val bits = ArrayBuffer.empty[Boolean]
    def append(value: Int, count: Int): Unit = (count - 1 to 0 by -1).foreach(i => bits += ((value >>> i & 1) != 0))
    if config.utf8 then { append(7, 4); append(26, 8) }
    append(4, 4)
    append(content.length, if config.version < 10 then 8 else 16)
    content.foreach(b => append(b & 255, 8))
    val capacity = dataCapacity(config.version, config.errorCorrection)
    append(0, math.min(4, capacity * 8 - bits.size))
    while bits.size % 8 != 0 do bits += false
    val data = ArrayBuffer.from(bits.grouped(8).map(_.foldLeft(0)((a, b) => a * 2 + (if b then 1 else 0))))
    var pad = 0
    while data.size < capacity do { data += (if pad % 2 == 0 then 0xec else 0x11); pad += 1 }
    var offset = 0
    val blocks = blockGroups(config.version, config.errorCorrection).flatMap { (count, total, size) =>
      (0 until count).map { _ =>
        val block = data.slice(offset, offset + size).toVector
        offset += size
        (block, remainder(block, total - size))
      }
    }
    def interleave(parts: Vector[Vector[Int]]): Vector[Int] =
      (0 until parts.map(_.size).max).flatMap(i => parts.flatMap(_.lift(i))).toVector
    interleave(blocks.map(_._1)) ++ interleave(blocks.map(_._2))
  }

  private[qr] def maskBit(mask: Int, x: Int, y: Int): Boolean = (mask match {
    case 0 => (x + y) % 2
    case 1 => y % 2
    case 2 => x % 3
    case 3 => (x + y) % 3
    case 4 => (y / 2 + x / 3) % 2
    case 5 => (x * y) % 2 + (x * y) % 3
    case 6 => ((x * y) % 2 + (x * y) % 3) % 2
    case 7 => ((x + y) % 2 + (x * y) % 3) % 2
  }) == 0

  def matrix(content: Array[Byte], config: QrCodeConfig): Vector[Vector[Boolean]] = {
    val size = config.version * 4 + 17
    val modules = Array.fill(size, size)(false)
    val function = Array.fill(size, size)(false)
    def set(x: Int, y: Int, value: Boolean): Unit = {
      if x >= 0 && y >= 0 && x < size && y < size then { modules(y)(x) = value; function(y)(x) = true }
    }
    // Timing first; finder and alignment patterns override their intersections.
    for i <- 0 until size do { set(6, i, i % 2 == 0); set(i, 6, i % 2 == 0) }
    for (cx, cy) <- Vector((3, 3), (size - 4, 3), (3, size - 4)); dy <- -4 to 4; dx <- -4 to 4 do {
      val distance = math.max(math.abs(dx), math.abs(dy))
      set(cx + dx, cy + dy, distance != 2 && distance != 4)
    }
    val centers = if config.version == 1 then Vector.empty else {
      val count = config.version / 7 + 2
      val step = if config.version == 32 then 26 else ((config.version * 4 + count * 2 + 1) / (count * 2 - 2)) * 2
      Vector(6) ++ (0 until count - 1).reverse.map(i => size - 7 - i * step)
    }
    for i <- centers.indices; j <- centers.indices
      if !((i == 0 && j == 0) || (i == 0 && j == centers.size - 1) || (i == centers.size - 1 && j == 0))
      dy <- -2 to 2; dx <- -2 to 2 do
      set(centers(i) + dx, centers(j) + dy, math.max(math.abs(dx), math.abs(dy)) != 1)

    val formatData = config.errorCorrection.formatBits * 8 + config.mask
    var formatRemainder = formatData
    for _ <- 0 until 10 do formatRemainder = (formatRemainder << 1) ^ (if (formatRemainder >>> 9) != 0 then 0x537 else 0)
    val format = ((formatData << 10) | formatRemainder) ^ 0x5412
    def formatBit(i: Int): Boolean = (format >>> i & 1) != 0
    for i <- 0 until 6 do set(8, i, formatBit(i))
    set(8, 7, formatBit(6)); set(8, 8, formatBit(7)); set(7, 8, formatBit(8))
    for i <- 9 until 15 do set(14 - i, 8, formatBit(i))
    for i <- 0 until 8 do set(size - 1 - i, 8, formatBit(i))
    for i <- 8 until 15 do set(8, size - 15 + i, formatBit(i))
    set(8, size - 8, true)
    if config.version >= 7 then {
      var remainder = config.version
      for _ <- 0 until 12 do remainder = (remainder << 1) ^ (if (remainder >>> 11) != 0 then 0x1f25 else 0)
      val bits = (config.version << 12) | remainder
      for i <- 0 until 18 do {
        val a = size - 11 + i % 3
        val b = i / 3
        set(a, b, (bits >>> i & 1) != 0); set(b, a, (bits >>> i & 1) != 0)
      }
    }
    val data = codewords(content, config)
    var bitIndex = 0
    var right = size - 1
    while right >= 1 do {
      if right == 6 then right = 5
      for vertical <- 0 until size; column <- 0 until 2 do {
        val x = right - column
        val y = if ((right + 1) & 2) == 0 then size - 1 - vertical else vertical
        if !function(y)(x) then {
          val bit = bitIndex < data.size * 8 && ((data(bitIndex / 8) >>> (7 - bitIndex % 8)) & 1) != 0
          modules(y)(x) = bit ^ maskBit(config.mask, x, y)
          bitIndex += 1
        }
      }
      right -= 2
    }
    modules.map(_.toVector).toVector
  }

  /** Four mask penalties: runs, 2x2 blocks, finder-like patterns and dark/light balance.
    * The quiet zone counts as light for finder-pattern detection at symbol edges.
    */
  private[qr] def penalty(matrix: Vector[Vector[Boolean]]): Int = {
    val size = matrix.size
    var result = 0
    def scoreLine(line: Vector[Boolean]): Unit = {
      var run = 1
      for i <- 1 until size do {
        if line(i) == line(i - 1) then run += 1 else {
          if run >= 5 then result += run - 2
          run = 1
        }
      }
      if run >= 5 then result += run - 2
      // Recognize the 1:1:3:1:1 ratio at any scale, with four light units on either side.
      val padded = Vector.fill(size)(false) ++ line ++ Vector.fill(size)(false)
      val runs = ArrayBuffer.empty[(Boolean, Int)]
      padded.foreach(b => if runs.nonEmpty && runs.last._1 == b then
        runs(runs.size - 1) = (b, runs.last._2 + 1) else runs += ((b, 1)))
      for i <- 1 until runs.size - 5 do {
        val n = runs(i)._2
        if runs(i)._1 && runs(i + 1)._2 == n && runs(i + 2)._2 == n * 3 &&
          runs(i + 3)._2 == n && runs(i + 4)._2 == n then {
          if runs(i - 1)._2 >= 4 * n && runs(i + 5)._2 >= n then result += 40
          if runs(i + 5)._2 >= 4 * n && runs(i - 1)._2 >= n then result += 40
        }
      }
    }
    matrix.foreach(scoreLine)
    matrix.transpose.foreach(scoreLine)
    for y <- 0 until size - 1; x <- 0 until size - 1
      if matrix(y)(x) == matrix(y)(x + 1) && matrix(y)(x) == matrix(y + 1)(x) && matrix(y)(x) == matrix(y + 1)(x + 1) do result += 3
    val dark = matrix.map(_.count(identity)).sum
    result + (math.abs(dark * 20 - size * size * 10) / (size * size)) * 10
  }
}
