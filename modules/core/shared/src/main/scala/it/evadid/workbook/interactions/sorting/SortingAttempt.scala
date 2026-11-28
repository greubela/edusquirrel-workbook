package it.evadid.workbook.interactions.sorting

import it.evadid.workbook.interactions.sorting.sortingExercise.AssignmentResult

/** The placement attempt shared by sorting exercises, so one board can render either state. */
trait SortingAttempt[S <: SortingAttempt[S]] {
  def placedFieldIndexByItem: Seq[Option[Int]]
  def sessionErrorCount: Int
  def lastSessionErrorCount: Int
  /** Temporary wrong drop (item, field) shown before the item returns to the source list. */
  def wrongPlacementPreview: Option[(Int, Int)]

  def sanitized(itemCount: Int, fieldCount: Int): S
  def activeItemIndex(itemCount: Int, fieldCount: Int): Option[Int]
  def isComplete(itemCount: Int, fieldCount: Int): Boolean
  def finalizeAttempt(): S
  def resetAttempt(itemCount: Int): S
  def recordWrongPlacement(itemIndex: Int, fieldIndex: Int): S
  def clearWrongPlacementPreview(): S
  def tryAssign(itemIndex: Int, fieldIndex: Int, itemCount: Int, fieldCount: Int, correctFieldIndex: Int): (S, AssignmentResult)
}
