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
case class RunLengthText(text: String = "AAABBBCCDDDDDA", achieved: List[Int] = Nil) extends CompressionExperiment derives ReadWriter {
  require(text.length <= 2000 && achieved.distinct == achieved && achieved.forall(i => i >= 0 && i < 3))
  def challenges: List[Boolean] = {
    val r = CompressionAlgorithms.runs(text); val original = CompressionAlgorithms.utf8Bytes(text); val encoded = CompressionAlgorithms.runBytes(r)
    List(text.length >= 5 && encoded <= original * 0.5, text.length >= 5 && encoded > original, r.exists(_.count >= 10))
  }
  def edit(next: String): RunLengthText = { val v = copy(text = next); v.copy(achieved = (achieved ++ v.challenges.zipWithIndex.collect { case (true,i) => i }).distinct.sorted) }
}
case class DictionaryText(text: String, step: Int = 0) extends CompressionExperiment derives ReadWriter {
  require(text.length <= 2000 && step >= 0 && step <= CompressionAlgorithms.wordCount(text))
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
case class ImageBlocks(imageResource: String, luminanceBlock: Int = 1, chromaBlock: Int = 1, separateChannels: Boolean = true, imageWidth: Int = 960, imageHeight: Int = 640, zoom: Int = 100, comparison: Option[ImageFileComparison] = None)
    extends CompressionExperiment derives ReadWriter {
  require(imageResource.nonEmpty && List(luminanceBlock, chromaBlock).forall(CompressionAlgorithms.blockSizes.contains))
  require(separateChannels || luminanceBlock == chromaBlock)
  require(imageWidth >= 1 && imageHeight >= 1 && zoom >= 100 && zoom <= 400)
  def samples(block: Int): Int = ((imageWidth + block - 1) / block) * ((imageHeight + block - 1) / block)
  def sampleBytes: Int = samples(luminanceBlock) + 2 * samples(chromaBlock)
  def estimatePercent: Int = math.round(100.0 * 16 / (luminanceBlock * chromaBlock + 15)).toInt
  def challenges: List[Boolean] = List(estimatePercent <= 20, luminanceBlock <= 4, chromaBlock <= 32)
}
case class ImageFileComparison(textBytes: Int, imageBytes: Int, note: Option[LanguageMapContentId] = None) derives ReadWriter {
  require(textBytes >= 0 && imageBytes > 0)
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

case class CharacterRun(symbol: String, count: Int) derives upickle.default.ReadWriter
case class DictionaryToken(literal: Option[String], reference: Option[Int]) derives upickle.default.ReadWriter
case class DictionaryResult(dictionary: List[String], encoded: List[DictionaryToken]) derives upickle.default.ReadWriter {
  def decoded: String = encoded.map(t => t.literal.getOrElse(dictionary(t.reference.get - 1))).mkString
  def display: String = encoded.map(t => t.literal.getOrElse(s"W${t.reference.get}")).mkString
  // Explicit teaching representation: UTF-8 words + NUL separators, bracketed decimal references.
  def modelBytes: Int = dictionary.zipWithIndex.filter((_,i) => encoded.exists(_.reference.contains(i+1))).map((w,_) => CompressionAlgorithms.utf8Bytes(w) + 1).sum +
    encoded.map(t => t.literal.map(CompressionAlgorithms.utf8Bytes).getOrElse(t.reference.get.toString.length + 1)).sum
}
object CompressionAlgorithms {
  val blockSizes: List[Int] = List(1, 2, 4, 8, 16, 32, 64, 128)
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
  def wordCount(text: String): Int = tokens(text).count(!_.forall(_.isWhitespace))
  def dictionary(text: String, steps: Int): DictionaryResult = {
    require(steps >= 0 && steps <= wordCount(text))
    var words = List.empty[String]
    var processed = 0
    val encoded = tokens(text).iterator.takeWhile(token => processed < steps || token.forall(_.isWhitespace) && processed > 0).map { token =>
      if token.forall(_.isWhitespace) then DictionaryToken(Some(token), None)
      else {
        processed += 1
        val existing = words.indexOf(token)
        if existing < 0 then { words = words :+ token; DictionaryToken(Some(token), None) }
        else DictionaryToken(None, Some(existing + 1))
      }
    }
    val output = encoded.toList
    DictionaryResult(words, output)
  }

  /** Average one row-major channel, retaining partial edge blocks. */
  def averageBlocks(channel: Vector[Double], width: Int, height: Int, block: Int): Vector[Double] = {
    require(width > 0 && height > 0 && channel.size == width * height && blockSizes.contains(block))
    val result = channel.toArray
    var top = 0
    while top < height do {
      var left = 0
      while left < width do {
        val bottom = math.min(top + block, height); val right = math.min(left + block, width)
        var total = 0.0; var y = top
        while y < bottom do { var x = left; while x < right do { total += channel(y * width + x); x += 1 }; y += 1 }
        val mean = total / ((bottom - top) * (right - left))
        y = top
        while y < bottom do { var x = left; while x < right do { result(y * width + x) = mean; x += 1 }; y += 1 }
        left += block
      }
      top += block
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


/** Small native activities keep learner values separate from their authored configuration. */
case class WrittenAnswer(text: String = "", code: Boolean = false) extends CompressionExperiment derives ReadWriter
case class EthicalReflection(selected: Option[Int] = None, reason: String = "") extends CompressionExperiment derives ReadWriter {
  require(selected.forall(i => i >= 0 && i < 3))
}
case class PhotoBudget(photoMB: Double = 4, capacityMB: Int = 2000) extends CompressionExperiment derives ReadWriter {
  require(photoMB.isFinite && photoMB > 0 && photoMB <= 1000000 && capacityMB > 0)
  def count: Long = math.floor(capacityMB / photoMB).toLong
}
case class BitComparison(password: TextBits = TextBits("M3inGeh3im!"), imageBit: Option[Int] = None,
    passwordFlips: Int = 0, imageFlips: Int = 0) extends CompressionExperiment derives ReadWriter {
  require(imageBit.forall(i => i >= 0 && i < 128 * 128) && passwordFlips >= 0 && imageFlips >= 0)
}
case class FileInspection(text: String, docxModelBytes: Int = 13460, expanded: List[String] = Nil, formattedContent: Option[LanguageMapContentId] = None) extends CompressionExperiment derives ReadWriter {
  require(docxModelBytes > 0)
}
case class TransferSimulation(files: List[(String, String)], step: Int = 0) extends CompressionExperiment derives ReadWriter {
  require(files.nonEmpty && files.size <= 100 && step >= 0 && step <= files.size * 4)
  def payload: Int = files.map(f => CompressionAlgorithms.utf8Bytes(f._2)).sum
}
case class FileOverview(scenarios: List[FileSimulation], selected: Int = 0) extends CompressionExperiment derives ReadWriter {
  require(scenarios.nonEmpty && selected >= 0 && selected < scenarios.size)
}
case class SimulationFile(name: String, bytes: Long, kind: String, metadata: List[String] = Nil, count: Int = 1,
    applied: List[String] = Nil) derives ReadWriter {
  require(name.nonEmpty && bytes >= 1 && count >= 1 && applied.distinct == applied)
  def extension: String = name.split('.').last.toLowerCase
  def problematic: Boolean = metadata.exists(CompressionSimulation.problematic)
}
case class FileSimulation(scenario: String, files: List[SimulationFile], tutorial: Boolean = false,
    tool: String = "", selected: List[Int] = Nil, steps: Int = 0, hovered: Boolean = false,
    chosenTool: Boolean = false, clickedFile: Boolean = false, error: Boolean = false) extends CompressionExperiment derives ReadWriter {
  require(files.nonEmpty && files.size <= 100 && steps >= 0)
  require(("" :: CompressionSimulation.tools).contains(tool))
  require(selected.distinct == selected && selected.forall(i => i >= 0 && i < files.size))
  def bytes: Long = files.map(_.bytes).sum
  def fileCount: Int = files.map(_.count).sum
  def hasProblematicMetadata: Boolean = files.exists(_.problematic)
  def complete: Boolean = steps > 0 && bytes <= CompressionSimulation.capacity && !hasProblematicMetadata
  def choose(next: String): FileSimulation = {
    require(CompressionSimulation.tools.contains(next))
    copy(tool = next, selected = Nil, chosenTool = true, error = false)
  }
  def click(index: Int): FileSimulation = {
    require(index >= 0 && index < files.size)
    if tool.isEmpty then this
    else if tool == "archive" then copy(selected = if selected.contains(index) then selected.filterNot(_ == index) else selected :+ index, clickedFile = true, error = false)
    else copy(files = files.updated(index, CompressionSimulation.applyTool(files(index), tool)), steps = steps + 1, clickedFile = true, error = false)
  }
  def archive: FileSimulation = {
    if selected.size < 2 then this
    else {
      val picked = selected.map(files)
      if picked.exists(_.problematic) then copy(error = true)
      else {
        val count = picked.map(_.count).sum
        val name = if count > picked.size then s"logs_${count}_dateien.rar" else s"paket_${count}_dateien.rar"
        val bytes = math.round(picked.map(f => f.bytes * (if f.kind == "text" || List("txt", "csv").contains(f.extension) then 0.22
          else if List("video", "encrypted").contains(f.kind) || List("mp4", "jpg", "jpeg").contains(f.extension) then 0.97 else 0.58)).sum + CompressionSimulation.archiveOverhead)
        copy(files = files.zipWithIndex.filterNot(f => selected.contains(f._2)).map(_._1) :+ SimulationFile(name, bytes, "archive", count = count),
          selected = Nil, steps = steps + 1, error = false)
      }
    }
  }
}
object CompressionSimulation {
  val capacity: Long = 16L * 1024 * 1024 * 1024
  val archiveOverhead: Long = 48L * 1024 * 1024
  val tools = List("convert", "lossless", "lossy", "archive")
  def problematic(value: String): Boolean = {
    val s = value.toLowerCase
    (s.startsWith("autor:") && !s.contains("unbekannt")) || List("gps", "bearbeitet von", "kamera-id", "verzeichnis", "inhalt:", "bearbeitungshistorie", "kommentar").exists(s.contains)
  }
  def applyTool(file: SimulationFile, tool: String): SimulationFile = {
    val ext = file.extension
    if file.kind == "encrypted" || file.kind == "archive" || file.applied.contains(tool) then file
    else {
      val action: Option[(Double, String)] = tool match {
        case "convert" if ext != "csv" => Some(ext match {
          case "docx" => (0.22, "txt")
          case "raw" => (0.42, "png")
          case "tiff" => (0.52, "png")
          case "psd" => (0.14, "png")
          case _ => (1.0, ext)
        })
        case "lossless" => ext match {
          case "txt" | "csv" => Some((0.34, ext))
          case "docx" => Some((0.86, ext))
          case "mp4" => Some((0.96, ext))
          case "raw" => Some((0.42, "png"))
          case "tiff" => Some((0.52, "png"))
          case _ => None
        }
        case "lossy" => ext match {
          case "mp4" => Some((0.11, ext))
          case "raw" | "tiff" => Some((0.09, "jpg"))
          case "psd" => Some((0.05, "jpg"))
          case _ => None
        }
        case _ => None
      }
      action.fold(file) { (factor, nextExt) =>
        val metadata = if tool == "convert" then Nil
          else if tool == "lossless" && List("raw", "tiff").contains(ext) && !file.metadata.exists(_.startsWith("Auflösung:")) then
            file.metadata :+ (if ext == "raw" then "Auflösung: 4032×3024" else "Auflösung: 4960×7016")
          else file.metadata
        file.copy(name = if nextExt == ext then file.name else file.name.replaceFirst("\\.[^.]+$", s".$nextExt"),
          bytes = math.max(1L, math.round(file.bytes * factor)), metadata = metadata, applied = (file.applied :+ tool).sortBy(tools.indexOf))
      }
    }
  }
}

/** A native live comparison resolves the referenced interaction in the loaded workbook. */
case class PreviousAnswer(referenceId: String) extends CompressionExperiment derives ReadWriter
