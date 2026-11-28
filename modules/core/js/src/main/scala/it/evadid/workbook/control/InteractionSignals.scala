package it.evadid.workbook.control

import com.raquo.airstream.state.{LazyDerivedVar, LazyStrictSignal}
import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.state.StateHelper.RichObservableValue
import it.evadid.core.datastructures.state.observable.ObservableValue
import it.evadid.workbook.abstractions.{InteractionFormat, WorkbookInteractionElement}
import it.evadid.workbook.sync.{SyncInformationWithContext, SyncResetState, UpdateImportance}
import it.evadid.workbook.model.InteractionInfo
import it.evadid.workbook.model.history.InteractionVariableHistory
import it.evadid.workbook.model.history.InteractionVariableState.InteractionVariableStateChanged
import it.evadid.workbook.reports.InteractionVariableSyncReport

import scala.util.Try

/** Airstream views of an [[InteractionController]]: deduplicated projections that observe the answers only while
  * they have observers, and editor Vars whose writes are user changes.
  */
object InteractionSignals {
  /** `project` of the latest value of `source`, current also while nothing observes it. */
  private final class Projection[S, A](source: ObservableValue[S], now: () => S, project: S => A) extends LazyStrictSignal[A, A](
    source.toSignal(now()).map(project).distinct, identity, "InteractionController", ".projection") {
    override def tryNow(): Try[A] = Try(project(now()))
  }

  extension (controller: InteractionController)
    private def projection[A](project: InteractionInfo => A): StrictSignal[A] = Projection(controller.changes, () => controller.info, project)

    def historySignal[F <: InteractionFormat](interaction: WorkbookInteractionElement[F]): StrictSignal[InteractionVariableHistory[F]] =
      projection(_.historyOf(interaction))

    def answerSignal[F <: InteractionFormat](interaction: WorkbookInteractionElement[F]): StrictSignal[InteractionFormat.State[F]] =
      projection(_.currentValueOf(interaction))

    /** The answer for an editor: each write is a user change whose importance `relevance` decides; an answer the
      * exercise rejects is reported and not recorded.
      */
    def boundVar[F <: InteractionFormat](interaction: WorkbookInteractionElement[F],
        relevance: InteractionVariableStateChanged[InteractionFormat.State[F]] => UpdateImportance): Var[InteractionFormat.State[F]] =
      new LazyDerivedVar[Unit, InteractionFormat.State[F]](Var(()), answerSignal(interaction), (unit, value) => {
        Try(controller.applyUserChange(interaction, value, relevance)).failed
          .foreach(error => controller.logger.logExceptionWarn(s"Ignored an answer of ${interaction.keyForSerialization}", error))
        unit
      }, ".answer")

    def boundVar[F <: InteractionFormat](interaction: WorkbookInteractionElement[F], importance: UpdateImportance): Var[InteractionFormat.State[F]] =
      boundVar(interaction, _ => importance)

    /** The reset progress of the current user. */
    def resetStateSignal: StrictSignal[SyncResetState] = Projection(controller.resetStates, () => controller.resetState, identity)

    /** The sync report of `interaction`; no destination counts as confirmed during a reset. */
    def reportSignal(interaction: WorkbookInteractionElement[? <: InteractionFormat]): Signal[InteractionVariableSyncReport[SyncInformationWithContext]] =
      projection(_.reportOf(interaction)).combineWith(resetStateSignal).map((report, reset) =>
        if reset == SyncResetState.Idle then report else report.copy(destinationsWithSufficientWrites = Set.empty))
}
