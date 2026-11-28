package it.evadid.workbook.interactions.reorderExercise

import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.workbook.abstractions.FeedbackEntity
import it.evadid.workbook.abstractions.grading.*
import it.evadid.workbook.abstractions.ExecutionContextInformation
import scala.concurrent.Future
import upickle.default.ReadWriter

private[reorderExercise] object ReorderGrading {
  /** Correct when nothing is [[misplaced]], so the grade and the marked items always agree. */
  def status(order: Seq[Int], constraints: Seq[(Int, Int)]): GradingStatus =
    if misplaced(order, constraints).isEmpty then GradingStatus.Correct else GradingStatus.Incorrect

  /** Elements to move for a correct order. With constraints, the elements of every violated constraint; otherwise
   * the elements outside a longest run that is already in correct relative order, so a single misplaced element
   * does not mark all elements it shifted. */
  def misplaced(order: Seq[Int], constraints: Seq[(Int, Int)]): Set[Int] =
    if constraints.nonEmpty then {
      val positions = order.zipWithIndex.toMap
      constraints.filterNot((before, after) => positions(before) < positions(after)).flatMap((a, b) => Seq(a, b)).toSet
    } else order.toSet -- longestIncreasing(order)

  private def longestIncreasing(order: Seq[Int]): Seq[Int] = {
    val runs = order.indices.foldLeft(Vector.empty[Seq[Int]]) { (endingAt, i) =>
      val longest = endingAt.indices.filter(j => order(j) < order(i)).map(endingAt).maxByOption(_.size).getOrElse(Seq.empty)
      endingAt :+ (longest :+ order(i))
    }
    runs.maxByOption(_.size).getOrElse(Seq.empty)
  }
}

case class ReorderCodeGrader(lines: Seq[String], orderConstraints: Seq[(Int, Int)])
    extends WorkbookInteractionGrader[ReorderInteractionState[String], Unit] derives ReadWriter {
  def persistedType: String = ReorderCodeGrader.codec.persistedType
  override val importance = GradingImportance.REQUIRED_TEST
  override val runningBehavior = GraderRunningBehavior.RUN_ONLY_MANUALLY
  override val gradingEntity = FeedbackEntity.TestEntity
  override protected def handleGrading(state: ReorderInteractionState[String], context: ExecutionContextInformation) = Future {
    require(state.elements == lines && state.correctOrder == lines.indices, "Stored reorder content does not match the exercise")
    GradingResult(this, state, ReorderGrading.status(state.sanitizedCurrentOrder, orderConstraints))
  }(using context.futureExecutionContext)
}

object ReorderCodeGrader {
  lazy val codec: WorkbookInteractionGrader.Codec[ReorderCodeGrader] = WorkbookInteractionGrader.codec("ReorderCodeGrader")
}

case class ReorderMapIdGrader(ids: Seq[LanguageMapContentId])
    extends WorkbookInteractionGrader[ReorderInteractionState[LanguageMapContentId], Unit] derives ReadWriter {
  def persistedType: String = ReorderMapIdGrader.codec.persistedType
  override val importance = GradingImportance.REQUIRED_TEST
  override val runningBehavior = GraderRunningBehavior.RUN_ONLY_MANUALLY
  override val gradingEntity = FeedbackEntity.TestEntity
  override protected def handleGrading(state: ReorderInteractionState[LanguageMapContentId], context: ExecutionContextInformation) = Future {
    require(state.elements == ids && state.correctOrder == ids.indices, "Stored reorder content does not match the exercise")
    GradingResult(this, state, ReorderGrading.status(state.sanitizedCurrentOrder, Seq.empty))
  }(using context.futureExecutionContext)
}

object ReorderMapIdGrader {
  lazy val codec: WorkbookInteractionGrader.Codec[ReorderMapIdGrader] = WorkbookInteractionGrader.codec("ReorderMapIdGrader")
}
