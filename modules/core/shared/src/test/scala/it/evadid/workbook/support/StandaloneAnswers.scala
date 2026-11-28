package it.evadid.workbook.support

import it.evadid.core.datastructures.state.State
import it.evadid.util.logging.Logger
import it.evadid.workbook.abstractions.{InteractionFormat, WorkbookInteractionElement}
import it.evadid.workbook.abstractions.scaffolding.ScaffoldingEvidence
import it.evadid.workbook.interaction.state.InteractionController
import it.evadid.workbook.interaction.sync.{SyncConnection, UpdateImportance, UsageContext}
import it.evadid.workbook.interaction.sync.model.InteractionHistoryJson
import it.evadid.workbook.interaction.variable.InteractionVariableState.InteractionVariableStateChanged
import it.evadid.workbook.interaction.variable.{InteractionScaffoldingAndGradingControl, InteractionVariableHistory, InteractionVariableState}
import it.evadid.workbook.meta.ExecutionContextInformation

import java.time.LocalDateTime

/** The answers of one exercise (and the interactions it contains) in an offline controller of their own, for tests
  * of a single exercise. Repeated lookups of the same exercise instance share the controller.
  */
final class StandaloneAnswers[F <: InteractionFormat](val interaction: WorkbookInteractionElement[F], val controller: InteractionController) {
  def currentHistory: InteractionVariableHistory[F] = controller.currentHistory(interaction)
  def currentValue: InteractionFormat.State[F] = controller.currentValue(interaction)
  def safeLastState: InteractionVariableState[InteractionFormat.State[F]] = controller.currentState(interaction)
  def control: InteractionScaffoldingAndGradingControl[F] = controller.feedback(interaction)
  def setStateFromUserInteraction(value: InteractionFormat.State[F], importance: UpdateImportance,
      timestamp: LocalDateTime = LocalDateTime.now()): Unit = controller.setStateFromUserInteraction(interaction, value, importance, timestamp)
  def updateHistory(transition: InteractionVariableHistory[F] => InteractionVariableHistory[F]): Unit =
    controller.updateHistory(interaction)(transition)
  def restoreHistory(logger: Logger, received: InteractionHistoryJson): Unit = controller.restoreHistory(logger, interaction, received)
  def resetLocalHistory(): Unit = controller.resetLocalHistory(interaction)
  def answerState(importance: UpdateImportance): State[InteractionFormat.State[F]] = controller.answerState(interaction, importance)

  /** What a hint about `state` reads from these answers. */
  def evidence(state: InteractionFormat.State[F]): ScaffoldingEvidence.Reader[InteractionFormat.State[F], InteractionFormat.Grading[F]] = () =>
    ScaffoldingEvidence(currentHistory.historyStates.lastStateOption,
      currentHistory.historyGrading.gradingsMap.get(state).filter(_.state == state).toList.flatMap(_.graderResultMap.values))

  /** Connects the answers to `context`, so following feedback requests and automatic grading use it. */
  def executingWith(context: ExecutionContextInformation): StandaloneAnswers[F] = {
    controller.connect(SyncConnection.offline.copy(execution = context))
    this
  }
}

object StandaloneAnswers {
  val context: UsageContext = UsageContext("test", "standalone", "student")

  private final class Identity(val element: AnyRef) {
    override def equals(other: Any): Boolean = other match {
      case identity: Identity => identity.element eq element
      case _ => false
    }
    override def hashCode(): Int = System.identityHashCode(element)
  }

  private val controllers = State(Map.empty[Identity, InteractionController])

  /** The answers of `interaction`; the first lookup binds it, and the interactions it contains, to a new controller. */
  def of[F <: InteractionFormat](interaction: WorkbookInteractionElement[F]): StandaloneAnswers[F] = synchronized {
    val controller = controllers.now().getOrElse(Identity(interaction), {
      val created = InteractionController(State(InteractionController.emptyState(context)))
      created.bind(context, interaction.allInteractionsInThisInstance, SyncConnection.offline)
      controllers.update(_ ++ interaction.allInteractionsInThisInstance.map(Identity(_) -> created))
      created
    })
    StandaloneAnswers(interaction, controller)
  }
}
