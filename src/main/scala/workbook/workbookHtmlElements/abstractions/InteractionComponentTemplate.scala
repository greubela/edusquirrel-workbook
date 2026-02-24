package workbook.workbookHtmlElements.abstractions

import com.raquo.laminar.api.L.*

abstract class InteractionComponentTemplate {

  val isHiddenVar: Var[Boolean] = Var(false)
  val isHighlightedVar: Var[Boolean] = Var(false)
  val isDisabledVar: Var[Boolean] = Var(false)

  def setHighlight(highlight: Boolean): Unit = isHighlightedVar.set(highlight)

  def setVisible(visible: Boolean): Unit = isHiddenVar.set(!visible)

  def setDisabled(disabled: Boolean): Unit = isDisabledVar.set(disabled)

}
