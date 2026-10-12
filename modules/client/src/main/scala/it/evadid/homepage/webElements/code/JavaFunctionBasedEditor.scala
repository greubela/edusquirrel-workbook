package it.evadid.homepage.webElements.code

import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L.{label as labelTag, *}
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.homepage.webElements.editor.code.CodeMirrorEditor
import it.evadid.vm.parsing.java.clean.JavaParser
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingState.ProgrammingState.ProgrammingStateJavaString
import it.evadid.workbook.elements.interactionElements.programming.state.{ProgrammingState}

final class JavaFunctionBasedEditor(
    val state: Var[ProgrammingState],
    onStateEdited: ProgrammingState => Unit = _ => ()
) extends HtmlAppElement {
  import JavaFunctionBasedEditor.*

  private var lastCommittedCode = state.now().toJava.code
  private val classes = Var(discover(lastCommittedCode))
  private val selectedFunction = Var(Option.empty[JavaFunction])
  private val functionDraft = Var("")
  private val synced = Var(true)
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

  private def handleFunctionInput(draft: String): Unit =
    selectedFunction.now().foreach { selected =>
      val merged = replaceFunction(lastCommittedCode, selected, draft)
      if parses(merged) then
        val refreshedClasses = discover(merged)
        findEditedFunction(refreshedClasses, selected, draft.length) match
          case Some(refreshedSelection) =>
            lastCommittedCode = merged
            classes.set(refreshedClasses)
            selectedFunction.set(Some(refreshedSelection))
            synced.set(true)
            publish(ProgrammingStateJavaString(merged))
          case None => synced.set(false)
      else synced.set(false)
    }

  private def selectFunction(function: JavaFunction): Unit =
    if synced.now() then
      selectedFunction.set(Some(function))
      functionDraft.set(lastCommittedCode.substring(function.start, function.end))

  private def commitExternalState(next: ProgrammingState): Unit = {
    val code = next.toJava.code
    if code != lastCommittedCode then
      val previousSelection = selectedFunction.now()
      lastCommittedCode = code
      val refreshedClasses = discover(code)
      classes.set(refreshedClasses)
      val replacementSelection = previousSelection
        .flatMap(previous =>
          refreshedClasses
            .find(_.name == previous.className)
            .flatMap(_.functions.find(_.name == previous.name))
        )
        .orElse(refreshedClasses.flatMap(_.functions).headOption)
      selectedFunction.set(replacementSelection)
      functionDraft.set(replacementSelection.fold("")(fn => code.substring(fn.start, fn.end)))
      synced.set(true)
  }

  private def openClassDialog(): Unit =
    if synced.now() then
      classNameDraft.set("")
      dialogError.set("")
      dialogMode.set(Some(DialogMode.AddClass))

  private def openFunctionDialog(className: String): Unit =
    if synced.now() then
      dialogClassName.set(Some(className))
      functionNameDraft.set("newFunction")
      returnTypeDraft.set("void")
      parametersDraft.set("")
      dialogError.set("")
      dialogMode.set(Some(DialogMode.AddFunction))

  private def addClass(): Unit = {
    val name = classNameDraft.now().trim
    if !name.matches("[A-Za-z_$][A-Za-z0-9_$]*") then
      dialogError.set("Enter a valid Java class name.")
    else {
      val separator = if lastCommittedCode.trim.isEmpty then "" else "\n\n"
      val candidate = lastCommittedCode.stripTrailing + separator + s"class $name {\n}\n"
      if parses(candidate) then {
        commit(candidate, None)
        dialogMode.set(None)
      } else dialogError.set("The class could not be added to the current Java source.")
    }
  }

  private def addFunction(): Unit = {
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
          val candidate = insertFunction(lastCommittedCode, targetClass, declaration)
          if parses(candidate) then {
            val refreshedClasses = discover(candidate)
            val addedFunction = refreshedClasses
              .find(_.name == targetClass.name)
              .flatMap(_.functions.find(_.name == name))
            commit(candidate, addedFunction)
            dialogMode.set(None)
          } else dialogError.set("The function signature or current Java source is invalid.")
        }
  }

  private def commit(code: String, selection: Option[JavaFunction]): Unit = {
    lastCommittedCode = code
    val refreshedClasses = discover(code)
    classes.set(refreshedClasses)
    val refreshedSelection = selection.flatMap { wanted =>
      refreshedClasses
        .find(_.name == wanted.className)
        .flatMap(_.functions.find(_.name == wanted.name))
    }
    selectedFunction.set(refreshedSelection)
    functionDraft.set(refreshedSelection.fold("")(fn => code.substring(fn.start, fn.end)))
    synced.set(true)
    publish(ProgrammingStateJavaString(code))
  }

  private def publish(next: ProgrammingState): Unit = {
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
        state.signal.changes.foreach(commitExternalState)(using ctx.owner)
      },
      div(
        cls := "java-function-editor__topbar",
        div(
          h2(cls := "java-function-editor__title", "Java function editor"),
          p(cls := "java-function-editor__subtitle", "Explore classes and edit one method at a time.")
        ),
        span(
          cls <-- synced.signal.map(isSynced =>
            if isSynced then "java-function-editor__sync java-function-editor__sync--synced"
            else "java-function-editor__sync java-function-editor__sync--unsynced"
          ),
          role := "status",
          aria.live := "polite",
          span(cls := "java-function-editor__sync-dot"),
          span(child.text <-- synced.signal.map(if _ then "Synced" else "Not synced"))
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
            disabled <-- synced.signal.map(!_),
            onClick --> (_ => openClassDialog())
          )
        ),
        div(
          cls := "java-function-editor__class-row",
          children <-- Signal.combine(classes.signal, selectedFunction.signal, synced.signal).map {
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
                          if current.exists(_.start == function.start) then
                            "java-function-editor__function java-function-editor__function--selected"
                          else "java-function-editor__function"
                        },
                        disabled := !isSynced,
                        aria.pressed := selected.exists(_.start == function.start).toString,
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
            h3(cls := "java-function-editor__section-title", child.text <-- selectedFunction.signal.map(_.fold("Function editor")(fn => s"${fn.className}.${fn.name}"))),
            p(cls := "java-function-editor__section-hint", "Edits are saved as soon as the complete Java source parses.")
          )
        ),
        div(
          cls <-- selectedFunction.signal.map(selection =>
            if selection.isDefined then "java-function-editor__editor-panel"
            else "java-function-editor__editor-panel java-function-editor__editor-panel--hidden"
          ),
          functionEditor.getDomElement(),
          span(
            cls <-- synced.signal.map(if _ then "java-function-editor__draft-warning java-function-editor__draft-warning--hidden" else "java-function-editor__draft-warning"),
            "The draft has syntax errors and has not been committed. Fix it before selecting another function."
          )
        ),
        div(
          cls <-- selectedFunction.signal.map(selection =>
            if selection.isDefined then "java-function-editor__editor-placeholder java-function-editor__editor-placeholder--hidden"
            else "java-function-editor__editor-placeholder"
          ),
          strong("Choose a function to get started"),
          span("Select a method from any class above. Its complete signature and body will appear here.")
        )
      ),
      child <-- dialogMode.signal.map(_.fold(emptyNode)(renderDialog))
    )
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
      currentClasses: List[JavaClass],
      previous: JavaFunction,
      draftLength: Int
  ): Option[JavaFunction] = {
    val candidates = currentClasses
      .find(_.name == previous.className)
      .toList
      .flatMap(_.functions)
      .filter(function => function.start >= previous.start && function.start <= previous.start + draftLength)
    candidates.minByOption(_.start)
  }

  private[code] def insertFunction(source: String, clazz: JavaClass, declaration: String): String = {
    val beforeClosingBrace = source.substring(0, clazz.bodyEnd).stripTrailing
    val afterClosingBrace = source.substring(clazz.bodyEnd)
    beforeClosingBrace + s"\n  $declaration\n" + afterClosingBrace
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
        case 1 if current == '\n' => state = 0
        case 1 | 2 | 3 | 4 =>
          if current != '\n' then masked(index) = ' '
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
