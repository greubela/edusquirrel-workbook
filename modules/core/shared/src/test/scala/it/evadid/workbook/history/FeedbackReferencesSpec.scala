package it.evadid.workbook.history

import it.evadid.core.datastructures.language.AppLanguage.Java
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.workbook.abstractions.InteractionFormat
import it.evadid.workbook.abstractions.InteractionFormat.{ProgrammingExerciseFormat, TextInputWithLlmGradingAndScaffoldingFormat}
import it.evadid.workbook.abstractions.grading.{GradingResult, GradingStatus}
import it.evadid.workbook.interactions.gpt.TextInputLlmGrader
import it.evadid.workbook.interactions.gpt.TextInputWithScaffoldingAndGrading.ContentForLlmInteraction
import it.evadid.workbook.interactions.programming.state.ProgrammingStateRaw
import it.evadid.workbook.sync.UpdateImportance
import it.evadid.workbook.sync.model.InteractionHistoryJson
import munit.FunSuite

import java.time.LocalDateTime

/** Saved feedback refers to its grader and answer instead of copying them. */
class FeedbackReferencesSpec extends FunSuite {
  private val format = TextInputWithLlmGradingAndScaffoldingFormat
  private val time = LocalDateTime.of(2026, 11, 28, 12, 0)
  private val criterion = "a long confidential criterion"
  private val grader = TextInputLlmGrader(LanguageMapContentId("spec/task"), None,
    ContentForLlmInteraction(LanguageMapContentId("spec/criterion"), Map.empty))

  test("a saved grading holds the grader's and the answer's references, not their content") {
    val answer = "a long student answer that should be stored once"
    val history = InteractionVariableHistory.empty(format).addState(InteractionVariableState(answer, UpdateImportance.MAJOR, time))
      .addGrading(GradingResult(grader, answer, GradingStatus.Correct, None, None, time))
    val saved = InteractionHistoryJson.fromHistory(format, history).serialized
    assertEquals(saved.split(java.util.regex.Pattern.quote(answer), -1).length - 1, 1, "the answer is saved once, in its answer event")
    assert(saved.contains(s"\"GraderReference(${grader.key})\""), saved)
    assert(saved.contains(s"\"StateReference(${history.referenceOf(answer).fingerprint})\""), saved)
    assert(!saved.contains("spec/criterion"), "the grader's definition is not copied")
    val restored = InteractionHistoryJson.deserialize(saved).typedAs(format)
    assertEquals(restored.gradingForState(Seq(grader), answer).graderResultMap(grader).gradingStatus, GradingStatus.Correct)
  }

  test("references are equal for equal answers and differ otherwise; programs are referenced by their source") {
    def ref[F <: InteractionFormat](f: F, state: InteractionFormat.State[F]) = InteractionFormat.referenceOf(f, state)
    assertEquals(ref(format, "a"), ref(format, "a"))
    assertNotEquals(ref(format, "a"), ref(format, "b"))
    val program = ProgrammingStateRaw(Java, "class A {}")
    assertEquals(ref(ProgrammingExerciseFormat, program), ref(ProgrammingExerciseFormat, ProgrammingStateRaw(Java, "class A {}")))
    assertNotEquals(ref(ProgrammingExerciseFormat, program), ref(ProgrammingExerciseFormat, ProgrammingStateRaw(Java, "class B {}")))
    assertEquals(ref(ProgrammingExerciseFormat, program).fingerprint,
      it.evadid.core.util.crypto.ContentIdentity.key(ujson.Str(program.fingerprint())))
  }

  test("a grading of a grader that is no longer configured is not resolved") {
    val other = grader.copy(exerciseText = LanguageMapContentId("spec/other"))
    val history = InteractionVariableHistory.empty(format).addGrading(GradingResult(other, "a", GradingStatus.Correct, None, None, time))
    assertEquals(history.gradingForState(Seq(grader), "a").graderResultMap(grader).gradingStatus, GradingStatus.NotRequested)
    assertEquals(history.completedGradings(Seq(grader), "a"), Nil)
  }
}
