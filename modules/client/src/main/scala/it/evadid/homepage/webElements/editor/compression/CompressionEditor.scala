package it.evadid.homepage.webElements.editor.compression

import com.raquo.laminar.api.L.*
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.homepage.webElements.FullscreenLifecycle
import it.evadid.workbook.model.compression.*

/** Edits the renderer's bound value; invalid numeric drafts never replace saved settings. */
case class CompressionEditor(answer: Var[CompressionExperiment], initial: CompressionExperiment, locked: Signal[Boolean])
    extends HtmlAppElement with FullscreenLifecycle {
  import CompressionExperimentView.{localized, summary}
  private val resetDrafts = new EventBus[Unit]
  private def numberControl(key: String, get: CompressionExperiment => Double, set: (CompressionExperiment, Double) => CompressionExperiment,
      min: Double, max: Double, integer: Boolean = true): Element = {
    def show(value: Double): String = if integer then value.toLong.toString else value.toString
    val draft = Var(show(get(answer.now())))
    val invalid = Var(false)
    var committed = get(answer.now())
    label(cls := "compression-control", span(text <-- localized(key)),
      input(typ := "number", minAttr := min.toString, maxAttr := max.toString, stepAttr := (if integer then "1" else "any"),
        disabled <-- locked, aria.invalid <-- invalid.signal.map(_.toString),
        onMountCallback(ctx => answer.signal.changes.foreach(value => {
          val next = get(value)
          if next != committed then { committed = next; draft.set(show(next)); invalid.set(false) }
        })(using ctx.owner)),
        onMountCallback(ctx => resetDrafts.events.foreach(_ => {
          committed = get(answer.now()); draft.set(show(committed)); invalid.set(false)
        })(using ctx.owner)),
        value <-- draft.signal, onInput.mapToValue --> (value => {
          draft.set(value)
          val valid = value.toDoubleOption.filter(v => v.isFinite && v >= min && v <= max && (!integer || v == math.floor(v)))
          invalid.set(valid.isEmpty)
          valid.foreach(v => answer.update(a => set(a, v)))
        })),
      child <-- invalid.signal.map(bad => if bad then span(cls := "compression-error", text <-- localized("invalidNumber")) else span()))
  }
  private def textControl(get: CompressionExperiment => String, set: (CompressionExperiment, String) => CompressionExperiment): Element =
    label(cls := "compression-control", span(text <-- localized("experimentText")),
      textArea(maxLength := 2000, disabled <-- locked,
        controlled(value <-- answer.signal.map(get), onInput.mapToValue --> (value => answer.update(a => set(a, value))))))
  private def blockControl(key: String, get: CompressionExperiment => Int, set: (CompressionExperiment, Int) => CompressionExperiment): Element =
    label(cls := "compression-control", span(text <-- localized(key)), select(disabled <-- locked,
      value <-- answer.signal.map(a => get(a).toString),
      CompressionAlgorithms.blockSizes.map(size => option(value := size.toString, s"$size × $size")),
      onChange.mapToValue --> (v => answer.update(a => set(a, v.toInt)))))

  private def controls: Element = initial match {
    case _: VideoBudget => div(cls := "compression-controls",
      numberControl("bitrate", _.asInstanceOf[VideoBudget].bitrateMbps, (a, v) => a.asInstanceOf[VideoBudget].copy(bitrateMbps = v), 0.01, 10000, false),
      numberControl("duration", _.asInstanceOf[VideoBudget].durationSeconds, (a, v) => a.asInstanceOf[VideoBudget].copy(durationSeconds = v.toInt), 1, 86400),
      numberControl("copies", _.asInstanceOf[VideoBudget].copies, (a, v) => a.asInstanceOf[VideoBudget].copy(copies = v.toInt), 1, 10000),
      numberControl("capacity", _.asInstanceOf[VideoBudget].capacityMB, (a, v) => a.asInstanceOf[VideoBudget].copy(capacityMB = v.toInt), 1, 1000000))
    case _: RunLengthText => div(
      textControl(_.asInstanceOf[RunLengthText].text, (a, text) => RunLengthText(text)),
      pre(cls := "compression-output", text <-- answer.signal.map(a => CompressionAlgorithms.runs(a.asInstanceOf[RunLengthText].text)
        .map(r => s"(${r.symbol},${r.count})").mkString(" "))),
      p(text <-- localized("rleCostNote")),
      h3(text <-- localized("decodedText")), pre(cls := "compression-output", text <-- answer.signal.map(a =>
        CompressionAlgorithms.decodeRuns(CompressionAlgorithms.runs(a.asInstanceOf[RunLengthText].text)))))
    case _: DictionaryText => div(
      textControl(_.asInstanceOf[DictionaryText].text, (a, text) => DictionaryText(text)),
      div(cls := "compression-controls",
        button(typ := "button", text <-- localized("previousStep"), disabled <-- locked.combineWith(answer.signal).map((l, a) => l || a.asInstanceOf[DictionaryText].step == 0),
          onClick --> (_ => answer.update(a => { val v = a.asInstanceOf[DictionaryText]; v.copy(step = v.step - 1) }))),
        button(typ := "button", text <-- localized("nextStep"), disabled <-- locked.combineWith(answer.signal).map((l, a) => { val v = a.asInstanceOf[DictionaryText]; l || v.step == CompressionAlgorithms.tokens(v.text).size }),
          onClick --> (_ => answer.update(a => { val v = a.asInstanceOf[DictionaryText]; v.copy(step = v.step + 1) }))),
        button(typ := "button", text <-- localized("allSteps"), disabled <-- locked,
          onClick --> (_ => answer.update(a => { val v = a.asInstanceOf[DictionaryText]; v.copy(step = CompressionAlgorithms.tokens(v.text).size) })))),
      child <-- answer.signal.map { a =>
        val v = a.asInstanceOf[DictionaryText]
        val result = CompressionAlgorithms.dictionary(v.text, v.step)
        div(div(cls := "compression-table-wrap", table(thead(tr(th(text <-- localized("dictionaryIndex")), th(text <-- localized("dictionaryWord")))),
          tbody(result.dictionary.zipWithIndex.map((word, i) => tr(td((i + 1).toString), td(word)))))),
          pre(cls := "compression-output", result.display), h3(text <-- localized("decodedText")), pre(cls := "compression-output", result.decoded),
          p(text <-- localized("dictionaryCostNote").map(_.replace("{size}", result.modelBytes.toString))))
      })
    case v: TextBits => div(p(text <-- localized("bitsNote")),
      div(cls := "compression-bit-grid",
        v.original.zipWithIndex.map { (character, index) => div(cls := "compression-byte",
          code(character.toString), (0 until 8).map { bit =>
            val position = index * 8 + bit
            button(typ := "button", cls.toggle("compression-bit-selected") <-- answer.signal.map(_.asInstanceOf[TextBits].flippedBit.contains(position)), disabled <-- locked,
              aria.label := s"${index + 1}: ${bit + 1}", aria.pressed <-- answer.signal.map(_.asInstanceOf[TextBits].flippedBit.contains(position).toString),
              text <-- answer.signal.map(a => ((a.asInstanceOf[TextBits].bytes(index) >> (7 - bit)) & 1).toString),
              onClick --> (_ => answer.update(a => { val current = a.asInstanceOf[TextBits];
                current.copy(flippedBit = if current.flippedBit.contains(position) then None else Some(position)) })))
          })
        }
      ))
    case v: ImageBlocks => div(
      div(cls := "compression-controls", blockControl("brightnessBlocks", _.asInstanceOf[ImageBlocks].luminanceBlock, (a, b) => {
        val v = a.asInstanceOf[ImageBlocks]; v.copy(luminanceBlock = b, chromaBlock = if v.separateChannels then v.chromaBlock else b)
      }), if v.separateChannels then blockControl("colorBlocks", _.asInstanceOf[ImageBlocks].chromaBlock, (a, b) => a.asInstanceOf[ImageBlocks].copy(chromaBlock = b)) else span()),
      CompressionImageView(v.imageResource, answer.signal.map(_.asInstanceOf[ImageBlocks])).getDomElement(),
      p(text <-- localized("imageCostNote")))
    case _: ArchiveBudget => div(cls := "compression-controls",
      numberControl("fileCount", _.asInstanceOf[ArchiveBudget].fileCount, (a, v) => a.asInstanceOf[ArchiveBudget].copy(fileCount = v.toInt), 1, 10000),
      numberControl("fileSize", _.asInstanceOf[ArchiveBudget].bytesPerFile, (a, v) => a.asInstanceOf[ArchiveBudget].copy(bytesPerFile = v.toInt), 1, 10000000),
      numberControl("archiveOverhead", _.asInstanceOf[ArchiveBudget].archiveOverhead, (a, v) => a.asInstanceOf[ArchiveBudget].copy(archiveOverhead = v.toInt), 0, 10000000),
      numberControl("openTime", _.asInstanceOf[ArchiveBudget].openMilliseconds, (a, v) => a.asInstanceOf[ArchiveBudget].copy(openMilliseconds = v.toInt), 0, 10000),
      numberControl("transferRate", _.asInstanceOf[ArchiveBudget].bytesPerSecond, (a, v) => a.asInstanceOf[ArchiveBudget].copy(bytesPerSecond = v.toInt), 1, 1000000000),
      p(text <-- localized("archiveCostNote")))
    case v: StorageStudy => div(
      label(span(text <-- localized("storageScenario")), select(disabled <-- locked,
        value <-- answer.signal.map(_.asInstanceOf[StorageStudy].selected.toString),
        v.packages.zipWithIndex.map((p, i) => option(value := i.toString, text <-- laminarHelper.plaintextStringSignal(p.label))),
        onChange.mapToValue --> (value => answer.update(a => a.asInstanceOf[StorageStudy].copy(selected = value.toInt))))),
      child <-- answer.signal.map { a =>
        val v = a.asInstanceOf[StorageStudy]
        val total = v.current.megabytes.toDouble
        var left = 0.0
        val blocks = v.current.files.zipWithIndex.map { (f, i) =>
          val width = f.megabytes / total * 1000
          val block = svg.g(svg.titleTag(s"${f.name}: ${f.megabytes} MB"),
            svg.rect(svg.x := left.toString, svg.y := "0", svg.width := width.toString, svg.height := "120", svg.cls := s"compression-area compression-area-${i % 4}"))
          left += width
          block
        }
        div(div(role := "img", aria.label <-- localized("storageMap"), svg.svg(svg.viewBox := "0 0 1000 120", svg.cls := "compression-treemap", blocks)),
          div(cls := "compression-table-wrap", table(thead(tr(List("storageName", "storageSize", "storageInformation").map(k => th(text <-- localized(k))))),
            tbody(v.current.files.map(f => tr(td(f.name), td(f.megabytes.toString), td(text <-- laminarHelper.plaintextStringSignal(f.information))))))))
      })
  }
  override def getDomElement(): Element = div(cls := "compression-editor",
    controls,
    p(cls := "compression-summary", role := "status", aria.live := "polite", text <-- answer.signal.flatMapSwitch(summary)),
    button(typ := "button", disabled <-- locked, text <-- localized("resetExperiment"), onClick --> (_ => {
      answer.set(initial); resetDrafts.writer.onNext(())
    })))
  override def onFullscreenClose(): Unit = ()
  override def dismissOnOutsideClick: Boolean = false
}
