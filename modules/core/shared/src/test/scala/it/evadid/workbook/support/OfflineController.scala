package it.evadid.workbook.support

import it.evadid.core.datastructures.state.State
import it.evadid.workbook.abstractions.{InteractionFormat, WorkbookInteractionElement}
import it.evadid.workbook.control.InteractionController
import it.evadid.workbook.model.{InteractionInfo, InteractionScope}
import it.evadid.workbook.sync.{SyncConnection, UsageContext}
import it.evadid.workbook.abstractions.ExecutionContextInformation

/** Controllers that hold the answers of single exercises without any sync destination, for exercise tests. */
object OfflineController {
  val context: UsageContext = UsageContext("test", "standalone", "student")

  /** A controller with `interactions`, and the interactions they contain, bound offline and executing feedback with
    * `execution`.
    */
  def of(execution: ExecutionContextInformation, interactions: WorkbookInteractionElement[? <: InteractionFormat]*): InteractionController = {
    val controller = InteractionController(State(InteractionInfo.empty(context)))
    controller.bind(InteractionScope(context, interactions.flatMap(_.allInteractionsInThisInstance).distinct,
      SyncConnection.offline.copy(execution = execution)))
    controller
  }

  def of(interactions: WorkbookInteractionElement[? <: InteractionFormat]*): InteractionController =
    of(ExecutionContextInformation.default, interactions*)
}
