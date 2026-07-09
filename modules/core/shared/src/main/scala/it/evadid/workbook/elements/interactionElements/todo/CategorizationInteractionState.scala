package it.evadid.workbook.elements.interactionElements.todo


/*
import it.evadid.core.util.io.Serializer
import upickle.default.{ReadWriter, macroRW}

/** One selected category for every item; None means unanswered. */
case class CategorizationInteractionState(selectedCategoryIndicesByItemIndex: List[Option[Int]]) {
  def sanitized(itemCount: Int, categoryCount: Int): CategorizationInteractionState = {
    val padded = selectedCategoryIndicesByItemIndex.take(itemCount).padTo(itemCount, None)
    CategorizationInteractionState(padded.map(_.filter(index => index >= 0 && index < categoryCount)))
  }

  def selectedCategoryIndex(itemIndex: Int, itemCount: Int, categoryCount: Int): Option[Int] =
    sanitized(itemCount, categoryCount).selectedCategoryIndicesByItemIndex.lift(itemIndex).flatten

  def withCategory(itemIndex: Int, categoryIndex: Option[Int], itemCount: Int, categoryCount: Int): CategorizationInteractionState = {
    val clean = sanitized(itemCount, categoryCount).selectedCategoryIndicesByItemIndex
    if itemIndex < 0 || itemIndex >= itemCount then CategorizationInteractionState(clean)
    else CategorizationInteractionState(clean.updated(itemIndex, categoryIndex.filter(index => index >= 0 && index < categoryCount)))
  }
}

object CategorizationInteractionState {
  private given ReadWriter[CategorizationInteractionState] = macroRW
  val serializer: Serializer[CategorizationInteractionState] = Serializer.fromUpickleJson(summon[ReadWriter[CategorizationInteractionState]])
}
*/