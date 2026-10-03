package it.evadid.homepage.webElements.editor.code.SnapEditor

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import it.evadid.workbook.elements.interactionElements.programming.SnapTurtlePythonBridge

/**
 * Snap toolbar pieces shared with the programming editor.
 *
 * The editable Python overlay used to live here. Switching editors is now
 * `ProgrammingExerciseEditor`; this file keeps the green-flag speed control
 * and the supported-function list shown in the Python panel.
 */
object SnapPythonPopup {

  /** Example signatures for the turtle allow-list (Python snake_case). */
  val OverviewExamples: List[String] =
    SnapTurtlePythonBridge.Primitives.map(_.example) ++
      SnapTurtlePythonBridge.ControlFlowExamples ++
      SnapTurtlePythonBridge.VariableExamples ++
      SnapTurtlePythonBridge.UserFunctionExamples

  private val OverviewKeywords: Set[String] = Set(
    "if", "else", "elif", "for", "while", "not", "and", "or", "in",
    "True", "False", "None", "pass", "def", "return"
  )

  /** Pause between blocks during Snap Execute, in milliseconds (>= 0). */
  def speedToolbar(setExecutionStepMs: Double => Unit): L.Element = {
    val stepMsVar: Var[String] = Var("20")

    def applyStepMsFromInput(raw: String): Unit =
      raw.trim.toDoubleOption match
        case Some(ms) if ms >= 0 && !ms.isNaN && !ms.isInfinity =>
          stepMsVar.set(raw.trim)
          setExecutionStepMs(ms)
        case _ =>
          ()

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
      )
    )
  }

  /** Collapsed list of Python the block editor can represent. */
  def supportedFunctions: L.Element =
    detailsTag(
      cls := "snap-python-popup__overview",
      summaryTag(
        cls := "snap-python-popup__overview-title",
        "Supported functions"
      ),
      p(
        cls := "snap-python-popup__overview-note",
        "Turtle-subset calls and `def` user functions convert to blocks. Other Python stays in this editor; switching to blocks is blocked until it fits the subset."
      ),
      ul(
        cls := "snap-python-popup__overview-list",
        OverviewExamples.map { example =>
          li(highlightedExample(example))
        }
      )
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
}
