package it.evadid.workbook.model.interaction.plugins.slideshow

import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.util.io.Serializer
import it.evadid.core.util.io.serializer.DefaultSerializer
import it.evadid.workbook.model.interaction.plugins.slideshow.SlideshowState.*
import upickle.default.{ReadWriter, macroRW}

import java.time.LocalDateTime

/**
 * Stores the interaction history for a slideshow without owning any UI rendering details.
 * Transition recording lives here so renderers only decide when navigation happened while the model decides how that navigation is represented.
 */
case class SlideshowState(
                           allPanels: List[SlideshowPanel],
                           events: Set[SlideshowProceededEvent]
                         ) {

  private lazy val eventsSorted: List[SlideshowProceededEvent] = events.toList.sortBy(_.proceededAt)

  /**
   * Records that the user moved from one panel index to another and returns the updated slideshow state.
   * Both indices are resolved against `allPanels`, keeping event creation tied to the model's panel list rather than to a specific renderer.
   */
  def recordTransitionByIndex(oldPanelIndex: Int, newPanelIndex: Int, proceededAt: LocalDateTime = LocalDateTime.now()): SlideshowState = {
    val event = SlideshowProceededEvent(allPanels(oldPanelIndex), allPanels(newPanelIndex), proceededAt)
    copy(events = events + event)
  }

  def timestampsWhereUserSwitchedFromOrToPanel(panel: SlideshowPanel): Set[LocalDateTime] =
    timestampsWhereUserSwitchedFromPanel(panel) ++ timestampsWhereUserSwitchedToPanel(panel)

  private def timestampsWhereUserSwitchedFromPanel(panel: SlideshowPanel): Set[LocalDateTime] =
    eventsSorted.filter(_.oldPanel == panel).map(_.proceededAt).toSet

  private def timestampsWhereUserSwitchedToPanel(panel: SlideshowPanel): Set[LocalDateTime] =
    eventsSorted.filter(_.newPanel == panel).map(_.proceededAt).toSet

  def serializer(): Serializer[SlideshowState] = new Serializer[SlideshowState] {
    override def serialize(obj: SlideshowState): String = {
      val serializedEvents = obj.eventsSorted.map(_.toSerialized(allPanels))
      SlideshowState.serializerSlideshowEventList.serialize(serializedEvents.toList)
    }

    override def deserialize(str: String): SlideshowState = {
      val states: List[SlideshowProceededEventSerialized] = SlideshowState.serializerSlideshowEventList.deserialize(str)
      SlideshowState(allPanels, states.map(_.toDeserialized(allPanels)).toSet)
    }
  }

}

object SlideshowState {

  case class SlideshowProceededEvent(oldPanel: SlideshowPanel, newPanel: SlideshowPanel, proceededAt: LocalDateTime) {
    def toSerialized(panels: List[SlideshowPanel]): SlideshowProceededEventSerialized =
      SlideshowProceededEventSerialized(panels.indexOf(oldPanel), panels.indexOf(newPanel), proceededAt.toString)
  }

  case class SlideshowProceededEventSerialized(oldPanelIndex: Int, newPanelIndex: Int, proceededAt: String) {
    def toDeserialized(panels: List[SlideshowPanel]): SlideshowProceededEvent =
      SlideshowProceededEvent(panels(oldPanelIndex), panels(newPanelIndex), LocalDateTime.parse(proceededAt))
  }

  private[slideshow] given lmci: ReadWriter[LanguageMapContentId] = LanguageMapContentId.serializer.uPickleReadWrite

  private[slideshow] given ldt: ReadWriter[LocalDateTime] = DefaultSerializer.serializerLocalDateTimeString.uPickleReadWrite

  private[slideshow] given ev: ReadWriter[SlideshowProceededEventSerialized] = macroRW


  private[slideshow] given serializerSlideshowEventList: Serializer[List[SlideshowProceededEventSerialized]] =
    Serializer.fromUpickleJson(
      summon[ReadWriter[List[SlideshowProceededEventSerialized]]]
    )

  /*
  private[slideshow] given sss: ReadWriter[SlideshowState] = macroRW


  private[slideshow] given sssList: ReadWriter[List[SlideshowState]] = macroRW

  private[slideshow] given serializerSlideshowStateList: Serializer[List[SlideshowState]] = Serializer.fromUpickleJson(sssList)
*/

}
