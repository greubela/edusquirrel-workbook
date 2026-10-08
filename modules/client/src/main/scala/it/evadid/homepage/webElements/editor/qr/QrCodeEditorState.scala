package it.evadid.homepage.webElements.editor.qr

import com.raquo.airstream.state.Var
import it.evadid.workbook.model.qr.{QrCode, QrErrorCorrection}
import scala.util.Try

/** Draft and saved state are separate so an invalid edit cannot overwrite a scannable code. */
final class QrCodeEditorState(val saved: Var[QrCode]) {
  val draft = Var(saved.now().text)
  val errorCorrection = Var(saved.now().config.errorCorrection)
  val versionChoice = Var(Option.empty[Int])
  val maskChoice = Var(Option.empty[Int])
  val error = Var(false)

  def updateCode(): Unit = {
    val result = Try(QrCode.fromText(draft.now(), errorCorrection.now(), versionChoice.now(), maskChoice.now()))
    error.set(result.isFailure)
    result.foreach(code => if code != saved.now() then saved.set(code))
  }
  def setText(text: String): Unit = { draft.set(text); updateCode() }
  def setVersion(version: Option[Int]): Unit = { versionChoice.set(version); updateCode() }
  def setMask(mask: Option[Int]): Unit = { maskChoice.set(mask); updateCode() }
  def setCorrection(level: QrErrorCorrection): Unit = { errorCorrection.set(level); updateCode() }
}
