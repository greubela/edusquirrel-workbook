package interactionPlugins.pythonExercises

import com.raquo.airstream.ownership.Owner
import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import com.raquo.laminar.api.L.HtmlElement
import workbook.model.display.InteractionComponent.InteractionComponentWithReactiveVars
import workbook.model.feedback.scaffolding.BasicVariableScaffoldingResult
import workbook.model.states.Stateless

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js

final class PythonCodeEditorComponent(codeVar: Var[PythonEditorState])
    extends InteractionComponentWithReactiveVars {

  private var editorInstance: Option[CodeMirrorInstance] = None
  private var suppressChangeEvent: Boolean = false
  private var latestCode: String = codeVar.now().code
  private var latestDisabled: Boolean = false

  private def applyReadOnly(disabled: Boolean): Unit =
    editorInstance.foreach { editor =>
      val value: js.Any = if disabled then "nocursor" else false
      editor.setOption("readOnly", value)
    }

  private def syncEditorValue(code: String): Unit =
    editorInstance.foreach { editor =>
      val currentValue = editor.getValue()
      if currentValue != code then
        suppressChangeEvent = true
        editor.setValue(code)
        suppressChangeEvent = false
    }

  private val editorTextArea =
    textArea(
      cls := "python-editor-textarea", // kept for accessibility and fallback
      spellCheck := false,
      display.none,
      defaultValue := latestCode,
      inContext { node =>
        onMountCallback { ctx =>
          implicit val owner: Owner = ctx.owner
          CodeMirrorLoader
            .ensureLoaded()
            .foreach { factory =>
              val options = js.Dynamic.literal(
                lineNumbers = true,
                mode = "python",
                theme = "eclipse",
                indentUnit = 4,
                tabSize = 4,
                indentWithTabs = false,
                autofocus = false,
                viewportMargin = js.Dynamic.global.Infinity
              )
              val instance = factory.fromTextArea(ctx.thisNode.ref, options)
              editorInstance = Some(instance)
              applyReadOnly(latestDisabled)
              syncEditorValue(latestCode)
              instance.on(
                "change",
                ((editor: CodeMirrorInstance, _: js.Any) => {
                  if !suppressChangeEvent then
                    val value = editor.getValue()
                    latestCode = value
                    codeVar.writer.onNext(PythonEditorState(value))
                }): js.Function2[CodeMirrorInstance, js.Any, Any]
              )
            }
        }
      },
      onUnmountCallback { _ =>
        editorInstance.foreach(_.toTextArea())
        editorInstance = None
      }
    )

  private val domElement =
    div(
      cls := "python-editor",
      hidden <-- isHiddenVar.signal,
      onMountCallback { ctx =>
        implicit val owner: Owner = ctx.owner
        codeVar.signal.foreach { state =>
          latestCode = state.code
          syncEditorValue(state.code)
        }
        isDisabledVar.signal.foreach { disabled =>
          latestDisabled = disabled
          applyReadOnly(disabled)
        }
      },
      label(cls := "python-editor-label", "Python code"),
      editorTextArea
    )

  override def getDomElement(): L.Element = domElement
}

final class PythonResultComponent(resultVar: Var[Option[PythonGradingResult]])
    extends InteractionComponentWithReactiveVars {

  private def formatScore(score: Double): String = f"${score * 100}%.1f%%"

  private def renderTestResult(test: PythonTestResult): HtmlElement = {
    val statusClass = test.status match {
      case PythonTestStatus.Passed  => "python-test-passed"
      case PythonTestStatus.Failed  => "python-test-failed"
      case PythonTestStatus.Errored => "python-test-errored"
    }

    val messageNode = test.message.filter(_.nonEmpty).map(msg => p(cls := "python-test-message", msg))
    val hintNode = test.hint.filter(_.nonEmpty).map(hint => p(cls := "python-test-hint", s"Hint: $hint"))

    val baseChildren: List[Modifier[HtmlElement]] = List(
      span(cls := "python-test-name", if test.isHidden then s"Hidden: ${test.name}" else test.name),
      span(cls := "python-test-status", test.status.toString),
      span(cls := "python-test-duration", f"${test.durationMs}%.1f ms")
    ) ++ messageNode.toList ++ hintNode.toList

    val modifiers = (cls := s"python-test-result $statusClass") :: baseChildren

    li(modifiers*)
  }

  private def renderResult(result: PythonGradingResult): HtmlElement = {
    val run = result.runResult
    val gradeLabel = result.grade.toString.toLowerCase.replace('_', ' ')
    div(
      cls := "python-result-content",
      h4(cls := "python-result-title", s"Result: $gradeLabel"),
      p(cls := "python-result-score", s"Score: ${formatScore(result.normalizedScore)}"),
      div(
        cls := "python-result-tests",
        h5("Test results"),
        if run.tests.isEmpty then p("No tests were executed.")
        else ul(run.tests.map(renderTestResult): _*)
      ),
      if run.stdout.nonEmpty then pre(cls := "python-stdout", run.stdout) else emptyNode,
      if run.stderr.nonEmpty then pre(cls := "python-stderr", run.stderr) else emptyNode,
      run.error.map(err => div(cls := "python-runtime-error", err))
    )
  }

  private val domElement =
    div(
      cls := "python-result-panel",
      hidden <-- isHiddenVar.signal,
      child <-- resultVar.signal.map {
        case Some(result) => renderResult(result)
        case None         => div(cls := "python-result-placeholder", "Run the tests to see feedback.")
      }
    )

  override def getDomElement(): L.Element = domElement
}

final class PythonScaffoldingResultComponent(
    resultVar: Var[Option[BasicVariableScaffoldingResult[String, Stateless]]]
) extends InteractionComponentWithReactiveVars {

  private val domElement =
    div(
      cls := "python-scaffolding-panel",
      hidden <-- isHiddenVar.signal,
      child <-- resultVar.signal.map {
        case Some(result) => p(result.variable)
        case None         => p("No scaffolding feedback requested yet.")
      }
    )

  override def getDomElement(): L.Element = domElement
}

final class PythonScaffoldingStateComponent extends InteractionComponentWithReactiveVars {

  private val domElement =
    div(
      cls := "python-scaffolding-state",
      hidden <-- isHiddenVar.signal,
      p("No scaffolding configuration required for this exercise.")
    )

  override def getDomElement(): L.Element = domElement
}

final class PythonGradingConfigComponent(
    exerciseContent: PythonExerciseContent,
    gradingStateVar: Var[PythonGradingState]
) extends InteractionComponentWithReactiveVars {

  private val domElement =
    div(
      cls := "python-grading-config",
      hidden <-- isHiddenVar.signal,
      h4("Configured tests"),
      ul(
        (exerciseContent.visibleTests.map(test =>
          li(
            span(cls := "python-config-test-name", test.name),
            span(cls := "python-config-test-weight", f"${test.weight}%.2f weight"),
            span(cls := "python-config-test-visibility", "Visible")
          )
        ) ++ exerciseContent.hiddenTests.map(test =>
          li(
            span(cls := "python-config-test-name", test.name),
            span(cls := "python-config-test-weight", f"${test.weight}%.2f weight"),
            span(cls := "python-config-test-visibility", "Hidden")
          )
        )): _*
      ),
      div(
        cls := "python-grading-last-run",
        child.text <-- gradingStateVar.signal.map(state => s"Last run characters: ${state.codeSnapshot.length}")
      )
    )

  override def getDomElement(): L.Element = domElement
}
