package it.evadid.workbook.elements.interactionElements.slideshow

import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.workbook.abstractions.{LangMapContentIdType, RoleInWorkbook, TypeOfTextDisplay, WorkbookElement}
import it.evadid.workbook.elements.displayElements.DisplayLangMapContent
import it.evadid.workbook.jsonFactory.WorkbookElementReference
import java.time.LocalDateTime
import munit.FunSuite
import upickle.default.*

class SlideshowSpec extends FunSuite {
  private def panel(id: String): DisplayLangMapContent =
    DisplayLangMapContent(id, LanguageMapContentId(s"slides/$id"),
      LangMapContentIdType(RoleInWorkbook.EXERCISE_DESCRIPTION, TypeOfTextDisplay.MARKDOWN))

  private val first = panel("first")
  private val second = panel("second")
  private val third = panel("third")
  private val time = LocalDateTime.of(2026, 11, 8, 12, 0)

  test("panel access handles empty decks and indices outside either boundary") {
    val deck = Slideshow("slides", List(first, second))
    assertEquals(deck.panelSize, 2)
    assertEquals(deck.getPanel(0).map(_.elementId), Some("first"))
    assertEquals(deck.getPanel(1).map(_.elementId), Some("second"))
    for index <- List(Int.MinValue, -1, 2, Int.MaxValue) do
      assertEquals(deck.getPanel(index), None)
    assertEquals(Slideshow("empty", Nil).getPanel(0), None)
  }

  test("panel lookup uses the first matching id and accepts references without a type hint") {
    val deck = Slideshow("slides", List(first, second, first))
    assertEquals(deck.indexOf(first), Some(0))
    assertEquals(deck.indexOf(second.asRef), Some(1))
    assertEquals(deck.indexOf(WorkbookElementReference("first", None)), Some(0))
    assertEquals(deck.indexOf(panel("missing")), None)
  }

  test("slideshow codecs and the reference-based factory preserve panel order") {
    val deck = Slideshow("slides", List(second, first))
    assertEquals(read[Slideshow](write(deck)), deck)
    assertEquals(deck.childrenOfThisElement, List(second, first))
    val serialized = deck.toSerialized
    assertEquals(Slideshow.factory.idsRequiredForDeserialization(serialized), Set("first", "second"))
    val panels: Map[String, WorkbookElement] = Map("first" -> first, "second" -> second)
    assertEquals(Slideshow.factory.fromSerializedElement(serialized, panels), deck)
    assertEquals(deck.defaultValue.allPanels, List(second.asRef, first.asRef))
  }

  test("transition lookup includes arrivals, departures and self-transitions without duplicate timestamps") {
    val initial = Slideshow("slides", List(first, second, third)).defaultValue
    val state = initial.recordTransitionByIndex(0, 1, time)
      .recordTransitionByIndex(1, 1, time.plusSeconds(1))
      .recordTransitionByIndex(1, 2, time.plusSeconds(2))

    assertEquals(state.timestampsWhereUserSwitchedFromOrToPanel(first), Set(time))
    assertEquals(state.timestampsWhereUserSwitchedFromOrToPanel(second),
      Set(time, time.plusSeconds(1), time.plusSeconds(2)))
    assertEquals(state.timestampsWhereUserSwitchedFromOrToPanel(third), Set(time.plusSeconds(2)))
    assertEquals(state.timestampsWhereUserSwitchedFromOrToPanel(panel("unrelated")), Set.empty[LocalDateTime])
    assertEquals(initial.events, Set.empty[SlideshowState.SlideshowProceededEvent])
    assertEquals(state.seenPanels, Set(first.asRef, second.asRef, third.asRef))
    assertEquals(state.unseenPanels, Set.empty[WorkbookElementReference])
  }

  test("transition identity is the panel id rather than the optional reference type or display content") {
    val references = List(WorkbookElementReference("first", None), WorkbookElementReference("second", None))
    val state = SlideshowState(references, Set.empty).recordTransitionByIndex(0, 1, time)
    val updatedDisplay = first.copy(content = LanguageMapContentId("slides/updated"))
    assertEquals(state.timestampsWhereUserSwitchedFromOrToPanel(updatedDisplay), Set(time))
  }

  test("serialized history retains timestamp lookup and the unseen panel set") {
    val initial = Slideshow("slides", List(first, second, third)).defaultValue
    val state = initial.recordTransitionByIndex(0, 1, time)
    val restored = state.serializer().deserialize(state.serializer().serialize(state))
    assertEquals(restored, state)
    assertEquals(read[SlideshowState](write(state)), state)
    assertEquals(restored.timestampsWhereUserSwitchedFromOrToPanel(second), Set(time))
    assertEquals(restored.unseenPanels, Set(third.asRef))
  }
}
