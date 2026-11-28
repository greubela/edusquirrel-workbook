package it.evadid.workbook.abstractions

import it.evadid.core.datastructures.language.WorkbookContent

import it.evadid.core.datastructures.language.AppLanguage.{English, German}
import it.evadid.core.datastructures.language.LanguageMap
import it.evadid.core.util.crypto.ContentIdentity
import it.evadid.workbook.abstractions.grading.{GradingImportance, GradingResult, GradingStatus, GraderRunningBehavior, WorkbookInteractionGrader}
import it.evadid.workbook.abstractions.scaffolding.ScaffoldingResult
import it.evadid.workbook.abstractions.scaffolding.ScaffoldingStatus.{ScaffoldingError, ScaffoldingFeedback}
import munit.FunSuite

import java.time.Instant
import scala.concurrent.Future

class ResultMergeSpec extends FunSuite {
  private object Grader extends WorkbookInteractionGrader[String, String] {
    override val persistedType = "MergeTestGrader"
    override val key = "merge-grader"
    override val importance = GradingImportance.REQUIRED_TEST
    override val runningBehavior = GraderRunningBehavior.RUN_ALWAYS
    override val gradingEntity = FeedbackEntity.TestEntity
    override protected def handleGrading(state: String, context: it.evadid.workbook.abstractions.ExecutionContextInformation) =
      Future.successful(GradingResult(this, state, GradingStatus.Correct))
  }
  private val t0 = java.time.LocalDateTime.of(2026, 1, 1, 10, 0).toInstant(java.time.ZoneOffset.UTC)
  private def grade(status: GradingStatus, time: Instant = t0, feedback: Option[WorkbookContent] = None) =
    GradingResult(Grader, "a", status, feedback, None, time)
  private def text(pairs: (it.evadid.core.datastructures.language.AppLanguage.HumanLanguage, String)*) = Some(WorkbookContent(LanguageMap.mapBasedLanguageMap(pairs.toMap)))

  test("grading merges prefer finished results, then newer ones, then the more informative status") {
    assertEquals(grade(GradingStatus.NotRequested).merge(grade(GradingStatus.Correct)).gradingStatus, GradingStatus.Correct)
    assertEquals(grade(GradingStatus.Incorrect).merge(grade(GradingStatus.NotFinished, t0.plus(java.time.Duration.ofDays(1)))).gradingStatus, GradingStatus.Incorrect)
    assertEquals(grade(GradingStatus.Incorrect).merge(grade(GradingStatus.Correct, t0.plusSeconds(1))).gradingStatus, GradingStatus.Correct)
    assertEquals(grade(GradingStatus.Correct).merge(grade(GradingStatus.Incorrect)).gradingStatus, GradingStatus.Incorrect)
    assertEquals(grade(GradingStatus.PartiallyCorrect).merge(grade(GradingStatus.Correct)).gradingStatus, GradingStatus.PartiallyCorrect)
    intercept[IllegalArgumentException](grade(GradingStatus.Correct).merge(grade(GradingStatus.Correct).copy(gradedState = "b")))
  }

  test("grading merges keep translations the preferred result lacks") {
    val merged = grade(GradingStatus.Correct, feedback = text(German -> "gut", English -> "good"))
      .merge(grade(GradingStatus.Correct, t0.plusSeconds(1), text(English -> "fine")))
    assertEquals((merged.feedbackInformation.collect { case WorkbookContent.LanguageMapBasedContent(map) => map }.get.tryGetInLanguage(English), merged.feedbackInformation.collect { case WorkbookContent.LanguageMapBasedContent(map) => map }.get.tryGetInLanguage(German)), (Some("fine"), Some("gut")))
    val complete = grade(GradingStatus.Correct, feedback = text(English -> "old"))
      .merge(grade(GradingStatus.Correct, t0.plusSeconds(1), text(English -> "new", German -> "neu")))
    assertEquals(complete.feedbackInformation, text(English -> "new", German -> "neu"))
    assertEquals(grade(GradingStatus.Correct, feedback = text(English -> "kept")).merge(grade(GradingStatus.Correct, t0.plusSeconds(1))).feedbackInformation,
      text(English -> "kept"))
  }

  test("feedback-free results merge safely and newer content IDs remain content IDs") {
    val empty = grade(GradingStatus.Correct)
    assertEquals(empty.merge(empty).feedbackInformation, None)
    val content = WorkbookContent(it.evadid.core.datastructures.language.LanguageMapContentId("test/feedback"))
    val latest = empty.copy(feedbackInformation = Some(content), gradingTime = t0.plusSeconds(1))
    assertEquals(empty.merge(latest).feedbackInformation, Some(content))
    assertEquals(latest.merge(empty).feedbackInformation, Some(content))
  }

  test("scaffolding merges prefer feedback over errors and newer results otherwise") {
    def hint(text: String, time: Instant) = ScaffoldingResult[String, String]("a", ScaffoldingFeedback(LanguageMap.universalMap(text)), FeedbackEntity.LlmEntity, None, time)
    val error = ScaffoldingResult.forError[String, String]("a", Left("offline"), FeedbackEntity.LlmEntity).copy(scaffoldingTime = t0.plus(java.time.Duration.ofDays(1)))
    assert(error.scaffoldingStatus.isInstanceOf[ScaffoldingError])
    assertEquals(hint("first", t0).merge(error).scaffoldingStatus, ScaffoldingFeedback(LanguageMap.universalMap("first")))
    assertEquals(error.merge(hint("first", t0)).scaffoldingStatus, ScaffoldingFeedback(LanguageMap.universalMap("first")))
    assertEquals(hint("first", t0).merge(hint("second", t0.plusSeconds(1))).scaffoldingStatus, ScaffoldingFeedback(LanguageMap.universalMap("second")))
    val older = ScaffoldingResult.forError[String, String]("a", Right(IllegalStateException("x")), FeedbackEntity.LlmEntity).copy(scaffoldingTime = t0)
    assert(older.merge(error).scaffoldingStatus.isInstanceOf[ScaffoldingError])
    assertEquals(error.merge(older).scaffoldingTime, error.scaffoldingTime)
    intercept[IllegalArgumentException](error.merge(error.copy(forState = "b")))
  }

  test("content identities are canonical for maps, sets and numbers and reject unknown values") {
    assertEquals(ContentIdentity.json(Map("b" -> 1, "a" -> 2)), ContentIdentity.json(Map("a" -> 2, "b" -> 1)))
    assertEquals(ContentIdentity.json(Set(3, 1)), ContentIdentity.json(Set(1, 3)))
    assertNotEquals(ContentIdentity.json(Seq(1, 3)), ContentIdentity.json(Seq(3, 1)))
    assertEquals(ContentIdentity.json(null), ujson.Null)
    assertEquals(ContentIdentity.json('x'), ujson.Obj("char" -> "x"))
    assertEquals(ContentIdentity.json(BigDecimal("1.50")), ujson.Obj("decimal" -> "1.5"))
    assertEquals(ContentIdentity.json(BigInt(7)), ujson.Obj("integer" -> "7"))
    assertEquals(ContentIdentity.json(7L), ujson.Obj("long" -> "7"))
    assertEquals(ContentIdentity.json(Double.NaN), ujson.Obj("number" -> "NaN"))
    assertEquals(ContentIdentity.json(-0.0), ujson.Num(0))
    assertEquals(ContentIdentity.json(true), ujson.Bool(true))
    assertEquals(ContentIdentity.key(ujson.Obj("b" -> 1, "a" -> 2)), ContentIdentity.key(ujson.Obj("a" -> 2, "b" -> 1)))
    assertEquals(ContentIdentity.json(ujson.Arr(ujson.Obj("z" -> 1, "y" -> 2))), ujson.Arr(ujson.Obj("y" -> 2, "z" -> 1)))
    intercept[IllegalArgumentException](ContentIdentity.json(new Object))
  }
}
