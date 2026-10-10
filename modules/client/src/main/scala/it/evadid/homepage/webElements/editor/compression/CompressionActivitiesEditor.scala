package it.evadid.homepage.webElements.editor.compression

import com.raquo.laminar.api.L.*
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.workbook.model.compression.*
import org.scalajs.dom

/** Native Laminar controls for the activities that accompany the compression models. */
case class CompressionActivitiesEditor(answer: Var[CompressionExperiment], initial: CompressionExperiment, locked: Signal[Boolean]) extends HtmlAppElement {
  private val lockedValue = Var(false)
  import CompressionExperimentView.localized
  private def src(key: String): Signal[String] = localized("src_" + key.replace('.', '_'))
  private def action(key: String)(run: => Unit): Element = button(typ := "button", disabled <-- locked,
    text <-- src(key), onClick --> (_ => run))
  private def size(bytes: Long): String = if bytes >= 1024L * 1024 * 1024 then f"${bytes.toDouble / (1024L * 1024 * 1024)}%.2f GiB"
    else if bytes >= 1024 * 1024 then f"${bytes.toDouble / (1024 * 1024)}%.2f MiB" else s"$bytes Bytes"
  private def formattedDocument(v: FileInspection): Element = v.formattedContent match {
    case None => pre(cls := "compression-output", v.text)
    case Some(id) => div(child <-- laminarHelper.plaintextStringSignal(id).map { content =>
      foreignHtmlElement(com.raquo.laminar.DomApi.unsafeParseHtmlString(s"<div>$content</div>"))
    })
  }
  private def fileView(v: FileSimulation, editable: Boolean): Element = {
    var left = 0.0
    val blocks = v.files.zipWithIndex.map { (f,i) =>
      val width = f.bytes.toDouble / v.bytes * 1000
      val node = svg.g(svg.titleTag(s"${f.name}: ${size(f.bytes)}; ${f.metadata.mkString("; ")}"),
        svg.rect(svg.x := left.toString, svg.y := "0", svg.width := width.toString, svg.height := "120", svg.cls := s"compression-area compression-area-${i % 4}"),
        onClick --> (_ => if editable && !lockedValue.now() then answer.update(a => a.asInstanceOf[FileSimulation].click(i))))
      left += width; node
    }
    div(
      p(cls := "compression-summary", s"${size(v.bytes)} / 16 GiB · ${v.fileCount} Dateien · Schritte: ${v.steps} · ",
        span(text <-- src(if v.hasProblematicMetadata then "widget.filesystemSimulator.metadataYes" else "widget.filesystemSimulator.metadataNo"))),
      div(role := "img", aria.label <-- localized("storageMap"), svg.svg(svg.viewBox := "0 0 1000 120", svg.cls := "compression-treemap", blocks)),
      div(cls := "compression-table-wrap", table(thead(tr(th("Datei"),th("Größe"),th("Metadaten / Dateityp"))),
        tbody(v.files.zipWithIndex.map { (f,i) => tr(
          td(if editable then button(typ := "button", disabled <-- locked,
            cls := (if v.selected.contains(i) then "compression-file-selected" else ""), aria.pressed := v.selected.contains(i).toString,
            title := f.metadata.mkString("; "), f.name,
            onMouseOver --> (_ => if !lockedValue.now() && !answer.now().asInstanceOf[FileSimulation].hovered then answer.update(a => a.asInstanceOf[FileSimulation].copy(hovered = true))),
            onFocus --> (_ => if !lockedValue.now() && !answer.now().asInstanceOf[FileSimulation].hovered then answer.update(a => a.asInstanceOf[FileSimulation].copy(hovered = true))),
            onClick --> (_ => answer.update(a => a.asInstanceOf[FileSimulation].click(i)))) else span(f.name)),
          td(size(f.bytes), if f.count > 1 then span(s" (${f.count} Dateien)") else span()),
          td(cls := (if f.problematic then "compression-error" else ""), f.metadata.mkString("; "), s" [${f.kind}]")) }))),
      if v.error then p(role := "alert", cls := "compression-error", text <-- src("widget.filesystemSimulator.archiveMetadataError")) else span(),
      if v.complete then p(role := "status", cls := "compression-success", text <-- src("widget.filesystemSimulator.success").map(_.replace("{steps}", v.steps.toString))) else span())
  }
  override def getDomElement(): Element = { val content: Element = initial match {
    case v: WrittenAnswer => textArea(cls := (if v.code then "compression-written-answer compression-code" else "compression-written-answer"),
      aria.label <-- src("common.answerPlaceholder"), disabled <-- locked,
      controlled(value <-- answer.signal.map(_.asInstanceOf[WrittenAnswer].text), onInput.mapToValue --> (text => answer.update(a => a.asInstanceOf[WrittenAnswer].copy(text = text)))),
      onKeyDown --> (event => if v.code && event.key == "Tab" && !lockedValue.now() then {
        event.preventDefault(); val target = event.target.asInstanceOf[dom.HTMLTextAreaElement]
        val start = target.selectionStart; val end = target.selectionEnd
        val current = answer.now().asInstanceOf[WrittenAnswer]
        answer.set(current.copy(text = current.text.take(start) + "  " + current.text.drop(end)))
        target.setSelectionRange(start + 2, start + 2)
      }))
    case _: EthicalReflection => div(
      List("Yes", "No", "Maybe").zipWithIndex.map((key,i) => label(input(typ := "radio", nameAttr := "compression-ethics", disabled <-- locked,
        checked <-- answer.signal.map(_.asInstanceOf[EthicalReflection].selected.contains(i)),
        onChange.mapToChecked --> (selected => if selected then answer.update(a => a.asInstanceOf[EthicalReflection].copy(selected = Some(i))))), span(text <-- src(s"intro.ethics$key")))),
      child <-- answer.signal.map(_.asInstanceOf[EthicalReflection].selected).distinct.map { selected =>
        selected.fold[Element](span()) { i => label(span(text <-- src(s"intro.ethicsFollowup${List("Yes","No","Maybe")(i)}")),
          textArea(disabled <-- locked, controlled(value <-- answer.signal.map(_.asInstanceOf[EthicalReflection].reason),
            onInput.mapToValue --> (text => answer.update(a => a.asInstanceOf[EthicalReflection].copy(reason = text)))))) }
      })
    case _: BitComparison => div(
      h3(text <-- src("widget.bitflip.passwordLabel")),
      pre(text <-- answer.signal.map(a => a.asInstanceOf[BitComparison].password.bytes.map(b => String.format("%8s", Integer.toBinaryString(b)).replace(' ', '0')).mkString(" "))),
      p(text <-- answer.signal.map(_.asInstanceOf[BitComparison].password.changed)),
      action("widget.bitflip.flipButton") { answer.update { a => val v = a.asInstanceOf[BitComparison]
        val bit = List(47,12,83,31,96)(v.passwordFlips % 5) % (v.password.original.length * 8)
        v.copy(password = v.password.copy(flippedBit = Some(bit)), passwordFlips = v.passwordFlips + 1) } },
      h3(text <-- src("widget.bitflip.imageLabel")), p(text <-- localized("pixelNote")),
      canvasTag(width := "128", height := "128", cls := "compression-bit-image", aria.label <-- src("widget.bitflip.imageLabel"),
        onMountCallback(ctx => { ctx.thisNode.ref.width = 128; ctx.thisNode.ref.height = 128
          answer.signal.foreach { a =>
          val v = a.asInstanceOf[BitComparison]; val c = ctx.thisNode.ref.getContext("2d").asInstanceOf[dom.CanvasRenderingContext2D]
          val data = c.createImageData(128,128)
          for i <- 0 until 16384 do {
            val d = math.hypot(i % 128 - 64, i / 128 - 64); val on = (d >= 48 && d <= 56) != v.imageBit.contains(i)
            val gray = if on then 0 else 255
            data.data(i*4) = gray;data.data(i*4+1)=gray;data.data(i*4+2)=gray;data.data(i*4+3)=255
          }; c.putImageData(data,0,0)
        }(using ctx.owner) })),
      p(text <-- answer.signal.map(a => a.asInstanceOf[BitComparison].imageBit.fold("0 Bits geändert")(i => s"1 Bit geändert: $i"))),
      action("widget.bitflip.flipButton") { answer.update { a => val v = a.asInstanceOf[BitComparison]
        v.copy(imageBit = Some(List(8192,1337,12000,2048,10000)(v.imageFlips % 5)), imageFlips = v.imageFlips + 1) } })
    case v: FileInspection => div(
      p(text <-- localized("inspectorNote").map(_.replace("{size}", CompressionAlgorithms.utf8Bytes(v.text).toString).replace("{docxSize}", v.docxModelBytes.toString))),
      div(cls := "compression-controls", div(h3("bericht.txt"),pre(cls := "compression-output",v.text)),
        div(h3("bericht.docx"), formattedDocument(v))),
      List("[Content_Types].xml" -> "contentTypesDesc", "_rels/.rels" -> "relsDesc", "docProps/core.xml" -> "coreDesc",
        "docProps/app.xml" -> "appDesc", "word/document.xml" -> "documentDesc", "word/styles.xml" -> "stylesDesc").map { (file,key) =>
        detailsTag(onMountCallback(ctx => answer.signal.foreach(v => ctx.thisNode.ref.asInstanceOf[scala.scalajs.js.Dynamic].open = v.asInstanceOf[FileInspection].expanded.contains(file))(using ctx.owner)), summaryTag(file), p(text <-- src(s"widget.fileInspector.$key")),
          onToggle --> (event => { val opened = event.target.asInstanceOf[scala.scalajs.js.Dynamic].open.asInstanceOf[Boolean]
            if !lockedValue.now() then answer.update { a => val v = a.asInstanceOf[FileInspection]
              v.copy(expanded = if opened then (v.expanded :+ file).distinct else v.expanded.filterNot(_ == file)) } }))
      })
    case v: TransferSimulation => div(
      p(text <-- localized("transferNote")),
      p(s"Einzelversand: ${v.files.size} Übertragungen, ${v.payload} Bytes. Archiv: 1 Übertragung, ${v.payload + 320} Bytes."),
      child <-- answer.signal.map { a => val state = a.asInstanceOf[TransferSimulation]
        div(state.files.zipWithIndex.map((file,i) => p(file._1, " · ",
          if state.step >= (i+1)*4 then "Fertig" else if state.step < i*4 then "Bereit" else List("Speicher reservieren","Datei öffnen","Daten kopieren","Datei schließen")(state.step % 4))),
          p("beweise.zip · ", if state.step >= 20 then "Fertig" else if state.step == 0 then "Bereit" else List("Speicher reservieren","Datei öffnen","Daten kopieren","Datei schließen")(math.min(3,state.step/5)))) },
      action("widget.zipArchive.simulateButton") { answer.update(a => a.asInstanceOf[TransferSimulation].copy(step = 0)); running.set(true) },
      button(typ := "button", disabled <-- locked, text <-- localized("nextStep"), onClick --> (_ => transferStep())),
      onMountCallback(ctx => EventStream.periodic(250).foreach(_ => if running.now() && !lockedValue.now() then transferStep())(using ctx.owner)),
      v.files.map((file,content) => detailsTag(summaryTag(file),pre(cls := "compression-output",content))),
      (0 until 4).map(i => detailsTag(summaryTag(text <-- src(s"widget.zipArchive.faq.$i.q")),
        p(text <-- src(s"widget.zipArchive.faq.$i.a").map(_.replace("{sizeHint}",s"${v.payload} / ${v.payload+320} Bytes").replace("{overhead}","Verwaltungsaufwand"))))))
    case _: FileSimulation => div(p(text <-- localized("modelAssumptions")), p(text <-- localized("binaryUnits")),
      div(cls := "compression-controls", CompressionSimulation.tools.map { tool => button(typ := "button", disabled <-- locked,
        text <-- src(s"widget.filesystemSimulator.tools.$tool.label"),
        aria.pressed <-- answer.signal.map(_.asInstanceOf[FileSimulation].tool == tool).map(_.toString),
        onClick --> (_ => answer.update(a => a.asInstanceOf[FileSimulation].choose(tool)))) }),
      p(text <-- answer.signal.flatMapSwitch(a => { val v=a.asInstanceOf[FileSimulation];
        src(if v.tool.isEmpty then "widget.filesystemSimulator.noToolHint" else s"widget.filesystemSimulator.tools.${v.tool}.hint") })),
      if initial.asInstanceOf[FileSimulation].tutorial then div(h3(text <-- src("widget.filesystemSimulator.tutorialTitle")),
        child <-- answer.signal.map { a => val v=a.asInstanceOf[FileSimulation]
          ul(List("hover" -> v.hovered,"tool" -> v.chosenTool,"click" -> v.clickedFile,"status" -> v.complete).map((key,done) =>
            li(if done then "✓ " else "○ ",text <-- src(s"widget.filesystemSimulator.tutorialSteps.$key")))) }) else span(),
      button(typ := "button", text <-- src("widget.filesystemSimulator.archiveCreate"),
        disabled <-- locked.combineWith(answer.signal).map((l,a) => l || a.asInstanceOf[FileSimulation].selected.size < 2),
        onClick --> (_ => answer.update(a => a.asInstanceOf[FileSimulation].archive))),
      child <-- answer.signal.map(a => fileView(a.asInstanceOf[FileSimulation], true)))
    case v: FileOverview => div(p(text <-- localized("binaryUnits")),
      label(span(text <-- localized("storageScenario")), select(disabled <-- locked,
        value <-- answer.signal.map(_.asInstanceOf[FileOverview].selected.toString),
        v.scenarios.zipWithIndex.map((s,i) => option(value := i.toString, text <-- src(s"widget.filesystemSimulator.scenarios.${s.scenario}"))),
        onChange.mapToValue --> (value => answer.update(a => a.asInstanceOf[FileOverview].copy(selected = value.toInt))))),
      child <-- answer.signal.map(a => { val v=a.asInstanceOf[FileOverview]; fileView(v.scenarios(v.selected),false) }),
      (0 until 8).map(i => detailsTag(summaryTag(text <-- src(s"widget.filesystem.fileTypesOverview.items.$i.label")),
        p(text <-- src(s"widget.filesystem.fileTypesOverview.items.$i.text")))))
    case v: PreviousAnswer =>
      fullInfo.current.allAvailableInteractions.collectFirst { case e: it.evadid.workbook.elements.interactionElements.compression.CompressionExperimentInteraction if e.elementId == v.referenceId => e } match {
        case Some(e) =>
          import it.evadid.core.datastructures.state.StateHelper.StateBasedVar
          val state=e.interactionVariable.createBoundStateWithUpdateImportance(fullInfo.syncControl,it.evadid.workbook.interaction.sync.UpdateImportance.MAJOR).toAirstreamVar
          blockQuote(text <-- state.signal.flatMapSwitch(a => { val text=a.asInstanceOf[WrittenAnswer].text; if text.isEmpty then localized("comparisonEmpty") else Val(text) }))
        case None => p(text <-- localized("comparisonEmpty"))
      }
    case _ => span()
  }; div(onMountCallback(ctx => locked.foreach(lockedValue.set)(using ctx.owner)), content) }
  def stop(): Unit = running.set(false)
  private val running = Var(false)
  private def transferStep(): Unit = answer.update { a => val v=a.asInstanceOf[TransferSimulation]
    val next=math.min(v.files.size*4,v.step+1);if next == v.files.size*4 then running.set(false)
    v.copy(step=next)
  }
}
