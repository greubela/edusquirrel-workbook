package it.evadid.homepage.workbook.htmlRenderer.controlElements.interactionInfo

import it.evadid.workbook.abstractions.grading.*
import GradingImportance.HIDDEN_TEST
import GradingStatus.*

/** Shared, UI-independent counts. Only authored graders for the current answer are displayed. */
private[homepage] object GradingSummary {
  enum Group(val labelKey: String, val tone: String) {
    case Correct extends Group("gradingGroupCorrect", "correct")
    case Incorrect extends Group("gradingGroupIncorrect", "incorrect")
    case Pending extends Group("gradingGroupPending", "pending")
    case Error extends Group("gradingGroupError", "error")
    case NotRequested extends Group("gradingGroupNotRequested", "neutral")
  }

  final case class Entry(index: Int, grader: WorkbookInteractionGrader[?, ?], result: Option[GradingResult[?, ?]]) {
    def hidden: Boolean = grader.importance == HIDDEN_TEST
    def group: Group = result.map(_.gradingStatus).getOrElse(NotRequested()) match {
      case GradingStatus.Correct() => Group.Correct
      case GradingStatus.Incorrect() | PartiallyCorrect() => Group.Incorrect
      case NotFinished() => Group.Pending
      case TestError(_) => Group.Error
      case GradingStatus.NotRequested() => Group.NotRequested
    }
    def executed: Boolean = group != Group.Pending && group != Group.NotRequested
  }

  def apply(graders: List[WorkbookInteractionGrader[?, ?]], state: GradedState[?, ?]): GradingSummary = {
    val results = state.graderResultMap.values.map(result => result.grader.key -> result).toMap
    GradingSummary(graders.distinctBy(_.key).zipWithIndex.map { (grader, index) =>
      Entry(index + 1, grader, results.get(grader.key))
    })
  }
}

private[homepage] final case class GradingSummary(entries: List[GradingSummary.Entry]) {
  import GradingSummary.Group
  def in(group: Group): List[GradingSummary.Entry] = entries.filter(_.group == group)
  def visibleIn(group: Group): List[GradingSummary.Entry] = in(group).filterNot(_.hidden)
  def correct: Int = in(Group.Correct).size
  def total: Int = entries.size
  def unavailable: Int = entries.count(entry => Set(Group.Pending, Group.Error, Group.NotRequested)(entry.group))
  def pending: Boolean = in(Group.Pending).nonEmpty
  def score: String = s"$correct / $total"
}
