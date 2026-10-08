package it.evadid.homepage.webElements.editor.qr

import com.raquo.laminar.api.L.*
import it.evadid.homepage.webElements.{FullscreenLifecycle, HtmlAppElement}
import it.evadid.workbook.elements.interactionElements.qr.QrCodeRequirements
import it.evadid.workbook.model.qr.{QrCode, QrErrorCorrection}

/** Text edits are live. Invalid drafts stay visible but never replace the last valid saved symbol. */
case class QrCodeEditor(state: Var[QrCode], requirements: QrCodeRequirements) extends HtmlAppElement with FullscreenLifecycle {
  private val editorState = new QrCodeEditorState(state)
  import editorState.*

  private def labelText(id: String): Signal[String] = laminarHelper.plaintextStringSignal(s"basic/$id")
  private def choice(labelId: String, current: Var[Option[Int]], choices: Range): Element = label(
    span(text <-- labelText(labelId)),
    select(
      value <-- current.signal.map(_.fold("")(_.toString)),
      onChange.mapToValue --> { v => current.set(v.toIntOption); updateCode() },
      option(value := "", text <-- labelText("qrAutomatic")),
      choices.map(i => option(value := i.toString, i.toString))
    )
  )
  override def getDomElement(): Element = div(
    cls := "qr-editor",
    onMountCallback { ctx =>
      // Restored/synchronized state also updates an already constructed editor.
      state.signal.changes.foreach { code =>
        if code.text != draft.now() then { draft.set(code.text); error.set(false) }
        errorCorrection.set(code.config.errorCorrection)
      }(using ctx.owner)
    },
    h2(text <-- labelText("qrTitle")),
    div(cls := "qr-editor__controls",
      label(span(text <-- labelText("qrText")), textArea(
        rows := 5,
        value <-- draft.signal,
        onInput.mapToValue --> { v => draft.set(v); updateCode() }
      )),
      div(cls := "qr-editor__options",
        label(span(text <-- labelText("qrErrorCorrection")), select(
          value <-- errorCorrection.signal.map(_.toString),
          onChange.mapToValue --> { v => errorCorrection.set(QrErrorCorrection.valueOf(v)); updateCode() },
          QrErrorCorrection.values.toList.map(e => option(value := e.toString, e.toString))
        )),
        choice("qrVersion", versionChoice, 1 to 40),
        choice("qrMask", maskChoice, 0 to 7)
      ),
      p(cls := "qr-editor__error", role := "alert",
        child <-- error.signal.map(invalid => if invalid then
          span(text <-- labelText("qrTooLarge")) else span())
      )
    ),
    div(cls := "qr-editor__result",
      child <-- error.signal.map(invalid => if invalid then
        div(cls := "qr-editor__invalid", text <-- labelText("qrInvalidPreview"))
      else QrCodeView.preview(state.signal, requirements))
    )
  )
  override def dismissOnOutsideClick: Boolean = false
  override def onFullscreenClose(): Unit = ()
}

/** SVG geometry stays in code; colors, size and crisp rendering live in CSS. */
object QrCodeView {
  def pathData(code: QrCode): String = (for {
    y <- 0 until code.size
    x <- 0 until code.size if code.isDark(x, y)
  } yield s"M${x + 4},${y + 4}h1v1h-1z").mkString

  def symbol(code: QrCode): Element = svg.svg(
    svg.cls := "qr-symbol",
    svg.viewBox := s"0 0 ${code.size + 8} ${code.size + 8}",
    svg.svgAttr("role", com.raquo.laminar.codecs.StringAsIsCodec, None) := "img",
    svg.titleTag(s"QR · ${code.byteCount} bytes · v${code.config.version} · ${code.config.errorCorrection} · mask ${code.config.mask}"),
    svg.path(svg.d := pathData(code))
  )
  def preview(code: Signal[QrCode], requirements: QrCodeRequirements): Element = {
    val helper = it.evadid.homepage.workbook.htmlRenderer.LaminarRenderHelper.singleton
    div(cls := "qr-preview",
      child <-- code.map(symbol),
      p(text <-- code.map(q => s"${q.byteCount} bytes · v${q.config.version} · ${q.config.errorCorrection} · mask ${q.config.mask}")),
      div(role := "status", aria.live := "polite",
        children <-- code.map(q => requirements.evaluate(q).map(result =>
          p(cls := (if result.passed then "qr-requirement is-passed" else "qr-requirement"),
            span(if result.passed then "✓ " else "○ "),
            span(text <-- helper.plaintextStringSignal(result.labelId)),
            s": ${result.expected}"
          )
        ).toList)
      )
    )
  }
}
