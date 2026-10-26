package it.evadid.homepage.webElements.code

import com.raquo.airstream.state.Var
import com.raquo.airstream.ownership.Subscription
import com.raquo.laminar.api.L.{label as labelTag, *}
import com.raquo.laminar.nodes.ReactiveSvgElement
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.homepage.webElements.editor.code.EditorAppearance
import it.evadid.homepage.webElements.editor.code.EvaEditor.{EvaEditor, EvaEditorTab}
import it.evadid.homepage.webElements.editor.code.codemirror.CodeMirrorEditor
import it.evadid.homepage.webElements.editor.config.CodeMirrorEditorConfig
import it.evadid.util.logging.Logger
import it.evadid.vm.parsing.java.clean.JavaParser
import org.scalajs.dom

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import scala.util.Try
import java.util.UUID
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingState.ProgrammingState.ProgrammingStateJavaString
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingState
import it.evadid.workbook.model.qr.QrErrorCorrection.L.*
import org.scalajs.dom.SVGSVGElement


final class JavaFunctionBasedEditor
(
  logger: Logger,
  val state: Var[ProgrammingState],
  reference: Option[() => Element] = None,
  appearance: Option[EditorAppearance] = None,
  editorConfig: CodeMirrorEditorConfig = CodeMirrorEditorConfig.Default
) extends HtmlAppElement {

  import JavaFunctionBasedEditor.*

  private val initialSource = Try(state.now().toJava.code).toOption
  private val sourceDraft = state.bimap {
    case ProgrammingStateJavaString(code) => code
    case _ => ""
  }(ProgrammingStateJavaString(_)).distinct
  private def sourceCode: String = state.now() match {
    case ProgrammingStateJavaString(code) => code
    case _ => ""
  }
  private val sourceAvailable = Var(initialSource.isDefined)
  private val classes = Var(discover(sourceCode))
  private val initialSelection = initialFunction(sourceCode)
  private val selectedFunction = Var(initialSelection)
  private val editingRange = Var(initialSelection.map(fn => FunctionRange(fn.start, fn.end)))
  private val functionDraft = Var(initialSelection.fold("")(fn => sourceCode.substring(fn.start, fn.end)))
  private val structureReady = Var(structurallyBalanced(sourceCode))
  private val sourceView = Var(initialSelection.isEmpty)
  private val structureOpen = Var(true)
  private val compactLayout = Var(false)
  private val dialogMode = Var(Option.empty[DialogMode])
  private val dialogClassName = Var(Option.empty[String])
  private val classNameDraft = Var("")
  private val functionNameDraft = Var("newFunction")
  private val returnTypeDraft = Var("void")
  private val parametersDraft = Var("")
  private val staticMethodDraft = Var(true)
  private val dialogError = Var("")
  private val presentation = appearance.getOrElse(new EditorAppearance)

  override def dismissOnOutsideClick: Boolean = false


  private lazy val functionEditor = CodeMirrorEditor(
    logger,
    functionDraft,
    language = it.evadid.core.datastructures.language.AppLanguage.Java,
    config = Var(editorConfig),
    firstLineNumber = sourceDraft.signal.combineWith(editingRange.signal).map { (source, range) =>
      firstLineNumber(source, range)
    }.distinct
  )

  private lazy val sourceEditor = CodeMirrorEditor(
    logger,
    sourceDraft,
    language = it.evadid.core.datastructures.language.AppLanguage.Java,
    config = Var(editorConfig)
  )

  private def currentSourceAvailable(): Boolean = sourceAvailable.now()

  private def refreshSource(code: String): List[JavaClass] = {
    sourceDraft.set(code)
    val refreshedClasses = discover(code)
    classes.set(refreshedClasses)
    structureReady.set(structurallyBalanced(code))
    refreshedClasses
  }

  private def showSource(): Unit = {
    editingRange.set(None)
    sourceView.set(true)
  }

  private def handleFunctionInput(draft: String): Unit =
    if draft == functionDraft.now() && currentSourceAvailable() && !sourceView.now() then
      (selectedFunction.now(), editingRange.now()) match
        case (Some(selected), Some(range)) =>
          range.replace(sourceCode, draft) match
            case Some((merged, nextRange)) if merged != sourceCode =>
              editingRange.set(Some(nextRange))
              val refreshedClasses = refreshSource(merged)
              if structurallyBalanced(merged) then
                findEditedFunction(merged, refreshedClasses, selected, nextRange) match
                  case Some(refreshedSelection) => selectedFunction.set(Some(refreshedSelection))
                  case None =>
                    selectedFunction.set(None)
                    showSource()
            case None => showSource()
            case _ => ()
        case _ => showSource()

  private def selectFunction(function: JavaFunction): Unit =
    if currentSourceAvailable() && structurallyBalanced(sourceCode) && discover(sourceCode).exists(_.functions.contains(function)) then
      showFunction(function)
      if compactLayout.now() then structureOpen.set(false)

  private def showFunction(function: JavaFunction, code: String = sourceCode): Unit =
    if FunctionRange(function.start, function.end).isWithin(code) then
      selectedFunction.set(Some(function))
      editingRange.set(Some(FunctionRange(function.start, function.end)))
      functionDraft.set(code.substring(function.start, function.end))
      sourceView.set(false)

  private def showMethod(): Unit =
    if currentSourceAvailable() && structurallyBalanced(sourceCode) then
      selectedFunction.now().flatMap(findSelection(discover(sourceCode), _)) match
        case Some(function) => showFunction(function)
        case None =>
          selectedFunction.set(None)
          editingRange.set(None)
          sourceView.set(false)

  private def observeProgram(next: ProgrammingState): Unit = {
    val code = next match {
      case ProgrammingStateJavaString(value) => value
      case _ => ""
    }
    val refreshedClasses = discover(code)
    classes.set(refreshedClasses)
    structureReady.set(structurallyBalanced(code))
    sourceAvailable.set(next.isInstanceOf[ProgrammingStateJavaString])
    if !sourceView.now() then {
      val range = editingRange.now()
      val fragmentMatches = range.exists(value => value.isWithin(code) && code.substring(value.start, value.end) == functionDraft.now())
      if !fragmentMatches then {
        selectedFunction.now().flatMap(findSelection(refreshedClasses, _)) match {
          case Some(function) => showFunction(function, code)
          case None => showSource()
        }
      }
    }
  }

  private def openClassDialog(): Unit =
    if currentSourceAvailable() && structureReady.now() then
      classNameDraft.set("")
      dialogError.set("")
      dialogMode.set(Some(DialogMode.AddClass))

  private def openFunctionDialog(className: String): Unit =
    if currentSourceAvailable() && structureReady.now() then
      dialogClassName.set(Some(className))
      functionNameDraft.set("newFunction")
      returnTypeDraft.set("void")
      parametersDraft.set("")
      staticMethodDraft.set(true)
      dialogError.set("")
      dialogMode.set(Some(DialogMode.AddFunction))

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

  private def addFunction(): Unit = {
    if !currentSourceAvailable() then return
    val name = functionNameDraft.now().trim
    val returnType = returnTypeDraft.now().trim
    val parameters = parametersDraft.now().trim
    if !name.matches("[A-Za-z_$][A-Za-z0-9_$]*") then
      dialogError.set("Enter a valid Java function name.")
    else if returnType.isEmpty then
      dialogError.set("Enter a return type.")
    else
      dialogClassName.now()
        .flatMap(className => classes.now().find(_.name == className))
        .foreach { targetClass =>
          val declaration = functionDeclaration(returnType = returnType, name = name,
            parameters = parameters, isStatic = staticMethodDraft.now())
          val candidate = insertFunction(sourceCode, targetClass, declaration)
          if parses(candidate) then {
            val refreshedClasses = discover(candidate)
            val addedFunction = refreshedClasses
              .find(_.name == targetClass.name)
              .flatMap(clazz => clazz.functions.find(fn => fn.start >= targetClass.bodyEnd && fn.name == name))
            commit(candidate, addedFunction)
            dialogMode.set(None)
          } else dialogError.set("The function signature or current Java source is invalid.")
        }
  }

  private def commit(code: String, selection: Option[JavaFunction]): Unit = {
    val refreshedClasses = refreshSource(code)
    val refreshedSelection = selection.flatMap(findSelection(refreshedClasses, _))
    selectedFunction.set(refreshedSelection)
    refreshedSelection match
      case Some(function) => showFunction(function, code)
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
        modalField("Function name", functionNameDraft),
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
          onClick --> (_ => Future { if isClassDialog then addClass() else addFunction() })
        )
      )
    )
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
      onMountCallback { ctx =>
        state.signal.foreach(observeProgram)(using ctx.owner)
        val lifetime = new Subscription(ctx.owner, () => ())
        // Parse after the transaction settles, so selecting a method cannot write an old fragment into a new range.
        functionDraft.signal.changes.distinct.foreach(draft => Future {
          if !lifetime.isKilled then handleFunctionInput(draft)
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
      },
      onUnmountCallback(_ => releaseLayout.now()()),
      div(
        cls := "java-function-editor__topbar",
        div(
          h2(cls := "java-function-editor__title", "Java editor"),
          p(cls := "java-function-editor__subtitle", "Explore classes and edit one method at a time.")
        ),
        div(
          cls := "java-function-editor__topbar-actions",
          if appearance.isEmpty then presentation.toggleButton() else emptyNode,
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
      ),
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
            disabled <-- structureReady.signal.combineWith(sourceAvailable.signal).map { (ready, available) => !ready || !available },
            onClick --> (_ => openClassDialog())
          )
        ),
        div(
          cls := "java-function-editor__class-row",
          children <-- classes.signal.combineWith(structureReady.signal).map {
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
                      onClick --> (_ => openFunctionDialog(clazz.name))
                    )
                  ),
                  div(
                    cls := "java-function-editor__function-list",
                    if clazz.functions.isEmpty then span(cls := "java-function-editor__empty-class", "No functions yet")
                    else clazz.functions.map { function =>
                      button(
                        typ := "button",
                        cls <-- selectedFunction.signal.map { current =>
                          if current.exists(current => sameFunction(current, function) && current.start == function.start) then
                            "java-function-editor__function java-function-editor__function--selected"
                          else "java-function-editor__function"
                        },
                        disabled := !isSynced,
                        aria.pressed <-- selectedFunction.signal.map(_.exists(current =>
                          sameFunction(current, function) && current.start == function.start).toString),
                        title := s"${function.returnType} ${function.name}(${function.parameters})",
                        span(cls := "java-function-editor__function-name", s"${function.name}()"),
                        span(cls := "java-function-editor__function-signature", s"${function.returnType} · ${function.parameters}"),
                        onClick --> (_ => selectFunction(function))
                      )
                    }
                  )
                )
              }
          }
        )
      ),
      div(
        cls := "java-function-editor__workspace",
        div(
          cls := "java-function-editor__workspace-heading",
          div(
            h3(cls := "java-function-editor__section-title", child.text <-- sourceView.signal.combineWith(selectedFunction.signal).map { (fullSource, selection) =>
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
              disabled <-- structureReady.signal.combineWith(sourceAvailable.signal).map { (ready, available) => !ready || !available },
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
              disabled <-- sourceAvailable.signal.map(!_),
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
              disabled <-- structureReady.signal.combineWith(sourceAvailable.signal).map { (ready, available) => !ready || !available },
              children <-- classes.signal.map { current =>
                option(value := "", "Choose a method") :: current.flatMap(_.functions).map { fn =>
                  option(value := functionKey(fn), s"${fn.className}.${fn.name}(${fn.parameters})")
                }
              },
              value <-- selectedFunction.signal.combineWith(classes.signal).map { (selected, _) => selected.fold("")(functionKey) },
              onChange.mapToValue --> { key =>
                classes.now().flatMap(_.functions).find(fn => functionKey(fn) == key).foreach(selectFunction)
              }
            )
          )
        ),
        div(
          cls <-- sourceView.signal.combineWith(selectedFunction.signal).map { (fullSource, selection) =>
            if fullSource || selection.isDefined then "java-function-editor__editor-panel"
            else "java-function-editor__editor-panel java-function-editor__editor-panel--hidden"
          },
          child <-- sourceView.signal.combineWith(sourceAvailable.signal).distinct.map { (fullSource, available) =>
            if !available then div(role := "alert", "This state cannot be shown as Java. Your source is unchanged.")
            else if fullSource then div(cls := "java-function-editor__source", sourceEditor.getDomElement())
            else div(cls := "java-function-editor__method", functionEditor.getDomElement())
          },
          span(
            cls <-- structureReady.signal.map(if _ then "java-function-editor__draft-warning java-function-editor__draft-warning--hidden" else "java-function-editor__draft-warning"),
            "Your draft is kept. Finish the brackets or comments here, or open the full source to continue."
          )
        ),
        div(
          cls <-- sourceView.signal.combineWith(selectedFunction.signal).map { (fullSource, selection) =>
            if fullSource || selection.isDefined then "java-function-editor__editor-placeholder java-function-editor__editor-placeholder--hidden"
            else "java-function-editor__editor-placeholder"
          },
          strong("Choose a function to get started"),
          span("Select a method in the class list. Its complete signature and body will appear here.")
        )
      ),
      reference.fold[Modifier[HtmlElement]](emptyMod) { renderReference =>
        asideTag(cls := "java-function-editor__reference", aria.label := "Task reference", renderReference())
      },
      child <-- dialogMode.signal.map(_.fold(emptyNode)(renderDialog))
    )
  }
}

object JavaFunctionBasedEditor {
  private enum DialogMode derives upickle.default.ReadWriter {
    case AddClass, AddFunction
  }

  private[code] final case class JavaFunction(
                                               className: String,
                                               name: String,
                                               returnType: String,
                                               parameters: String,
                                               start: Int,
                                               end: Int
                                             ) derives upickle.default.ReadWriter

  private[code] final case class JavaClass(name: String, bodyStart: Int, bodyEnd: Int, functions: List[JavaFunction]) derives upickle.default.ReadWriter

  private def functionKey(function: JavaFunction): String =
    s"${function.className}:${function.start}:${function.end}"

  private[code] final case class FunctionRange(start: Int, end: Int) {
    def isWithin(source: String): Boolean = start >= 0 && end >= start && end <= source.length

    def replace(source: String, draft: String): Option[(String, FunctionRange)] =
      Option.when(isWithin(source))(
        (source.substring(0, start) + draft + source.substring(end), FunctionRange(start, start + draft.length))
      )
  }

  private[code] def firstLineNumber(source: String, range: Option[FunctionRange]): Int =
    range.filter(_.isWithin(source)).fold(1) { current =>
      1 + (0 until current.start).count { index =>
        source.charAt(index) == '\n' || source.charAt(index) == '\r' &&
          (index + 1 == source.length || source.charAt(index + 1) != '\n')
      }
    }

  private val classDeclaration = """\bclass\s+([A-Za-z_$][A-Za-z0-9_$]*)""".r
  private val functionHeader =
    """(?s)(?:(?:public|private|protected|static|final|abstract|synchronized|native|strictfp|default)\s+)*(?:<[^>]+>\s*)?([A-Za-z_$][\w.$<>?,\[\] ]*)\s+([A-Za-z_$][\w$]*)\s*\(([^{}]*)\)\s*(?:throws\s+[\w.$, ]+\s*)?""".r

  private[code] def parses(code: String): Boolean =
    JavaParser.parse(code).isRight && structurallyBalanced(code)

  private[code] def initialFunction(source: String): Option[JavaFunction] = {
    if !structurallyBalanced(source) then None
    else {
      val currentClasses = discover(source)
      currentClasses.flatMap(_.functions).filter(_.name == "main") match
        case List(function) if function.returnType == "void" &&
          function.parameters.matches("(?:java\\.lang\\.)?String\\s*\\[\\s*\\]\\s+[A-Za-z_$][\\w$]*") &&
          findSelection(currentClasses, function).isDefined =>
          val modifiers = maskCommentsAndStrings(source.substring(function.start, function.end))
            .takeWhile(_ != '(').split("\\s+").toSet
          Option.when(modifiers.contains("public") && modifiers.contains("static"))(function)
        case _ => None
    }
  }

  private def structurallyBalanced(source: String): Boolean = {
    val (masked, closedLexemes) = maskAndCheckLexemes(source)
    if !closedLexemes then false
    else {
      masked.foldLeft(Option(List.empty[Char])) {
        case (Some(stack), opening@('(' | '[' | '{')) => Some(opening :: stack)
        case (Some(stack), closing@(')' | ']' | '}')) =>
          val expectedOpening = closing match
            case ')' => '('
            case ']' => '['
            case _ => '{'
          stack match
            case head :: tail if head == expectedOpening => Some(tail)
            case _ => None
        case (stack, _) => stack
      }.contains(Nil)
    }
  }

  private[code] def discover(source: String): List[JavaClass] = {
    val masked = maskCommentsAndStrings(source)
    val matchingBraces = braceMatches(masked)
    classDeclaration.findAllMatchIn(masked).flatMap { declaration =>
      val opening = masked.indexOf('{', declaration.end)
      if opening < 0 || matchingBraces(opening) < 0 then None
      else {
        val name = declaration.group(1)
        val closing = matchingBraces(opening)
        val functions = discoverFunctions(masked, matchingBraces, name, opening, closing)
        Some(JavaClass(name, opening, closing, functions))
      }
    }.toList
  }

  private def discoverFunctions(
                                 masked: String,
                                 braceMatches: Vector[Int],
                                 className: String,
                                 classOpening: Int,
                                 classClosing: Int
                               ): List[JavaFunction] = {
    @scala.annotation.tailrec
    def scan(index: Int, segmentStart: Int, functions: List[JavaFunction]): List[JavaFunction] =
      if index >= classClosing then functions.reverse
      else masked.charAt(index) match
        case ';' => scan(index + 1, index + 1, functions)
        case '{' =>
          functionHeader.findFirstMatchIn(masked.substring(segmentStart, index).trim) match
            case Some(header) =>
              val signatureStart = segmentStart + masked.substring(segmentStart, index).indexWhere(!_.isWhitespace)
              val end = braceMatches(index) + 1
              val function = JavaFunction(className, header.group(2), header.group(1).trim,
                header.group(3).trim, signatureStart, end)
              scan(end, end, function :: functions)
            case None =>
              val end = braceMatches(index)
              if end > index then scan(end + 1, end + 1, functions)
              else scan(index + 1, segmentStart, functions)
        case _ => scan(index + 1, segmentStart, functions)
    scan(classOpening + 1, classOpening + 1, Nil)
  }

  private[code] def replaceFunction(source: String, function: JavaFunction, draft: String): String =
    source.substring(0, function.start) + draft + source.substring(function.end)

  private[code] def findEditedFunction(
                                        source: String,
                                        currentClasses: List[JavaClass],
                                        previous: JavaFunction,
                                        range: FunctionRange
                                      ): Option[JavaFunction] = {
    if !range.isWithin(source) then None
    else currentClasses.filter(_.name == previous.className) match
      case List(clazz) if range.start > clazz.bodyStart && range.end <= clazz.bodyEnd =>
        clazz.functions.filter(function => function.start >= range.start && function.end <= range.end) match
          case List(function) if onlyTrivia(source.substring(range.start, function.start)) &&
            onlyTrivia(source.substring(function.end, range.end)) => Some(function)
          case _ => None
      case _ => None
  }

  private[code] def sameFunction(first: JavaFunction, second: JavaFunction): Boolean =
    first.className == second.className && first.name == second.name &&
      first.returnType == second.returnType && first.parameters == second.parameters

  private[code] def findSelection(classes: List[JavaClass], previous: JavaFunction): Option[JavaFunction] =
    classes.filter(_.name == previous.className) match
      case List(clazz) => clazz.functions.filter(sameFunction(_, previous)) match
        case List(function) => Some(function)
        case _ => None
      case _ => None

  private def onlyTrivia(source: String): Boolean = {
    @scala.annotation.tailrec
    def scan(index: Int): Boolean =
      if index >= source.length then true
      else if source.charAt(index).isWhitespace then scan(index + 1)
      else if source.startsWith("//", index) then
        val end = source.indexWhere(char => char == '\r' || char == '\n', index + 2)
        scan(if end < 0 then source.length else end)
      else if source.startsWith("/*", index) then
        val end = source.indexOf("*/", index + 2)
        if end < 0 then false else scan(end + 2)
      else false
    scan(0)
  }

  private def lineSeparator(source: String): String =
    "\r\n|\r|\n".r.findFirstIn(source).getOrElse("\n")

  private[code] def functionDeclaration(returnType: String, name: String, parameters: String, isStatic: Boolean): String = {
    val modifiers = if isStatic then "public static" else "public"
    s"$modifiers $returnType $name($parameters) {\n    \n  }"
  }

  private[code] def insertFunction(source: String, clazz: JavaClass, declaration: String): String = {
    val beforeClosingBrace = source.substring(0, clazz.bodyEnd)
    val afterClosingBrace = source.substring(clazz.bodyEnd)
    val newline = lineSeparator(source)
    val inserted = declaration.replaceAll("\r\n|\r|\n", newline)
    beforeClosingBrace + s"$newline  $inserted$newline" + afterClosingBrace
  }

  private def maskCommentsAndStrings(source: String): String = {
    maskAndCheckLexemes(source)._1
  }

  private def maskAndCheckLexemes(source: String): (String, Boolean) = {
    @scala.annotation.tailrec
    def scan(index: Int, state: Int, escaped: Boolean, reversed: List[Char]): (String, Boolean) =
      if index >= source.length then (reversed.reverse.mkString, state == 0 || state == 1)
      else {
        val current = source.charAt(index)
        val next = if index + 1 < source.length then source.charAt(index + 1) else 0.toChar
        state match
          case 0 if current == '/' && (next == '/' || next == '*') =>
            scan(index + 2, if next == '/' then 1 else 2, escaped, ' ' :: ' ' :: reversed)
          case 0 if current == '"' || current == '\'' =>
            scan(index + 1, if current == '"' then 3 else 4, false, ' ' :: reversed)
          case 1 if current == '\n' || current == '\r' =>
            scan(index + 1, 0, escaped, current :: reversed)
          case 1 | 2 | 3 | 4 =>
            val masked = if current == '\n' || current == '\r' then current else ' '
            if state == 2 && current == '*' && next == '/' then
              scan(index + 2, 0, escaped, ' ' :: ' ' :: reversed)
            else if state == 3 || state == 4 then
              if escaped then scan(index + 1, state, false, masked :: reversed)
              else if current == '\\' then scan(index + 1, state, true, masked :: reversed)
              else if (state == 3 && current == '"') || (state == 4 && current == '\'') then
                scan(index + 1, 0, escaped, masked :: reversed)
              else scan(index + 1, state, escaped, masked :: reversed)
            else scan(index + 1, state, escaped, masked :: reversed)
          case _ => scan(index + 1, state, escaped, current :: reversed)
      }
    scan(0, 0, false, Nil)
  }

  private def braceMatches(source: String): Vector[Int] =
    source.indices.foldLeft((List.empty[Int], Vector.fill(source.length)(-1))) { case ((stack, matches), index) =>
      source.charAt(index) match
        case '{' => (index :: stack, matches)
        case '}' => stack match
          case opening :: rest => (rest, matches.updated(opening, index))
          case Nil => (stack, matches)
        case _ => (stack, matches)
    }._2


  def forEvaTab(tabLogger: Logger, config: CodeMirrorEditorConfig): EvaEditorTab = new EvaEditorTab {
    override val language = it.evadid.core.datastructures.language.AppLanguage.Java
    override val label = "Java"
    override val showLabel = false
    override val css = " eva-editor--java"
    override def icon: Option[ReactiveSvgElement[SVGSVGElement]] = Some(svg.svg(svg.cls := "eva-editor__language-icon", svg.viewBox := "0 0 24 24",
      svg.fill := "none", svg.stroke := "currentColor", svg.strokeWidth := "1.6",
      svg.strokeLineCap := "round", svg.strokeLineJoin := "round",
      svg.path(svg.d := "M5 11h11v5a4 4 0 0 1-4 4H9a4 4 0 0 1-4-4Zm11 1h2a3 3 0 0 1 0 6h-3M4 23h14M8 8c-3-3 3-3 0-6M12 8c-3-3 3-3 0-6")))
    override def create(state: Var[ProgrammingState], appearance: EditorAppearance): HtmlAppElement =
      new JavaFunctionBasedEditor(tabLogger, state, appearance = Some(appearance), editorConfig = config)
  }
}
