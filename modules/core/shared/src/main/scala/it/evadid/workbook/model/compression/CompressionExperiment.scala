package it.evadid.workbook.model.compression

import it.evadid.workbook.model.text.UnicodeText
import it.evadid.core.datastructures.language.LanguageMapContentId
import upickle.default.*

/** Persisted experiment settings; computed results and browser resources are not learner state. */
sealed trait CompressionExperiment derives ReadWriter
case class VideoBudget(bitrateMbps: Double = 5, durationSeconds: Int = 300, copies: Int = 1, capacityMB: Int = 2000)
    extends CompressionExperiment derives ReadWriter {
  require(bitrateMbps.isFinite && bitrateMbps > 0 && bitrateMbps <= 10000)
  require(durationSeconds >= 1 && durationSeconds <= 86400 && copies >= 1 && copies <= 10000)
  require(capacityMB >= 1 && capacityMB <= 1000000)
  def megabytes: Double = bitrateMbps * durationSeconds * copies / 8
  def fits: Boolean = megabytes <= capacityMB
}
case class RunLengthText(text: String = "AAABBBCCDDDDDA") extends CompressionExperiment derives ReadWriter {
  require(text.length <= 2000)
}
case class DictionaryText(text: String, step: Int = 0) extends CompressionExperiment derives ReadWriter {
  require(text.length <= 2000 && step >= 0 && step <= CompressionAlgorithms.tokens(text).size)
}
case class TextBits(original: String = "Passwort!", flippedBit: Option[Int] = None)
    extends CompressionExperiment derives ReadWriter {
  require(original.nonEmpty && original.length <= 32 && original.forall(c => c >= ' ' && c <= '~'))
  require(flippedBit.forall(i => i >= 0 && i < original.length * 8))
  def bytes: List[Int] = original.zipWithIndex.map { (c, index) =>
    c.toInt ^ flippedBit.filter(_ / 8 == index).map(i => 1 << (7 - i % 8)).getOrElse(0)
  }.toList
  def changed: String = bytes.map(_.toChar).mkString
}
case class ImageBlocks(imageResource: String, luminanceBlock: Int = 1, chromaBlock: Int = 1, separateChannels: Boolean = true)
    extends CompressionExperiment derives ReadWriter {
  require(imageResource.nonEmpty && List(luminanceBlock, chromaBlock).forall(CompressionAlgorithms.blockSizes.contains))
  require(separateChannels || luminanceBlock == chromaBlock)
  // 64 x 64 pixels, one 8-bit Y sample and two 8-bit chroma samples per block.
  def sampleBytes: Int = 4096 / (luminanceBlock * luminanceBlock) + 8192 / (chromaBlock * chromaBlock)
}
case class ArchiveBudget(fileCount: Int = 10, bytesPerFile: Int = 1024, archiveOverhead: Int = 320,
    openMilliseconds: Int = 100, bytesPerSecond: Int = 1024)
    extends CompressionExperiment derives ReadWriter {
  require(fileCount >= 1 && fileCount <= 10000 && bytesPerFile >= 1 && bytesPerFile <= 10000000)
  require(archiveOverhead >= 0 && archiveOverhead <= 10000000 && openMilliseconds >= 0 && openMilliseconds <= 10000)
  require(bytesPerSecond >= 1 && bytesPerSecond <= 1000000000)
  def payloadBytes: Long = fileCount.toLong * bytesPerFile
  def archiveBytes: Long = payloadBytes + archiveOverhead
  def individualSeconds: Double = payloadBytes.toDouble / bytesPerSecond + fileCount * openMilliseconds / 1000.0
  def archiveSeconds: Double = archiveBytes.toDouble / bytesPerSecond + openMilliseconds / 1000.0
}
case class StorageFile(name: String, megabytes: Int, information: LanguageMapContentId) derives ReadWriter {
  require(name.nonEmpty && megabytes >= 1)
}
case class StoragePackage(label: LanguageMapContentId, files: List[StorageFile]) derives ReadWriter {
  require(files.nonEmpty && files.size <= 100)
  def megabytes: Long = files.map(_.megabytes.toLong).sum
}
case class StorageStudy(packages: List[StoragePackage], selected: Int = 0, capacityMB: Int = 32000)
    extends CompressionExperiment derives ReadWriter {
  require(packages.nonEmpty && packages.size <= 10 && selected >= 0 && selected < packages.size && capacityMB > 0)
  def current: StoragePackage = packages(selected)
}

case class CharacterRun(symbol: String, count: Int)
case class DictionaryToken(literal: Option[String], reference: Option[Int])
case class DictionaryResult(dictionary: List[String], encoded: List[DictionaryToken]) {
  def decoded: String = encoded.map(t => t.literal.getOrElse(dictionary(t.reference.get - 1))).mkString
  def display: String = encoded.map(t => t.literal.getOrElse(s"[${t.reference.get}]")).mkString
  // Explicit teaching representation: UTF-8 words + NUL separators, bracketed decimal references.
  def modelBytes: Int = dictionary.map(w => CompressionAlgorithms.utf8Bytes(w) + 1).sum +
    encoded.map(t => t.literal.map(CompressionAlgorithms.utf8Bytes).getOrElse(t.reference.get.toString.length + 2)).sum
}
object CompressionAlgorithms {
  val blockSizes: List[Int] = List(1, 2, 4, 8, 16, 32, 64)
  def utf8Bytes(text: String): Int = text.getBytes("UTF-8").length
  def runs(text: String): List[CharacterRun] = UnicodeText.characters(text).foldLeft(List.empty[CharacterRun]) { (reversed, c) =>
    reversed match {
      case head :: tail if head.symbol == c.glyph => head.copy(count = head.count + 1) :: tail
      case _ => CharacterRun(c.glyph, 1) :: reversed
    }
  }.reverse
  def decodeRuns(runs: List[CharacterRun]): String = runs.map(r => r.symbol * r.count).mkString
  // A symbol's UTF-8 bytes plus decimal count; punctuation in the displayed tuples is not counted.
  def runBytes(runs: List[CharacterRun]): Int = runs.map(r => utf8Bytes(r.symbol) + r.count.toString.length).sum
  def tokens(text: String): List[String] = "\\S+|\\s+".r.findAllIn(text).toList
  def dictionary(text: String, steps: Int): DictionaryResult = {
    require(steps >= 0 && steps <= tokens(text).size)
    var words = List.empty[String]
    val encoded = tokens(text).take(steps).map { token =>
      if token.forall(_.isWhitespace) then DictionaryToken(Some(token), None)
      else {
        val existing = words.indexOf(token)
        if existing < 0 then words = words :+ token
        DictionaryToken(None, Some(if existing < 0 then words.size else existing + 1))
      }
    }
    DictionaryResult(words, encoded)
  }

  /** Average one row-major channel, retaining partial edge blocks. */
  def averageBlocks(channel: Vector[Double], width: Int, height: Int, block: Int): Vector[Double] = {
    require(width > 0 && height > 0 && channel.size == width * height && blockSizes.contains(block))
    val result = channel.toArray
    for (top <- 0 until height by block; left <- 0 until width by block) {
      val indices = for (y <- top until math.min(top + block, height); x <- left until math.min(left + block, width)) yield y * width + x
      val mean = indices.map(channel).sum / indices.size
      indices.foreach(i => result(i) = mean)
    }
    result.toVector
  }
  def imageBlocks(rgb: Vector[(Int, Int, Int)], width: Int, height: Int, yBlock: Int, cBlock: Int): Vector[(Int, Int, Int)] = {
    val y = averageBlocks(rgb.map((r, g, b) => 0.299 * r + 0.587 * g + 0.114 * b), width, height, yBlock)
    val cb = averageBlocks(rgb.map((r, g, b) => 128 - 0.168736 * r - 0.331264 * g + 0.5 * b), width, height, cBlock)
    val cr = averageBlocks(rgb.map((r, g, b) => 128 + 0.5 * r - 0.418688 * g - 0.081312 * b), width, height, cBlock)
    def clip(value: Double): Int = math.max(0, math.min(255, math.round(value).toInt))
    rgb.indices.map(i => (clip(y(i) + 1.402 * (cr(i) - 128)),
      clip(y(i) - 0.344136 * (cb(i) - 128) - 0.714136 * (cr(i) - 128)), clip(y(i) + 1.772 * (cb(i) - 128)))).toVector
  }
}
