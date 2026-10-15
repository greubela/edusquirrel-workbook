package it.evadid.homepage.webElements.editor.code.EvaEditor

import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L.*
import com.raquo.laminar.nodes.ReactiveSvgElement
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.util.logging.Logger
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingState
import org.scalajs.dom.SVGSVGElement


trait EvaEditorTab[InternalRepresentation] extends HtmlAppElement {

  protected val underlyingVar: Var[InternalRepresentation]

  def underlyingSignal: Signal[Option[ProgrammingState]] = underlyingVar.signal.map(internal => try {
    val converted = convert(internal)
    Some(converted)
  } catch case (err: Throwable) => {
    logger.logExceptionWarn(s"cannot parse internal state to programming state (${label})", err)
    None
  })

  def setWith(state: ProgrammingState): Unit = try {
    val converted = convert(state)
    if (underlyingVar.now() != converted) underlyingVar.set(convert(state))
  } catch case (err: Throwable) => {
    logger.logExceptionWarn(s"cannot parse externally changed state to this Editor Tab (${label})", err)
  }

  def logger: Logger

  def convert(internal: InternalRepresentation): ProgrammingState

  def convert(external: ProgrammingState): InternalRepresentation

  def label: String

  def additionalCssForEvaEditor: String

  def icon: Option[ReactiveSvgElement[SVGSVGElement]]

}

