package it.evadid.workbook.interaction.variable

import it.evadid.core.util.io.Serializer
import it.evadid.distribution.command.SerializedException
import it.evadid.workbook.abstractions.grading.{GradedState, GradingResult}
import it.evadid.workbook.abstractions.scaffolding.ScaffoldingResult
import it.evadid.workbook.interaction.sync.SyncStrategy
import it.evadid.workbook.interaction.sync.UpdateImportance.DEFAULT
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

  def cleanedHistory(): InteractionVariableHistory[T, GA, SA] = {
    val cleanedHistory = historyStates.cleanedHistory()
    val allExistingStates: Set[T] = cleanedHistory.events.map(_.value).toSet
    val cleanedGradings: Set[GradedState[T, GA]] = historyGrading.gradingsFinished.filter(curGrading => allExistingStates.contains(curGrading.state))
    val cleanedScaffolding: Set[ScaffoldingResult[T, SA]] = historyScaffolding.scaffoldingsFinished.filter(curScaffolding => allExistingStates.contains(curScaffolding.forState))
    InteractionVariableHistory[T, GA, SA].apply(historyStates.events, cleanedGradings, cleanedScaffolding)
  }

  def filterWithStrategy(syncStrategy: SyncStrategy): InteractionVariableHistory[T, GA, SA] = {
    copy(historyStates = historyStates.copy(events = syncStrategy.selectEventsToSync(historyStates.events))).cleanedHistory()
  }

  def updateStates(eventFunc: Set[InteractionVariableState[T]] => Set[InteractionVariableState[T]]): InteractionVariableHistory[T, GA, SA] = {
    this.copy(historyStates.updateEvents(eventFunc).cleanedHistory(), historyGrading, historyScaffolding)
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
    this.copy(historyStates, historyGrading, historyScaffolding.merge(InteractionVariableScaffoldingHistory(scaffoldings.toSet)))
  }

  def addGrading(grading: GradingResult[T, GA]): InteractionVariableHistory[T, GA, SA] = {
    addGradings(Set(grading))
  }

  def addGrading(grading: GradedState[T, GA]): InteractionVariableHistory[T, GA, SA] = {
    this.copy(historyStates, historyGrading.merge(InteractionVariableGradingHistory(Set(grading))), historyScaffolding)
  }

  def addGradings(gradings: Iterable[GradingResult[T, GA]]): InteractionVariableHistory[T, GA, SA] = {
    this.copy(historyStates, historyGrading.merge(InteractionVariableGradingHistory.fromResults(gradings)), historyScaffolding)
  }

  def mergeWith(others: Seq[InteractionVariableHistory[T, GA, SA]]): InteractionVariableHistory[T, GA, SA] = {
    if others.isEmpty then this
    else {
      val grades = others.foldLeft(historyGrading)((current, other) => current.merge(other.historyGrading))
      val hints = others.foldLeft(historyScaffolding)((current, other) => current.merge(other.historyScaffolding))
      InteractionVariableHistory(
        historyStates.withAddedEvents(others.flatMap(_.historyStates.events).toSet),
        grades,
        hints)
    }
  }

  def mergeWith(other: InteractionVariableHistory[T, GA, SA]): InteractionVariableHistory[T, GA, SA] = mergeWith(Seq(other))


}

object InteractionVariableHistory {




  private case class Payload[T, GA, SA](historyStates: InteractionVariableStateHistory[T],
                                        historyGrading: InteractionVariableGradingHistory[T, GA], historyScaffolding: InteractionVariableScaffoldingHistory[T, SA])

  private given [T: ReadWriter, GA: ReadWriter, SA: ReadWriter]: ReadWriter[Payload[T, GA, SA]] = macroRW

  given [T: ReadWriter, GA: ReadWriter, SA: ReadWriter]: ReadWriter[InteractionVariableHistory[T, GA, SA]] =
    readwriter[ujson.Value].bimap(
      history => writeJs(Payload(history.historyStates, history.historyGrading, history.historyScaffolding)),
      value => {
        val parsed = read[Payload[T, GA, SA]](value)
        InteractionVariableHistory(parsed.historyStates, parsed.historyGrading, parsed.historyScaffolding)
      })

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
