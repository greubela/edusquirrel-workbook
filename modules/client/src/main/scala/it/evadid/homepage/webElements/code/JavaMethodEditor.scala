package it.evadid.homepage.webElements.code

import it.evadid.core.datastructures.language.AppLanguage.Java

import it.evadid.workbook.elements.interactionElements.programming.state.{ProgrammingState, ProgrammingStateRaw, ProgrammingStateJavaStructured}
import com.raquo.airstream.state.Var
import com.raquo.airstream.ownership.Subscription
import com.raquo.laminar.api.L.{label as labelTag, *}
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.homepage.webElements.editor.code.EditorAppearance
import it.evadid.vm.code.errors.ProgrammingConversionError
import it.evadid.homepage.webElements.editor.code.codemirror.CodeMirrorEditor
import it.evadid.homepage.webElements.editor.config.CodeMirrorEditorConfig
import it.evadid.util.logging.Logger
import org.scalajs.dom

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import scala.util.Try
import java.util.UUID

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
  sameSource: (String, String) => Boolean = _ == _
) extends HtmlAppElement {

  import JavaMethodSource.*

  private enum DialogMode {
    case AddClass, AddMethod
  }

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
  private val initialSelection = sourceAnalysis.now().initialMethod(requirePublicMain)
  private val selectedMethod = Var(initialSelection)
  private val editingRange = Var(initialSelection.map(fn => MethodRange(fn.start, fn.end)))
  private val methodDraft = Var(initialSelection.fold("")(fn => sourceCode.substring(fn.start, fn.end)))
  private val structureReady = sourceAnalysis.signal.map(_.balanced)
  private val sourceView = Var(initialSelection.isEmpty)
  private val structureOpen = Var(true)
  private val compactLayout = Var(false)
  private val dialogMode = Var(Option.empty[DialogMode])
  private val dialogClassName = Var(Option.empty[String])
  private val classNameDraft = Var("")
  private val methodNameDraft = Var("newFunction")
  private val returnTypeDraft = Var("void")
  private val parametersDraft = Var("")
  private val staticMethodDraft = Var(true)
  private val dialogError = Var("")
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

  private def openClassDialog(): Unit =
    if currentSourceAvailable() && analysis().balanced then
      classNameDraft.set("")
      dialogError.set("")
      dialogMode.set(Some(DialogMode.AddClass))

  private def openMethodDialog(className: String): Unit =
    if currentSourceAvailable() && analysis().balanced then
      dialogClassName.set(Some(className))
      methodNameDraft.set("newFunction")
      returnTypeDraft.set("void")
      parametersDraft.set("")
      staticMethodDraft.set(true)
      dialogError.set("")
      dialogMode.set(Some(DialogMode.AddMethod))

  private def addClass(): Unit = {
    if !currentSourceAvailable() then return
    val name = classNameDraft.now().trim
    if !name.matches("[A-Za-z_$][A-Za-z0-9_$]*") then
      dialogError.set("Enter a valid Java class name.")
    else {
      val newline = lineSeparator(sourceCode)
      val separator = if sourceCode.isEmpty then "" else newline + newline
      val candidate = sourceCode + separator + s"class $name {$newline}$newline"
      if parses(candidate) then {
        commit(candidate, None)
        dialogMode.set(None)
      } else dialogError.set("The class could not be added to the current Java source.")
    }
  }

  private def addMethod(): Unit = {
    if !currentSourceAvailable() then return
    val name = methodNameDraft.now().trim
    val returnType = returnTypeDraft.now().trim
    val parameters = parametersDraft.now().trim
    if !name.matches("[A-Za-z_$][A-Za-z0-9_$]*") then
      dialogError.set("Enter a valid Java function name.")
    else if returnType.isEmpty then
      dialogError.set("Enter a return type.")
    else
      dialogClassName.now()
        .flatMap(className => analysis().classes.find(_.name == className))
        .foreach { targetClass =>
          val declaration = methodDeclaration(returnType = returnType, name = name,
            parameters = parameters, isStatic = staticMethodDraft.now())
          val candidate = insertMethod(sourceCode, targetClass, declaration)
          if parses(candidate) then {
            val refreshedClasses = discover(candidate)
            val addedFunction = refreshedClasses
              .find(_.name == targetClass.name)
              .flatMap(clazz => clazz.methods.find(fn => fn.start >= targetClass.bodyEnd && fn.name == name))
            commit(candidate, addedFunction)
            dialogMode.set(None)
          } else dialogError.set("The function signature or current Java source is invalid.")
        }
  }

  private def commit(code: String, selection: Option[JavaMethod]): Unit = {
    val refreshedClasses = refreshSource(code)
    val refreshedSelection = selection.flatMap(findSelection(refreshedClasses, _))
    selectedMethod.set(refreshedSelection)
    refreshedSelection match
      case Some(function) => showMethodSelection(function, code)
      case None => showSource()
  }

  private def modalField(fieldLabel: String, fieldValue: Var[String]): Element =
    labelTag(
      cls := "java-function-editor__field",
      span(fieldLabel),
      input(
        typ := "text",
        value <-- fieldValue.signal,
        onInput.mapToValue --> fieldValue.writer
      )
    )

  private def renderDialog(mode: DialogMode): Element = {
    val isClassDialog = mode == DialogMode.AddClass
    dialogTag(
      role := "dialog",
      aria.label := (if isClassDialog then "Add class" else "Add function"),
      cls := "java-function-editor__dialog",
      new EventProp[dom.Event]("cancel") --> { event =>
        event.preventDefault()
        event.stopPropagation()
        dialogMode.set(None)
      },
      onMountCallback(ctx => ctx.thisNode.ref.asInstanceOf[dom.html.Dialog].showModal()),
      onUnmountCallback(node => node.ref.asInstanceOf[dom.html.Dialog].close()),
      h2(cls := "java-function-editor__dialog-title", if isClassDialog then "Add class" else "Add function"),
      if isClassDialog then modalField("Class name", classNameDraft)
      else div(cls := "java-function-editor__dialog-fields",
        modalField("Function name", methodNameDraft),
        modalField("Return type", returnTypeDraft),
        modalField("Parameters (for example: int count, String label)", parametersDraft),
        labelTag(
          cls := "java-function-editor__method-option",
          input(typ := "checkbox", checked <-- staticMethodDraft.signal,
            onChange.mapToChecked --> staticMethodDraft.writer),
          span("Static method")
        )
      ),
      p(
        cls := "java-function-editor__dialog-error",
        role := "alert",
        child.text <-- dialogError.signal
      ),
      div(
        cls := "java-function-editor__dialog-actions",
        button(
          typ := "button",
          cls := "java-function-editor__button java-function-editor__button--secondary",
          "Cancel",
          onClick --> (_ => dialogMode.set(None))
        ),
        button(
          typ := "button",
          cls := "java-function-editor__button java-function-editor__button--primary",
          if isClassDialog then "Add class" else "Add function",
          onClick --> (_ => Future { if isClassDialog then addClass() else addMethod() })
        )
      )
    )
  }

  private def renderToolbar(navigationId: String): Element =
    div(
      cls := "java-function-editor__topbar",
      div(
        h2(cls := "java-function-editor__title", "Java editor"),
        p(cls := "java-function-editor__subtitle", "Explore classes and edit one method at a time.")
      ),
      div(
        cls := "java-function-editor__topbar-actions",
        button(
          typ := "button",
          cls := "java-function-editor__button java-function-editor__button--secondary",
          aria.controls := navigationId,
          aria.expanded <-- structureOpen.signal,
          child.text <-- structureOpen.signal.map(if _ then "Hide classes" else "Show classes"),
          onClick --> (_ => structureOpen.update(!_))
        ),
        span(
          cls := "java-function-editor__sync",
          role := "status",
          aria.live := "polite",
          child.text <-- sourceView.signal.map(if _ then "Full source" else "Method view")
        )
      )
    )

  private def renderClassNavigation(navigationId: String): Element =
    navTag(
      idAttr := navigationId,
      aria.label := "Java classes and methods",
      cls := "java-function-editor__diagram-section",
      div(
        cls := "java-function-editor__section-heading",
        div(
          h3(cls := "java-function-editor__section-title", "Classes"),
          p(cls := "java-function-editor__section-hint", "Select a method to open it in the editor.")
        ),
        button(
          typ := "button",
          cls := "java-function-editor__button java-function-editor__button--primary",
          "Add class",
          disabled <-- structureReady.combineWith(sourceAvailable).map { (ready, available) => !ready || !available },
          onClick --> (_ => openClassDialog())
        )
      ),
      div(
        cls := "java-function-editor__class-row",
        children <-- classes.combineWith(structureReady).map {
          case (currentClasses, isSynced) =>
            if currentClasses.isEmpty then List(
              div(
                cls := "java-function-editor__empty-diagram",
                strong("No classes yet"),
                span("Add a class to start building your program.")
              )
            )
            else currentClasses.map { clazz =>
              div(
                cls := "java-function-editor__class-card",
                div(
                  cls := "java-function-editor__class-header",
                  strong(cls := "java-function-editor__class-name", clazz.name),
                  button(
                    typ := "button",
                    cls := "java-function-editor__button java-function-editor__button--small",
                    "Add function",
                    disabled := !isSynced,
                    onClick --> (_ => openMethodDialog(clazz.name))
                  )
                ),
                div(
                  cls := "java-function-editor__function-list",
                  if clazz.methods.isEmpty then span(cls := "java-function-editor__empty-class", "No functions yet")
                  else clazz.methods.map { function =>
                    button(
                      typ := "button",
                      cls <-- selectedMethod.signal.map { current =>
                        if current.exists(current => sameMethod(current, function) && current.start == function.start) then
                          "java-function-editor__function java-function-editor__function--selected"
                        else "java-function-editor__function"
                      },
                      disabled := !isSynced,
                      aria.pressed <-- selectedMethod.signal.map(_.exists(current =>
                        sameMethod(current, function) && current.start == function.start).toString),
                      title := s"${function.returnType} ${function.name}(${function.parameters})",
                      span(cls := "java-function-editor__function-name", s"${function.name}()"),
                      span(cls := "java-function-editor__function-signature", s"${function.returnType} · ${function.parameters}"),
                      onClick --> (_ => selectMethod(function))
                    )
                  }
                )
              )
            }
        }
      )
    )

  private def renderWorkspaceHeader(): Element =
    div(
      cls := "java-function-editor__workspace-heading",
      div(
        h3(cls := "java-function-editor__section-title", child.text <-- sourceView.signal.combineWith(selectedMethod.signal).map { (fullSource, selection) =>
          if fullSource then "Java source" else selection.fold("Function editor")(fn => s"${fn.className}.${fn.name}")
        }),
        p(cls := "java-function-editor__section-hint", "Your edits are kept, including unfinished code.")
      ),
      div(
        cls := "java-function-editor__view-toggle",
        button(
          typ := "button",
          cls <-- sourceView.signal.map(fullSource =>
            "java-function-editor__button " + (if fullSource then "java-function-editor__button--secondary" else "java-function-editor__button--primary")
          ),
          aria.pressed <-- sourceView.signal.map(!_).map(_.toString),
          disabled <-- structureReady.combineWith(sourceAvailable).map { (ready, available) => !ready || !available },
          "Method",
          aria.label := "Method",
          title := "Method view",
          onClick --> (_ => showMethod())
        ),
        button(
          typ := "button",
          cls <-- sourceView.signal.map(fullSource =>
            "java-function-editor__button " + (if fullSource then "java-function-editor__button--primary" else "java-function-editor__button--secondary")
          ),
          aria.pressed <-- sourceView.signal.map(_.toString),
          disabled <-- sourceAvailable.map(!_),
          "Full source",
          aria.label := "Full source",
          title := "Full Java source",
          onClick --> (_ => if currentSourceAvailable() then showSource())
        )
      ),
      labelTag(
        cls := "java-function-editor__compact-navigation",
        span("Method"),
        select(
          aria.label := "Java method",
          disabled <-- structureReady.combineWith(sourceAvailable).map { (ready, available) => !ready || !available },
          children <-- classes.map { current =>
            option(value := "", "Choose a method") :: current.flatMap(_.methods).map { fn =>
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
          if !available then div(role := "alert", "This state cannot be shown as Java. Your source is unchanged.")
          else if fullSource then div(cls := "java-function-editor__source", sourceEditor.getDomElement())
          else div(cls := "java-function-editor__method", methodEditor.getDomElement())
        },
        span(
          cls <-- structureReady.map(if _ then "java-function-editor__draft-warning java-function-editor__draft-warning--hidden" else "java-function-editor__draft-warning"),
          "Your draft is kept. Finish the brackets or comments here, or open the full source to continue."
        )
      ),
      div(
        cls <-- sourceView.signal.combineWith(selectedMethod.signal).map { (fullSource, selection) =>
          if fullSource || selection.isDefined then "java-function-editor__editor-placeholder java-function-editor__editor-placeholder--hidden"
          else "java-function-editor__editor-placeholder"
        },
        strong("Choose a function to get started"),
        span("Select a method in the class list. Its complete signature and body will appear here.")
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
      renderClassNavigation(navigationId),
      renderWorkspace(),
      reference.fold[Modifier[HtmlElement]](emptyMod) { renderReference =>
        asideTag(cls := "java-function-editor__reference", aria.label := "Task reference", renderReference())
      },
      child <-- dialogMode.signal.map(_.fold(emptyNode)(renderDialog))
    )
  }
}
