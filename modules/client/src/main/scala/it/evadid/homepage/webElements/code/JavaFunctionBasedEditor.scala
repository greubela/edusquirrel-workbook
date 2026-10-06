package it.evadid.homepage.webElements.code

import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L.{label as labelTag, *}
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.homepage.webElements.editor.code.CodeMirrorEditor
import it.evadid.workbook.elements.interactionElements.programming.{ProgrammingState, ProgrammingStateJavaString}
import it.evadid.vm.parsing.java.clean.JavaParser

import scala.util.Try

final class JavaFunctionBasedEditor(
    val state: Var[ProgrammingState],
    onStateEdited: ProgrammingState => Unit = _ => ()
) extends HtmlAppElement {
  import JavaFunctionBasedEditor.*

  private var displayedState = state.now()
  private val initialSource = Try(displayedState.toJava.code).toOption
  private var sourceCode = initialSource.getOrElse("")
  private val sourceAvailable = Var(initialSource.isDefined)
  private val classes = Var(discover(sourceCode))
  private val selectedFunction = Var(Option.empty[JavaFunction])
  private var editingRange = Option.empty[FunctionRange]
  private val functionDraft = Var("")
  private val sourceDraft = Var(sourceCode)
  private val structureReady = Var(structurallyBalanced(sourceCode))
  private val sourceView = Var(!structureReady.now() || classes.now().flatMap(_.functions).isEmpty)
  private val editorRevision = Var(0)
  private val dialogMode = Var(Option.empty[DialogMode])
  private val dialogClassName = Var(Option.empty[String])
  private val classNameDraft = Var("")
  private val functionNameDraft = Var("newFunction")
  private val returnTypeDraft = Var("void")
  private val parametersDraft = Var("")
  private val dialogError = Var("")

  private val functionEditor = CodeMirrorEditor(
    functionDraft,
    handleFunctionInput,
    language = it.evadid.core.datastructures.language.AppLanguage.Java
  )

  private val sourceEditor = CodeMirrorEditor(
    sourceDraft,
    handleSourceInput,
    language = it.evadid.core.datastructures.language.AppLanguage.Java
  )

  private def currentSourceAvailable(): Boolean =
    if state.now() != displayedState then
      commitExternalState(state.now())
      false
    else sourceAvailable.now()

  private def refreshSource(code: String): List[JavaClass] = {
    sourceCode = code
    sourceDraft.set(code)
    val refreshedClasses = discover(code)
    classes.set(refreshedClasses)
    structureReady.set(structurallyBalanced(code))
    refreshedClasses
  }

  private def showSource(): Unit = {
    editingRange = None
    sourceDraft.set(sourceCode)
    sourceView.set(true)
  }

  private def handleFunctionInput(draft: String): Unit =
    if currentSourceAvailable() && !sourceView.now() then
      (selectedFunction.now(), editingRange) match
        case (Some(selected), Some(range)) =>
          range.replace(sourceCode, draft) match
            case Some((merged, nextRange)) if merged != sourceCode =>
              editingRange = Some(nextRange)
              val refreshedClasses = refreshSource(merged)
              if structurallyBalanced(merged) then
                findEditedFunction(merged, refreshedClasses, selected, nextRange) match
                  case Some(refreshedSelection) => selectedFunction.set(Some(refreshedSelection))
                  case None =>
                    selectedFunction.set(None)
                    showSource()
              publish(ProgrammingStateJavaString(merged))
            case None => showSource()
            case _ => ()
        case _ => showSource()

  private def handleSourceInput(code: String): Unit =
    if currentSourceAvailable() && sourceView.now() && code != sourceCode then
      editingRange = None
      val previous = selectedFunction.now()
      val refreshedClasses = refreshSource(code)
      selectedFunction.set(previous.flatMap(findSelection(refreshedClasses, _)))
      publish(ProgrammingStateJavaString(code))

  private def selectFunction(function: JavaFunction): Unit =
    if currentSourceAvailable() && structurallyBalanced(sourceCode) && discover(sourceCode).exists(_.functions.contains(function)) then
      showFunction(function)

  private def showFunction(function: JavaFunction): Unit =
    if FunctionRange(function.start, function.end).isWithin(sourceCode) then
      selectedFunction.set(Some(function))
      editingRange = Some(FunctionRange(function.start, function.end))
      functionDraft.set(sourceCode.substring(function.start, function.end))
      sourceView.set(false)

  private def showMethod(): Unit =
    if currentSourceAvailable() && structurallyBalanced(sourceCode) then
      selectedFunction.now().flatMap(findSelection(discover(sourceCode), _)) match
        case Some(function) => showFunction(function)
        case None =>
          selectedFunction.set(None)
          editingRange = None
          sourceView.set(false)

  private def commitExternalState(next: ProgrammingState): Unit = {
    if next != displayedState then
      val previousSelection = selectedFunction.now()
      val wasSourceView = sourceView.now()
      displayedState = next
      editingRange = None
      dialogMode.set(None)
      val converted = Try(next.toJava.code).toOption
      sourceAvailable.set(converted.isDefined)
      val code = converted.getOrElse("")
      val refreshedClasses = refreshSource(code)
      val replacementSelection = previousSelection.flatMap(findSelection(refreshedClasses, _))
      selectedFunction.set(replacementSelection)
      if converted.isEmpty || !structurallyBalanced(code) || wasSourceView || replacementSelection.isEmpty then showSource()
      else replacementSelection.foreach(showFunction)
      editorRevision.update(_ + 1)
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
          val declaration = s"public $returnType $name($parameters) {\n    \n  }"
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
      case Some(function) => showFunction(function)
      case None => showSource()
    publish(ProgrammingStateJavaString(code))
  }

  private def publish(next: ProgrammingState): Unit = {
    displayedState = next
    state.set(next)
    onStateEdited(next)
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
    div(
      role := "dialog",
      aria.label := (if isClassDialog then "Add class" else "Add function"),
      cls := "java-function-editor__dialog-backdrop",
      div(
        cls := "java-function-editor__dialog",
        h2(cls := "java-function-editor__dialog-title", if isClassDialog then "Add class" else "Add function"),
        if isClassDialog then modalField("Class name", classNameDraft)
        else div(cls := "java-function-editor__dialog-fields",
          modalField("Function name", functionNameDraft),
          modalField("Return type", returnTypeDraft),
          modalField("Parameters (for example: int count, String label)", parametersDraft)
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
            onClick --> (_ => if isClassDialog then addClass() else addFunction())
          )
        )
      )
    )
  }

  override def getDomElement(): Element =
    div(
      cls := "java-function-based-editor",
      onMountCallback { ctx =>
        state.signal.foreach(commitExternalState)(using ctx.owner)
      },
      div(
        cls := "java-function-editor__topbar",
        div(
          h2(cls := "java-function-editor__title", "Java function editor"),
          p(cls := "java-function-editor__subtitle", "Explore classes and edit one method at a time.")
        ),
        span(
          cls := "java-function-editor__sync",
          role := "status",
          aria.live := "polite",
          span(child.text <-- sourceView.signal.map(if _ then "Full source" else "Method view"))
        )
      ),
      div(
        cls := "java-function-editor__diagram-section",
        div(
          cls := "java-function-editor__section-heading",
          div(
            h3(cls := "java-function-editor__section-title", "Class diagram"),
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
          children <-- Signal.combine(classes.signal, selectedFunction.signal, structureReady.signal).map {
            case (currentClasses, selected, isSynced) =>
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
                        aria.pressed := selected.exists(current => sameFunction(current, function) && current.start == function.start).toString,
                        s"${function.returnType} ${function.name}(${function.parameters})",
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
              onClick --> (_ => if currentSourceAvailable() then showSource())
            )
          )
        ),
        div(
          cls <-- sourceView.signal.combineWith(selectedFunction.signal).map { (fullSource, selection) =>
            if fullSource || selection.isDefined then "java-function-editor__editor-panel"
            else "java-function-editor__editor-panel java-function-editor__editor-panel--hidden"
          },
          child <-- Signal.combine(sourceView.signal, sourceAvailable.signal, editorRevision.signal).map { (fullSource, available, _) =>
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
          span("Select a method from any class above. Its complete signature and body will appear here.")
        )
      ),
      child <-- dialogMode.signal.map(_.fold(emptyNode)(renderDialog))
    )
}

object JavaFunctionBasedEditor {
  private enum DialogMode {
    case AddClass, AddFunction
  }

  private[code] final case class JavaFunction(
      className: String,
      name: String,
      returnType: String,
      parameters: String,
      start: Int,
      end: Int
  )

  private[code] final case class JavaClass(name: String, bodyStart: Int, bodyEnd: Int, functions: List[JavaFunction])

  private[code] final case class FunctionRange(start: Int, end: Int) {
    def isWithin(source: String): Boolean = start >= 0 && end >= start && end <= source.length

    def replace(source: String, draft: String): Option[(String, FunctionRange)] =
      Option.when(isWithin(source))(
        (source.substring(0, start) + draft + source.substring(end), FunctionRange(start, start + draft.length))
      )
  }

  private val classDeclaration = """\bclass\s+([A-Za-z_$][A-Za-z0-9_$]*)""".r
  private val functionHeader =
    """(?s)(?:(?:public|private|protected|static|final|abstract|synchronized|native|strictfp|default)\s+)*(?:<[^>]+>\s*)?([A-Za-z_$][\w.$<>?,\[\] ]*)\s+([A-Za-z_$][\w$]*)\s*\(([^{}]*)\)\s*(?:throws\s+[\w.$, ]+\s*)?""".r

  private[code] def parses(code: String): Boolean =
    JavaParser.parse(code).isRight && structurallyBalanced(code)

  private def structurallyBalanced(source: String): Boolean = {
    val (masked, closedLexemes) = maskAndCheckLexemes(source)
    if !closedLexemes then false
    else {
      val stack = scala.collection.mutable.ArrayBuffer.empty[Char]
      var balanced = true
      masked.foreach {
        case opening @ ('(' | '[' | '{') => stack += opening
        case closing @ (')' | ']' | '}') =>
          val expectedOpening = closing match
            case ')' => '('
            case ']' => '['
            case _ => '{'
          if stack.lastOption.contains(expectedOpening) then stack.remove(stack.length - 1)
          else balanced = false
        case _ => ()
      }
      balanced && stack.isEmpty
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
      braceMatches: Array[Int],
      className: String,
      classOpening: Int,
      classClosing: Int
  ): List[JavaFunction] = {
    val functions = List.newBuilder[JavaFunction]
    var segmentStart = classOpening + 1
    var index = segmentStart
    while index < classClosing do
      masked.charAt(index) match
        case ';' => segmentStart = index + 1
        case '{' =>
          functionHeader.findFirstMatchIn(masked.substring(segmentStart, index).trim) match
            case Some(header) =>
              val signatureStart = segmentStart + masked.substring(segmentStart, index).indexWhere(!_.isWhitespace)
              val end = braceMatches(index) + 1
              functions += JavaFunction(
                className,
                header.group(2),
                header.group(1).trim,
                header.group(3).trim,
                signatureStart,
                end
              )
              index = end - 1
              segmentStart = end
            case None =>
              val end = braceMatches(index)
              if end > index then {
                index = end
                segmentStart = end + 1
              }
        case _ => ()
      index += 1
    functions.result()
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
    var index = 0
    while index < source.length do
      if source.charAt(index).isWhitespace then index += 1
      else if source.startsWith("//", index) then
        index += 2
        while index < source.length && source.charAt(index) != '\r' && source.charAt(index) != '\n' do index += 1
      else if source.startsWith("/*", index) then
        val end = source.indexOf("*/", index + 2)
        if end < 0 then return false
        index = end + 2
      else return false
    true
  }

  private def lineSeparator(source: String): String =
    "\r\n|\r|\n".r.findFirstIn(source).getOrElse("\n")

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
    val masked = source.toCharArray
    var index = 0
    var state = 0
    var escaped = false
    while index < source.length do
      val current = source.charAt(index)
      val next = if index + 1 < source.length then source.charAt(index + 1) else 0.toChar
      state match
        case 0 if current == '/' && next == '/' =>
          masked(index) = ' '
          masked(index + 1) = ' '
          index += 1
          state = 1
        case 0 if current == '/' && next == '*' =>
          masked(index) = ' '
          masked(index + 1) = ' '
          index += 1
          state = 2
        case 0 if current == '"' || current == '\'' =>
          masked(index) = ' '
          state = if current == '"' then 3 else 4
          escaped = false
        case 1 if current == '\n' || current == '\r' => state = 0
        case 1 | 2 | 3 | 4 =>
          if current != '\n' && current != '\r' then masked(index) = ' '
          if state == 2 && current == '*' && next == '/' then
            masked(index + 1) = ' '
            index += 1
            state = 0
          else if state == 3 || state == 4 then
            if escaped then escaped = false
            else if current == '\\' then escaped = true
            else if (state == 3 && current == '"') || (state == 4 && current == '\'') then state = 0
        case _ => ()
      index += 1
    (new String(masked), state == 0 || state == 1)
  }

  private def braceMatches(source: String): Array[Int] = {
    val matches = Array.fill(source.length)(-1)
    val stack = scala.collection.mutable.ArrayBuffer.empty[Int]
    source.indices.foreach { index =>
      source.charAt(index) match
        case '{' => stack += index
        case '}' if stack.nonEmpty =>
          val opening = stack.remove(stack.length - 1)
          matches(opening) = index
        case _ => ()
    }
    matches
  }
}
