package it.evadid.homepage.webElements.code

import it.evadid.vm.structure.JavaUmlDiagram
import com.raquo.laminar.api.L.{label as labelTag, *}
import com.raquo.laminar.nodes.ReactiveSvgElement
import it.evadid.core.datastructures.language.AppLanguage.Java
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.homepage.webElements.basic.PageScrollLock
import it.evadid.homepage.webElements.editor.code.EditorAppearance
import it.evadid.homepage.webElements.editor.code.EvaEditor.EvaEditorTab
import it.evadid.homepage.webElements.editor.code.EvaEditor.uml.UmlStructureSelector
import it.evadid.vm.structure.UmlClassDiagram
import it.evadid.vm.structure.UmlClassDiagram.{EntityKey, MemberKind}
import it.evadid.homepage.webElements.editor.code.codemirror.CodeMirrorEditor
import it.evadid.homepage.webElements.editor.config.CodeMirrorEditorConfig
import it.evadid.util.logging.Logger
import it.evadid.vm.code.errors.ProgrammingConversionError
import it.evadid.vm.conversion.structured.SourceStructure
import it.evadid.vm.naming.BeEntityName
import it.evadid.workbook.elements.interactionElements.programming.state.*
import org.scalajs.dom
import org.scalajs.dom.SVGSVGElement

import java.util.UUID
import scala.collection.immutable.VectorMap
import scala.scalajs.js
import scala.util.Try

/** Edits a structured Java answer: its classes as a UML diagram on top and, below, the selection in a
  * [[JavaFunctionEditor]] (a function) or a [[JavaClassEditor]] (a class or one of its attributes), or the
  * full source. Both editors and the diagram observe the same answer Var.
  */
final class JavaComponentEditor(
    logger: Logger,
    val state: Var[ProgrammingStateJavaStructured],
    reference: Option[() => Element] = None,
    appearance: Option[EditorAppearance] = None,
    editorConfig: CodeMirrorEditorConfig = CodeMirrorEditorConfig.Default,
    conversionMarker: Signal[Option[ProgrammingConversionError]] = Val(None),
    loadingLabel: Signal[String] = Val("Loading editor…")
) extends HtmlAppElement {
  import JavaComponentEditor.*
  import JavaMethodSource.*

  private val classDiagram = state.signal.map(JavaUmlDiagram(_))
  private val structureReady = state.signal.map(_.definedClasses.values.forall(SourceStructure.balanced(_))).distinct

  private val diagram = UmlStructureSelector(classDiagram,
    classFooter = clazz => button(typ := "button", cls := "java-function-editor__button", "Add function",
      disabled <-- structureReady.map(!_), onClick --> (_ => openDialog(Dialog.AddMethod(clazz.sourceKey, Var(MethodFields()))))),
    trailing = button(typ := "button", cls := "java-function-editor__button uml-add-class", "Add class",
      disabled <-- structureReady.map(!_), onClick --> (_ => openDialog(Dialog.AddClass(Var(""))))))
  private val functionEditor = JavaFunctionEditor(logger, state, editorConfig, loadingLabel)
  private val classEditor = JavaClassEditor(logger, state, editorConfig, loadingLabel)
  private val source = state.bimap(_.toString)(ProgrammingStateJavaStructured(_)).distinct
  private lazy val sourceEditor = CodeMirrorEditor(logger, source, language = Java, config = Var(editorConfig),
    diagnostics = CodeMirrorEditor.marks(conversionMarker), loadingLabel = loadingLabel,
    sameSource = (left, right) => left == right || ProgrammingStateJavaStructured(left) == ProgrammingStateJavaStructured(right))

  private val initialKey = entryFunction(state.now()).flatMap(function => JavaUmlDiagram(state.now())
    .key(function.className, Some(methodCode(state.now(), function.className, function.method))))
  diagram.select(initialKey)

  private val mode = Var(if initialKey.isEmpty then Mode.Full else Mode.Function)
  private val structureOpen = Var(true)
  private val compactLayout = Var(false)
  private val dialog = Var(Option.empty[Dialog])
  private val dialogError = Var("")
  private val presentation = appearance.getOrElse(new EditorAppearance)

  private val panel: Signal[Panel] =
    mode.signal.combineWith(diagram.selectedKeySignal, functionEditor.located.combineWith(classEditor.located)).map {
      case (Mode.Full, _, _) => Panel.Source
      case (Mode.Function, Some(key), (true, _)) if isFunction(key) => Panel.Function
      case (Mode.Class, Some(_), (_, true)) => Panel.Class
      case _ => Panel.Empty
    }.distinct

  override def dismissOnOutsideClick: Boolean = false

  private def isFunction(key: EntityKey): Boolean = key.member.exists(_.kind == MemberKind.Function)

  /** Points the editors at a diagram selection: its class, and the function if one is selected. */
  private def open(key: EntityKey): Unit = {
    val current = state.now()
    val classes = JavaUmlDiagram(current)
    classes.sourceKey(key).foreach { className =>
      classEditor.select(className)
      if isFunction(key) then
        classes.entity(key).flatMap(entity => JavaFunctionEditor.find(current, className, entity.fullCode))
          .fold(functionEditor.clear())(functionEditor.select)
    }
  }

  private def show(className: BeEntityName, functionCode: Option[String]): Unit = {
    diagram.select(JavaUmlDiagram(state.now()).key(className, functionCode))
    mode.set(if functionCode.isDefined then Mode.Function else Mode.Class)
  }

  private def openDialog(next: Dialog): Unit = {
    dialogError.set("")
    dialog.set(Some(next))
  }

  private def addClass(name: String): Unit = {
    val current = state.now()
    val key = BeEntityName.fromCodeString(name)
    if !validName(name) then dialogError.set("Enter a valid Java class name.")
    else if current.definedClasses.keys.exists(JavaFragmentRange.sameName(_, key)) then dialogError.set("A class with this name already exists.")
    else {
      val next = ProgrammingStateJavaStructured(VectorMap.from(current.definedClasses) + (key -> s"class $name {\n}"))
      if parses(next.toString) then {
        state.set(next)
        dialog.set(None)
        show(key, None)
      } else dialogError.set("The class could not be added to the current Java source.")
    }
  }

  private def addMethod(className: BeEntityName, fields: MethodFields): Unit = {
    val current = state.now()
    if !validName(fields.name.trim) then dialogError.set("Enter a valid Java function name.")
    else if fields.returnType.trim.isEmpty then dialogError.set("Enter a return type.")
    else for {
      text <- current.definedClasses.get(className)
      target <- discover(text).headOption
    } {
      val declaration = methodDeclaration(fields.returnType.trim, fields.name.trim, fields.parameters.trim, fields.isStatic)
      val edited = insertMethod(text, target, declaration)
      val next = ProgrammingStateJavaStructured(current.definedClasses.updated(className, edited))
      if parses(next.toString) then {
        state.set(next)
        dialog.set(None)
        show(className, discover(edited).flatMap(_.methods)
          .find(method => method.start >= target.bodyEnd && method.name == fields.name.trim)
          .map(methodCode(next, className, _)))
      } else dialogError.set("The function signature or current Java source is invalid.")
    }
  }

  private def renderDialog(current: Dialog): Element = {
    val (caption, fields, submit) = current match {
      case Dialog.AddClass(name) => ("Add class", modalField("Class name", name), () => addClass(name.now().trim))
      case Dialog.AddMethod(className, method) => ("Add function", div(cls := "java-function-editor__dialog-fields",
        modalField("Function name", method.zoomLazy(_.name)((value, next) => value.copy(name = next))),
        modalField("Return type", method.zoomLazy(_.returnType)((value, next) => value.copy(returnType = next))),
        modalField("Parameters (for example: int count, String label)",
          method.zoomLazy(_.parameters)((value, next) => value.copy(parameters = next))),
        labelTag(cls := "java-function-editor__method-option",
          input(typ := "checkbox", checked <-- method.signal.map(_.isStatic),
            onChange.mapToChecked --> (checked => method.update(_.copy(isStatic = checked)))),
          span("Static method"))), () => addMethod(className, method.now()))
    }
    dialogTag(role := "dialog", aria.label := caption, cls := "java-function-editor__dialog",
      new EventProp[dom.Event]("cancel") --> { event =>
        event.preventDefault()
        event.stopPropagation()
        dialog.set(None)
      },
      onMountCallback(ctx => ctx.thisNode.ref.asInstanceOf[dom.html.Dialog].showModal()),
      onUnmountCallback(node => node.ref.asInstanceOf[dom.html.Dialog].close()),
      PageScrollLock.whileMounted,
      h2(cls := "java-function-editor__dialog-title", caption),
      fields,
      p(cls := "java-function-editor__dialog-error", role := "alert", child.text <-- dialogError.signal),
      div(cls := "java-function-editor__dialog-actions",
        button(typ := "button", cls := "java-function-editor__button java-function-editor__button--secondary",
          "Cancel", onClick --> (_ => dialog.set(None))),
        button(typ := "button", cls := "java-function-editor__button java-function-editor__button--primary",
          caption, onClick --> (_ => submit()))))
  }

  private def modalField(fieldLabel: String, fieldValue: Var[String]): Element =
    labelTag(cls := "java-function-editor__field", span(fieldLabel),
      input(typ := "text", value <-- fieldValue.signal, onInput.mapToValue --> fieldValue.writer))

  private def renderToolbar(navigationId: String): Element =
    div(cls := "java-function-editor__topbar",
      div(h2(cls := "java-function-editor__title", "Java editor"),
        p(cls := "java-function-editor__subtitle", "Select a class or function in the diagram to edit it.")),
      div(cls := "java-function-editor__topbar-actions",
        button(typ := "button", cls := "java-function-editor__button java-function-editor__button--secondary",
          aria.controls := navigationId, aria.expanded <-- structureOpen.signal,
          child.text <-- structureOpen.signal.map(if _ then "Hide classes" else "Show classes"),
          onClick --> (_ => structureOpen.update(!_))),
        span(cls := "java-function-editor__sync", role := "status", aria.live := "polite",
          child.text <-- panel.map(_.status))))

  private def renderDiagram(navigationId: String): Element =
    navTag(idAttr := navigationId, aria.label := "Java classes and members", cls := "java-function-editor__diagram-section",
      // A disabled fieldset disables the diagram's buttons while the source has unfinished brackets or comments.
      fieldSet(cls := "java-component-editor__diagram", disabled <-- structureReady.map(!_),
        diagram.getDomElement()),
      child.maybe <-- classDiagram.map(_.classes.isEmpty).distinct.map(empty => Option.when(empty)(
        div(cls := "java-function-editor__empty-diagram",
          strong("No classes yet"), span("Add a class to start building your program.")))))

  private def heading(view: Panel, key: Option[EntityKey]): String = view match {
    case Panel.Source => "Java source"
    case Panel.Function => key.flatMap(selected => selected.member.map(member => s"${selected.className}.${member.name}")).getOrElse("")
    case Panel.Class => key.fold("")(selected => s"class ${selected.className}")
    case Panel.Empty => "Selection"
  }

  private def renderWorkspace(): Element =
    div(cls := "java-function-editor__workspace",
      div(cls := "java-function-editor__workspace-heading",
        div(h3(cls := "java-function-editor__section-title",
            child.text <-- panel.combineWith(diagram.selectedKeySignal).map(heading)),
          p(cls := "java-function-editor__section-hint", "Your edits are kept, including unfinished code.")),
      ),
      div(cls := "java-function-editor__editor-panel",
        child <-- panel.map {
          case Panel.Source => div(cls := "java-function-editor__source", sourceEditor.getDomElement())
          case Panel.Function => functionEditor.getDomElement()
          case Panel.Class => classEditor.getDomElement()
          case Panel.Empty => div(cls := "java-function-editor__editor-placeholder",
            strong("Choose a class or function to get started"),
            span("Select it in the diagram. Its complete source will appear here."))
        },
        span(cls <-- structureReady.map(if _ then "java-function-editor__draft-warning java-function-editor__draft-warning--hidden"
            else "java-function-editor__draft-warning"),
          "Your draft is kept. Finish the brackets or comments here, or open the full source to continue.")))

  private val running = Var(false)
  private val consoleOutput = Var("")
  private val runRevision = Var(0L)

  private def runProgram(): Unit = {
    runRevision.update(_ + 1)
    val revision = runRevision.now()
    val program = state.now()
    running.set(true)
    consoleOutput.set("")
    scala.concurrent.Future(program.toBeExpression.programExecution)(using scala.scalajs.concurrent.JSExecutionContext.queue).onComplete { result =>
      if revision == runRevision.now() && program == state.now() then {
        running.set(false)
        consoleOutput.set(result.fold(error => Option(error.getMessage).getOrElse(error.toString), execution =>
          execution.stdout + (if execution.completed then "" else s"\n${execution.status}")))
      }
    }(using scala.scalajs.concurrent.JSExecutionContext.queue)
  }

  private def consolePanel: Element = sectionTag(cls := "java-component-editor__console", aria.label := "Console",
    button(typ := "button", "Run", disabled <-- running.signal, onClick --> (_ => scala.scalajs.concurrent.JSExecutionContext.queue.execute(() => runProgram()))),
    pre(role := "log", aria.live := "polite", child.text <-- consoleOutput.signal),
    onUnmountCallback { _ => runRevision.update(_ + 1); running.set(false) },
    state.signal.changes --> { _ => runRevision.update(_ + 1); running.set(false); consoleOutput.set("") })

  private def switchMode(next: Mode): Unit = {
    mode.set(next)
    if next == Mode.Class && diagram.selectedKey.isEmpty then
      JavaUmlDiagram(state.now()).classes.headOption.foreach(clazz => diagram.select(Some(clazz.key)))
    if next == Mode.Function && !diagram.selectedKey.exists(isFunction) then {
      val classes = JavaUmlDiagram(state.now())
      val selected = diagram.selectedKey.flatMap(key => classes.classes.find(_.name == key.className))
      selected.orElse(classes.classes.headOption).flatMap(_.functions.headOption).foreach(member => diagram.select(Some(member.key)))
    }
  }

  private def modeTabs: Element = div(cls := "java-component-editor__modes", role := "tablist", aria.label := "Java editing mode",
    Mode.values.toSeq.map { choice => button(typ := "button", role := "tab", choice.label,
      aria.selected <-- mode.signal.map(_ == choice),
      disabled <-- structureReady.map(ready => !ready && choice != Mode.Full),
      onClick --> (_ => switchMode(choice))) })

  private def bindEditor(releaseLayout: Var[() => Unit]): Modifier[HtmlElement] =
    onMountCallback { ctx =>
      presentation.watch(ctx.owner)
      diagram.selectedKeySignal.foreach(_.foreach(open))(using ctx.owner)
      functionEditor.target.combineWith(state.signal).foreach { (selected, program) =>
        if mode.now() == Mode.Function then selected.foreach { function =>
          val key = JavaUmlDiagram(program).key(function.className,
            program.definedClasses.get(function.className).filter(_.length >= function.method.end)
              .map(_.substring(function.method.start, function.method.end)))
          key.filterNot(value => diagram.selectedKey.contains(value)).foreach(value => diagram.select(Some(value)))
        }
      }(using ctx.owner)
      diagram.selectedKeySignal.changes.foreach(_.foreach(key => mode.set(if isFunction(key) then Mode.Function else Mode.Class)))(using ctx.owner)
      Try(dom.window.matchMedia("(min-width: 761px) and (min-height: 601px)")).toOption.foreach { query =>
        def adapt(): Unit = {
          compactLayout.set(!query.matches)
          if compactLayout.now() then structureOpen.set(false)
        }
        val listener: js.Function1[dom.Event, Unit] = _ => adapt()
        adapt()
        query.addEventListener("change", listener)
        releaseLayout.set(() => query.removeEventListener("change", listener))
      }
    }

  override def getDomElement(): Element = {
    val navigationId = s"java-navigation-${UUID.randomUUID()}"
    val releaseLayout = Var[() => Unit](() => ())
    div(
      cls <-- structureOpen.signal.map { open =>
        "java-function-based-editor java-component-editor editor-appearance" +
          (if reference.isDefined then " java-function-based-editor--with-reference" else "") +
          (if open then "" else " java-function-based-editor--structure-collapsed")
      },
      dataAttr("editor-theme") <-- presentation.theme,
      bindEditor(releaseLayout),
      onUnmountCallback(_ => releaseLayout.now()()),
      // On a compact screen the diagram covers the editor; choosing an entry closes it.
      diagram.selectedKeySignal.changes --> (_ => if compactLayout.now() then structureOpen.set(false)),
      modeTabs,
      renderToolbar(navigationId),
      renderDiagram(navigationId),
      div(cls := "java-component-editor__working-area", renderWorkspace(), consolePanel),
      reference.fold[Modifier[HtmlElement]](emptyMod) { renderReference =>
        asideTag(cls := "java-function-editor__reference", aria.label := "Task reference", renderReference())
      },
      child <-- dialog.signal.map(_.fold(emptyNode)(renderDialog)))
  }
}

object JavaComponentEditor {
  private enum Mode(val label: String) {
    case Class extends Mode("Class Mode")
    case Function extends Mode("Function Mode")
    case Full extends Mode("Full Mode")
  }

  private enum Panel(val status: String) {
    case Function extends Panel("Function view")
    case Class extends Panel("Class view")
    case Source extends Panel("Full source")
    case Empty extends Panel("No selection")
  }

  private final case class MethodFields(name: String = "newFunction", returnType: String = "void",
      parameters: String = "", isStatic: Boolean = true)

  private enum Dialog {
    case AddClass(name: Var[String])
    case AddMethod(className: BeEntityName, fields: Var[MethodFields])
  }

  private def validName(name: String): Boolean = name.matches("[A-Za-z_$][A-Za-z0-9_$]*")

  private def methodCode(state: ProgrammingStateJavaStructured, className: BeEntityName, method: JavaMethodSource.JavaMethod): String =
    state.definedClasses(className).substring(method.start, method.end)

  /** `static void main(String[] args)`, if it is the only entry point (of class `Main`, if present). */
  private[code] def entryFunction(state: ProgrammingStateJavaStructured): Option[JavaFunctionEditor.Function] = {
    val main = state.definedClasses.filter((name, _) => JavaFragmentRange.sameName(name, BeEntityName.fromCodeString("Main")))
    (if main.nonEmpty then main else state.definedClasses).toSeq.flatMap((name, text) =>
      JavaMethodSource.initialMethod(text, requirePublicMain = false).map(JavaFunctionEditor.Function(name, _))) match {
      case Seq(function) => Some(function)
      case _ => None
    }
  }

  def forEvaTab(logger: Logger, config: CodeMirrorEditorConfig, tabLabel: String = "Java"): EvaEditorTab = new EvaEditorTab {
    override val language = Java
    override val label = tabLabel
    override val showLabel = true
    override val css = " eva-editor--java"
    override def icon: Option[ReactiveSvgElement[SVGSVGElement]] = Some(svg.svg(svg.cls := "eva-editor__language-icon", svg.viewBox := "0 0 24 24",
      svg.fill := "none", svg.stroke := "currentColor", svg.strokeWidth := "1.6",
      svg.strokeLineCap := "round", svg.strokeLineJoin := "round",
      svg.path(svg.d := "M5 11h11v5a4 4 0 0 1-4 4H9a4 4 0 0 1-4-4Zm11 1h2a3 3 0 0 1 0 6h-3M4 23h14M8 8c-3-3 3-3 0-6M12 8c-3-3 3-3 0-6")))
    override def create(state: Var[ProgrammingState], appearance: EditorAppearance,
        marker: Signal[Option[ProgrammingConversionError]], loading: Signal[String]): HtmlAppElement = {
      // A language change reaches bound signals before Laminar unmounts the old tab.
      val structured = state.bimap {
        case value: ProgrammingStateJavaStructured => value
        case value @ ProgrammingStateRaw(Java, _) => value.toJavaStructured
        case _ => ProgrammingStateJavaStructured(Map.empty)
      }(identity)
      new JavaComponentEditor(logger, structured, appearance = Some(appearance),
        editorConfig = config, conversionMarker = marker, loadingLabel = loading)
    }
  }
}
