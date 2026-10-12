package it.evadid.homepage.webElements.editor.neuron

import com.raquo.laminar.api.L.*
import it.evadid.homepage.webElements.{FullscreenLifecycle, HtmlAppElement}
import it.evadid.workbook.elements.interactionElements.neuron.*
import it.evadid.core.datastructures.language.LanguageMapContentId

case class ThresholdNeuronEditor(state: Var[NeuronParameters], labels: List[LanguageMapContentId], examples: List[NeuronExample], initial: NeuronParameters)
    extends HtmlAppElement with FullscreenLifecycle {
  private val draft = new NeuronEditorState(state, examples)
  private def textFor(key: String): Signal[String] = laminarHelper.plaintextStringSignal(s"digitalWorkbooks/$key")
  private def binary(value: Boolean): String = if value then "1" else "0"
  override def getDomElement(): Element = div(cls := "neuron-editor",
    onMountCallback(ctx => state.signal.changes.foreach(draft.restore)(using ctx.owner)),
    h2(text <-- textFor("neuronTitle")), p(text <-- textFor("neuronExplanation")),
    div(cls := "neuron-controls",
      labels.zipWithIndex.map { (labelId, i) => label(
        span(text <-- laminarHelper.plaintextStringSignal(labelId)),
        input(typ := "number", stepAttr := "any", value <-- draft.weights.signal.map(_.lift(i).getOrElse("")),
          onInput.mapToValue --> { value => draft.weights.update(_.updated(i, value)); draft.commit() })) },
      label(span(text <-- textFor("threshold")), input(typ := "number", stepAttr := "any", value <-- draft.threshold.signal,
        onInput.mapToValue --> { value => draft.threshold.set(value); draft.commit() })),
      button(typ := "button", text <-- textFor("reset"), onClick --> (_ => {
        state.set(initial); draft.restore(initial)
      }))
    ),
    p(cls := "neuron-error", role := "alert", text <-- draft.invalid.signal.flatMapSwitch(b => if b then textFor("invalidNumbers") else Val(""))),
    div(cls := "neuron-results", table(
      caption(text <-- textFor("examples")),
      thead(tr(th(text <-- textFor("example")), labels.map(id => th(text <-- laminarHelper.plaintextStringSignal(id))),
        th(text <-- textFor("sum")), th(text <-- textFor("output")), th(text <-- textFor("expected")), th(text <-- textFor("result")))),
      tbody(examples.map(e => tr(
        th(text <-- laminarHelper.plaintextStringSignal(e.label)), e.inputs.map(x => td(x.toInt.toString)),
        td(text <-- state.signal.map(_.weightedSum(e.inputs).toString)),
        td(text <-- state.signal.map(p => binary(p.activates(e.inputs)))), td(binary(e.expected)),
        td(text <-- state.signal.map(_.activates(e.inputs) == e.expected).flatMapSwitch(b => textFor(if b then "matches" else "differs")))
      )))
    )),
    p(role := "status", aria.live := "polite", text <-- state.signal.map(p => examples.count(e => p.activates(e.inputs) == e.expected))
      .combineWith(textFor("neuronProgress")).map((count, text) => text.replace("{count}", count.toString).replace("{total}", examples.size.toString)))
  )
  override def onFullscreenClose(): Unit = ()
  override def dismissOnOutsideClick: Boolean = false
}
