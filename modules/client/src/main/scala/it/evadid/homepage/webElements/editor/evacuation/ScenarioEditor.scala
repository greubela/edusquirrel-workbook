package it.evadid.homepage.webElements.editor.evacuation

import com.raquo.laminar.api.L.*
import it.evadid.evacuation.eva2.control.modes.ScenarioEditorMode
import it.evadid.evacuation.eva2.model.EvaFloorMap
import it.evadid.homepage.webElements.{FullscreenLifecycle, HtmlAppElement}
import it.evadid.workbook.model.evacuation.*
import it.evadid.workbook.elements.interactionElements.evacuation.EvacuationFloorRequirements

/** Local EVA2 state; no standalone ProgramState, resource downloads or simulation timers. */
case class ScenarioEditor(answer: Var[EvacuationFloorPlan], initial: EvacuationFloorPlan, locked: Signal[Boolean],
    requirements: EvacuationFloorRequirements = EvacuationFloorRequirements())
    extends HtmlAppElement with FullscreenLifecycle {
  val floorMap: Var[EvaFloorMap] = Var(EvacuationFloorAdapter.decode(answer.now()))
  private val selected = Var(0)
  private var isLocked = false
  private def textFor(key: String): Signal[String] = laminarHelper.plaintextStringSignal(s"digitalWorkbooks/$key")
  private val mode = ScenarioEditorMode(
    () => floorMap.now(),
    map => { val normalized = EvacuationFloorAdapter.encode(map)
      floorMap.set(EvacuationFloorAdapter.decode(normalized))
      answer.set(normalized)
    },
    () => EvacuationFloorAdapter.sprites, () => (), () => !isLocked,
    Some(EvacuationFloorPlan.maxDimension))
  mode.selectSprite(EvacuationFloorAdapter.floor)

  private def resize(key: String, horizontal: Boolean, add: Boolean): Element = button(typ := "button",
    text <-- textFor(key), disabled <-- locked.combineWith(floorMap.signal).map((disabled, map) => {
      val size = if (horizontal) map.floorMatrix.dim.cols else map.floorMatrix.dim.rows
      disabled || (if (add) size >= EvacuationFloorPlan.maxDimension else size <= 1)
    }), onClick --> (_ => if (add) mode.handleExtend(false, false, !horizontal, horizontal)
      else mode.handleShrink(false, false, !horizontal, horizontal)))

  override def getDomElement(): Element = div(cls := "evacuation-editor",
    onMountCallback(ctx => {
      isLocked = locked.observe(using ctx.owner).now()
      locked.changes.foreach(value => isLocked = value)(using ctx.owner)
      answer.signal.changes.foreach(plan => {
        if (EvacuationFloorAdapter.encode(floorMap.now()) != plan) floorMap.set(EvacuationFloorAdapter.decode(plan))
      })(using ctx.owner)
    }),
    h2(text <-- textFor("evacuationTitle")), p(text <-- textFor("evacuationTask").map(_.replace("{minPeople}", requirements.minPeople.toString)
      .replace("{minExits}", requirements.minExits.toString))),
    div(cls := "evacuation-tools", EvacuationFloorAdapter.sprites.sprites.zipWithIndex.map((sprite, index) =>
      button(typ := "button", disabled <-- locked, text <-- textFor(s"evacuation${List("Floor", "Wall", "Exit", "Person")(index)}"),
        aria.pressed <-- selected.signal.map(i => (i == index).toString), onClick --> (_ => {
          selected.set(index); mode.selectSprite(sprite)
        })))),
    div(cls := "evacuation-tools", resize("evacuationAddRow", false, true), resize("evacuationRemoveRow", false, false),
      resize("evacuationAddColumn", true, true), resize("evacuationRemoveColumn", true, false),
      button(typ := "button", disabled <-- locked, text <-- textFor("evacuationReset"), onClick --> (_ => {
        floorMap.set(EvacuationFloorAdapter.decode(initial)); answer.set(initial)
      }))),
    EvacuationFloorView(floorMap.signal, locked, pos => mode.mainAreaTileMapController.onMouseClickingOnTile(pos)).getDomElement())
  override def onFullscreenClose(): Unit = mode.onLeavingMode()
  override def dismissOnOutsideClick: Boolean = false
}
