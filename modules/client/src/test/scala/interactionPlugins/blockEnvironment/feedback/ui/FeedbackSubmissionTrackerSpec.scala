package it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.feedback.ui

import munit.FunSuite
import scala.scalajs.js

final class FeedbackSubmissionTrackerSpec extends FunSuite:
  test("a current submission can publish its result") {
    val tracker = new FeedbackSubmissionTracker
    val submission = tracker.begin().get
    assertEquals(tracker.finish(submission), Some(true))
    assert(tracker.begin().nonEmpty)
  }

  test("editing invalidates the result without releasing the running request") {
    val tracker = new FeedbackSubmissionTracker
    val submission = tracker.begin().get
    tracker.invalidate()
    assertEquals(tracker.begin(), None)
    assertEquals(tracker.finish(submission), Some(false))
    val next = tracker.begin().get
    assertEquals(tracker.finish(next), Some(true))
  }

  test("changing away and back does not revive an earlier result") {
    val tracker = new FeedbackSubmissionTracker
    val submission = tracker.begin().get
    tracker.invalidate()
    tracker.invalidate()
    assertEquals(tracker.finish(submission), Some(false))
  }

  test("repeated starts cannot replace a running submission") {
    val tracker = new FeedbackSubmissionTracker
    val submission = tracker.begin().get
    assertEquals(tracker.begin(), None)
    assertEquals(tracker.begin(), None)
    assertEquals(tracker.finish(submission), Some(true))
  }

  test("duplicate completion cannot finish a newer request") {
    val tracker = new FeedbackSubmissionTracker
    val previous = tracker.begin().get
    assertEquals(tracker.finish(previous), Some(true))
    val current = tracker.begin().get
    assertEquals(tracker.finish(previous), None)
    assertEquals(tracker.begin(), None)
    assertEquals(tracker.finish(current), Some(true))
  }

  test("late completion of an invalidated request cannot replace a newer result") {
    val tracker = new FeedbackSubmissionTracker
    val previous = tracker.begin().get
    tracker.invalidate()
    assertEquals(tracker.finish(previous), Some(false))
    val current = tracker.begin().get
    assertEquals(tracker.finish(previous), None)
    assertEquals(tracker.begin(), None)
    assertEquals(tracker.finish(current), Some(true))
    assertEquals(tracker.finish(previous), None)
    assertEquals(tracker.finish(current), None)
  }

  test("invalidation before a submission does not invalidate that new submission") {
    val tracker = new FeedbackSubmissionTracker
    tracker.invalidate()
    tracker.invalidate()
    val submission = tracker.begin().get
    assertEquals(tracker.finish(submission), Some(true))
  }

  test("disposal rejects late results and permits a fresh run") {
    val tracker = new FeedbackSubmissionTracker
    val previous = tracker.begin().get
    tracker.dispose()
    assertEquals(tracker.finish(previous), None)
    val current = tracker.begin().get
    assertEquals(tracker.finish(previous), None)
    assertEquals(tracker.begin(), None)
    assertEquals(tracker.finish(current), Some(true))
  }

final class FeedbackDemoRestoreSpec extends FunSuite:
  private val source = "def add(a, b):\n    return a + b\n"

  private def savedState(): js.Dynamic =
    js.Dynamic.literal(
      feedbackVersion = 2,
      exerciseId = "addition",
      language = "English",
      code = source,
      feedback = js.Dynamic.literal(
        exerciseId = "addition",
        language = "English",
        rawPython = source
      )
    )

  test("feedback is restorable when its task, language and source match") {
    assert(FeedbackDemoElement.canRestoreFeedback(savedState()))
  }

  test("source matching accepts CRLF and LF in either direction") {
    val state = savedState()
    state.code = source.replace("\n", "\r\n")
    assert(FeedbackDemoElement.canRestoreFeedback(state))
    state.code = source
    state.feedback.rawPython = source.replace("\n", "\r\n")
    assert(FeedbackDemoElement.canRestoreFeedback(state))
  }

  test("old, missing and incorrectly typed cache versions are rejected") {
    Seq[js.Any](1, js.undefined, null, "2").foreach { version =>
      val state = savedState()
      state.feedbackVersion = version
      assert(!FeedbackDemoElement.canRestoreFeedback(state))
    }
  }

  test("feedback from another task is rejected even when the code matches") {
    val state = savedState()
    state.feedback.exerciseId = "maximum"
    assert(!FeedbackDemoElement.canRestoreFeedback(state))
  }

  test("feedback in another language is rejected even when the code matches") {
    val state = savedState()
    state.feedback.language = "German"
    assert(!FeedbackDemoElement.canRestoreFeedback(state))
  }

  test("feedback for another source is rejected") {
    val state = savedState()
    state.feedback.rawPython = source.replace("a + b", "a - b")
    assert(!FeedbackDemoElement.canRestoreFeedback(state))
  }

  test("source matching does not collapse whitespace inside Python literals") {
    val state = savedState()
    state.code = "def text():\n    return 'a  b'\n"
    state.feedback.rawPython = "def text():\n    return 'a b'\n"
    assert(!FeedbackDemoElement.canRestoreFeedback(state))
  }

  test("source matching does not trim differences other than CRLF line endings") {
    val state = savedState()
    state.feedback.rawPython = source.stripSuffix("\n")
    assert(!FeedbackDemoElement.canRestoreFeedback(state))
  }

  test("missing, null or non-string session context fields are rejected") {
    for
      field <- Seq("exerciseId", "language", "code")
      invalid <- Seq[js.Any](js.undefined, null, 42, false)
    do
      val state = savedState()
      state.updateDynamic(field)(invalid)
      assert(!FeedbackDemoElement.canRestoreFeedback(state), field)
  }

  test("missing, null or non-string feedback context fields are rejected") {
    for
      field <- Seq("exerciseId", "language", "rawPython")
      invalid <- Seq[js.Any](js.undefined, null, 42, false)
    do
      val state = savedState()
      state.feedback.updateDynamic(field)(invalid)
      assert(!FeedbackDemoElement.canRestoreFeedback(state), field)
  }

  test("missing, null and non-object feedback are rejected") {
    Seq[js.Any](js.undefined, null, "feedback", 42, false).foreach { invalid =>
      val state = savedState()
      state.feedback = invalid
      assert(!FeedbackDemoElement.canRestoreFeedback(state))
    }
  }

  test("null and empty session objects are rejected") {
    assert(!FeedbackDemoElement.canRestoreFeedback(null.asInstanceOf[js.Dynamic]))
    assert(!FeedbackDemoElement.canRestoreFeedback(js.Dynamic.literal()))
  }
