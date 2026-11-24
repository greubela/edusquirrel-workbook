package it.evadid.homepage.webElements.basic

import com.raquo.laminar.api.L.*

/** Shows one of a finite set of texts, chosen by a value.
  *
  * Prefer this over `value.flatMapSwitch(text)`: a switch emits the new text in a separately scheduled transaction,
  * so when `value` changes again in a follow-up transaction (e.g. an observer of the same source updates it) the
  * switched signal can end on an outdated text. Here all candidate texts are combined once and the value only
  * selects among them.
  */
object LabelSelection {
  def apply[A, K](value: Signal[A], options: Iterable[K], text: K => Signal[String])(key: A => Option[K]): Signal[String] = {
    val texts = Signal.combineSeq(options.toSeq.distinct.map(option => text(option).map(option -> _))).map(_.toMap)
    value.combineWith(texts).map((current, all) => key(current).flatMap(all.get).getOrElse(""))
  }
}
