package it.evadid.homepage.webElements.editor.compression

import com.raquo.laminar.api.L.*
import it.evadid.homepage.workbook.htmlRenderer.LaminarRenderHelper
import it.evadid.workbook.model.compression.*

object CompressionExperimentView {
  def localized(key: String): Signal[String] = LaminarRenderHelper.singleton.plaintextStringSignal(s"CompressionWorkbook/$key")
  def summary(value: CompressionExperiment): Signal[String] = {
    val (key, fields) = value match {
      case v: VideoBudget => ("videoSummary", Map("size" -> f"${v.megabytes}%.2f", "capacity" -> v.capacityMB.toString))
      case v: RunLengthText => ("rleSummary", Map("original" -> CompressionAlgorithms.utf8Bytes(v.text).toString,
        "encoded" -> CompressionAlgorithms.runBytes(CompressionAlgorithms.runs(v.text)).toString))
      case v: DictionaryText => ("dictionarySummary", Map("step" -> v.step.toString, "total" -> CompressionAlgorithms.tokens(v.text).size.toString))
      case v: TextBits => ("bitsSummary", Map("original" -> v.original, "changed" -> v.changed.map(c => if c.isControl then '\uFFFD' else c).mkString))
      case v: ImageBlocks => ("imageSummary", Map("y" -> v.luminanceBlock.toString, "c" -> v.chromaBlock.toString,
        "size" -> v.sampleBytes.toString))
      case v: ArchiveBudget => ("archiveSummary", Map("individual" -> f"${v.individualSeconds}%.2f", "archive" -> f"${v.archiveSeconds}%.2f",
        "payload" -> v.payloadBytes.toString, "size" -> v.archiveBytes.toString))
      case v: StorageStudy => ("storageSummary", Map("size" -> v.current.megabytes.toString, "capacity" -> v.capacityMB.toString,
        "missing" -> math.max(0L, v.current.megabytes - v.capacityMB).toString))
    }
    localized(key).map(template => fields.foldLeft(template) { case (text, (field, replacement)) => text.replace(s"{$field}", replacement) })
  }
}
