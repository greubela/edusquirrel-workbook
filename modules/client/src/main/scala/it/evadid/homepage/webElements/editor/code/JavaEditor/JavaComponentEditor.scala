package it.evadid.homepage.webElements.editor.code.JavaEditor

import it.evadid.vm.editing.JavaMethodEditing
import it.evadid.vm.conversion.dependencies.ProgrammingEnvironment
import it.evadid.vm.structure.JavaUmlDiagram
import com.raquo.laminar.api.L.*
import it.evadid.homepage.webElements.editor.code.ProgrammingStateView
import it.evadid.homepage.webElements.editor.code.program.ProgramAnalyzer
import it.evadid.vm.analysis.SourceWarning
import com.raquo.laminar.nodes.ReactiveSvgElement
import it.evadid.core.datastructures.language.AppLanguage.Java
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.homepage.webElements.basic.MediaQuery
import it.evadid.homepage.webElements.editor.code.JavaEditor.JavaEditorLabels.Key
import it.evadid.homepage.webElements.editor.code.EditorAppearance
import it.evadid.homepage.webElements.editor.code.EvaEditor.{EvaEditorContext, EvaEditorTab}
import it.evadid.homepage.webElements.editor.code.EvaEditor.uml.UmlStructureSelector
import it.evadid.core.datastructures.uml.*
import it.evadid.homepage.webElements.editor.code.codemirror.CodeMirrorEditor
import it.evadid.homepage.webElements.editor.config.CodeMirrorEditorConfig
import it.evadid.util.logging.Logger
import it.evadid.vm.parsing.source.SourceParsing
import it.evadid.vm.parsing.source.SourceParsing.SourceLanguage
import it.evadid.vm.naming.BeEntityName
import it.evadid.workbook.elements.interactionElements.programming.state.*
import org.scalajs.dom.SVGSVGElement

import java.util.UUID

/** Edits a structured Java answer: its classes as a UML diagram on top and, below, the selection in a
  * [[JavaFunctionEditor]] (a function) or a [[JavaClassEditor]] (a class or one of its attributes), or the
  * full source. Both editors and the diagram observe the same answer Var.
  */
final class JavaComponentEditor(
    logger: Logger,
    val state: Var[ProgrammingStateJavaStructured],
    labels: JavaEditorLabels,
    loadingLabel: Signal[String],
    reference: Option[() => Element] = None,
    appearance: Option[EditorAppearance] = None,
    editorConfig: CodeMirrorEditorConfig = CodeMirrorEditorConfig.Default,
    diagnostics: Signal[Seq[CodeMirrorEditor.Diagnostic]] = Val(Nil),
    environment: ProgrammingEnvironment = ProgrammingEnvironment.all,
    analyzer: ProgramAnalyzer = ProgramAnalyzer.shared
) extends HtmlAppElement {
  import JavaComponentEditor.*
  import JavaEditorNavigation.{Mode, isFunction}

  private val classDiagram = state.signal.map(JavaUmlDiagram(_))
  private val analysis = analyzer.statusOf(state.signal, environment)
  /** What the program uses without defining it, from the worker's analysis; the last result stays while the next is pending. */
  private val externalClasses = ProgramAnalyzer.latest(analysis, UmlClassDiagram(Nil))(result =>
    JavaUmlDiagram.external(result.libraryDependencies, result.unknownDependency))
  /** Located warnings (unknown dependencies, unreadable statements), only while the analysis describes the current source. */
  private[JavaEditor] val warnings = analysis.combineWith(labels.warnings.all).map {
    case (ProgramAnalyzer.Status.Ready(result), texts) => result.warnings.map(warning =>
      CodeMirrorEditor.mark(warning, texts(warning.kind).replace("{name}", warning.subject)))
    case _ => Nil
  }
  /** Marks of the printed program; the fragment editors show those within their fragment. */
  private lazy val marks = diagnostics.combineWith(warnings).map(_ ++ _)
  private val structureReady = state.signal.map(_.definedClasses.values.forall(source =>
    SourceParsing.parse(source, SourceLanguage.Java).structured.balanced)).distinct

  private val navigation = JavaEditorNavigation(state)
  private val diagram = UmlStructureSelector(classDiagram.combineWith(externalClasses).map(_.including(_)), navigation.selection, navigation.choose,
    classFooter = clazz => div(
      child.maybe <-- navigation.selection.map(_.filter(key => key.className == clazz.name && isFunction(key))).map(_.map { key =>
        button(typ := "button", cls := "java-function-editor__button", child.text <-- labels(Key.javaDelete),
          onClick --> { _ =>
            for code <- state.now().definedClasses.get(clazz.sourceKey); entity <- JavaUmlDiagram(state.now()).entity(key);
              owner <- SourceParsing.parse(code, SourceLanguage.Java).structured.programClasses.headOption;
              method <- owner.functions.find(_.code.trim == entity.fullCode.trim) do {
              state.update(current => ProgrammingStateJavaStructured(current.definedClasses.updated(clazz.sourceKey,
                code.substring(0, method.span.start) + code.substring(method.span.end))))
              navigation.reveal(clazz.sourceKey, None)
            }
          })
      }),
      button(typ := "button", cls := "java-function-editor__button", child.text <-- labels(Key.javaAddAttribute),
        disabled <-- structureReady.map(!_), onClick --> (_ => dialog.addField(clazz.sourceKey))),
      Option.when(!clazz.isInterface)(button(typ := "button", cls := "java-function-editor__button", child.text <-- labels(Key.javaAddConstructor),
        disabled <-- structureReady.map(!_), onClick --> (_ => dialog.addConstructor(clazz.sourceKey)))),
      button(typ := "button", cls := "java-function-editor__button", child.text <-- labels(Key.javaAddFunction),
      disabled <-- structureReady.map(!_), onClick --> (_ => dialog.addFunction(clazz.sourceKey)))),
    trailing = button(typ := "button", cls := "java-function-editor__button uml-add-class", child.text <-- labels(Key.javaAddClass),
      disabled <-- structureReady.map(!_), onClick --> (_ => dialog.addClass())),
    labels = labels.uml)
  private val dialog = JavaStructureDialog(state, labels, navigation.reveal)
  private val functionEditor = JavaFunctionEditor(logger, state, loadingLabel, editorConfig, marks)
  private val classEditor = JavaClassEditor(logger, state, loadingLabel, editorConfig, marks)
  private val source = state.bimap(_.toString)(ProgrammingStateJavaStructured(_)).distinct
  private lazy val sourceEditor = CodeMirrorEditor(logger, source, language = Java, config = Var(editorConfig),
    diagnostics = marks, loadingLabel = loadingLabel,
    sameSource = (left, right) => left == right || ProgrammingStateJavaStructured(left) == ProgrammingStateJavaStructured(right))

  private val structureOpen = Var(true)
  /** On a small screen the diagram covers the editor; it then closes when an entry is chosen. */
  private val compactLayout = MediaQuery.matches("(max-width: 760px), (max-height: 600px)")
  private val presentation = appearance.getOrElse(new EditorAppearance)

  private val panel: Signal[Panel] =
    navigation.mode.combineWith(navigation.selection, functionEditor.located.combineWith(classEditor.located)).map {
      case (Mode.Full, _, _) => Panel.Source
      case (Mode.Function, Some(key), (true, _)) if isFunction(key) => Panel.Function
      case (Mode.Class, Some(_), (_, true)) => Panel.Class
      case _ => Panel.Empty
    }.distinct

  override def dismissOnOutsideClick: Boolean = false

  /** Points the editors at a diagram selection: its class, and the function if one is selected. */
  private def open(key: UmlEntityKey): Unit = {
    val current = state.now()
    val classes = JavaUmlDiagram(current)
    classes.sourceKey(key).foreach { className =>
      classEditor.select(className)
      key.member.filter(_.kind == UmlEntityKind.Attribute).foreach { member =>
        current.definedClasses.get(className).foreach { code =>
          val owner = SourceParsing.parse(code, SourceLanguage.Java).structured.classes.headOption
          owner.flatMap(_.fields.find(_.name == member.name)).filter(field => owner.get.fields.count(_.span == field.span) == 1)
            .foreach(dialog.editField(className, _))
        }
      }
      if isFunction(key) then
        classes.entity(key).flatMap(entity => JavaFunctionEditor.find(current, className, entity.fullCode))
          .fold(functionEditor.clear())(functionEditor.select)
    }
  }

  private def renderToolbar(navigationId: String): Element =
    div(cls := "java-function-editor__topbar",
      div(h2(cls := "java-function-editor__title", child.text <-- labels(Key.javaTitle)),
        p(cls := "java-function-editor__subtitle", child.text <-- labels(Key.javaSubtitle))),
      div(cls := "java-function-editor__topbar-actions",
        button(typ := "button", cls := "java-function-editor__button java-function-editor__button--secondary",
          aria.controls := navigationId, aria.expanded <-- structureOpen.signal,
          child.text <-- labels.select(structureOpen.signal)(open => if open then Key.javaHideClasses else Key.javaShowClasses),
          onClick --> (_ => structureOpen.update(!_))),
        span(cls := "java-function-editor__sync", role := "status", aria.live := "polite",
          child.text <-- labels.select(panel)(_.status))))

  private def renderDiagram(navigationId: String): Element =
    navTag(idAttr := navigationId, aria.label <-- labels(Key.javaNavigation), cls := "java-function-editor__diagram-section",
      // A disabled fieldset disables the diagram's buttons while the source has unfinished brackets or comments.
      fieldSet(cls := "java-component-editor__diagram", disabled <-- structureReady.map(!_),
        diagram.getDomElement()),
      child.maybe <-- classDiagram.map(_.editable.classes.isEmpty).distinct.map(empty => Option.when(empty)(
        div(cls := "java-function-editor__empty-diagram",
          strong(child.text <-- labels(Key.javaNoClasses)), span(child.text <-- labels(Key.javaNoClassesHint))))))

  private def heading(view: Panel, key: Option[UmlEntityKey], texts: Map[Key, String]): String = view match {
    case Panel.Source => texts(Key.javaSourceHeading)
    case Panel.Function => key.flatMap(selected => selected.member.map(member => s"${selected.className}.${member.name}")).getOrElse("")
    case Panel.Class => key.fold("")(selected => s"class ${selected.className}")
    case Panel.Empty => texts(Key.javaSelectionHeading)
  }

  private def renderWorkspace(): Element =
    div(cls := "java-function-editor__workspace",
      div(cls := "java-function-editor__workspace-heading",
        div(h3(cls := "java-function-editor__section-title",
            child.text <-- panel.combineWith(navigation.selection, labels.all).map(heading)),
          p(cls := "java-function-editor__section-hint", child.text <-- labels(Key.javaEditsKept))),
      ),
      div(cls := "java-function-editor__editor-panel",
        child <-- panel.map {
          case Panel.Source => div(cls := "java-function-editor__source", sourceEditor.getDomElement())
          case Panel.Function => functionEditor.getDomElement()
          case Panel.Class => classEditor.getDomElement()
          case Panel.Empty => div(cls := "java-function-editor__editor-placeholder",
            strong(child.text <-- labels(Key.javaChooseEntry)),
            span(child.text <-- labels(Key.javaChooseEntryHint)))
        },
        span(cls <-- structureReady.map(if _ then "java-function-editor__draft-warning java-function-editor__draft-warning--hidden"
            else "java-function-editor__draft-warning"),
          child.text <-- labels(Key.javaDraftWarning))))

  private def modeTabs: Element = div(cls := "java-component-editor__modes", role := "tablist", aria.label <-- labels(Key.javaModes),
    Mode.values.toSeq.map { choice => button(typ := "button", role := "tab", child.text <-- labels(choice.label),
      aria.selected <-- navigation.mode.map(_ == choice),
      disabled <-- structureReady.map(ready => !ready && choice != Mode.Full),
      onClick --> (_ => navigation.switchTo(choice))) })

  /** The editors follow the selection; the selection follows the function edited in Function Mode, and the mode
    * widens to show a draft that holds several declarations.
    */
  private val binders: Seq[Modifier[HtmlElement]] = Seq(
    onMountCallback(ctx => presentation.watch(ctx.owner)),
    navigation.selection --> (_.foreach(open)),
    functionEditor.target.combineWith(state.signal) --> ((function, program) => function.foreach(navigation.follow(_, program))),
    functionEditor.widened.changes.collect { case Some(scope) => scope } --> (navigation.widen(Mode.Function, _)),
    classEditor.widened.changes.collect { case Some(scope) => scope } --> (navigation.widen(Mode.Class, _)),
    compactLayout --> (compact => if compact then structureOpen.set(false)),
    navigation.selection.changes.withCurrentValueOf(compactLayout) --> ((_, compact) => if compact then structureOpen.set(false)))

  override def getDomElement(): Element = {
    val navigationId = s"java-navigation-${UUID.randomUUID()}"
    div(
      cls <-- structureOpen.signal.map { open =>
        "java-function-based-editor java-component-editor editor-appearance" +
          (if reference.isDefined then " java-function-based-editor--with-reference" else "") +
          (if open then "" else " java-function-based-editor--structure-collapsed")
      },
      dataAttr("editor-theme") <-- presentation.theme,
      binders,
      modeTabs,
      renderToolbar(navigationId),
      renderDiagram(navigationId),
      renderWorkspace(),
      reference.fold[Modifier[HtmlElement]](emptyMod) { renderReference =>
        asideTag(cls := "java-function-editor__reference", aria.label <-- labels(Key.javaTaskReference), renderReference())
      },
      dialog.node)
  }
}

object JavaComponentEditor {


  private enum Panel(val status: Key) {
    case Function extends Panel(Key.javaViewFunction)
    case Class extends Panel(Key.javaViewClass)
    case Source extends Panel(Key.javaViewSource)
    case Empty extends Panel(Key.javaViewEmpty)
  }

  /** `static void main(String[] args)`, if it is the only entry point (of class `Main`, if present). */
  private[JavaEditor] def entryFunction(state: ProgrammingStateJavaStructured): Option[JavaFunctionEditor.Function] = {
    val main = state.definedClasses.filter((name, _) => it.evadid.homepage.webElements.editor.code.fragment.FragmentSource.sameName(name, BeEntityName.fromCodeString("Main")))
    (if main.nonEmpty then main else state.definedClasses).toSeq.flatMap((name, text) =>
      JavaMethodEditing.initialMethod(text, requirePublicMain = false).map(JavaFunctionEditor.Function(name, _))) match {
      case Seq(function) => Some(function)
      case _ => None
    }
  }

  def forEvaTab(config: CodeMirrorEditorConfig): EvaEditorTab = new EvaEditorTab {
    override val language = Java
    override val css = " eva-editor--java"
    override def icon: Option[ReactiveSvgElement[SVGSVGElement]] = Some(svg.svg(svg.cls := "eva-editor__language-icon", svg.viewBox := "0 0 24 24",
      svg.fill := "none", svg.stroke := "currentColor", svg.strokeWidth := "1.6",
      svg.strokeLineCap := "round", svg.strokeLineJoin := "round",
      svg.path(svg.d := "M5 11h11v5a4 4 0 0 1-4 4H9a4 4 0 0 1-4-4Zm11 1h2a3 3 0 0 1 0 6h-3M4 23h14M8 8c-3-3 3-3 0-6M12 8c-3-3 3-3 0-6")))
    override def create(state: Var[ProgrammingState], context: EvaEditorContext): HtmlAppElement = {
      val structured = ProgrammingStateView(state, inactive = ProgrammingStateJavaStructured(Map.empty)) {
        case value: ProgrammingStateJavaStructured => value
        case value @ ProgrammingStateRaw(Java, _) => value.toJavaStructured
      }(identity)
      new JavaComponentEditor(context.logger, structured, context.labels.java, context.loading,
        appearance = Some(context.appearance), editorConfig = config, diagnostics = context.conversionDiagnostics,
        environment = context.environment.programming)
    }
  }
}
