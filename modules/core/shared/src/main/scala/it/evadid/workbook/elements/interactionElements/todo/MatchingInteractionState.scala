package it.evadid.workbook.elements.interactionElements.basic


import it.evadid.core.util.io.Serializer
import upickle.default.{ReadWriter, macroRW}

/** One selected right-side item for every left-side item; None means unanswered. */
case class MatchingInteractionState(selectedRightIndicesByLeftIndex: List[Option[Int]]) {


  def sanitized(leftCount: Int, rightCount: Int): MatchingInteractionState = {
    val padded = selectedRightIndicesByLeftIndex.take(leftCount).padTo(leftCount, None)
    MatchingInteractionState(padded.map(_.filter(index => index >= 0 && index < rightCount)))
  }

  def selectedRightIndex(leftIndex: Int, leftCount: Int, rightCount: Int): Option[Int] =
    sanitized(leftCount, rightCount).selectedRightIndicesByLeftIndex.lift(leftIndex).flatten

  def withMatch(leftIndex: Int, rightIndex: Option[Int], leftCount: Int, rightCount: Int): MatchingInteractionState = {
    val clean = sanitized(leftCount, rightCount).selectedRightIndicesByLeftIndex
    if leftIndex < 0 || leftIndex >= leftCount then MatchingInteractionState(clean)
    else MatchingInteractionState(clean.updated(leftIndex, rightIndex.filter(index => index >= 0 && index < rightCount)))
  }
}

object MatchingInteractionState {
  private given ReadWriter[MatchingInteractionState] = macroRW
  val serializer: Serializer[MatchingInteractionState] = Serializer.fromUpickleJson(summon[ReadWriter[MatchingInteractionState]])
}
