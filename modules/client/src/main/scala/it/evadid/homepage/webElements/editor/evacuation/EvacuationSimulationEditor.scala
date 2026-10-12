package it.evadid.homepage.webElements.editor.evacuation

import com.raquo.laminar.api.L.*
import it.evadid.homepage.webElements.{FullscreenLifecycle, HtmlAppElement}
import it.evadid.workbook.model.evacuation.*
import org.scalajs.dom
import scala.util.Try

case class EvacuationSimulationEditor(answer: Var[EvacuationExperiment], initial: EvacuationExperiment, locked: Signal[Boolean])
    extends HtmlAppElement with FullscreenLifecycle {
  private val editing = Var(true)
  private val plan = Var(answer.now().floor)
  private val speedDraft = Var(answer.now().settings.speedMetresPerSecond.toString)
  private val labelDraft = Var("")
  private val error = Var("")
  private val playback = new EvacuationPlayback(callback => {
    val timer = dom.window.setTimeout(() => callback(), 350)
    () => dom.window.clearTimeout(timer)
  })
  private def textFor(key: String): Signal[String] = laminarHelper.plaintextStringSignal(s"digitalWorkbooks/$key")
  private def resetPlayback(): Unit = { playback.clear(); editing.set(true); error.set("") }
  private def settingsSpeed(value: String): Option[Double] = EvacuationSettings.parseSpeed(value)
  override def getDomElement(): Element = div(cls := "evacuation-experiment",
    onMountCallback(ctx => {
      plan.signal.changes.foreach(floor => if (answer.now().floor != floor) {
        resetPlayback(); answer.update(_.copy(floor = floor))
      })(using ctx.owner)
      answer.signal.changes.foreach(value => {
        if (plan.now() != value.floor) plan.set(value.floor)
        if (!settingsSpeed(speedDraft.now()).contains(value.settings.speedMetresPerSecond))
          speedDraft.set(value.settings.speedMetresPerSecond.toString)
        if (playback.ready.now() && playback.result.now().exists(run => run.floor != value.floor || run.settings != value.settings)) resetPlayback()
      })(using ctx.owner)
      locked.changes.foreach(value => if (value) playback.pause())(using ctx.owner)
    }), onUnmountCallback(_ => playback.pause()),
    h2(text <-- textFor("evacuationSimulationTitle")), p(text <-- textFor("evacuationRules")),
    div(cls := "evacuation-tools",
      label(span(text <-- textFor("evacuationNeighbourhood")), select(disabled <-- locked,
        value <-- answer.signal.map(_.settings.neighbourhood.toString),
        option(value := "Four", text <-- textFor("evacuationFour")), option(value := "Eight", text <-- textFor("evacuationEight")),
        onChange.mapToValue --> (value => { resetPlayback(); answer.update(e => e.copy(settings = e.settings.copy(
          neighbourhood = EvacuationNeighbourhood.valueOf(value)))) }))),
      label(span(text <-- textFor("evacuationSpeed")), input(typ := "text", disabled <-- locked,
        aria.invalid <-- speedDraft.signal.map(v => settingsSpeed(v).isEmpty.toString),
        value <-- speedDraft.signal, onInput.mapToValue --> (value => {
          speedDraft.set(value); resetPlayback()
          settingsSpeed(value).foreach(speed => answer.update(e => e.copy(settings = e.settings.copy(speedMetresPerSecond = speed))))
        }))),
      button(typ := "button", text <-- textFor("evacuationPrepare"),
        disabled <-- locked.combineWith(speedDraft.signal).map((disabled, value) => disabled || settingsSpeed(value).isEmpty),
        onClick --> (_ => { Try(playback.prepare(answer.now())).fold(_ => error.set("evacuationRunLimit"), _ => {
          error.set(""); editing.set(false)
        }) })),
      button(typ := "button", text <-- textFor("evacuationEdit"), disabled <-- locked,
        onClick --> (_ => resetPlayback()))),
    p(cls := "evacuation-error", role := "alert", text <-- error.signal.combineWith(speedDraft.signal).flatMapSwitch((key, draft) =>
      if (settingsSpeed(draft).isEmpty) textFor("evacuationSpeedInvalid") else if (key.isEmpty) Val("") else textFor(key))),
    child <-- editing.signal.map(edit => if (edit) ScenarioEditor(plan, initial.floor, locked).getDomElement()
      else div(cls := "evacuation-editor evacuation-playback",
        div(cls := "evacuation-tools",
          button(typ := "button", text <-- textFor("evacuationPrevious"), disabled <-- locked.combineWith(playback.step.signal).map((disabled, step) => disabled || step == 0), onClick --> (_ => playback.previous())),
          button(typ := "button", text <-- textFor("evacuationNext"), disabled <-- locked.combineWith(playback.atEnd.signal).map(_ || _), onClick --> (_ => playback.next())),
          button(typ := "button", text <-- textFor("evacuationPlay"), disabled <-- locked.combineWith(playback.running.signal, playback.atEnd.signal).map((disabled, busy, done) => disabled || busy || done), onClick --> (_ => playback.play())),
          button(typ := "button", text <-- textFor("evacuationPause"), disabled <-- playback.running.signal.map(!_), onClick --> (_ => playback.pause())),
          button(typ := "button", text <-- textFor("evacuationRestart"), onClick --> (_ => playback.reset()))),
        p(role := "status", aria.live := "polite", text <-- playback.step.signal.combineWith(playback.floor.signal, playback.atEnd.signal)
          .flatMapSwitch((step, floor, done) => textFor(if (done) s"evacuation${playback.result.now().get.outcome}" else "evacuationPlaying")
            .map(_.replace("{steps}", step.toString).replace("{remaining}", floor.persons.size.toString)))),
        EvacuationFloorView(playback.floor.signal, Val(true), _ => ()).getDomElement())),
    div(cls := "evacuation-tools", label(span(text <-- textFor("evacuationRunLabel")),
      input(typ := "text", maxLength := 80, disabled <-- locked, value <-- labelDraft.signal, onInput.mapToValue --> labelDraft)),
      button(typ := "button", text <-- textFor("evacuationRecord"),
        disabled <-- locked.combineWith(playback.atEnd.signal, labelDraft.signal, answer.signal)
          .map((disabled, done, label, value) => disabled || !done || label.trim.isEmpty || value.measurements.size >= EvacuationExperiment.maxMeasurements),
        onClick --> (_ => { answer.update(_.record(playback.measurement(labelDraft.now()))); labelDraft.set("") }))),
    div(cls := "evacuation-measurements", child <-- answer.signal.map(value => table(
      thead(tr(List("evacuationRunLabel", "evacuationSetup", "evacuationSteps", "evacuationSeconds", "evacuationRemaining", "evacuationOutcome", "evacuationAction").map(key => th(text <-- textFor(key))))),
      tbody(value.measurements.zipWithIndex.map((run, index) => tr(td(run.label), td(text <-- textFor("evacuationRunSetup").combineWith(textFor(if (run.settings.neighbourhood == EvacuationNeighbourhood.Four) "evacuationFour" else "evacuationEight"))
          .map((template, neighbours) => template.replace("{cols}", run.floor.cols.toString).replace("{rows}", run.floor.rows.toString)
            .replace("{people}", run.floor.people.size.toString).replace("{exits}", run.floor.exitCount.toString)
            .replace("{neighbours}", neighbours).replace("{speed}", run.settings.speedMetresPerSecond.toString))), td(run.steps.toString), td(f"${run.seconds}%.2f"),
        td(run.remainingPeople.toString), td(text <-- textFor(s"evacuation${run.outcome}Name")),
        td(button(typ := "button", disabled <-- locked, text <-- textFor("evacuationRestoreRun"),
          onClick --> (_ => { resetPlayback(); answer.update(_.copy(floor = run.floor, settings = run.settings)) })),
        button(typ := "button", disabled <-- locked, text <-- textFor("evacuationRemoveRun"),
          onClick --> (_ => answer.update(e => e.copy(measurements = e.measurements.patch(index, Nil, 1)))))))))))),
    p(text <-- textFor("evacuationMeasurementsNote")))
  override def onFullscreenClose(): Unit = playback.pause()
  override def dismissOnOutsideClick: Boolean = false
}
