package it.evadid.homepage.workbook.htmlRenderer.controlElements.interactionInfo

import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L.*
import com.raquo.laminar.nodes.ReactiveSvgElement
import it.evadid.core.datastructures.state.StateHelper.RichObservableValue
import it.evadid.homepage.control.model.FullInfo
import it.evadid.homepage.workbook.htmlRenderer.LaminarRenderHelper
import it.evadid.homepage.workbook.htmlRenderer.controlElements.interactionInfo.IconGrading.GradingView
import it.evadid.workbook.abstractions.grading.{GradingImportance, GradingResult, GradingStatus}
import it.evadid.workbook.abstractions.{WorkbookInteractionElementFull, WorkbookInteractionUntyped}
import it.evadid.workbook.interaction.variable.InteractionVariableHistory
import it.evadid.workbook.meta.InteractionVariableGradingReporter
import org.scalajs.dom.{SVGPathElement, SVGSVGElement}

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.util.Try

object IconGrading {

  private case class GradingView(mount: Option[AnyRef] = None, revision: Long = 0,
                                 pending: Option[AnyRef] = None, failed: Boolean = false)






}



case class IconGrading(fullInfo: FullInfo, laminarHelper: LaminarRenderHelper, workbookInteractionElement: WorkbookInteractionUntyped) {

  val gradingReporter: InteractionVariableGradingReporter[?, ?, ?] = InteractionVariableGradingReporter(workbookInteractionElement.interactionVariable)

  val domElement: Element = div(cls := "interaction-grading",
    button(typ := "button", cls := "interaction-grading__request",
      onClick --> (_ => workbookInteractionElement.interactionVariable.control.getOrCalculateGrading(fullInfo.executionContextInformation, true)))
  )





  def gradingControls[T, GA, SA](interaction: WorkbookInteractionElementFull[T, GA, SA]): Option[Element] =
    Option.when(interaction.gradingElements.nonEmpty) {
      val variable = interaction.interactionVariable
      val history = variable.observableHistoryWithDefault.toEventStream().toSignal(variable.currentHistory)
      val view = Var(GradingView())
      val visible = interaction.gradingElements.filter(_.importance != GradingImportance.HIDDEN_TEST).distinct

      def invalidate(mount: Option[AnyRef]): Unit =
        view.update(previous => GradingView(mount, previous.revision + 1))

      def status(history: InteractionVariableHistory[T, GA, SA]): String = {
        val results: List[GradingResult[?, ?]] = history.historyStates.lastStateOption.toList.flatMap { current =>
          history.historyGrading.gradingsMap.get(current.value).filter(_.state == current.value).toList.flatMap(_.testsWithResults).filter(_.gradedState == current.value)
        }
        val statuses = visible.map { grader =>
          val matches = results.filter(_.grader == grader)
          val latest = matches.maxByOption(_.gradingTime).toList.flatMap { last =>
            matches.filter(_.gradingTime == last.gradingTime).map(_.gradingStatus)
          }.distinct
          latest
        }
        if visible.isEmpty then "gradingPrivate"
        else if statuses.exists(value => value.size > 1 || value.exists(_.isInstanceOf[GradingStatus.TestError])) then "gradingError"
        else if statuses.exists(value => value.isEmpty || value.contains(GradingStatus.NotRequested())) then "gradingNotRun"
        else if statuses.exists(value => value.isEmpty || value.contains(GradingStatus.NotFinished())) then "gradingLoading"
        else if statuses.flatten.contains(GradingStatus.Incorrect()) then "gradingIncorrect"
        else if statuses.flatten.contains(GradingStatus.PartiallyCorrect()) then "gradingPartial"
        else "gradingCorrect"
      }

      def request(): Unit = {
        val clicked = view.now()
        val source = variable.currentValue
        queue.execute { () =>
          def unchanged: Boolean = clicked.mount.nonEmpty && view.now().mount == clicked.mount &&
            view.now().revision == clicked.revision && variable.currentValue == source

          if unchanged && view.now().pending.isEmpty then {
            val id = new Object
            view.set(clicked.copy(pending = Some(id)))

            def pending: Boolean = unchanged && view.now().pending.contains(id)

            if pending then Try(variable.control.getOrCalculateGrading(fullInfo.executionContextInformation, forceGrading = true))
              .fold(Future.failed, identity).onComplete { result =>
                if pending then view.update(_.copy(pending = None, failed = result.isFailure))
              }
          }
        }
      }

      val message: Signal[String] = history.combineWith(view.signal).map { (snapshot, current) =>
        if current.pending.nonEmpty then "gradingLoading"
        else if current.failed then "gradingError"
        else status(snapshot)
      }.distinct

      div(cls := "interaction-grading",
        onMountCallback(_ => invalidate(Some(new Object))),
        onUnmountCallback(_ => invalidate(None)),
        onMountBind(_ => history.map(_.historyStates.lastStateOption.map(_.value)).distinct.changes --> (_ => invalidate(view.now().mount))),
        onMountBind(_ => fullInfo.signals.currentLanguage.distinct.changes --> (_ => invalidate(view.now().mount))),
        onMountBind(_ => EventStream.merge(fullInfo.signals.user.distinct.changes.map(_ => ()),
          fullInfo.signals.workbook.map(_.map(_.loadedWorkbook)).distinct.changes.map(_ => ())) -->
          (_ => invalidate(view.now().mount))),
        button(typ := "button", cls := "interaction-grading__request",
          disabled <-- view.signal.map(_.pending.nonEmpty), child.text <-- laminarHelper.plaintextStringSignal("basic/gradingRequest"),
          onClick --> (_ => request())),
        p(cls := "interaction-grading__status", role := "status", aria.live := "polite",
          aria.busy <-- view.signal.map(_.pending.nonEmpty), dataAttr("status") <-- message,
          child.text <-- message.flatMapSwitch(key => laminarHelper.plaintextStringSignal(s"basic/$key"))
        ))
    }


}
