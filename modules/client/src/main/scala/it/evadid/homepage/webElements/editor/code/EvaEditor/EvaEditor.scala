package it.evadid.homepage.webElements.editor.code.EvaEditor

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.AppLanguage.ProgrammingLanguage
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.homepage.webElements.editor.code.EditorAppearance
import it.evadid.util.logging.Logger
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingState
import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.util.{Failure, Success, Try}

/** One editor bound to the answer; panels only observe that same answer. */
case class EvaEditor(logger: Logger, state: Var[ProgrammingState], panels: Seq[HtmlAppElement], config: EvaEditorConfig,
    choices: Option[Seq[EvaEditorTab]] = None) extends HtmlAppElement {
  val appearance = EditorAppearance()
  private val tabs = choices.getOrElse(EvaEditorConfig.tabs(logger, config)).distinctBy(_.language)
  private val selected = state.signal.map(source => tabs.find(_.language == source.associatedLanguage)).distinct
  private val lastError = Var(Option.empty[Throwable])
  private val request = Var(Option.empty[AnyRef])
  def selectedLanguage: Option[ProgrammingLanguage] = tabs.find(_.language == state.now().associatedLanguage).map(_.language)
  def error: Option[Throwable] = lastError.now()

  def switchTabTo(language: ProgrammingLanguage): Future[Unit] = tabs.find(_.language == language) match {
    case None => Future.unit
    case Some(tab) if selectedLanguage.contains(language) =>
      request.set(None)
      lastError.set(None)
      Future.unit
    case Some(tab) =>
      val source = state.now()
      val token = new Object
      request.set(Some(token))
      Future(tab.convert(source)).transform { result =>
        if request.now().contains(token) && state.now() == source then result match {
          case Success(converted) => lastError.set(None); state.set(converted)
          case Failure(error) => lastError.set(Some(error))
        }
        Success(())
      }
  }

  private def tabButton(tab: EvaEditorTab): Element = button(
    typ := "button", aria.label := tab.label, title := tab.label,
    aria.selected <-- selected.map(_.contains(tab)),
    aria.pressed <-- selected.map(_.contains(tab).toString),
    tab.icon, Option.when(tab.showLabel)(span(tab.label)),
    disabled <-- state.signal.flatMapSwitch { source =>
      if source.associatedLanguage == tab.language then Val(false)
      else EventStream.fromFuture(Future(Try(tab.convert(source)).isFailure)).startWith(true)
    },
    onClick --> (_ => switchTabTo(tab.language)))

  override def getDomElement(): Element = div(
    cls <-- selected.map(tab => "eva-editor editor-appearance" + tab.fold("")(_.css)),
    dataAttr("editor-theme") <-- appearance.theme,
    onMountCallback(_ => if selectedLanguage.isEmpty then tabs.headOption.foreach(tab => switchTabTo(tab.language))),
    onUnmountCallback(_ => request.set(None)),
    state.signal.changes --> (_ => request.set(None)),
    div(cls := "eva-editor__toolbar", div(cls := "eva-editor__tabs", tabs.map(tabButton)), appearance.toggleButton()),
    child.maybe <-- lastError.signal.map(_.map(error => div(cls := "eva-editor__error", role := "alert", error.getMessage))),
    div(cls := "eva-editor__content", child <-- selected.map {
      case Some(tab) => tab.create(state, appearance).getDomElement()
      case None => div("This draft is not available in the configured languages.")
    }),
    panels.map(_.getDomElement())
  )
  override def dismissOnOutsideClick: Boolean = false
}
