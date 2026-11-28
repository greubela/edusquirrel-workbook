package it.evadid.server

import it.evadid.core.datastructures.language.AppLanguage.English
import it.evadid.core.datastructures.language.{LanguageMapContentId, WorkbookContent}
import it.evadid.core.datastructures.user.{User, UserConfig}
import it.evadid.sql.{DatabaseConfigWithCredentials, DatabaseHistoryFormat}
import it.evadid.workbook.abstractions.InteractionFormat.{ChoiceInteractionFormat, TextInteractionFormat}
import it.evadid.workbook.abstractions.grading.{GraderRunningBehavior, GradingResult, GradingStatus}
import it.evadid.workbook.interactions.basic.TextInteraction
import it.evadid.workbook.interactions.choice.WorkbookContentChoiceInteraction
import it.evadid.workbook.interactions.choice.WorkbookContentChoiceInteraction.*
import it.evadid.workbook.elements.structure.Workbook.WorkbookMetadata
import it.evadid.workbook.elements.structure.WorkbookSection.WorkbookSectionMetadata
import it.evadid.workbook.elements.structure.{Workbook, WorkbookSection}
import it.evadid.workbook.sync.destination.SyncDestinationDefinition.SyncDestinationBasedOnHistoryDatabase
import it.evadid.workbook.sync.model.InteractionHistoryJson
import it.evadid.workbook.sync.{EventSyncStrategy, SyncContext, SyncInformation, UpdateImportance, UsageContext}
import it.evadid.workbook.history.{InteractionVariableHistory, InteractionVariableState}
import it.evadid.workbook.session.{AllWorkbookInfo, SessionData, WorkbookConfig}

import java.time.Instant

/** A submitted session with a revised text answer, a graded choice, an unanswered task, a foreign-workbook history and
  * destination credentials that must never leave the backend. */
object SubmissionFixtures {
  private def id(key: String) = LanguageMapContentId(s"submission/$key")
  private val savedAt = java.time.LocalDateTime.of(2026, 11, 27, 10, 15, 30).toInstant(java.time.ZoneOffset.UTC)

  val essay: TextInteraction = TextInteraction("essay")
  val quiz: WorkbookContentChoiceInteraction = WorkbookContentChoiceInteraction("quiz",
    ChoiceConfig(id("prompt"), Some(false), GraderRunningBehavior.RUN_ALWAYS),
    Seq(ChoiceOptionWorkbookContent(WorkbookContent(id("right")), Some(GradingForChoice(GradingStatus.Correct, id("yes")))),
      ChoiceOptionWorkbookContent(WorkbookContent(id("wrong")), Some(GradingForChoice(GradingStatus.Incorrect, id("no"))))))
  val untouched: TextInteraction = TextInteraction("untouched")

  val workbook: Workbook = Workbook("courses/submission-test",
    WorkbookMetadata(Set(User("Author", "author", "author@example.invalid")), Set.empty, id("title"), List(English)),
    Seq(WorkbookSection("main", WorkbookSectionMetadata(id("section")), Seq(essay, quiz, untouched))))

  val student: User = User("Änne Student", "student-1", "student@example.org")

  val quizAnswer: ChoiceState = quiz.select(quiz.defaultValue, 0, selected = true, savedAt.minus(java.time.Duration.ofMinutes(5)))

  private def context(scenario: String, key: String): SyncContext = UsageContext("edusquirrel", scenario, student.id).toSyncContext(key)

  private val essayHistory = InteractionHistoryJson.fromHistory(TextInteractionFormat, InteractionVariableHistory.empty(TextInteractionFormat)
    .addState(InteractionVariableState("first draft", UpdateImportance.MAJOR, savedAt.minus(java.time.Duration.ofMinutes(20))))
    .addState(InteractionVariableState("final answer", UpdateImportance.MAJOR, savedAt.minus(java.time.Duration.ofMinutes(10)))))

  private val quizHistory = InteractionHistoryJson.fromHistory(ChoiceInteractionFormat, InteractionVariableHistory.empty(ChoiceInteractionFormat)
    .addState(InteractionVariableState(quizAnswer, UpdateImportance.MAJOR, savedAt.minus(java.time.Duration.ofMinutes(5))))
    .addGrading(GradingResult(quiz.gradingElements.head, quizAnswer, GradingStatus.Correct, None, None, savedAt.minus(java.time.Duration.ofMinutes(4)))))

  /** Destination credentials are stripped before logging and mailing. */
  val credentials: DatabaseConfigWithCredentials =
    DatabaseConfigWithCredentials("mysql", "db.example.invalid", "3306", "school", "private-user", "private-password")

  def session(mail: String = student.mail): SessionData = SessionData(savedAt, Map(
    context(workbook.workbookId, essay.keyForSerialization) -> essayHistory,
    context(workbook.workbookId, quiz.keyForSerialization) -> quizHistory,
    context("another-workbook", essay.keyForSerialization) -> quizHistory),
    student.copy(mail = mail),
    Some(UserConfig(Seq(SyncInformation(SyncDestinationBasedOnHistoryDatabase(credentials, DatabaseHistoryFormat.BlobFormat, buffered = false),
      EventSyncStrategy.SYNC_MAJOR)), isOnlineAccount = true)),
    AllWorkbookInfo(workbook, WorkbookConfig(None), Map.empty))

  /** The same session with an unreadable stored history for the essay. */
  def sessionWithUnreadableEssay: SessionData = {
    val stored = session()
    stored.copy(knownUsageData = stored.knownUsageData.updated(
      context(workbook.workbookId, essay.keyForSerialization), InteractionHistoryJson(TextInteractionFormat, "{\"not\": \"a history\"}")))
  }
}
