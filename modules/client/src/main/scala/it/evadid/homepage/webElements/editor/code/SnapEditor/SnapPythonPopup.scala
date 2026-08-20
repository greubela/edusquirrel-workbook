package it.evadid.homepage.webElements.editor.code.SnapEditor

import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.AppLanguage.{English, Python}
import it.evadid.homepage.webElements.editor.code.CodeMirrorEditor
import it.evadid.vm.code.abstractions.BeExpression
import it.evadid.vm.code.controlStructures.BeSequence
import it.evadid.vm.code.others.BeStartProgram
import it.evadid.workbook.elements.interactionElements.programming.{
  ProgrammingExerciseState,
  SnapCanvasScript,
  SnapTurtlePythonBridge
}

/**
 * FEATURE: Snap editable Python overlay (dual-mode with blocks).
 *
 * Removable as a unit:
 *  1. Delete this file (`SnapPythonPopup.scala`)
 *  2. Remove the `SnapPythonPopup.chrome(...)` child from `SnapCodeEditor.editorCanvas`
 *  3. Delete the CSS block marked `FEATURE: SnapPythonPopup` in
 *     `homepage/css/workbook/workbook-interactions.css`
 *  4. Delete `SnapPythonPopupSpec` if present
 *
 * Overlay/panel chrome reuses `.sorting-error-popup` styles; only Snap-specific
 * toolbar/button/code rules live in the FEATURE CSS block.
 *
 * Apply path: parse + turtle-subset check → Snap XML. Apply is disabled when
 * the current project contains blocks outside the Python-compatible allow-list.
 */
object SnapPythonPopup {

  /** One Snap top-level script rendered as Python (plus canvas position). */
  final case class ScriptView(index: Int, x: Int, y: Int, python: String)

  /** Example signatures for the turtle allow-list (Python snake_case). */
  private val SupportedFunctionExamples: List[String] =
    SnapTurtlePythonBridge.Primitives.map(_.example) ++
      SnapTurtlePythonBridge.ControlFlowExamples ++
      SnapTurtlePythonBridge.VariableExamples

  private val OverviewKeywords: Set[String] = Set(
    "if", "else", "elif", "for", "while", "not", "and", "or", "in",
    "True", "False", "None", "pass", "def", "return"
  )

  /** Tokenize a short Python snippet with the same highlight classes as CodeMirror. */
  private def highlightedExample(example: String): HtmlElement = {
    val children = List.newBuilder[Modifier[HtmlElement]]
    var i = 0
    while i < example.length do
      val ch = example.charAt(i)
      if ch.isLetter || ch == '_' then
        var end = i + 1
        while end < example.length && {
            val c = example.charAt(end)
            c.isLetterOrDigit || c == '_'
          }
        do end += 1
        val word = example.substring(i, end)
        val nextNonSpace =
          example.substring(end).dropWhile(_.isWhitespace).headOption.map(_.toString).getOrElse("")
        val isCall = nextNonSpace == "("
        val tokenClass =
          if OverviewKeywords.contains(word) then "cm-keyword"
          else if word == "receive_go" && isCall then "cm-receive-go"
          else if isCall then "cm-accent-name"
          else "cm-plain-name"
        children += span(cls := tokenClass, word)
        i = end
      else
        var end = i + 1
        while end < example.length && {
            val c = example.charAt(end)
            !(c.isLetter || c == '_')
          }
        do end += 1
        children += span(example.substring(i, end))
        i = end
    code(children.result()*)
  }

  /**
   * Bottom-right toolbar + editable overlay chrome for `SnapCodeEditor.editorCanvas`.
   * @param state live exercise state (canonical Snap XML)
   * @param flushPending call before reading state so Snap XML edits are published
   * @param onStateEdited persist / sync callback used for block edits and Python apply
   * @param setExecutionStepMs apply Execute pause between blocks (ms, >= 0)
   */
  def chrome(
      state: Var[ProgrammingExerciseState],
      flushPending: () => Unit,
      onStateEdited: ProgrammingExerciseState => Unit,
      setExecutionStepMs: Double => Unit = _ => ()
  ): L.Element = {
    val showPopup: Var[Boolean] = Var(false)
    val textVar: Var[String] = Var("")
    val textDirty: Var[Boolean] = Var(false)
    val warningVar: Var[Option[String]] = Var(None)
    val applyAllowed: Var[Boolean] = Var(true)
    val scriptHints: Var[List[ScriptView]] = Var(Nil)
    val stepMsVar: Var[String] = Var("20")

    def loadFromState(): Unit =
      flushPending()
      val current = state.now()
      val derived = SnapProgramDerivation.fromState(current)
      textVar.set(derived.python.trim)
      scriptHints.set(scriptsOf(current))
      textDirty.set(false)
      applyAllowed.set(derived.pythonCompatible)
      warningVar.set(derived.applyBlockedMessage)

    def open(): Unit =
      loadFromState()
      showPopup.set(true)

    def close(): Unit =
      showPopup.set(false)

    def applyPython(): Unit = {
      if !applyAllowed.now() then return
      val current = state.now()
      val derived = SnapProgramDerivation.fromState(current)
      SnapTurtlePythonBridge.applyPython(textVar.now(), derived.canvasLayout) match
        case Left(message) =>
          warningVar.set(Some(message))
        case Right(next) =>
          textDirty.set(false)
          warningVar.set(None)
          onStateEdited(next)
          scriptHints.set(scriptsOf(next))
          val nextDerived = SnapProgramDerivation.fromState(next)
          textVar.set(nextDerived.python.trim)
          applyAllowed.set(nextDerived.pythonCompatible)
    }

    def applyStepMsFromInput(raw: String): Unit =
      raw.trim.toDoubleOption match
        case Some(ms) if ms >= 0 && !ms.isNaN && !ms.isInfinity =>
          stepMsVar.set(raw.trim)
          setExecutionStepMs(ms)
        case _ =>
          ()

    val pythonEditor: CodeMirrorEditor =
      CodeMirrorEditor(
        textVar,
        onUserInput = _ => textDirty.set(true),
        language = Python
      )

    div(
      div(
        cls := "snap-editor-toolbar",
        label(
          cls := "snap-editor-toolbar__speed",
          span(cls := "snap-editor-toolbar__speed-label", "Speed (ms)"),
          input(
            typ := "number",
            cls := "snap-editor-toolbar__speed-input",
            minAttr := "0",
            stepAttr := "1",
            controlled(
              value <-- stepMsVar.signal,
              onInput.mapToValue --> { raw =>
                stepMsVar.set(raw)
                applyStepMsFromInput(raw)
              }
            )
          )
        ),
        button(
          typ := "button",
          cls := "snap-python-button",
          "Edit Python",
          onClick --> { ev =>
            ev.stopPropagation()
            open()
          }
        )
      ),
      div(
        cls := "sorting-error-popup snap-python-popup",
        cls.toggle("is-visible") <-- showPopup.signal,
        // Backdrop clicks must not dismiss; only the Close button does.
        div(
          cls := "sorting-error-popup__content snap-python-popup__content",
          onClick --> (_.stopPropagation()),
          h3("Python"),
          detailsTag(
            cls := "snap-python-popup__overview",
            summaryTag(
              cls := "snap-python-popup__overview-title",
              "Supported functions"
            ),
            p(
              cls := "snap-python-popup__overview-note",
              "Only these Python calls convert to blocks. Other Python is rejected on Apply. Apply is disabled when the project contains unsupported Snap blocks."
            ),
            ul(
              cls := "snap-python-popup__overview-list",
              SupportedFunctionExamples.map { example =>
                li(highlightedExample(example))
              }
            )
          ),
          div(
            cls := "snap-python-popup__script-hints",
            children <-- scriptHints.signal.map { views =>
              if views.isEmpty then Nil
              else
                views.map { script =>
                  div(
                    cls := "snap-python-popup__script-label",
                    s"Script ${script.index} @ (${script.x}, ${script.y})"
                  )
                }
            }
          ),
          child <-- warningVar.signal.map {
            case Some(msg) if msg.nonEmpty =>
              div(cls := "snap-python-popup__warning", msg)
            case _ =>
              emptyNode
          },
          div(
            cls := "snap-python-popup__editor",
            pythonEditor.getDomElement()
          ),
          div(
            cls := "snap-python-popup__actions",
            button(
              typ := "button",
              cls := "sorting-error-popup__button",
              disabled <-- applyAllowed.signal.map(allowed => !allowed),
              "Apply to blocks",
              onClick --> { ev =>
                ev.stopPropagation()
                applyPython()
              }
            ),
            button(
              typ := "button",
              cls := "sorting-error-popup__button snap-python-popup__reload",
              "Reload from blocks",
              onClick --> { ev =>
                ev.stopPropagation()
                loadFromState()
              }
            ),
            button(
              typ := "button",
              cls := "sorting-error-popup__button",
              "Close",
              onClick --> { ev =>
                ev.stopPropagation()
                close()
              }
            )
          )
        )
      )
    )
  }

  /** Partition top-level BeProgram statements by derived canvas layout. */
  def scriptsOf(state: ProgrammingExerciseState): List[ScriptView] = {
    val derived = SnapProgramDerivation.fromState(state)
    val statements = SnapTurtlePythonBridge.topLevelStatements(derived.program.fullProgram)
    if statements.isEmpty then Nil
    else
      val chunks =
        if derived.canvasLayout.isEmpty ||
            !SnapTurtlePythonBridge.layoutMatches(derived.canvasLayout, statements.size) then
          List((156, 66, statements))
        else
          splitByLayout(statements, derived.canvasLayout.scripts)
      chunks.zipWithIndex.map { case ((x, y, chunk), idx) =>
        ScriptView(idx + 1, x, y, renderStatements(chunk))
      }
  }

  private def splitByLayout(
      statements: List[BeExpression],
      scripts: List[SnapCanvasScript]
  ): List[(Int, Int, List[BeExpression])] = {
    var remaining = statements
    scripts.map { script =>
      val (chunk, rest) = remaining.splitAt(script.callCount)
      remaining = rest
      (script.x, script.y, chunk)
    }
  }

  private def renderStatements(statements: List[BeExpression]): String =
    if statements.isEmpty then ""
    else
      BeStartProgram(BeSequence.optionalBody(statements))
        .structureInfo
        .toStringInLanguage(Python, English, false)
        .trim
}
