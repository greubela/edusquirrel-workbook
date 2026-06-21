package it.evadid.homepage.webElements.editor

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.datastructures.state.State
import it.evadid.core.datastructures.state.StateHelper.StateBasedVar
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.homepage.workbook.htmlRenderer.HtmlRenderFactory.contentIdStringSignal
import SimpleNumberEditor.*

case class SimpleNumberEditor[T](
  varToBind: Var[T],
  config: Var[NumberEditorConfig[T]]
)(using numeric: Numeric[T]) extends HtmlAppElement {

  override def getDomElement(): Element = domElement

  private val domElement: Element = div(
    cls := RootClass,
    child <-- config.signal.map(createNumberEditor)
  )

  private def createNumberEditor(curConfig: NumberEditorConfig[T]): Element = label(
    cls := BodyClass,
    curConfig.label.map { labelId =>
      span(
        cls := LabelClass,
        text <-- contentIdStringSignal(labelId)
      )
    },
    input(
      typ := "text",
      cls := InputClass,
      controlled(
        value <-- varToBind.signal.map(curConfig.format),
        onInput.mapToValue
          .map(curConfig.parse)
          .collect { case Some(value) => clamp(value, curConfig) } --> varToBind.writer
      )
    ),
    div(
      cls := SpinnerClass,
      button(
        typ := "button",
        cls := SpinnerButtonClass,
        aria.label <-- contentIdStringSignal(curConfig.incrementLabel),
        "▲",
        onClick.mapTo(adjust(curConfig.diff, curConfig)) --> varToBind.writer
      ),
      button(
        typ := "button",
        cls := SpinnerButtonClass,
        aria.label <-- contentIdStringSignal(curConfig.decrementLabel),
        "▼",
        onClick.mapTo(adjust(numeric.negate(curConfig.diff), curConfig)) --> varToBind.writer
      )
    )
  )

  private def adjust(delta: T, curConfig: NumberEditorConfig[T]): T = {
    val next = numeric.plus(varToBind.now(), delta)
    clamp(next, curConfig)
  }

  private def clamp(value: T, curConfig: NumberEditorConfig[T]): T = {
    val withMin = curConfig.minimum match {
      case Some(minimum) if numeric.lt(value, minimum) => minimum
      case _ => value
    }

    curConfig.maximum match {
      case Some(maximum) if numeric.gt(withMin, maximum) => maximum
      case _ => withMin
    }
  }
}

object SimpleNumberEditor {
  private val RootClass = "simple-number-editor"
  private val BodyClass = "simple-number-editor__body"
  private val LabelClass = "simple-number-editor__label-text"
  private val InputClass = "simple-number-editor__input"
  private val SpinnerClass = "simple-number-editor__spinner"
  private val SpinnerButtonClass = "simple-number-editor__spinner-button"

  private val DefaultIncrementLabel = LanguageMapContentId("basic/increaseNumber")
  private val DefaultDecrementLabel = LanguageMapContentId("basic/decreaseNumber")

  case class NumberEditorConfig[T](
    diff: T,
    minimum: Option[T] = None,
    maximum: Option[T] = None,
    label: Option[LanguageMapContentId] = None,
    parse: String => Option[T],
    format: T => String = (value: T) => value.toString,
    incrementLabel: LanguageMapContentId = DefaultIncrementLabel,
    decrementLabel: LanguageMapContentId = DefaultDecrementLabel
  )

  def defaultConfig[T](diff: T)(using numeric: Numeric[T]): NumberEditorConfig[T] = NumberEditorConfig(
    diff = diff,
    parse = value => numeric.parseString(value.trim)
  )

  def defaultConfig[T](
    diff: T,
    minimum: Option[T],
    maximum: Option[T],
    label: Option[LanguageMapContentId] = None
  )(using numeric: Numeric[T]): NumberEditorConfig[T] =
    defaultConfig(diff).copy(minimum = minimum, maximum = maximum, label = label)

  def apply[T](varToBind: Var[T], diff: T)(using numeric: Numeric[T]): SimpleNumberEditor[T] =
    SimpleNumberEditor(varToBind, Var(defaultConfig(diff)))

  def apply[T](stateToBind: State[T], config: Var[NumberEditorConfig[T]])(using numeric: Numeric[T]): SimpleNumberEditor[T] =
    SimpleNumberEditor(stateToBind.toAirstreamVar, config)

  def apply[T](stateToBind: State[T], diff: T)(using numeric: Numeric[T]): SimpleNumberEditor[T] =
    SimpleNumberEditor(stateToBind.toAirstreamVar, Var(defaultConfig(diff)))
}
