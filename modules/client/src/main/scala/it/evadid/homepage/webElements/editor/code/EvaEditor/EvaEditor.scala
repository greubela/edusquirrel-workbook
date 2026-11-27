package it.evadid.homepage.webElements.editor.code.EvaEditor

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.AppLanguage.ProgrammingLanguage
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.homepage.webElements.download.ProjectDownloadButton
import it.evadid.homepage.webElements.editor.abstractions.SimpleWebEditor
import it.evadid.homepage.webElements.editor.code.EditorAppearance
import it.evadid.homepage.webElements.editor.code.program.ProgrammingExecutionPanel
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingState

import scala.concurrent.Future

/** One language editor bound to the answer, with one toolbar line above it (language tabs, then the editor's
  * [[EvaToolbarControls]]) and the console with Run, Stop and the project download below it.
  *
  * Each typing editor publishes after a pause, focus loss, page hiding or unmount. Language switching is
  * [[EvaConversionController]]'s, the tab list [[EvaLanguageTabs]]'. The configured languages determine the
  * tabs; a configuration change rebuilds the tabs and the editor.
  */
final case class EvaEditor(
    underlyingVar: Var[ProgrammingState],
    config: Var[EvaEditorConfig],
    environment: EvaEditorEnvironment,
    choices: Option[Seq[EvaEditorTab]] = None
) extends SimpleWebEditor[ProgrammingState, EvaEditorConfig] {
  val appearance: EditorAppearance = EditorAppearance()
  private val conversion = EvaConversionController(underlyingVar, underlyingVar.set, environment.programming)
  private val context = EvaEditorContext(environment, appearance, conversion.marker)

  private def tabsFor(current: EvaEditorConfig): Seq[EvaEditorTab] =
    choices.getOrElse(EvaEditorConfig.tabs(current)).distinctBy(_.language)
  /** For lookups by language; the rendered tabs come from `tabsSignal`, whose identity selects the editor. */
  private def tabs: Seq[EvaEditorTab] = tabsFor(config.now())
  private val tabsSignal = config.signal.distinct.map(tabsFor)
  private val selected = underlyingVar.signal.combineWith(tabsSignal).map((source, current) =>
    current.find(_.language == source.associatedLanguage)).distinct

  /** The selected tab's editor, created once per selection and shared by the toolbar and the workspace. */
  private val editor: Signal[Option[HtmlAppElement]] = selected.map(_.map(_.create(underlyingVar, context)))

  def selectedLanguage: Option[ProgrammingLanguage] = tabs.find(_.language == underlyingVar.now().associatedLanguage).map(_.language)

  def error: Option[EvaConversionFailure] = conversion.error

  /** Convert the bound answer; its resulting representation determines the active editor. */
  def setActiveTab(language: ProgrammingLanguage): Future[Unit] =
    tabs.find(_.language == language).fold(Future.unit)(conversion.switchTo)

  private def conversionNotice: Signal[Option[Element]] =
    conversion.lastOutcome.combineWith(environment.labels.switchFailed, environment.labels.conversion).map {
      case (Some(result), prefix, texts) => EvaConversionFailure.of(result) match {
        case Some(failure) => Some(div(cls := "eva-editor__error", role := "alert", s"$prefix: ${texts.describe(failure)}"))
        // A successful switch may lose functionality; the details are technical, the heading localized.
        case None => Option.when(result.conversionInfo.nonEmpty)(div(cls := "instruction-callout instruction-callout--warning",
          role := "status", strong(texts.warnings), result.conversionInfo.map(info => div(info.warningMsg))))
      }
      case _ => None
    }

  override def getDomElement(): Element = div(
    cls <-- selected.map(tab => "eva-editor editor-appearance" + tab.fold("")(_.css)),
    dataAttr("editor-theme") <-- appearance.theme,
    conversion.binders,
    onMountCallback(context => appearance.watch(context.owner)),
    div(cls := "eva-editor__toolbar",
      child <-- tabsSignal.map(current => EvaLanguageTabs(current, selected, conversion, environment.labels).element),
      child.maybe <-- editor.map(_.collect { case controls: EvaToolbarControls => controls.toolbarControls }),
      child.maybe <-- conversionNotice),
    div(cls := "eva-editor__workspace",
      div(cls := "eva-editor__content", child <-- editor.map {
        case Some(current) => current.getDomElement()
        case None => div(child.text <-- environment.labels.unavailable)
      }),
      // The console runs what was committed, never the keystrokes of an unfinished token.
      ProgrammingExecutionPanel(underlyingVar.signal, environment.programming, environment.labels.execution,
        actions = Seq(ProjectDownloadButton(underlyingVar.signal, environment.labels.execution, environment.projectName)))
        .getDomElement()))

  override def dismissOnOutsideClick: Boolean = false
}
