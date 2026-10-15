package it.evadid.homepage.webElements.editor.code.EvaEditor

import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L.*
import com.raquo.laminar.nodes.ReactiveSvgElement
import it.evadid.homepage.webElements.{FullscreenLifecycle, HtmlAppElement}
import it.evadid.util.logging.Logger
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingState
import org.scalajs.dom.SVGSVGElement

import scala.util.Try

/** A standalone editor's adapter. Eva knows neither its representation nor its runtime. */
trait EvaEditorTab[InternalRepresentation] extends HtmlAppElement with FullscreenLifecycle {
  protected val underlyingVar: Var[InternalRepresentation]
  def logger: Logger
  def toProgram(internal: InternalRepresentation): ProgrammingState
  def fromProgram(external: ProgrammingState): InternalRepresentation
  def label: String
  def showLabel: Boolean = true
  def additionalCssForEvaEditor: String = ""
  def icon: Option[ReactiveSvgElement[SVGSVGElement]] = None

  // Synchronization is transient UI state, never another persistent answer.
  private val synchronizedValue = Var(Option.empty[InternalRepresentation])

  /** Validate and convert before Eva closes the current view. No cache or scheduling. */
  final def prepare(source: ProgrammingState): Try[() => Unit] =
    Try(fromProgram(source)).map(value => () => install(value))

  private def install(value: InternalRepresentation): Unit = {
    synchronizedValue.set(Some(value))
    if underlyingVar.now() != value then underlyingVar.set(value)
  }
  final def setWith(source: ProgrammingState): Unit = install(fromProgram(source))

  final def underlyingSignal: Signal[Option[ProgrammingState]] = underlyingVar.signal.map(value => Try(toProgram(value)).toOption)
  final def edits: EventStream[ProgrammingState] = underlyingVar.signal.changes
    .filter(value => !synchronizedValue.now().contains(value))
    .map { value => synchronizedValue.set(Some(value)); Try(toProgram(value)).toOption }.collect { case Some(program) => program }

  def captureSource(): ProgrammingState = toProgram(underlyingVar.now())
  override def onFullscreenClose(): Unit = ()
}
