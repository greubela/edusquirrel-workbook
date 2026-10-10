package it.evadid.workbook.elements.interactionElements.slideshow

import it.evadid.core.util.io.Serializer
import it.evadid.core.util.io.serializer.DefaultSerializer
import it.evadid.workbook.abstractions.WorkbookDisplayElement
import it.evadid.workbook.elements.interactionElements.slideshow.SlideshowState.*
import it.evadid.workbook.jsonFactory.WorkbookElementReference
import upickle.default.*

import java.time.LocalDateTime

/**
 * Stores the interaction history for a slideshow without owning any UI rendering details.
 * Transition recording lives here so renderers only decide when navigation happened while the model decides how that navigation is represented.
 */
case class SlideshowState(
                           allPanels: List[WorkbookElementReference],
                           events: Set[SlideshowProceededEvent]
                         ) {

  lazy val seenPanels: Set[WorkbookElementReference] = events.flatMap(state => List(state.newPanel, state.oldPanel)).toSet

  lazy val unseenPanels: Set[WorkbookElementReference] = allPanels.filter(curPanel => !seenPanels.contains(curPanel)).toSet

  private lazy val eventsSorted: List[SlideshowProceededEvent] = events.toList.sortBy(_.proceededAt)

  /**
   * Records that the user moved from one panel index to another and returns the updated slideshow state.
   * Both indices are resolved against `allPanels`, keeping event creation tied to the model's panel list rather than to a specific renderer.
   */
  def recordTransitionByIndex(oldPanelIndex: Int, newPanelIndex: Int, proceededAt: LocalDateTime = LocalDateTime.now()): SlideshowState = {
    val event = SlideshowProceededEvent(allPanels(oldPanelIndex), allPanels(newPanelIndex), proceededAt)
    copy(events = events + event)
  }

  def timestampsWhereUserSwitchedFromOrToPanel(panel: WorkbookDisplayElement): Set[LocalDateTime] =
    timestampsWhereUserSwitchedFromPanel(panel) ++ timestampsWhereUserSwitchedToPanel(panel)

  private def timestampsWhereUserSwitchedFromPanel(panel: WorkbookDisplayElement): Set[LocalDateTime] =
    eventsSorted.filter(_.oldPanel == panel).map(_.proceededAt).toSet

  private def timestampsWhereUserSwitchedToPanel(panel: WorkbookDisplayElement): Set[LocalDateTime] =
    eventsSorted.filter(_.newPanel == panel).map(_.proceededAt).toSet

  def serializer(): Serializer[SlideshowState] = new Serializer[SlideshowState] {
    override def serialize(obj: SlideshowState): String = write(obj)

    override def deserialize(str: String): SlideshowState = read(str)
  }

}

object SlideshowState {


  case class SlideshowProceededEvent(oldPanel: WorkbookElementReference, newPanel: WorkbookElementReference, proceededAt: LocalDateTime) derives ReadWriter {

  }

  private[slideshow] given ldt: ReadWriter[LocalDateTime] = DefaultSerializer.serializerLocalDateTimeString.uPickleReadWrite

   given ReadWriter[SlideshowState] = macroRW

}
