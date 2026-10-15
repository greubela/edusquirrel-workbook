package it.evadid.homepage.webElements.editor.code.EvaEditor

import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L.*
import com.raquo.laminar.nodes.ReactiveSvgElement
import it.evadid.core.datastructures.storage.AsyncDataCache
import it.evadid.homepage.webElements.{FullscreenLifecycle, HtmlAppElement}
import it.evadid.util.logging.Logger
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingState
import org.scalajs.dom.SVGSVGElement

import scala.concurrent.{ExecutionContext, Future}
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
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

  private var synchronizedValue = Option.empty[InternalRepresentation]
  private var latestConversions = Option.empty[(ProgrammingState, AsyncDataCache[ProgrammingState, InternalRepresentation])]
  private def newConversionCache() = new AsyncDataCache[ProgrammingState, InternalRepresentation](logger, retryFailedOnNextFetch = false) {
    override protected def executeLoading(source: ProgrammingState)(ec: ExecutionContext): Future[InternalRepresentation] =
      Try(convertAsync(source)).fold(Future.failed, identity)
    override protected def formatInputForLogging(source: ProgrammingState): String = source.getClass.getSimpleName
    override protected def formatOutputForLogging(value: InternalRepresentation): String = label
  }
  protected def convertAsync(source: ProgrammingState): Future[InternalRepresentation] = Future(fromProgram(source))

  /** Prepare without changing the visible draft. Only the winning navigation installs it. */
  final def prepare(source: ProgrammingState): Future[() => Unit] = {
    // Availability follows every keystroke. Retain only the latest source, not an editing history.
    val cache = latestConversions.filter(_._1 == source).map(_._2).getOrElse {
      val created = newConversionCache()
      latestConversions = Some(source -> created)
      created
    }
    cache.loadAsFuture(source).map(value => () => install(value))
  }

  private def install(value: InternalRepresentation): Unit = {
    synchronizedValue = Some(value)
    if underlyingVar.now() != value then underlyingVar.set(value)
  }
  final def setWith(source: ProgrammingState): Unit = install(fromProgram(source))

  final def underlyingSignal: Signal[Option[ProgrammingState]] = underlyingVar.signal.map(value => Try(toProgram(value)).toOption)
  final def edits: EventStream[ProgrammingState] = underlyingVar.signal.changes
    .filter(value => !synchronizedValue.contains(value))
    .map { value => synchronizedValue = Some(value); Try(toProgram(value)).toOption }.collect { case Some(program) => program }

  def captureSource(): ProgrammingState = toProgram(underlyingVar.now())
  override def onFullscreenClose(): Unit = ()
}
