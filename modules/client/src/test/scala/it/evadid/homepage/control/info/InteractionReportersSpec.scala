package it.evadid.homepage.control.info

import it.evadid.workbook.session.{AllWorkbookInfo, WorkbookConfig}
import com.raquo.laminar.api.L.*
import com.raquo.airstream.ownership.ManualOwner
import it.evadid.core.datastructures.language.AppLanguage.{English, German, HumanLanguage}
import it.evadid.core.datastructures.language.{LanguageMap, LanguageMapContentId}
import it.evadid.core.datastructures.language.control.LanguageMapStorage
import it.evadid.core.datastructures.user.{AllUserInfo, UserConfig}
import it.evadid.homepage.control.info.interaction.{GradingReporter, SyncReporter}
import it.evadid.homepage.control.model.*
import it.evadid.homepage.webElements.editor.code.EditorDomSuite
import it.evadid.workbook.abstractions.{InteractionFormat, WorkbookInteractionElement, FeedbackEntity}
import it.evadid.workbook.abstractions.grading.{GradingResult, GradingStatus}
import it.evadid.workbook.abstractions.scaffolding.{ScaffoldingResult, ScaffoldingStatus}
import it.evadid.workbook.interactions.gpt.TextInputWithScaffoldingAndGrading
import TextInputWithScaffoldingAndGrading.ContentForLlmInteraction
import it.evadid.workbook.interactions.basic.TextInteraction
import it.evadid.workbook.elements.structure.{Workbook, WorkbookSection}
import it.evadid.workbook.history.InteractionVariableState
import it.evadid.workbook.sync.UpdateImportance
import it.evadid.workbook.abstractions.ExecutionContextInformation
import it.evadid.workbook.reports.{InteractionVariableSyncReport, ScaffoldingReport}
import it.evadid.core.datastructures.chat.MessengerModel
import java.time.Instant

class InteractionReportersSpec extends EditorDomSuite {
  private val at = java.time.LocalDateTime.of(2026, 11, 19, 12, 0).toInstant(java.time.ZoneOffset.UTC)
  private def exercise(id: String) = TextInputWithScaffoldingAndGrading(id, LanguageMapContentId("test/task"),
    contentGrading = Seq(ContentForLlmInteraction(LanguageMapContentId("test/check"), Map.empty)),
    contentScaffolding = Some(ContentForLlmInteraction(LanguageMapContentId("test/hint"), Map.empty)))
  private def workbook(elements: Seq[WorkbookInteractionElement[? <: InteractionFormat]]): AllWorkbookInfo = {
    val section = WorkbookSection("section", WorkbookSection.WorkbookSectionMetadata(LanguageMapContentId("test/section")), elements)
    AllWorkbookInfo(Workbook("workbook", Workbook.WorkbookMetadata(Set.empty, Set.empty,
      LanguageMapContentId("test/title"), Seq(English, German)), Seq(section)), WorkbookConfig(Some(section)), Map.empty)
  }
  private def info(workbook: AllWorkbookInfo) = FullInfo(HomepageInfo(English, Some(workbook), None,
    AllDisplayInfo(false, None), LanguageMapStorage.empty))
  /** Records a grading of `element`'s answer; the page ignores it unless the element's workbook is open. */
  private def complete(page: FullInfo, element: TextInputWithScaffoldingAndGrading, status: GradingStatus, offset: Long): Unit =
    page.interactions.updateHistory(element)(_.addGrading(GradingResult(element.gradingElements.head,
      page.interactions.currentValue(element), status, None, None, at.plusSeconds(offset))))

  test("registry covers all formats and reuses entries across UI and user changes") {
    val element = exercise("shared")
    val other = TextInteraction("text")
    val loaded = workbook(Seq(element, other))
    val full = info(loaded)
    val registry = full.reporters.all
    val initial = registry.now()
    assertEquals(initial.keySet, Set[WorkbookInteractionElement[? <: InteractionFormat]](element, other))
    val reporter = full.reporters.reportersFor(element)
    assert(reporter eq initial(element))
    full.homepage.setLanguage(German)
    full.homepage.updateDisplay(_ => AllDisplayInfo(true, None))
    full.state.switchWorkbook(Some(loaded.copy(config = WorkbookConfig(None))))
    val user = AllUserInfo.createNewUser("Student", "test@example.invalid", UserConfig(Seq.empty, true))
    full.state.switchUser(Some(user))
    assert(registry.now() eq initial)
    assert(full.reporters.reportersFor(element).grading eq reporter.grading)
    val owner = new ManualOwner
    try {
      val sync = reporter.sync.report.observe(using owner)
      assertEquals(sync.now().status, SyncReporter.Status.Disabled)
      full.state.switchUser(None)
      assert(reporter.sync eq full.reporters.reportersFor(element).sync)
      assertEquals(sync.now().status, SyncReporter.Status.Disabled)
    } finally owner.killSubscriptions()
  }

  test("simultaneous views receive the same snapshot and remounting reads current history") {
    val element = exercise("once")
    val full = info(workbook(Seq(element)))
    val reporter = full.reporters.reportersFor(element).grading
    val firstOwner = new ManualOwner
    val secondOwner = new ManualOwner
    val first = Var(Option.empty[GradingReporter.Report])
    val second = Var(Option.empty[GradingReporter.Report])
    reporter.report.foreach(value => first.set(Some(value)))(using firstOwner)
    reporter.report.foreach(value => second.set(Some(value)))(using secondOwner)
    assert(first.now().get eq second.now().get)
    complete(full, element, GradingStatus.Correct, 1)
    assert(first.now().get eq second.now().get)
    assertEquals(first.now().get.grading.correct, 1)
    assert(first.now().get.grading.status.isCorrect)
    firstOwner.killSubscriptions()
    secondOwner.killSubscriptions()
    complete(full, element, GradingStatus.Incorrect, 2)
    assert(first.now().get.grading.status.isCorrect)
    val remounted = new ManualOwner
    try {
      val latest = reporter.report.observe(using remounted)
      assertEquals(latest.now().grading.correct, 0)
      assertEquals(latest.now().grading.status, GradingStatus.Incorrect)
    } finally remounted.killSubscriptions()
  }

  test("workbook replacement isolates old results with identical IDs and returning is fresh") {
    val oldElement = exercise("same-id")
    val newElement = exercise("same-id")
    val oldWorkbook = workbook(Seq(oldElement))
    val newWorkbook = workbook(Seq(newElement))
    val full = info(oldWorkbook)
    val oldReporter = full.reporters.reportersFor(oldElement)
    val owner = new ManualOwner
    try {
      val active = full.reporters.all.flatMapSwitch(entries =>
        if entries.isEmpty then Val(Option.empty[GradingReporter.Report]) else entries.values.head.grading.report.map(Some(_))).observe(using owner)
      full.state.switchWorkbook(Some(newWorkbook))
      assert(!(full.reporters.reportersFor(newElement) eq oldReporter))
      intercept[IllegalArgumentException](full.reporters.reportersFor(oldElement))
      complete(full, oldElement, GradingStatus.Correct, 1)
      assertEquals(active.now().get.grading.correct, 0, "a closed workbook's interaction has no answers on the page")
      complete(full, newElement, GradingStatus.Incorrect, 2)
      assertEquals(active.now().get.grading.status, GradingStatus.Incorrect)
      full.state.switchWorkbook(Some(oldWorkbook))
      assertEquals(active.now().get.grading.correct, 0, "reopening starts from the stored answers")
      assert(!(full.reporters.reportersFor(oldElement) eq oldReporter))
      complete(full, newElement, GradingStatus.Correct, 3)
      assertEquals(active.now().get.grading.correct, 0)
      full.state.switchWorkbook(None)
      assert(full.reporters.all.now().isEmpty)
      assert(active.now().isEmpty)
    } finally owner.killSubscriptions()
  }

  test("selection inside an Airstream observer updates the registry before dependent views render") {
    val original = exercise("same-selection-id")
    val replacement = exercise("same-selection-id")
    val full = info(workbook(Seq(original)))
    val target = workbook(Seq(replacement))
    val owner = new ManualOwner
    try {
      val selected = Var(Option.empty[AllWorkbookInfo])
      selected.signal.changes.foreach(full.workbookNavigationControl.changeWorkbook)(using owner)
      val rendered = full.signals.workbook.combineWith(full.reporters.all).map { (loaded, _) =>
        loaded.toSeq.flatMap(_.loadedWorkbook.allContainedInteractionsById.values).map(full.reporters.reportersFor)
      }.observe(using owner)
      selected.set(Some(target))
      assert(rendered.now().head.interaction eq replacement)
      assert(full.reporters.reportersFor(replacement) eq rendered.now().head)
    } finally owner.killSubscriptions()
  }

  test("current-answer hints are shared and late old-answer hints stay hidden") {
    val element = exercise("hints")
    val full = info(workbook(Seq(element)))
    val reporter = full.reporters.reportersFor(element).grading
    val owner = new ManualOwner
    try {
      val report = reporter.report.observe(using owner)
      def hint(status: ScaffoldingStatus, offset: Long): Unit = full.interactions.updateHistory(element)(_.addScaffolding(ScaffoldingResult[String, MessengerModel]("", status, FeedbackEntity.TestEntity, None, at.plusSeconds(offset))))
      hint(ScaffoldingStatus.ScaffoldingRequestedButNotFinished(), 1)
      assertEquals(report.now().scaffolding.status, ScaffoldingReport.Status.Pending)
      assert(report.now().scaffolding.feedback.isEmpty)
      hint(ScaffoldingStatus.ScaffoldingFeedback(LanguageMap.universalMap[HumanLanguage]("hint")), 2)
      assertEquals(report.now().scaffolding.status, ScaffoldingReport.Status.Ready)
      full.interactions.updateHistory(element)(_.addState(InteractionVariableState("new", UpdateImportance.MAJOR, at.plusSeconds(3))))
      hint(ScaffoldingStatus.ScaffoldingFeedback(LanguageMap.universalMap[HumanLanguage]("late")), 4)
      assertEquals(report.now().scaffolding.status, ScaffoldingReport.Status.NotRequested)
      assert(report.now().scaffolding.feedback.isEmpty)
    } finally owner.killSubscriptions()
  }

  test("sync classification ignores obsolete confirmations and shares its snapshot") {
    import SyncReporter.Status
    assertEquals(SyncReporter.summarize(InteractionVariableSyncReport(Set.empty[String], Set("obsolete"))).status, Status.Disabled)
    assertEquals(SyncReporter.summarize(InteractionVariableSyncReport(Set("a", "b"), Set.empty[String])).status, Status.Unsynced)
    assertEquals(SyncReporter.summarize(InteractionVariableSyncReport(Set("a", "b"), Set("a", "obsolete"))).status, Status.Partial)
    assertEquals(SyncReporter.summarize(InteractionVariableSyncReport(Set("a", "b"), Set("a", "b"))).status, Status.Synced)
    val owner = new ManualOwner
    try {
      val source = Var(InteractionVariableSyncReport(Set.empty[it.evadid.workbook.sync.SyncInformationWithContext], Set.empty))
      val reporter = new SyncReporter(source.signal)
      val first = reporter.report.observe(using owner)
      val second = reporter.report.observe(using owner)
      assert(first.now() eq second.now())
      assertEquals(first.now().status, Status.Disabled)
    } finally owner.killSubscriptions()
  }
}
