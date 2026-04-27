package workbook.model.abstractions

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import contentmanagement.webElements.HtmlAppElement

trait InteractionComponent extends HtmlAppElement {

  def setHighlight(highlight: Boolean): Unit

  def setVisible(visible: Boolean): Unit

  def setDisabled(disabled: Boolean): Unit

  def isHiddenVar: Var[Boolean] = Var(false)
  def isHighlightedVar: Var[Boolean] = Var(false)
  def isDisabledVar: Var[Boolean] = Var(false)

}

object InteractionComponent {

  abstract class InteractionComponentWithReactiveVars(val initHiddenValue: Boolean = false, initHighlightValue: Boolean = false, initDisabledValue: Boolean = false) extends InteractionComponent {

    override val isHiddenVar: Var[Boolean] = Var(initHiddenValue)
    override val isHighlightedVar: Var[Boolean] = Var(initHighlightValue)
    override val isDisabledVar: Var[Boolean] = Var(initDisabledValue)

    def setHighlight(highlight: Boolean): Unit = isHighlightedVar.set(highlight)

    def setVisible(visible: Boolean): Unit = isHiddenVar.set(!visible)

    def setDisabled(disabled: Boolean): Unit = isDisabledVar.set(disabled)

  }



}
