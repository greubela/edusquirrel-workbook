package it.evadid.homepage.webElements.editor.code.EvaEditor

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.AppLanguage.ProgrammingLanguage
import it.evadid.homepage.webElements.editor.abstractions.{SimpleWebEditor, TypingDraftBinding}
import it.evadid.homepage.webElements.editor.code.EditorAppearance
import it.evadid.homepage.webElements.editor.code.program.ProgrammingExecutionPanel
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingState

import scala.concurrent.Future

/** One language editor bound to the answer, with language tabs above it and the console below it.
  *
  * Code views edit a local draft ([[TypingDraftBinding]]): keystrokes inside a token stay in the editor and
  * reach the answer, and therefore panels, grading and storage, only when committed. Language switching is
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
  private val typing = TypingDraftBinding(underlyingVar)
  private val draft = typing.draft
  private val conversion = EvaConversionController(draft, typing.commit, environment.programming)
  private val context = EvaEditorContext(environment, appearance, conversion.marker)

  private def tabsFor(current: EvaEditorConfig): Seq[EvaEditorTab] =
    choices.getOrElse(EvaEditorConfig.tabs(current)).distinctBy(_.language)
  /** For lookups by language; the rendered tabs come from `tabsSignal`, whose identity selects the editor. */
  private def tabs: Seq[EvaEditorTab] = tabsFor(config.now())
  private val tabsSignal = config.signal.distinct.map(tabsFor)
  private val selected = draft.signal.combineWith(tabsSignal).map((source, current) =>
    current.find(_.language == source.associatedLanguage)).distinct

  def selectedLanguage: Option[ProgrammingLanguage] = tabs.find(_.language == draft.now().associatedLanguage).map(_.language)

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
    typing.binders,
    conversion.binders,
    onMountCallback(context => appearance.watch(context.owner)),
    div(cls := "eva-editor__toolbar",
      child <-- tabsSignal.map(current => EvaLanguageTabs(current, selected, conversion, environment.labels).element),
      child.maybe <-- conversionNotice),
    div(cls := "eva-editor__workspace",
      div(cls := "eva-editor__content", child <-- selected.map {
        case Some(tab) => tab.create(draft, context).getDomElement()
        case None => div(child.text <-- environment.labels.unavailable)
      }),
      // The console runs what was committed, never the keystrokes of an unfinished token.
      ProgrammingExecutionPanel(underlyingVar.signal, environment.programming, environment.labels.execution).getDomElement()))

  override def dismissOnOutsideClick: Boolean = false
}
