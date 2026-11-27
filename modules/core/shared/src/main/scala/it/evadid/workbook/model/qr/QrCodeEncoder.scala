package it.evadid.workbook.model.qr

import scala.annotation.tailrec

/** ISO/IEC 18004: byte segment, RS blocks over GF(256), placement, BCH information and masking. */
private[qr] object QrCodeEncoder {
  private def blockGroups(version: Int, ecc: QrErrorCorrection): Vector[(Int, Int, Int)] =
    QrCodeTables.blocks((version - 1) * 4 + ecc.ordinal).grouped(3)
      .map(g => (g(0), g(1), g(2))).toVector
  def dataCapacity(version: Int, ecc: QrErrorCorrection): Int =
    blockGroups(version, ecc).map((count, _, data) => count * data).sum

  /** Each RS block's total codeword count, data codeword count and the offset of its data in the data sequence. */
  private def blockLayout(version: Int, ecc: QrErrorCorrection): Vector[(Int, Int, Int)] = {
    val blocks = blockGroups(version, ecc).flatMap((count, total, data) => Vector.fill(count)((total, data)))
    blocks.zip(blocks.scanLeft(0)(_ + _._2)).map { case ((total, data), offset) => (total, data, offset) }
  }

  /** The first entry of every part, then every second entry, and so on. */
  private def interleave(parts: Vector[Vector[Int]]): Vector[Int] =
    (0 until parts.map(_.size).max).flatMap(i => parts.flatMap(_.lift(i))).toVector

  /** Carry-less multiplication in GF(256) modulo x^8 + x^4 + x^3 + x^2 + 1. */
  @tailrec
  private def multiply(a: Int, b: Int, product: Int = 0): Int =
    if b == 0 then product
    else {
      val shifted = a << 1
      multiply(if (shifted & 0x100) != 0 then shifted ^ 0x11d else shifted, b >>> 1, if (b & 1) != 0 then product ^ a else product)
    }

  private[qr] def remainder(data: Vector[Int], degree: Int): Vector[Int] = {
    // Generator polynomial (x - 2^0)(x - 2^1)...(x - 2^(degree - 1)), highest coefficient first.
    val (generator, _) = (0 until degree).foldLeft((Vector(1), 1)) { case ((polynomial, root), _) =>
      val next = (0 to polynomial.size).map { i =>
        polynomial.lift(i).getOrElse(0) ^ (if i > 0 then multiply(polynomial(i - 1), root) else 0)
      }.toVector
      (next, multiply(root, 2))
    }
    data.foldLeft(Vector.fill(degree)(0)) { (result, byte) =>
      val factor = byte ^ result(0)
      (result.drop(1) :+ 0).zipWithIndex.map((value, i) => value ^ multiply(generator(i + 1), factor))
    }
  }

  private[qr] def codewords(content: Array[Byte], config: QrCodeConfig): Vector[Int] = {
    def bits(value: Int, count: Int): Vector[Boolean] = (count - 1 to 0 by -1).map(i => (value >>> i & 1) != 0).toVector
    val segment = (if config.utf8 then bits(7, 4) ++ bits(26, 8) else Vector.empty) ++ bits(4, 4) ++
      bits(content.length, if config.version < 10 then 8 else 16) ++ content.toVector.flatMap(b => bits(b & 255, 8))
    val capacity = dataCapacity(config.version, config.errorCorrection)
    val terminated = segment ++ bits(0, math.min(4, capacity * 8 - segment.size))
    val byteAligned = terminated ++ Vector.fill((8 - terminated.size % 8) % 8)(false)
    val bytes = byteAligned.grouped(8).map(_.foldLeft(0)((a, b) => a * 2 + (if b then 1 else 0))).toVector
    val data = bytes ++ Vector.tabulate(math.max(0, capacity - bytes.size))(pad => if pad % 2 == 0 then 0xec else 0x11)
    val blocks = blockLayout(config.version, config.errorCorrection).map { (total, size, offset) =>
      val block = data.slice(offset, offset + size)
      (block, remainder(block, total - size))
    }
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

  def matrix(content: Array[Byte], config: QrCodeConfig): Vector[Vector[Boolean]] = symbol(content, config).modules

  def symbol(content: Array[Byte], config: QrCodeConfig): QrCodeSymbol = {
    val size = config.version * 4 + 17
    val modules = Array.fill(size, size)(false)
    val function = Array.fill(size, size)(false)
    val regions = Array.fill(size, size)(QrCodeRegion.Remainder)
    var activeRegion = QrCodeRegion.Timing
    def set(x: Int, y: Int, value: Boolean): Unit = {
      if x >= 0 && y >= 0 && x < size && y < size then { modules(y)(x) = value; function(y)(x) = true; regions(y)(x) = activeRegion }
    }
    // Timing first; finder and alignment patterns override their intersections.
    for i <- 0 until size do { set(6, i, i % 2 == 0); set(i, 6, i % 2 == 0) }
    for (cx, cy) <- Vector((3, 3), (size - 4, 3), (3, size - 4)); dy <- -4 to 4; dx <- -4 to 4 do {
      val distance = math.max(math.abs(dx), math.abs(dy))
      activeRegion = if distance == 4 then QrCodeRegion.Separator else QrCodeRegion.Finder
      set(cx + dx, cy + dy, distance != 2 && distance != 4)
    }
    val centers = if config.version == 1 then Vector.empty else {
      val count = config.version / 7 + 2
      val step = if config.version == 32 then 26 else ((config.version * 4 + count * 2 + 1) / (count * 2 - 2)) * 2
      Vector(6) ++ (0 until count - 1).reverse.map(i => size - 7 - i * step)
    }
    activeRegion = QrCodeRegion.Alignment
    for i <- centers.indices; j <- centers.indices
      if !((i == 0 && j == 0) || (i == 0 && j == centers.size - 1) || (i == centers.size - 1 && j == 0))
      dy <- -2 to 2; dx <- -2 to 2 do
      set(centers(i) + dx, centers(j) + dy, math.max(math.abs(dx), math.abs(dy)) != 1)

    activeRegion = QrCodeRegion.Format
    val formatData = config.errorCorrection.formatBits * 8 + config.mask
    val formatRemainder = (0 until 10).foldLeft(formatData)((r, _) => (r << 1) ^ (if (r >>> 9) != 0 then 0x537 else 0))
    val format = ((formatData << 10) | formatRemainder) ^ 0x5412
    def formatBit(i: Int): Boolean = (format >>> i & 1) != 0
    for i <- 0 until 6 do set(8, i, formatBit(i))
    set(8, 7, formatBit(6)); set(8, 8, formatBit(7)); set(7, 8, formatBit(8))
    for i <- 9 until 15 do set(14 - i, 8, formatBit(i))
    for i <- 0 until 8 do set(size - 1 - i, 8, formatBit(i))
    for i <- 8 until 15 do set(8, size - 15 + i, formatBit(i))
    activeRegion = QrCodeRegion.FixedDark
    set(8, size - 8, true)
    if config.version >= 7 then {
      activeRegion = QrCodeRegion.Version
      val remainder = (0 until 12).foldLeft(config.version)((r, _) => (r << 1) ^ (if (r >>> 11) != 0 then 0x1f25 else 0))
      val bits = (config.version << 12) | remainder
      for i <- 0 until 18 do {
        val a = size - 11 + i % 3
        val b = i / 3
        set(a, b, (bits >>> i & 1) != 0); set(b, a, (bits >>> i & 1) != 0)
      }
    }
    val data = codewords(content, config)
    // Recover original data-byte offsets after interleaving to identify header bits.
    // A byte can straddle the header/payload boundary, so classification is per bit.
    val dataOffsets = interleave(blockLayout(config.version, config.errorCorrection).map((_, length, offset) =>
      (offset until offset + length).toVector))
    val headerBits = (if config.utf8 then 12 else 0) + 4 + (if config.version < 10 then 8 else 16)
    // Two-column strips from the right edge, alternately upwards and downwards; the vertical timing column is skipped.
    val stripRightColumns = (size - 1 to 8 by -2) ++ (5 to 1 by -2)
    val dataPositions = for {
      right <- stripRightColumns
      vertical <- 0 until size
      column <- 0 until 2
      x = right - column
      y = if ((right + 1) & 2) == 0 then size - 1 - vertical else vertical
      if !function(y)(x)
    } yield (x, y)
    dataPositions.zipWithIndex.foreach { case ((x, y), bitIndex) =>
      val bit = bitIndex < data.size * 8 && ((data(bitIndex / 8) >>> (7 - bitIndex % 8)) & 1) != 0
      modules(y)(x) = bit ^ maskBit(config.mask, x, y)
      regions(y)(x) = if bitIndex >= data.size * 8 then QrCodeRegion.Remainder
        else if bitIndex / 8 >= dataOffsets.size then QrCodeRegion.ErrorCorrection
        else if dataOffsets(bitIndex / 8) * 8 + bitIndex % 8 < headerBits then QrCodeRegion.Encoding
        else QrCodeRegion.Data
    }
    QrCodeSymbol(modules.map(_.toVector).toVector, regions.map(_.toVector).toVector)
  }

  /** Four mask penalties: runs, 2x2 blocks, finder-like patterns and dark/light balance.
    * The quiet zone counts as light for finder-pattern detection at symbol edges.
    */
  private[qr] def penalty(matrix: Vector[Vector[Boolean]]): Int = {
    val size = matrix.size
    val uniformBlocks = (0 until size - 1).map(y => (0 until size - 1).count { x =>
      val module = matrix(y)(x)
      module == matrix(y)(x + 1) && module == matrix(y + 1)(x) && module == matrix(y + 1)(x + 1)
    }).sum
    val dark = matrix.map(_.count(identity)).sum
    (matrix ++ matrix.transpose).map(linePenalty).sum + uniformBlocks * 3 + (math.abs(dark * 20 - size * size * 10) / (size * size)) * 10
  }

  /** Runs of five or more equal modules, and 1:1:3:1:1 finder-like patterns at any scale, with four light units on either side. */
  private def linePenalty(line: Vector[Boolean]): Int = {
    val longRuns = runLengths(line).collect { case (_, length) if length >= 5 => length - 2 }.sum
    val runs = runLengths(Vector.fill(line.size)(false) ++ line ++ Vector.fill(line.size)(false))
    val finderLike = (1 until runs.size - 5).map { i =>
      val (dark, n) = runs(i)
      if dark && runs(i + 1)._2 == n && runs(i + 2)._2 == n * 3 && runs(i + 3)._2 == n && runs(i + 4)._2 == n then
        (if runs(i - 1)._2 >= 4 * n && runs(i + 5)._2 >= n then 40 else 0) +
          (if runs(i + 5)._2 >= 4 * n && runs(i - 1)._2 >= n then 40 else 0)
      else 0
    }.sum
    longRuns + finderLike
  }

  /** Maximal runs of equal modules with their lengths. */
  private def runLengths(line: Vector[Boolean]): Vector[(Boolean, Int)] =
    line.foldLeft(Vector.empty[(Boolean, Int)]) { (runs, module) =>
      runs.lastOption match {
        case Some((value, length)) if value == module => runs.updated(runs.size - 1, (module, length + 1))
        case _ => runs :+ ((module, 1))
      }
    }
}
