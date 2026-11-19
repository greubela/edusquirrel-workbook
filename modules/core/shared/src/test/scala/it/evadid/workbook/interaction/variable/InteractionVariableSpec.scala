package it.evadid.workbook.interaction.variable

import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.util.logging.Logger
import it.evadid.util.logging.derived.PrintToStdLogger
import it.evadid.workbook.abstractions.InteractionFormat.{BinaryPixelInteractionFormat, TextInteractionFormat}
import it.evadid.workbook.elements.interactionElements.basic.TextInteraction
import it.evadid.workbook.elements.interactionElements.pixel.BinaryPixelInteraction
import it.evadid.workbook.interaction.sync.UpdateImportance.*
import it.evadid.workbook.interaction.sync.model.InteractionHistoryJson
import it.evadid.workbook.interaction.variable.events.{AnswerChangedEvent, InteractionEvent, InteractionEvents}
import it.evadid.workbook.meta.ExecutionContextInformation
import it.evadid.workbook.model.pixel.BinaryPixelImage
import java.time.LocalDateTime
import munit.FunSuite

/** The variable owns one history: user edits, restored records and resets all go through it. */
class InteractionVariableSpec extends FunSuite {
  private val context = ExecutionContextInformation.default
  private val logger = Logger.withNameAndPrefixes(Some("InteractionVariableSpec"), PrintToStdLogger.printNothing)
  private val at = LocalDateTime.of(2026, 6, 1, 8, 0)
  private type Text = TextInteractionFormat.type
  private def text(values: (String, Int)*): InteractionVariableHistory[Text] =
    InteractionVariableHistory(InteractionEvents(values.map((value, second) =>
      AnswerChangedEvent[Text](value, at.plusSeconds(second), MINOR): InteractionEvent[Text]).toSet))

  test("a new variable holds the element's default and a stable storage key") {
    val variable = TextInteraction("essay").interactionVariable
    assertEquals(variable.currentValue, "")
    assertEquals(variable.currentHistory.allStates.map(_.updateImportance), Set(DEFAULT))
    assertEquals(variable.keyForSerialization, "essay_history")
  }

  test("user edits append states; repeating the same value and importance is ignored") {
    val variable = TextInteraction("essay").interactionVariable
    variable.setStateFromUserInteraction("draft", MINOR, at, context)
    variable.setStateFromUserInteraction("draft", MINOR, at.plusSeconds(1), context)
    assertEquals(variable.currentHistory.allStates.map(_.newState), Set("draft"), "the default is superseded by the first edit")
    variable.setStateFromUserInteraction("draft", MAJOR, at.plusSeconds(2), context)
    variable.updateStateFromUserInteraction(_ + "!", MINOR, at.plusSeconds(3), context)
    assertEquals(variable.currentValue, "draft!")
    assertEquals(variable.currentHistory.allStates.toList.sortBy(_.timestamp).map(event => event.newState -> event.updateImportance),
      List("draft" -> MINOR, "draft" -> MAJOR, "draft!" -> MINOR))
  }

  test("observers and bound states follow the history in both directions") {
    val variable = TextInteraction("essay").interactionVariable
    val seen = it.evadid.core.datastructures.state.State(Vector.empty[String])
    variable.observableValue.addObserver(value => seen.update(_ :+ value))
    val bound = variable.createBoundStateWithUpdateImportance(MINOR, context)
    bound.set("typed")
    assertEquals(variable.currentValue, "typed")
    variable.setStateFromUserInteraction("restored", MAJOR, LocalDateTime.now().plusSeconds(5), context)
    assertEquals(bound.now(), "restored")
    assert(seen.now().endsWith(Seq("typed", "restored")), seen.now().toString)
  }

  test("restoring a stored record merges it; the newest answer becomes current, also across sessions") {
    val variable = TextInteraction("essay").interactionVariable
    variable.setStateFromUserInteraction("local", MINOR, at.plusSeconds(1), context)
    variable.restoreHistory(logger, InteractionHistoryJson.fromHistory(TextInteractionFormat, text("remote" -> 2, "older" -> 0)), context)
    assertEquals(variable.currentValue, "remote")
    assertEquals(variable.currentHistory.allStates.map(_.newState), Set("older", "local", "remote"))
    val before = variable.currentHistory
    variable.restoreHistory(logger, before, context)
    assertEquals(variable.currentHistory, before, "restoring what is already known changes nothing")
  }

  test("an unreadable or foreign stored record is discarded without touching the current history") {
    val variable = TextInteraction("essay").interactionVariable
    variable.setStateFromUserInteraction("kept", MINOR, at, context)
    val before = variable.currentHistory
    variable.restoreHistory(logger, InteractionHistoryJson(TextInteractionFormat, "{\"not\": \"a history\"}"), context)
    variable.restoreHistory(logger, InteractionHistoryJson.fromHistory(BinaryPixelInteractionFormat,
      InteractionVariableHistory.empty[BinaryPixelInteractionFormat.type]), context)
    assertEquals(variable.currentHistory, before)
  }

  test("a stored record holding an answer the exercise rejects is discarded instead of failing the restore") {
    val exercise = BinaryPixelInteraction("pixels", LanguageMapContentId("spec/pixels"), BinaryPixelImage.blank(2, 2))
    val variable = exercise.interactionVariable
    val before = variable.currentHistory
    val resized = InteractionVariableHistory[BinaryPixelInteractionFormat.type](InteractionEvents(Set(
      AnswerChangedEvent[BinaryPixelInteractionFormat.type](BinaryPixelImage.blank(3, 3), at, MAJOR))))
    variable.restoreHistory(logger, InteractionHistoryJson.fromHistory(BinaryPixelInteractionFormat, resized), context)
    variable.restoreHistory(logger, resized, context)
    assertEquals(variable.currentHistory, before)
    intercept[IllegalArgumentException](variable.setStateFromUserInteraction(BinaryPixelImage.blank(3, 3), MAJOR, at, context))
    assertEquals(variable.currentHistory, before, "a rejected edit leaves the history unchanged")
  }

  test("resets return to the default answer") {
    val variable = TextInteraction("essay").interactionVariable
    variable.setStateFromUserInteraction("draft", MAJOR, at, context)
    variable.resetLocalHistory()
    assertEquals(variable.currentValue, "")
    variable.setStateFromUserInteraction("again", MAJOR, at, context)
    variable.resetForNewSession()
    assertEquals(variable.currentValue, "")
    assertEquals(variable.currentHistory.allStates.map(_.updateImportance), Set(DEFAULT))
  }
}
