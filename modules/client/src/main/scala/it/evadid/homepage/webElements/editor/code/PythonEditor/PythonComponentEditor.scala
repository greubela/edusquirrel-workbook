package it.evadid.homepage.webElements.editor.code.PythonEditor

import com.raquo.laminar.api.L.{label as labelTag, *}
import it.evadid.core.datastructures.language.AppLanguage.Python
import it.evadid.core.datastructures.uml.*
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.homepage.webElements.basic.PageScrollLock
import it.evadid.homepage.webElements.editor.code.EvaEditor.{EvaEditorContext, EvaEditorTab}
import it.evadid.homepage.webElements.editor.code.EvaEditor.uml.UmlStructureSelector
import it.evadid.homepage.webElements.editor.code.codemirror.CodeMirrorEditor
import it.evadid.homepage.webElements.editor.code.fragment.FragmentCodeEditor
import it.evadid.homepage.webElements.editor.code.ProgrammingStateView
import it.evadid.homepage.webElements.editor.code.program.ProgramAnalyzer
import it.evadid.homepage.webElements.editor.config.CodeMirrorEditorConfig
import it.evadid.vm.editing.PythonMethodEditing
import it.evadid.vm.naming.BeEntityName
import it.evadid.vm.parsing.source.SourceParsing
import it.evadid.vm.parsing.source.SourceParsing.SourceLanguage
import it.evadid.vm.structure.PythonUmlDiagram
import it.evadid.workbook.elements.interactionElements.programming.state.*
import org.scalajs.dom

/** Python's class/function/full views share source parsing, UML rendering and CodeMirror with Java. */
final class PythonComponentEditor(state: Var[ProgrammingStatePythonStructured], context: EvaEditorContext,
    config: CodeMirrorEditorConfig, analyzer: ProgramAnalyzer = ProgramAnalyzer.shared) extends HtmlAppElement {
  private val labels = context.labels.python
  private val mode = Var("function")
  private val selection = Var(Option.empty[UmlEntityKey])
  private val dialog = Var(Option.empty[String])
  private val owner = Var(PythonMethodEditing.module)
  private val name = Var("")
  private val parameters = Var("")
  private val annotation = Var("")
  private val initializer = Var("")
  private val static = Var(false)
  private val error = Var(false)
  /** The program's modules and classes, then the read-only modules it uses without defining them (worker analysis). */
  private val diagram = state.signal.map(PythonUmlDiagram(_)).combineWith(ProgramAnalyzer.latest(
    analyzer.statusOf(state.signal, context.environment.programming), UmlClassDiagram(Nil))(result =>
      PythonUmlDiagram.external(result.libraryDependencies, result.unknownDependency))).map(_.including(_))

  private def choose(key: UmlEntityKey): Unit = {
    selection.set(Some(key))
    mode.set(if key.member.exists(_.kind == UmlEntityKind.Function) then "function" else "class")
  }

  private def open(kind: String, className: BeEntityName): Unit = {
    owner.set(className); name.set(""); parameters.set(""); annotation.set(""); initializer.set(""); static.set(false); error.set(false)
    dialog.set(Some(kind))
  }

  private def submit(kind: String): Unit = {
    val current = state.now()
    val className = owner.now()
    val module = className == PythonMethodEditing.module
    val identifier = name.now().trim
    val validName = PythonMethodEditing.validName(identifier)
    val result = if kind == "pythonAddClass" then {
      val key = BeEntityName.fromCodeString(identifier)
      Option.when(validName && !current.definedClasses.contains(key) && PythonMethodEditing.valid(s"class $identifier:\n    pass\n"))(
        current.copy(definedClasses = current.definedClasses.updated(key, s"class $identifier:\n    pass\n")))
    } else PythonMethodEditing.source(current, className).flatMap { code =>
      val functionName = if kind == "pythonAddConstructor" then "__init__" else identifier
      val ownParameters = parameters.now().trim
      val receiver = if module || static.now() then "" else "self" + (if ownParameters.nonEmpty then ", " else "")
      val returns = annotation.now().trim match { case "" | "None" => ""; case value => " -> " + value }
      val decorator = if static.now() && !module then "@staticmethod\n" else ""
      val declaration = if kind == "pythonAddAttribute" then
        identifier + Option.when(annotation.now().trim.nonEmpty)(": " + annotation.now().trim).getOrElse("") +
          " = " + Option(initializer.now().trim).filter(_.nonEmpty).getOrElse("None")
      else {
        val skeleton = s"${decorator}def $functionName($receiver$ownParameters)$returns:\n    pass"
        val parsed = SourceParsing.parse(skeleton, SourceLanguage.Python).structured.functions.headOption
        val assignments = if kind == "pythonAddConstructor" then parsed.toSeq.flatMap(_.parameters).filterNot(_.name == "self")
          .map(p => s"    self.${p.name} = ${p.name}").mkString("\n") else ""
        if assignments.nonEmpty then skeleton.stripSuffix("    pass") + assignments else skeleton
      }
      val next = PythonMethodEditing.insert(code, declaration, module)
      val duplicate = if kind == "pythonAddAttribute" then PythonUmlDiagram(current).classes.find(_.sourceKey == className)
        .exists(_.attributes.exists(_.name == identifier))
      else PythonMethodEditing.functions(code, module).exists(_.name == functionName)
      Option.when((validName || kind == "pythonAddConstructor") && !duplicate && PythonMethodEditing.valid(next))(
        PythonMethodEditing.updated(current, className, next))
    }
    result match {
      case Some(next) =>
        state.set(next); dialog.set(None)
        val key = if kind == "pythonAddClass" then BeEntityName.fromCodeString(identifier) else className
        val model = PythonUmlDiagram(next).classes.find(_.sourceKey == key)
        val member = if kind == "pythonAddFunction" || kind == "pythonAddConstructor" then
          model.flatMap(_.functions.find(_.name == (if kind == "pythonAddConstructor" then "__init__" else identifier))).map(_.key)
        else None
        model.foreach(clazz => choose(member.getOrElse(clazz.key)))
      case _ => error.set(true)
    }
  }

  private def inputField(key: String, valueVar: Var[String]): Element = labelTag(cls := "java-function-editor__field",
    span(child.text <-- labels(key)), input(value <-- valueVar.signal, onInput.mapToValue --> valueVar.writer))

  private def renderDialog(kind: String): Element = dialogTag(cls := "java-function-editor__dialog", aria.label <-- labels(kind),
    onMountCallback(ctx => ctx.thisNode.ref.asInstanceOf[dom.html.Dialog].showModal()),
    onUnmountCallback(node => node.ref.asInstanceOf[dom.html.Dialog].close()), PageScrollLock.whileMounted,
    new EventProp[dom.Event]("cancel") --> { event => event.preventDefault(); dialog.set(None) },
    h2(child.text <-- labels(kind)),
    Option.when(kind != "pythonAddConstructor")(inputField("pythonName", name)),
    Option.when(kind == "pythonAddFunction" || kind == "pythonAddConstructor")(inputField("pythonParameters", parameters)),
    Option.when(kind == "pythonAddFunction" || kind == "pythonAddAttribute")(inputField("pythonType", annotation)),
    Option.when(kind == "pythonAddAttribute")(inputField("pythonInitializer", initializer)),
    Option.when(kind == "pythonAddFunction" && owner.now() != PythonMethodEditing.module)(labelTag(
      input(typ := "checkbox", checked <-- static.signal, onChange.mapToChecked --> static.writer), span(child.text <-- labels("pythonStatic")))),
    p(role := "alert", child.text <-- error.signal.combineWith(labels("pythonInvalidDeclaration")).map((bad, message) => if bad then message else "")),
    div(cls := "java-function-editor__dialog-actions",
      button(typ := "button", child.text <-- labels("cancel"), onClick --> (_ => dialog.set(None))),
      button(typ := "button", child.text <-- labels(kind), onClick --> (_ => submit(kind)))))

  /** The editor of the selected entry, created for this selection and released when another one is chosen. */
  private def sourceEditor(key: Option[UmlEntityKey], view: String): Element = {
    val owner = key.map(entry => BeEntityName.fromCodeString(entry.className)).getOrElse(PythonMethodEditing.module)
    val marks = context.conversionDiagnostics.combineWith(context.sourceDiagnostics(state.signal)).map(_ ++ _)
    def selected[I](editor: FragmentCodeEditor[ProgrammingStatePythonStructured, I], identity: I): Element = {
      editor.select(identity)
      editor.getDomElement()
    }
    (view, key.flatMap(_.member)) match {
      case ("full", _) => div(cls := "java-function-editor__source", CodeMirrorEditor(context.logger,
        state.bimap(_.toString)(ProgrammingStatePythonStructured(_)).distinct, Python, Var(config), context.loading, diagnostics = marks,
        sameSource = (left, right) => left == right || ProgrammingStatePythonStructured(left) == ProgrammingStatePythonStructured(right)).getDomElement())
      case ("function", Some(member)) if member.kind == UmlEntityKind.Function =>
        selected(PythonFunctionEditor(context.logger, state, context.loading, config, marks),
          PythonFunctionEditor.Function(owner, member.name, member.occurrence))
      case _ => selected(PythonClassEditor(context.logger, state, context.loading, config, marks), owner)
    }
  }

  override def getDomElement(): Element = div(cls := "java-function-based-editor python-component-editor",
    onMountCallback { _ =>
      val entry = PythonUmlDiagram(state.now()).classes.flatMap(_.functions).find(_.name == "main")
      selection.set(entry.map(_.key).orElse(Some(UmlEntityKey(PythonMethodEditing.module.spelling(), None))))
      if entry.isEmpty then mode.set("full")
    },
    div(cls := "java-function-editor__topbar", h2(child.text <-- labels("pythonEditorTitle"))),
    div(role := "tablist", aria.label <-- labels("pythonModes"), cls := "java-function-editor__modes",
      Seq("class", "function", "full").map(value => button(typ := "button", role := "tab",
        child.text <-- labels("pythonMode" + value.capitalize), aria.selected <-- mode.signal.map(_ == value), onClick --> (_ => mode.set(value))))),
    new UmlStructureSelector(diagram, selection.signal, Observer(choose), classFooter = clazz => div(
      button(typ := "button", child.text <-- labels("pythonAddFunction"), onClick --> (_ => open("pythonAddFunction", clazz.sourceKey))),
      button(typ := "button", child.text <-- labels("pythonAddAttribute"), onClick --> (_ => open("pythonAddAttribute", clazz.sourceKey))),
      Option.when(clazz.sourceKey != PythonMethodEditing.module)(button(typ := "button", child.text <-- labels("pythonAddConstructor"),
        onClick --> (_ => open("pythonAddConstructor", clazz.sourceKey))))),
      trailing = button(typ := "button", cls := "uml-add-class", child.text <-- labels("pythonAddClass"), onClick --> (_ => open("pythonAddClass", PythonMethodEditing.module))),
      labels = UmlStructureSelector.OriginLabels(labels(_))).getDomElement(),
    div(cls := "java-function-editor__workspace", child <-- selection.signal.combineWith(mode.signal).distinct.map(sourceEditor)),
    child.maybe <-- dialog.signal.map(_.map(renderDialog)))

  override def dismissOnOutsideClick: Boolean = false
}

object PythonComponentEditor {
  def forEvaTab(config: CodeMirrorEditorConfig): EvaEditorTab = new EvaEditorTab {
    override val language = Python
    override def icon = Some(it.evadid.homepage.webElements.editor.code.EvaEditor.EvaLanguageIcons.python)
    override def label(labels: it.evadid.homepage.webElements.editor.code.EvaEditor.EvaEditorLabels): Signal[String] = Val("Python")
    override def create(state: Var[ProgrammingState], context: EvaEditorContext): HtmlAppElement = {
      val structured = ProgrammingStateView(state, inactive = ProgrammingStatePythonStructured("")) {
        case value: ProgrammingStatePythonStructured => value
        case value @ ProgrammingStateRaw(Python, _) => value.toPythonStructured
      }(identity)
      PythonComponentEditor(structured, context, config)
    }
  }
}
