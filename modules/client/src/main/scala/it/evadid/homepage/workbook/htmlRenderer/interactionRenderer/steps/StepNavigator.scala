package it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.steps

import com.raquo.laminar.api.L.*
import org.scalajs.dom.KeyboardEvent

/**
 * Navigation over the steps of one execution: step 0 is the initial state, step k the state after k
 * operations. A new execution (a changed total) jumps to its last step. Arrow keys, Home and End work
 * while the controls have focus.
 */
final class StepNavigator(total: Signal[Int], label: String => Signal[String]) {
  /** Int.MaxValue means "the last step", whatever the total. */
  private val requested = Var(Int.MaxValue)

  val step: Signal[Int] = requested.signal.combineWith(total).map((wanted, count) => wanted.max(0).min(count)).distinct

  private def go(target: (Int, Int) => Int): Observer[(Int, Int)] = Observer((current, count) => requested.set(target(current, count).max(0).min(count)))

  private def control(key: String, symbol: String, target: (Int, Int) => Int, disabledWhen: (Int, Int) => Boolean): Element =
    button(typ := "button", cls := "step-navigator__button", title <-- label(key), aria.label <-- label(key),
      span(aria.hidden := true, symbol),
      disabled <-- step.combineWith(total).map(disabledWhen.tupled),
      onClick.compose(_.sample(step.combineWith(total))) --> go(target))

  def element: Element = {
    val position = step.combineWith(total, label("stepPosition")).map { (current, count, template) =>
      template.replace("{step}", current.toString).replace("{total}", count.toString)
    }
    div(cls := "step-navigator", role := "group", aria.label <-- label("stepNavigation"),
      total.changes --> (_ => requested.set(Int.MaxValue)),
      onKeyDown.compose(_.withCurrentValueOf(step, total)) --> { (event: KeyboardEvent, current: Int, count: Int) =>
        val target = event.key match {
          case "ArrowLeft" => Some(current - 1)
          case "ArrowRight" => Some(current + 1)
          case "Home" => Some(0)
          case "End" => Some(count)
          case _ => None
        }
        target.foreach { value => event.preventDefault(); requested.set(value.max(0).min(count)) }
      },
      control("stepFirst", "⏮", (_, _) => 0, (current, _) => current == 0),
      control("stepPrevious", "◀", (current, _) => current - 1, (current, _) => current == 0),
      input(typ := "range", cls := "step-navigator__range", minAttr := "0",
        maxAttr <-- total.map(_.toString), value <-- step.map(_.toString), aria.label <-- position,
        onInput.mapToValue.map(_.toIntOption.getOrElse(0)) --> requested.writer),
      control("stepNext", "▶", (current, _) => current + 1, (current, count) => current >= count),
      control("stepLast", "⏭", (_, count) => count, (current, count) => current >= count),
      // Compact visually; the range's label carries the full "step k of n" wording.
      span(cls := "step-navigator__position", aria.hidden := true,
        child.text <-- step.combineWith(total).map((current, count) => s"$current / $count")))
  }
}
