package it.evadid.homepage.webElements.editor

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.state.State
import it.evadid.core.datastructures.state.StateHelper.StateBasedVar
import it.evadid.homepage.webElements.HtmlAppElement
import SimpleNumberEditor.*

case class SimpleNumberEditor[T](
  varToBind: Var[T],
  config: Var[NumberEditorConfig[T]]
)(using numeric: Numeric[T]) extends HtmlAppElement {

  override def getDomElement(): Element = domElement

  private val domElement: Element = div(
    cls := "simple-number-editor",
    child <-- config.signal.map(createNumberEditor)
  )

  private def createNumberEditor(curConfig: NumberEditorConfig[T]): Element = div(
    cls := curConfig.containerClass,
    input(
      typ := "text",
      cls := curConfig.inputClass,
      controlled(
        value <-- varToBind.signal.map(curConfig.format),
        onInput.mapToValue
          .map(curConfig.parse)
          .collect { case Some(value) => clamp(value, curConfig) } --> varToBind.writer
      )
    ),
    div(
      cls := curConfig.spinnerClass,
      button(
        typ := "button",
        cls := curConfig.buttonClass,
        aria.label := curConfig.incrementLabel,
        curConfig.incrementContent,
        onClick.mapTo(adjust(curConfig.diff, curConfig)) --> varToBind.writer
      ),
      button(
        typ := "button",
        cls := curConfig.buttonClass,
        aria.label := curConfig.decrementLabel,
        curConfig.decrementContent,
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
  case class NumberEditorConfig[T](
    diff: T,
    minimum: Option[T] = None,
    maximum: Option[T] = None,
    parse: String => Option[T],
    format: T => String = (value: T) => value.toString,
    containerClass: String = "simple-number-editor__body",
    inputClass: String = "simple-number-editor__input",
    spinnerClass: String = "simple-number-editor__spinner",
    buttonClass: String = "simple-number-editor__spinner-button",
    incrementLabel: String = "Increase number",
    decrementLabel: String = "Decrease number",
    incrementContent: String = "▲",
    decrementContent: String = "▼"
  )

  def defaultConfig[T](diff: T)(using numeric: Numeric[T]): NumberEditorConfig[T] = NumberEditorConfig(
    diff = diff,
    parse = value => numeric.parseString(value.trim)
  )

  def defaultConfig[T](diff: T, minimum: Option[T], maximum: Option[T])(using numeric: Numeric[T]): NumberEditorConfig[T] =
    defaultConfig(diff).copy(minimum = minimum, maximum = maximum)

  def apply[T](varToBind: Var[T], diff: T)(using numeric: Numeric[T]): SimpleNumberEditor[T] =
    SimpleNumberEditor(varToBind, Var(defaultConfig(diff)))

  def apply[T](stateToBind: State[T], config: Var[NumberEditorConfig[T]])(using numeric: Numeric[T]): SimpleNumberEditor[T] =
    SimpleNumberEditor(stateToBind.toAirstreamVar, config)

  def apply[T](stateToBind: State[T], diff: T)(using numeric: Numeric[T]): SimpleNumberEditor[T] =
    SimpleNumberEditor(stateToBind.toAirstreamVar, Var(defaultConfig(diff)))
}
