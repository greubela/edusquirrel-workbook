package it.evadid.workbook.interaction.variable

import it.evadid.workbook.abstractions.grading.{GradedState, GradingResult}
import it.evadid.workbook.abstractions.scaffolding.ScaffoldingResult
import it.evadid.workbook.interaction.sync.SyncStrategy
import upickle.default.*

import java.time.LocalDateTime

case class InteractionVariableHistory[T, GA, SA]
(
  historyStates: InteractionVariableStateHistory[T],
  historyGrading: InteractionVariableGradingHistory[T, GA],
  historyScaffolding: InteractionVariableScaffoldingHistory[T, SA]
) {

  def size: Integer = {
    historyStates.events.size + historyGrading.gradingsFinished.size + historyScaffolding.scaffoldingsFinished.size
  }

  def timestampLastChanged: Option[LocalDateTime] = {
    val timestamps = historyScaffolding.scaffoldingsFinished.map(_.scaffoldingTime).maxOption.toList ++
      historyGrading.gradingsFinished.map(_.timestampLatestTest).maxOption ++
      historyStates.lastStateOption.map(_.timestamp)
    timestamps.maxOption
  }

  private def cleanGradingAndScaffolding(): InteractionVariableHistory[T, GA, SA] = {
    val allStates: Set[T] = historyStates.events.map(_.value).toSet
    val cleanedGradings: Set[GradedState[T, GA]] = historyGrading.gradingsFinished.filter(curGrading => allStates.contains(curGrading.state))
    val cleanedScaffolding: Set[ScaffoldingResult[T, SA]] = historyScaffolding.scaffoldingsFinished.filter(curScaffolding => allStates.contains(curScaffolding.forState))
    InteractionVariableHistory[T, GA, SA].apply(historyStates.events, cleanedGradings, cleanedScaffolding)
  }

  def filterWithStrategy(syncStrategy: SyncStrategy): InteractionVariableHistory[T, GA, SA] = {
    updateStates(syncStrategy.selectEventsToSync)
  }

  def updateStates(eventFunc: Set[InteractionVariableState[T]] => Set[InteractionVariableState[T]]): InteractionVariableHistory[T, GA, SA] = {
    this.copy(historyStates.updateEvents(eventFunc), historyGrading, historyScaffolding).cleanGradingAndScaffolding()
  }

  def addState(state: InteractionVariableState[T]): InteractionVariableHistory[T, GA, SA] = {
    addStates(List(state))
  }

  def addStates(states: Iterable[InteractionVariableState[T]]): InteractionVariableHistory[T, GA, SA] = {
    updateStates(_ ++ states)
  }

  def addScaffolding(scaffolding: ScaffoldingResult[T, SA]): InteractionVariableHistory[T, GA, SA] = {
    addScaffoldings(Set(scaffolding))
  }

  def addScaffoldings(scaffoldings: Iterable[ScaffoldingResult[T, SA]]): InteractionVariableHistory[T, GA, SA] = {
    this.copy(historyStates, historyGrading, historyScaffolding.merge(InteractionVariableScaffoldingHistory(scaffoldings.toSet))).cleanGradingAndScaffolding()
  }

  def addGrading(grading: GradingResult[T, GA]): InteractionVariableHistory[T, GA, SA] = {
    addGradings(Set(grading))
  }

  def addGrading(grading: GradedState[T, GA]): InteractionVariableHistory[T, GA, SA] = {
    this.copy(historyStates, historyGrading.merge(InteractionVariableGradingHistory.forGratedState(Set(grading))), historyScaffolding).cleanGradingAndScaffolding()
  }

  def addGradings(gradings: Iterable[GradingResult[T, GA]]): InteractionVariableHistory[T, GA, SA] = {
    this.copy(historyStates, historyGrading.merge(InteractionVariableGradingHistory.forSingleGradings(gradings.toSet)), historyScaffolding).cleanGradingAndScaffolding()
  }

  def mergeWith(others: Seq[InteractionVariableHistory[T, GA, SA]]): InteractionVariableHistory[T, GA, SA] = {
    if (others.isEmpty) this
    else if (others.size == 1) mergeWith(others.head)
    else {
      val firstMerged = mergeWith(others.head)
      firstMerged.mergeWith(others.tail)
    }
  }

  def mergeWith(other: InteractionVariableHistory[T, GA, SA]): InteractionVariableHistory[T, GA, SA] = {
    InteractionVariableHistory[T, GA, SA](
      historyStates.withAddedEvents(other.historyStates.events),
      historyGrading.merge(other.historyGrading),
      historyScaffolding.merge(other.historyScaffolding)
    )
  }


}
/*
val all = this +: histories.toVector
val restoredGradings = all.flatMap(_.gradings).groupMap(_._1)(_._2).flatMap { (state, snapshots) =>
  val results = snapshots.filter(_.state == state).flatMap(_.testsWithResults).filter(_.gradedState == state)
  Option.when(results.nonEmpty) {
    val latest = results.groupBy(_.grader).values.flatMap { sameGrader =>
      val time = sameGrader.map(_.gradingTime).max
      sameGrader.filter(_.gradingTime == time)
    }.toSet
    state -> GradedState(state, latest)
  }
}
val restoredHints = all.flatMap(_.scaffoldings).filter((state, hint) => hint.forState == state)
  .groupMap(_._1)(_._2).flatMap { (state, candidates) =>
    candidates.distinct match {
      case Vector(single) => Some(state -> single)
      case _ => None
    }
  }
copy(events = all.flatMap(_.events).toSet, gradings = restoredGradings,
  scaffoldings = restoredHints ++ scaffoldings.filter((state, hint) => hint.forState == state)).cleanedHistory()
}
*/


object InteractionVariableHistory {

  given [T: ReadWriter, GA: ReadWriter, SA: ReadWriter]: ReadWriter[InteractionVariableHistory[T, GA, SA]] = macroRW

  def apply[T, GA, SA](
                        states: Set[InteractionVariableState[T]],
                      ): InteractionVariableHistory[T, GA, SA] = {
    apply(states, Set(), Set())
  }

  def apply[T, GA, SA](
                        states: Set[InteractionVariableState[T]],
                        gradings: Set[GradedState[T, GA]],
                        scaffoldings: Set[ScaffoldingResult[T, SA]]
                      ): InteractionVariableHistory[T, GA, SA] = InteractionVariableHistory[T, GA, SA](
    InteractionVariableStateHistory[T](states),
    InteractionVariableGradingHistory[T, GA](gradings),
    InteractionVariableScaffoldingHistory[T, SA](scaffoldings)
  )

  def empty[T, GA, SA]: InteractionVariableHistory[T, GA, SA] = new InteractionVariableHistory[T, GA, SA](
    InteractionVariableStateHistory(Set()),
    InteractionVariableGradingHistory(Set()),
    InteractionVariableScaffoldingHistory(Set())
  )

}
