package it.evadid.homepage.webElements.code

import it.evadid.homepage.webElements.basic.PageScrollLock
import it.evadid.core.datastructures.language.AppLanguage.Java

import it.evadid.workbook.elements.interactionElements.programming.state.{ProgrammingState, ProgrammingStateRaw, ProgrammingStateJavaStructured}
import com.raquo.airstream.state.Var
import com.raquo.airstream.ownership.Subscription
import com.raquo.laminar.api.L.{label as labelTag, *}
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.homepage.webElements.editor.code.EditorAppearance
import it.evadid.vm.code.errors.ProgrammingConversionError
import it.evadid.vm.conversion.structured.SourceStructure.{Param, Visibility}
import it.evadid.homepage.webElements.editor.code.codemirror.CodeMirrorEditor
import it.evadid.homepage.webElements.editor.config.CodeMirrorEditorConfig
import it.evadid.util.logging.Logger
import org.scalajs.dom

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import scala.util.Try
import java.util.UUID

/** The Java editor: a UML class diagram shows the program's structure, the code editor its behaviour.
  * Selecting an operation in the diagram opens that method or constructor; the full source stays one
  * toggle away. The diagram is derived from the source, so typing in either view updates it.
  */
final class JavaMethodEditor
(
  logger: Logger,
  val state: Var[ProgrammingState],
  reference: Option[() => Element] = None,
  appearance: Option[EditorAppearance] = None,
  editorConfig: CodeMirrorEditorConfig = CodeMirrorEditorConfig.Default,
  conversionMarker: Signal[Option[ProgrammingConversionError]] = Val(None),
  loadingLabel: Signal[String] = Val("Loading editor…"),
  requirePublicMain: Boolean = true,
  sameSource: (String, String) => Boolean = _ == _,
  labels: JavaEditorLabels = JavaEditorLabels.keys,
  features: JavaStructureFeatures = JavaStructureFeatures.Full
) extends HtmlAppElement {

  import JavaMethodSource.*

  private enum DialogMode {
    case AddClass
    case AddField(className: String)
    case EditField(field: JavaField)
    case AddConstructor(className: String)
    case AddMethod(className: String)
    case ConfirmDelete(className: String, member: String, start: Int, end: Int, operation: Boolean)
  }

  private enum DialogError {
    case ClassName, Name, Type, Duplicate, Invalid
  }

  private final case class ParameterRow(id: String, javaType: String, name: String)

  private val sourceDraft = state.bimap {
    case ProgrammingStateRaw(Java, code) => code
    case value: ProgrammingStateJavaStructured => value.toString
    case _ => ""
  }(ProgrammingStateRaw(Java, _)).distinct
  private def sourceCode: String = state.now() match {
    case ProgrammingStateRaw(Java, code) => code
    case value: ProgrammingStateJavaStructured => value.toString
    case _ => ""
  }
  private val sourceAnalysis = Var(JavaMethodSource(sourceCode))
  private def analysis(code: String = sourceCode): JavaMethodSource = {
    val current = sourceAnalysis.now()
    if current.code == code then current else {
      val next = JavaMethodSource(code)
      sourceAnalysis.set(next)
      next
    }
  }
  private val sourceAvailable = state.signal.map(isJavaSource).distinct
  private val classes = sourceAnalysis.signal.map(_.classes)
  /** The diagram keeps the last complete source while brackets or comments are unfinished. */
  private val diagramSource = sourceAnalysis.signal.scanLeft(identity)((last, next) => if next.balanced then next else last)
  private val initialSelection = sourceAnalysis.now().initialMethod(requirePublicMain)
  private val selectedMethod = Var(initialSelection)
  private val editingRange = Var(initialSelection.map(fn => MethodRange(fn.start, fn.end)))
  private val methodDraft = Var(initialSelection.fold("")(fn => sourceCode.substring(fn.start, fn.end)))
  private val structureReady = sourceAnalysis.signal.map(_.balanced)
  private val structureLocked = structureReady.combineWith(sourceAvailable).map { (ready, available) => !ready || !available }
  private val sourceView = Var(initialSelection.isEmpty)
  private val structureOpen = Var(true)
  private val compactLayout = Var(false)
  private val dialogMode = Var(Option.empty[DialogMode])
  private val nameDraft = Var("")
  private val typeDraft = Var("")
  private val initializerDraft = Var("")
  private val visibilityDraft = Var(Visibility.Public)
  private val staticDraft = Var(false)
  private val assignDraft = Var(true)
  private val parameterRows = Var(Vector.empty[ParameterRow])
  private val dialogError = Var(Option.empty[DialogError])
  private val presentation = appearance.getOrElse(new EditorAppearance)

  override def dismissOnOutsideClick: Boolean = false

  private lazy val methodEditor = CodeMirrorEditor(
    logger,
    methodDraft,
    language = it.evadid.core.datastructures.language.AppLanguage.Java,
    config = Var(editorConfig),
    loadingLabel = loadingLabel,
    firstLineNumber = sourceDraft.signal.combineWith(editingRange.signal).map { (source, range) =>
      firstLineNumber(source, range)
    }.distinct
  )

  private lazy val sourceEditor = CodeMirrorEditor(
    logger,
    sourceDraft,
    language = it.evadid.core.datastructures.language.AppLanguage.Java,
    config = Var(editorConfig),
    diagnostics = CodeMirrorEditor.marks(conversionMarker),
    loadingLabel = loadingLabel,
    sameSource = sameSource
  )

  private def isJavaSource(value: ProgrammingState): Boolean = value match {
    case ProgrammingStateRaw(Java, _) | _: ProgrammingStateJavaStructured => true
    case _ => false
  }
  private def currentSourceAvailable(): Boolean = isJavaSource(state.now())

  private def refreshSource(code: String): List[JavaClass] = {
    val refreshed = analysis(code)
    sourceDraft.set(code)
    refreshed.classes
  }

  private def showSource(): Unit = {
    editingRange.set(None)
    sourceView.set(true)
  }

  private def handleMethodInput(draft: String): Unit =
    if draft == methodDraft.now() && currentSourceAvailable() && !sourceView.now() then
      (selectedMethod.now(), editingRange.now()) match
        case (Some(selected), Some(range)) =>
          range.replace(sourceCode, draft) match
            case Some((merged, nextRange)) if merged != sourceCode =>
              editingRange.set(Some(nextRange))
              val refreshedClasses = refreshSource(merged)
              if analysis(merged).balanced then
                findEditedMethod(merged, refreshedClasses, selected, nextRange) match
                  case Some(refreshedSelection) => selectedMethod.set(Some(refreshedSelection))
                  case None =>
                    selectedMethod.set(None)
                    showSource()
            case None => showSource()
            case _ => ()
        case _ => showSource()

  private def selectMethod(function: JavaMethod): Unit =
    if currentSourceAvailable() && analysis().balanced && analysis().classes.exists(_.methods.contains(function)) then
      showMethodSelection(function)
      if compactLayout.now() then structureOpen.set(false)

  private def showMethodSelection(function: JavaMethod, code: String = sourceCode): Unit =
    if MethodRange(function.start, function.end).isWithin(code) then
      selectedMethod.set(Some(function))
      editingRange.set(Some(MethodRange(function.start, function.end)))
      methodDraft.set(code.substring(function.start, function.end))
      sourceView.set(false)

  private def showMethod(): Unit =
    if currentSourceAvailable() && analysis().balanced then
      selectedMethod.now().flatMap(findSelection(analysis().classes, _)) match
        case Some(function) => showMethodSelection(function)
        case None =>
          selectedMethod.set(None)
          editingRange.set(None)
          sourceView.set(false)

  private def observeProgram(next: ProgrammingState): Unit = {
    val code = next match {
      case ProgrammingStateRaw(Java, value) => value
      case value: ProgrammingStateJavaStructured => value.toString
      case _ => ""
    }
    val refreshedClasses = analysis(code).classes
    if !sourceView.now() then {
      val range = editingRange.now()
      val fragmentMatches = range.exists(value => value.isWithin(code) && code.substring(value.start, value.end) == methodDraft.now())
      if !fragmentMatches then {
        selectedMethod.now().flatMap(findSelection(refreshedClasses, _)) match {
          case Some(function) => showMethodSelection(function, code)
          case None => showSource()
        }
      }
    }
  }

  // Structure dialogs: each edit is a new source text, accepted when the analysis finds the change.

  private def structureEditable: Boolean = currentSourceAvailable() && analysis().balanced

  private def openDialog(mode: DialogMode): Unit =
    if structureEditable then {
      def reset(name: String, javaType: String, visibility: Visibility, isStatic: Boolean, initializer: String = ""): Unit = {
        nameDraft.set(name)
        typeDraft.set(javaType)
        visibilityDraft.set(visibility)
        staticDraft.set(isStatic)
        initializerDraft.set(initializer)
      }
      parameterRows.set(Vector.empty)
      assignDraft.set(true)
      dialogError.set(None)
      mode match {
        case DialogMode.AddClass => reset("", "", Visibility.Public, isStatic = false)
        case DialogMode.AddField(_) => reset("", "int", Visibility.Private, isStatic = false)
        case DialogMode.EditField(field) =>
          reset(field.name, field.javaType, field.visibility, field.isStatic, field.initializer.getOrElse(""))
        case DialogMode.AddConstructor(_) => reset("", "", Visibility.Public, isStatic = false)
        case DialogMode.AddMethod(className) =>
          // Classes without object state or constructors keep to static methods, as in procedural programs.
          val procedural = analysis().classes.find(_.name == className).forall(clazz =>
            clazz.fields.forall(_.isStatic) && clazz.methods.forall(_.isStatic))
          reset("", "void", Visibility.Public, isStatic = procedural)
        case _: DialogMode.ConfirmDelete => ()
      }
      dialogMode.set(Some(mode))
    }

  private def targetClass(className: String): Option[JavaClass] = analysis().classes.find(_.name == className)

  private def accept(candidate: String, accepted: JavaMethodSource => Boolean, selection: JavaMethodSource => Option[JavaMethod],
      keepDiagramView: Boolean = false): Unit = {
    val next = JavaMethodSource(candidate)
    if next.balanced && accepted(next) then {
      commit(candidate, selection(next), keepDiagramView)
      dialogMode.set(None)
    } else dialogError.set(Some(DialogError.Invalid))
  }

  /** The parameter rows as parameters, or the first error among them. */
  private def parameters(): Either[DialogError, List[Param]] = {
    val rows = parameterRows.now().toList.map(row => Param(row.javaType.trim, row.name.trim))
    rows.collectFirst {
      case param if !validType(param.javaType) => DialogError.Type
      case param if !validName(param.name) => DialogError.Name
    }.orElse(Option.when(rows.map(_.name).distinct.size != rows.size)(DialogError.Duplicate)).toLeft(rows)
  }

  private def addClass(): Unit = {
    val name = nameDraft.now().trim
    if !validName(name) then dialogError.set(Some(DialogError.ClassName))
    else if analysis().classes.exists(_.name == name) then dialogError.set(Some(DialogError.Duplicate))
    else {
      val newline = lineSeparator(sourceCode)
      val separator = if sourceCode.isEmpty then "" else newline + newline
      accept(sourceCode + separator + s"class $name {$newline}$newline", _.classes.exists(_.name == name),
        _ => selectedMethod.now(), keepDiagramView = true)
    }
  }

  private def saveField(className: String, edited: Option[JavaField]): Unit = targetClass(className).foreach { clazz =>
    val name = nameDraft.now().trim
    val javaType = typeDraft.now().trim
    if !validName(name) then dialogError.set(Some(DialogError.Name))
    else if !validType(javaType) then dialogError.set(Some(DialogError.Type))
    else if clazz.fields.exists(field => field.name == name && !edited.exists(_.name == name)) then dialogError.set(Some(DialogError.Duplicate))
    else {
      val declaration = fieldDeclaration(visibilityDraft.now(), staticDraft.now(), javaType, name, initializerDraft.now())
      val candidate = edited.fold(insertField(sourceCode, clazz, declaration))(field => replaceRange(sourceCode, field.start, field.end, declaration))
      accept(candidate, _.classes.find(_.name == className).exists(_.fields.exists(_.name == name)), _ => selectedMethod.now(),
        keepDiagramView = true)
    }
  }

  private def addConstructor(className: String): Unit = targetClass(className).foreach { clazz =>
    parameters() match {
      case Left(error) => dialogError.set(Some(error))
      case Right(params) if clazz.methods.exists(method => method.isConstructor && method.params.map(_.javaType) == params.map(_.javaType)) =>
        dialogError.set(Some(DialogError.Duplicate))
      case Right(params) =>
        val fieldNames = clazz.fields.filterNot(_.isStatic).map(_.name).toSet
        val assigned = if assignDraft.now() then params.map(_.name).filter(fieldNames) else Nil
        val candidate = insertConstructor(sourceCode, clazz, constructorDeclaration(className, visibilityDraft.now(), params, assigned))
        accept(candidate, _.classes.find(_.name == className).exists(_.methods.count(_.isConstructor) > clazz.methods.count(_.isConstructor)),
          added(className, className, clazz))
    }
  }

  private def addMethod(className: String): Unit = targetClass(className).foreach { clazz =>
    val name = nameDraft.now().trim
    val returnType = typeDraft.now().trim
    if !validName(name) then dialogError.set(Some(DialogError.Name))
    else if returnType != "void" && !validType(returnType) then dialogError.set(Some(DialogError.Type))
    else parameters() match {
      case Left(error) => dialogError.set(Some(error))
      case Right(params) if clazz.methods.exists(method => method.name == name && method.params.map(_.javaType) == params.map(_.javaType)) =>
        dialogError.set(Some(DialogError.Duplicate))
      case Right(params) =>
        val declaration = methodDeclaration(returnType, name, javaParameters(params), staticDraft.now(), visibilityDraft.now())
        accept(insertMethod(sourceCode, clazz, declaration), _.classes.find(_.name == className).exists(_.methods.exists(_.name == name)),
          added(className, name, clazz))
    }
  }

  /** The operation a structure edit added, so the editor opens it. */
  private def added(className: String, name: String, before: JavaClass)(next: JavaMethodSource): Option[JavaMethod] =
    next.classes.find(_.name == className).flatMap(_.methods.find(method => method.name == name && !before.methods.exists(sameMethod(_, method))))

  private def deleteMember(className: String, start: Int, end: Int, operation: Boolean): Unit = {
    val deletedSelection = operation && selectedMethod.now().exists(method => method.start == start && method.className == className)
    val candidate = removeMember(sourceCode, start, end)
    accept(candidate, _.classes.exists(_.name == className), _ => if deletedSelection then None else selectedMethod.now(),
      keepDiagramView = true)
  }

  private def submit(mode: DialogMode): Unit = mode match {
    case DialogMode.AddClass => addClass()
    case DialogMode.AddField(className) => saveField(className, None)
    case DialogMode.EditField(field) => saveField(field.className, Some(field))
    case DialogMode.AddConstructor(className) => addConstructor(className)
    case DialogMode.AddMethod(className) => addMethod(className)
    case DialogMode.ConfirmDelete(className, _, start, end, operation) => deleteMember(className, start, end, operation)
  }

  /** Writes a structure edit and opens `selection`; without one the diagram view shows its placeholder. */
  private def commit(code: String, selection: Option[JavaMethod], keepDiagramView: Boolean = false): Unit = {
    // Writing the source lets `observeProgram` fall back to the full source; the view chosen before the edit wins.
    val wasSourceView = sourceView.now()
    val refreshedClasses = refreshSource(code)
    val refreshedSelection = selection.flatMap(findSelection(refreshedClasses, _))
    selectedMethod.set(refreshedSelection)
    refreshedSelection match
      case Some(function) if !keepDiagramView || !wasSourceView => showMethodSelection(function, code)
      case None if keepDiagramView && !wasSourceView =>
        editingRange.set(None)
        sourceView.set(false)
      case _ => showSource()
  }

  // Dialog rendering.

  private val typeListId = s"java-types-${UUID.randomUUID()}"
  private val returnTypeListId = s"java-return-types-${UUID.randomUUID()}"

  private def typeSuggestions(listId: String, withVoid: Boolean): Element =
    dataList(
      idAttr := listId,
      children <-- classes.map { current =>
        ((if withVoid then List("void") else Nil) ++ List("int", "double", "boolean", "char", "long", "String") ++
          current.map(_.name) ++ List("int[]", "String[]", "List<String>", "List<Integer>")).distinct.map(suggestion => option(value := suggestion))
      }
    )

  private def textField(caption: Signal[String], fieldValue: Var[String], listId: Option[String] = None,
      placeholderText: String = ""): Element =
    labelTag(
      cls := "java-function-editor__field",
      span(child.text <-- caption),
      input(
        typ := "text",
        spellCheck := false,
        listId.fold[Modifier[Input]](emptyMod)(id => htmlAttr("list", com.raquo.laminar.codecs.StringAsIsCodec) := id),
        placeholder := placeholderText,
        value <-- fieldValue.signal,
        onInput.mapToValue --> fieldValue.writer
      )
    )

  private def visibilityField(): Element =
    labelTag(
      cls := "java-function-editor__field",
      span(child.text <-- labels.visibility),
      select(
        value <-- visibilityDraft.signal.map(_.toString),
        Visibility.values.toList.map { visibility =>
          option(value := visibility.toString, s"${visibility.symbol} ${javaModifier(visibility).getOrElse("package")}")
        },
        onChange.mapToValue --> (value => visibilityDraft.set(Visibility.valueOf(value)))
      )
    )

  private def checkboxField(caption: Signal[String], checkedValue: Var[Boolean]): Element =
    labelTag(
      cls := "java-function-editor__method-option",
      input(typ := "checkbox", checked <-- checkedValue.signal, onChange.mapToChecked --> checkedValue.writer),
      span(child.text <-- caption)
    )

  private def parameterFields(): Element =
    fieldSet(
      cls := "java-function-editor__parameters",
      legend(child.text <-- labels.parameters),
      children <-- parameterRows.signal.split(_.id) { (id, _, row) =>
        def update(change: ParameterRow => ParameterRow): Unit = parameterRows.update(_.map(current => if current.id == id then change(current) else current))
        div(
          cls := "java-function-editor__parameter",
          input(typ := "text", spellCheck := false, cls := "java-function-editor__parameter-type",
            aria.label <-- labels.javaType, htmlAttr("list", com.raquo.laminar.codecs.StringAsIsCodec) := typeListId,
            value <-- row.map(_.javaType), onInput.mapToValue --> (value => update(_.copy(javaType = value)))),
          input(typ := "text", spellCheck := false, cls := "java-function-editor__parameter-name",
            aria.label <-- labels.name, value <-- row.map(_.name), onInput.mapToValue --> (value => update(_.copy(name = value)))),
          button(typ := "button", cls := "java-function-editor__button java-function-editor__button--small",
            aria.label <-- labels.removeParameter, title <-- labels.removeParameter, "×",
            onClick --> (_ => parameterRows.update(_.filterNot(_.id == id))))
        )
      },
      button(
        typ := "button",
        cls := "java-function-editor__button java-function-editor__button--small",
        span(aria.hidden := true, "+ "),
        child.text <-- labels.addParameter,
        onClick --> (_ => parameterRows.update(_ :+ ParameterRow(UUID.randomUUID().toString, "int", "")))
      )
    )

  /** The member as it will appear in the diagram and in Java, updated while typing. */
  private def preview(mode: DialogMode): Element = {
    val texts: Signal[(String, String)] = mode match {
      case DialogMode.AddField(_) | DialogMode.EditField(_) =>
        nameDraft.signal.combineWith(typeDraft.signal, visibilityDraft.signal, staticDraft.signal, initializerDraft.signal).map {
          (name, javaType, visibility, isStatic, initializer) =>
            (s"${visibility.symbol} ${name.trim}: ${javaType.trim}", fieldDeclaration(visibility, isStatic, javaType.trim, name.trim, initializer))
        }
      case DialogMode.AddConstructor(className) =>
        parameterRows.signal.combineWith(visibilityDraft.signal).map { (rows, visibility) =>
          val params = rows.toList.map(row => Param(row.javaType.trim, row.name.trim))
          val method = JavaMethod(className, className, "", javaParameters(params), 0, 0, visibility, isConstructor = true, params = params)
          (umlOperation(method), constructorDeclaration(className, visibility, params, Nil).takeWhile(_ != '{').trim)
        }
      case DialogMode.AddMethod(className) =>
        nameDraft.signal.combineWith(typeDraft.signal, visibilityDraft.signal, staticDraft.signal, parameterRows.signal).map {
          (name, returnType, visibility, isStatic, rows) =>
            val params = rows.toList.map(row => Param(row.javaType.trim, row.name.trim))
            val method = JavaMethod(className, name.trim, returnType.trim, javaParameters(params), 0, 0, visibility, isStatic, params = params)
            (umlOperation(method), methodDeclaration(returnType.trim, name.trim, javaParameters(params), isStatic, visibility).takeWhile(_ != '{').trim)
        }
      case _ => Val(("", ""))
    }
    val isStatic = mode match {
      case DialogMode.AddConstructor(_) => Val(false)
      case _ => staticDraft.signal
    }
    div(
      cls := "java-function-editor__preview",
      span(cls := "java-function-editor__preview-caption", child.text <-- labels.preview),
      code(cls <-- isStatic.map(if _ then "java-uml-line java-uml__member--static" else "java-uml-line"), child.text <-- texts.map(_._1)),
      code(cls := "java-function-editor__preview-java", child.text <-- texts.map(_._2))
    )
  }

  private def errorText(error: DialogError): Signal[String] = error match {
    case DialogError.ClassName => labels.errorClassName
    case DialogError.Name => labels.errorName
    case DialogError.Type => labels.errorType
    case DialogError.Duplicate => labels.errorDuplicate
    case DialogError.Invalid => labels.errorInvalid
  }

  private def dialogTitle(mode: DialogMode): Signal[String] = mode match {
    case DialogMode.AddClass => labels.addClass
    case DialogMode.AddField(_) => labels.addField
    case DialogMode.EditField(_) => labels.editField
    case DialogMode.AddConstructor(_) => labels.addConstructor
    case DialogMode.AddMethod(_) => labels.addMethod
    case _: DialogMode.ConfirmDelete => labels.delete
  }

  private def renderDialog(mode: DialogMode): Element = {
    val fields: Modifier[HtmlElement] = mode match {
      case DialogMode.AddClass => textField(labels.className, nameDraft)
      case DialogMode.AddField(_) | DialogMode.EditField(_) => div(
        cls := "java-function-editor__dialog-fields",
        visibilityField(),
        textField(labels.javaType, typeDraft, Some(typeListId)),
        textField(labels.name, nameDraft),
        textField(labels.initialValue, initializerDraft),
        checkboxField(labels.isStatic, staticDraft),
        preview(mode)
      )
      case DialogMode.AddConstructor(_) => div(
        cls := "java-function-editor__dialog-fields",
        visibilityField(),
        parameterFields(),
        if features.addField then checkboxField(labels.assignFields, assignDraft) else emptyMod,
        preview(mode)
      )
      case DialogMode.AddMethod(_) => div(
        cls := "java-function-editor__dialog-fields",
        visibilityField(),
        textField(labels.name, nameDraft),
        parameterFields(),
        textField(labels.returnType, typeDraft, Some(returnTypeListId)),
        checkboxField(labels.isStatic, staticDraft),
        preview(mode)
      )
      case DialogMode.ConfirmDelete(_, member, _, _, _) => div(
        p(child.text <-- labels.deleteConfirm),
        code(cls := "java-uml-line", member)
      )
    }
    val deletable = mode match {
      case DialogMode.EditField(field) if features.deleteMember && !field.shared => Some(field)
      case _ => None
    }
    dialogTag(
      role := "dialog",
      aria.label <-- dialogTitle(mode),
      cls := "java-function-editor__dialog",
      new EventProp[dom.Event]("cancel") --> { event =>
        event.preventDefault()
        event.stopPropagation()
        dialogMode.set(None)
      },
      onMountCallback(ctx => ctx.thisNode.ref.asInstanceOf[dom.html.Dialog].showModal()),
      onUnmountCallback(node => node.ref.asInstanceOf[dom.html.Dialog].close()),
      PageScrollLock.whileMounted,
      h2(cls := "java-function-editor__dialog-title", child.text <-- dialogTitle(mode)),
      typeSuggestions(typeListId, withVoid = false),
      typeSuggestions(returnTypeListId, withVoid = true),
      fields,
      p(
        cls := "java-function-editor__dialog-error",
        role := "alert",
        child.text <-- dialogError.signal.flatMapSwitch(_.fold(Val(""))(errorText))
      ),
      div(
        cls := "java-function-editor__dialog-actions",
        deletable.fold[Modifier[HtmlElement]](emptyMod) { field =>
          button(
            typ := "button",
            cls := "java-function-editor__button java-function-editor__button--danger",
            child.text <-- labels.delete,
            onClick --> (_ => dialogMode.set(Some(DialogMode.ConfirmDelete(field.className, umlAttribute(field), field.start, field.end, operation = false))))
          )
        },
        button(
          typ := "button",
          cls := "java-function-editor__button java-function-editor__button--secondary",
          child.text <-- labels.cancel,
          onClick --> (_ => dialogMode.set(None))
        ),
        button(
          typ := "button",
          cls := (mode match {
            case _: DialogMode.ConfirmDelete => "java-function-editor__button java-function-editor__button--danger"
            case _ => "java-function-editor__button java-function-editor__button--primary"
          }),
          dataAttr("action") := "submit",
          child.text <-- (mode match {
            case DialogMode.EditField(_) => labels.save
            case other => dialogTitle(other)
          }),
          onClick --> (_ => Future(submit(mode)))
        )
      )
    )
  }

  // Page layout.

  private def renderToolbar(navigationId: String): Element =
    div(
      cls := "java-function-editor__topbar",
      div(
        h2(cls := "java-function-editor__title", child.text <-- labels.title),
        p(cls := "java-function-editor__subtitle", child.text <-- labels.subtitle)
      ),
      div(
        cls := "java-function-editor__topbar-actions",
        button(
          typ := "button",
          cls := "java-function-editor__button java-function-editor__button--secondary",
          dataAttr("action") := "toggle-diagram",
          aria.controls := navigationId,
          aria.expanded <-- structureOpen.signal,
          child.text <-- structureOpen.signal.flatMapSwitch(if _ then labels.hideDiagram else labels.showDiagram),
          onClick --> (_ => structureOpen.update(!_))
        )
      )
    )

  private def renderDiagram(navigationId: String): Element =
    JavaClassDiagram.render(
      analysis = diagramSource,
      draft = structureReady.map(!_),
      locked = structureLocked,
      selected = selectedMethod.signal,
      features = features,
      labels = labels,
      actions = JavaClassDiagram.Actions(
        select = selectMethod,
        editField = field => openDialog(DialogMode.EditField(field)),
        addClass = () => openDialog(DialogMode.AddClass),
        addField = className => openDialog(DialogMode.AddField(className)),
        addConstructor = className => openDialog(DialogMode.AddConstructor(className)),
        addMethod = className => openDialog(DialogMode.AddMethod(className))
      ),
      navigationId = navigationId
    )

  private def renderSelectionHeading(): Element =
    div(
      child <-- sourceView.signal.combineWith(selectedMethod.signal).map {
        case (false, Some(method)) => div(
          h3(cls := "java-function-editor__section-title", s"${method.className}.${method.name}"),
          code(cls := (if method.isStatic then "java-uml-line java-uml__member--static" else "java-uml-line"), umlOperation(method))
        )
        case (fullSource, _) => div(
          h3(cls := "java-function-editor__section-title", child.text <-- (if fullSource then labels.sourceTitle else labels.chooseMethod)),
          p(cls := "java-function-editor__section-hint", child.text <-- labels.sourceHint)
        )
      }
    )

  private def renderWorkspaceHeader(): Element =
    div(
      cls := "java-function-editor__workspace-heading",
      renderSelectionHeading(),
      div(
        cls := "java-function-editor__view-toggle",
        button(
          typ := "button",
          cls <-- sourceView.signal.map(fullSource =>
            "java-function-editor__button " + (if fullSource then "java-function-editor__button--secondary" else "java-function-editor__button--primary")
          ),
          dataAttr("view") := "diagram",
          aria.pressed <-- sourceView.signal.map(!_).map(_.toString),
          disabled <-- structureLocked,
          child.text <-- labels.diagramView,
          onClick --> (_ => showMethod())
        ),
        button(
          typ := "button",
          cls <-- sourceView.signal.map(fullSource =>
            "java-function-editor__button " + (if fullSource then "java-function-editor__button--primary" else "java-function-editor__button--secondary")
          ),
          dataAttr("view") := "source",
          aria.pressed <-- sourceView.signal.map(_.toString),
          disabled <-- sourceAvailable.map(!_),
          child.text <-- labels.sourceView,
          onClick --> (_ => if currentSourceAvailable() then showSource())
        ),
        if features.deleteMember then child <-- sourceView.signal.combineWith(selectedMethod.signal).map {
          case (false, Some(method)) => button(
            typ := "button",
            cls := "java-function-editor__button java-function-editor__button--danger",
            dataAttr("action") := "delete-member",
            disabled <-- structureLocked,
            child.text <-- labels.delete,
            onClick --> (_ => openDialog(DialogMode.ConfirmDelete(method.className, umlOperation(method), method.start, method.end, operation = true)))
          )
          case _ => emptyNode
        } else emptyMod
      ),
      labelTag(
        cls := "java-function-editor__compact-navigation",
        span(child.text <-- labels.diagramView),
        select(
          aria.label <-- labels.methodMenu,
          disabled <-- structureLocked,
          children <-- classes.combineWith(labels.chooseMethod).map { (current, choose) =>
            option(value := "", choose) :: current.flatMap(_.methods).map { fn =>
              option(value := methodKey(fn), s"${fn.className}.${fn.name}(${fn.parameters})")
            }
          },
          value <-- selectedMethod.signal.combineWith(classes).map { (selected, _) => selected.fold("")(methodKey) },
          onChange.mapToValue --> { key =>
            analysis().classes.flatMap(_.methods).find(fn => methodKey(fn) == key).foreach(selectMethod)
          }
        )
      )
    )

  private def renderWorkspace(): Element =
    div(
      cls := "java-function-editor__workspace",
      renderWorkspaceHeader(),
      div(
        cls <-- sourceView.signal.combineWith(selectedMethod.signal).map { (fullSource, selection) =>
          if fullSource || selection.isDefined then "java-function-editor__editor-panel"
          else "java-function-editor__editor-panel java-function-editor__editor-panel--hidden"
        },
        child <-- sourceView.signal.combineWith(sourceAvailable).distinct.map { (fullSource, available) =>
          if !available then div(role := "alert", child.text <-- labels.notJava)
          else if fullSource then div(cls := "java-function-editor__source", sourceEditor.getDomElement())
          else div(cls := "java-function-editor__method", methodEditor.getDomElement())
        },
        span(
          cls <-- structureReady.map(if _ then "java-function-editor__draft-warning java-function-editor__draft-warning--hidden" else "java-function-editor__draft-warning"),
          child.text <-- labels.draftWarning
        )
      ),
      div(
        cls <-- sourceView.signal.combineWith(selectedMethod.signal).map { (fullSource, selection) =>
          if fullSource || selection.isDefined then "java-function-editor__editor-placeholder java-function-editor__editor-placeholder--hidden"
          else "java-function-editor__editor-placeholder"
        },
        strong(child.text <-- labels.chooseMethod),
        span(child.text <-- labels.chooseMethodHint)
      )
    )

  private def bindEditor(releaseLayout: Var[() => Unit]): Modifier[HtmlElement] =
    onMountCallback { ctx =>
      presentation.watch(ctx.owner)
      state.signal.foreach(observeProgram)(using ctx.owner)
      val lifetime = new Subscription(ctx.owner, () => ())
      // Parse after the transaction settles, so selecting a method cannot write an old fragment into a new range.
      methodDraft.signal.changes.distinct.foreach(draft => Future {
        if !lifetime.isKilled then handleMethodInput(draft)
      })(using ctx.owner)
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
        "java-function-based-editor editor-appearance" +
          (if reference.isDefined then " java-function-based-editor--with-reference" else "") +
          (if open then "" else " java-function-based-editor--structure-collapsed")
      },
      dataAttr("editor-theme") <-- presentation.theme,
      bindEditor(releaseLayout),
      onUnmountCallback(_ => releaseLayout.now()()),
      renderToolbar(navigationId),
      renderDiagram(navigationId),
      renderWorkspace(),
      reference.fold[Modifier[HtmlElement]](emptyMod) { renderReference =>
        asideTag(cls := "java-function-editor__reference", aria.label <-- labels.reference, renderReference())
      },
      child <-- dialogMode.signal.map(_.fold(emptyNode)(renderDialog))
    )
  }
}
