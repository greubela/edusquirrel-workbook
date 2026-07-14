package it.evadid.workbook.elements.interactionElements.reorderExercise

object ReorderCorrectness {

  def isOrderCorrect[T](reorder: ReorderInteraction[T], state: ReorderInteractionState[T]): Boolean = {
    val current = state.sanitizedCurrentOrder
    reorder match {
      case codeInteraction: ReorderInteraction.ReorderCodeInteraction if codeInteraction.orderConstraints.nonEmpty =>
        val positions = current.zipWithIndex.toMap
        codeInteraction.orderConstraints.forall { case (first, second) =>
          positions.get(first).exists(firstIdx => positions.get(second).exists(secondIdx => firstIdx < secondIdx))
        }
      case _ =>
        current == state.correctOrder
    }
  }

}
