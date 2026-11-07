package it.evadid.workbook.interaction.variable

import it.evadid.workbook.abstractions.scaffolding.ScaffoldingResult
import upickle.default.*

case class InteractionVariableScaffoldingHistory[T, SA]
(
  scaffoldingMap: Map[T, ScaffoldingResult[T, SA]]
) {

  lazy val scaffoldingsFinished: Set[ScaffoldingResult[T, SA]] = scaffoldingMap.values.toSet

  private[interaction] def merge(other: InteractionVariableScaffoldingHistory[T, SA]): InteractionVariableScaffoldingHistory[T, SA] = {
    val merged = other.scaffoldingMap.foldLeft(scaffoldingMap) { case (current, (state, incoming)) =>
      if incoming.forState != state then current
      else current.get(state) match {
        case Some(saved) if saved.forState == state && incoming.scaffoldingTime.isBefore(saved.scaffoldingTime) => current
        case _ => current.updated(state, incoming)
      }
    }
    new InteractionVariableScaffoldingHistory(merged)
  }

  private[interaction] def get(state: T): Option[ScaffoldingResult[T, SA]] = scaffoldingsFinished.find(_.forState == state)

}

object InteractionVariableScaffoldingHistory {

  given [T: ReadWriter, SA: ReadWriter]: ReadWriter[InteractionVariableScaffoldingHistory[T, SA]] = macroRW

  def apply[T, SA](scaffoldingsFinished: Set[ScaffoldingResult[T, SA]]): InteractionVariableScaffoldingHistory[T, SA] = {
    val resMap = scaffoldingsFinished.map(curGrad => curGrad.forState -> curGrad).toMap
    InteractionVariableScaffoldingHistory(resMap)
  }

}
