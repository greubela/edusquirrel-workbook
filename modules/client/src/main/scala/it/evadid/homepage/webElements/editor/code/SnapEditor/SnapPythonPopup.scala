package it.evadid.homepage.webElements.editor.code.SnapEditor

import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.AppLanguage.{English, Python}
import it.evadid.vm.code.BeExpression
import it.evadid.vm.code.controlStructures.BeSequence
import it.evadid.vm.code.others.BeStartProgram
import it.evadid.vm.code.usage.BeFunctionCall
import it.evadid.workbook.elements.interactionElements.programming.{
  ProgrammingExerciseState,
  SnapCanvasLayout,
  SnapCanvasScript
}

/**
 * FEATURE: Snap "Show Python" popup (debug / demo overlay on the fullscreen editor).
 *
 * Removable as a unit:
 *  1. Delete this file (`SnapPythonPopup.scala`)
 *  2. Remove the `SnapPythonPopup.chrome(...)` child from `SnapCodeEditor.editorCanvas`
 *  3. Delete the CSS block marked `FEATURE: SnapPythonPopup` in
 *     `homepage/css/workbook/workbook-interactions.css`
 *  4. Delete `SnapPythonPopupSpec` if present
 *
 * Overlay/panel chrome reuses `.sorting-error-popup` styles; only Snap-specific
 * button/code rules live in the FEATURE CSS block.
 *
 * Does not affect BeProgram persistence, layout sidecar, or Snap sync.
 */
object SnapPythonPopup {

  /** One Snap top-level script rendered as Python (plus canvas position). */
  final case class ScriptView(index: Int, x: Int, y: Int, python: String)

  /**
   * Button + overlay chrome for `SnapCodeEditor.editorCanvas`.
   * @param state live exercise state (program + canvas layout)
   * @param flushPending call before reading state so Snap XML edits are published
   */
  def chrome(state: Var[ProgrammingExerciseState], flushPending: () => Unit): L.Element = {
    val showPopup: Var[Boolean] = Var(false)
    val scripts: Var[List[ScriptView]] = Var(Nil)

    def open(): Unit =
      flushPending()
      scripts.set(scriptsOf(state.now()))
      showPopup.set(true)

    def close(): Unit =
      showPopup.set(false)

    div(
      // FEATURE: SnapPythonPopup — root is a positioning context sibling of the canvas chrome.
      button(
        typ := "button",
        cls := "snap-python-button",
        "Show Python",
        onClick --> { ev =>
          ev.stopPropagation()
          open()
        }
      ),
      div(
        // Reuse sorting-error-popup overlay/panel styles; keep snap-python-* for code layout.
        cls := "sorting-error-popup snap-python-popup",
        cls.toggle("is-visible") <-- showPopup.signal,
        onClick --> { ev =>
          if ev.target == ev.currentTarget then
            ev.stopPropagation()
            close()
        },
        div(
          cls := "sorting-error-popup__content snap-python-popup__content",
          onClick --> (_.stopPropagation()),
          h3("Python"),
          div(
            cls := "snap-python-popup__scripts",
            children <-- scripts.signal.map { views =>
              if views.isEmpty then
                List(pre(cls := "snap-python-popup__code", "(empty program)"))
              else
                views.map { script =>
                  div(
                    div(
                      cls := "snap-python-popup__script-label",
                      s"Script ${script.index} @ (${script.x}, ${script.y})"
                    ),
                    pre(cls := "snap-python-popup__code", script.python)
                  )
                }
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
  }

  /** Partition flat BeProgram calls by canvas layout; used by the popup (and its tests). */
  def scriptsOf(state: ProgrammingExerciseState): List[ScriptView] = {
    val calls = topLevelCalls(state.program.fullProgram)
    if calls.isEmpty then Nil
    else
      val chunks =
        if state.canvasLayout.isEmpty || !layoutMatches(state.canvasLayout, calls.size) then
          List((156, 66, calls))
        else
          splitByLayout(calls, state.canvasLayout.scripts)
      chunks.zipWithIndex.map { case ((x, y, chunk), idx) =>
        ScriptView(idx + 1, x, y, renderCalls(chunk))
      }
  }

  private def topLevelCalls(expression: BeExpression): List[BeFunctionCall] =
    expression match
      case BeStartProgram(Some(sequence)) => sequence.body.collect { case c: BeFunctionCall => c }
      case BeStartProgram(None) => Nil
      case seq: BeSequence => seq.body.collect { case c: BeFunctionCall => c }
      case call: BeFunctionCall => List(call)
      case _ => Nil

  private def layoutMatches(layout: SnapCanvasLayout, totalCalls: Int): Boolean =
    layout.scripts.map(_.callCount).sum == totalCalls && layout.scripts.forall(_.callCount > 0)

  private def splitByLayout(
      calls: List[BeFunctionCall],
      scripts: List[SnapCanvasScript]
  ): List[(Int, Int, List[BeFunctionCall])] = {
    var remaining = calls
    scripts.map { script =>
      val (chunk, rest) = remaining.splitAt(script.callCount)
      remaining = rest
      (script.x, script.y, chunk)
    }
  }

  private def renderCalls(calls: List[BeFunctionCall]): String =
    if calls.isEmpty then ""
    else
      BeStartProgram(BeSequence.optionalBody(calls))
        .expressionIO
        .toStringInLanguage(Python, English, false)
        .trim
}
