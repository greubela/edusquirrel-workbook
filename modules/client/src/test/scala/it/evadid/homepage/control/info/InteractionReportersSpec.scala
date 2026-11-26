package it.evadid.homepage.control.info

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
import it.evadid.workbook.elements.interactionElements.gpt.TextInputWithScaffoldingAndGrading
import TextInputWithScaffoldingAndGrading.ContentForLlmInteraction
import it.evadid.workbook.elements.interactionElements.basic.TextInteraction
import it.evadid.workbook.elements.structureElements.{Workbook, WorkbookSection}
import it.evadid.workbook.interaction.variable.InteractionVariableState
import it.evadid.workbook.interaction.sync.UpdateImportance
import it.evadid.workbook.meta.{ExecutionContextInformation, InteractionVariableSyncReport, ScaffoldingReport}
import it.evadid.core.datastructures.chat.MessengerModel
import java.time.LocalDateTime

class InteractionReportersSpec extends EditorDomSuite {
  private val at = LocalDateTime.of(2026, 11, 19, 12, 0)
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
  private def complete(element: TextInputWithScaffoldingAndGrading, status: GradingStatus, offset: Long): Unit =
    element.interactionVariable.updateHistory(_.addGrading(GradingResult(element.gradingElements.head,
      element.interactionVariable.currentValue, status, None, None, at.plusSeconds(offset))), ExecutionContextInformation.default)

  test("registry covers all formats and reuses entries across UI and user changes") {
    val element = exercise("shared")
    val other = TextInteraction("text")
    val loaded = workbook(Seq(element, other))
    val full = info(loaded)
    val registry = full.signals.interactionReporters
    val initial = registry.now()
    assertEquals(initial.keySet, Set[WorkbookInteractionElement[? <: InteractionFormat]](element, other))
    val reporter = full.signals.reportersFor(element)
    assert(reporter eq initial(element))
    full.homepageInfoState.update(_.copy(currentLanguage = German, displayInfo = AllDisplayInfo(true, None)))
    full.homepageInfoState.update(_.copy(workbookInfo = Some(loaded.copy(config = WorkbookConfig(None)))))
    val user = AllUserInfo.createNewUser("Student", "test@example.invalid", UserConfig(Seq.empty, true))
    full.homepageInfoState.update(_.copy(userInfo = Some(user)))
    assert(registry.now() eq initial)
    assert(full.signals.reportersFor(element).grading eq reporter.grading)
    val owner = new ManualOwner
    try {
      val sync = reporter.sync.report.observe(using owner)
      assertEquals(sync.now().status, SyncReporter.Status.Disabled)
      full.homepageInfoState.update(_.copy(userInfo = None))
      assert(reporter.sync eq full.signals.reportersFor(element).sync)
      assertEquals(sync.now().status, SyncReporter.Status.Disabled)
    } finally owner.killSubscriptions()
  }

  test("simultaneous views receive the same snapshot and remounting reads current history") {
    val element = exercise("once")
    val full = info(workbook(Seq(element)))
    val reporter = full.signals.reportersFor(element).grading
    val firstOwner = new ManualOwner
    val secondOwner = new ManualOwner
    val first = Var(Option.empty[GradingReporter.Report])
    val second = Var(Option.empty[GradingReporter.Report])
    reporter.report.foreach(value => first.set(Some(value)))(using firstOwner)
    reporter.report.foreach(value => second.set(Some(value)))(using secondOwner)
    assert(first.now().get eq second.now().get)
    complete(element, GradingStatus.Correct, 1)
    assert(first.now().get eq second.now().get)
    assertEquals(first.now().get.grading.correct, 1)
    assert(first.now().get.grading.status.isCorrect)
    firstOwner.killSubscriptions()
    secondOwner.killSubscriptions()
    complete(element, GradingStatus.Incorrect, 2)
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
    val oldReporter = full.signals.reportersFor(oldElement)
    val owner = new ManualOwner
    try {
      val active = full.signals.interactionReporters.flatMapSwitch(entries =>
        if entries.isEmpty then Val(Option.empty[GradingReporter.Report]) else entries.values.head.grading.report.map(Some(_))).observe(using owner)
      full.homepageInfoState.update(_.copy(workbookInfo = Some(newWorkbook)))
      assert(!(full.signals.reportersFor(newElement) eq oldReporter))
      intercept[IllegalArgumentException](full.signals.reportersFor(oldElement))
      complete(oldElement, GradingStatus.Correct, 1)
      assertEquals(active.now().get.grading.correct, 0)
      complete(newElement, GradingStatus.Incorrect, 2)
      assertEquals(active.now().get.grading.status, GradingStatus.Incorrect)
      full.homepageInfoState.update(_.copy(workbookInfo = Some(oldWorkbook)))
      assertEquals(active.now().get.grading.correct, 1)
      assert(!(full.signals.reportersFor(oldElement) eq oldReporter))
      complete(newElement, GradingStatus.Correct, 3)
      assertEquals(active.now().get.grading.correct, 1)
      full.homepageInfoState.update(_.copy(workbookInfo = None))
      assert(full.signals.interactionReporters.now().isEmpty)
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
      val rendered = full.signals.workbook.combineWith(full.signals.interactionReporters).map { (loaded, _) =>
        loaded.toSeq.flatMap(_.loadedWorkbook.allContainedInteractionsById.values).map(full.signals.reportersFor)
      }.observe(using owner)
      selected.set(Some(target))
      assert(rendered.now().head.interaction eq replacement)
      assert(full.signals.reportersFor(replacement) eq rendered.now().head)
    } finally owner.killSubscriptions()
  }

  test("current-answer hints are shared and late old-answer hints stay hidden") {
    val element = exercise("hints")
    val full = info(workbook(Seq(element)))
    val reporter = full.signals.reportersFor(element).grading
    val owner = new ManualOwner
    try {
      val report = reporter.report.observe(using owner)
      def hint(status: ScaffoldingStatus, offset: Long): Unit = element.interactionVariable.updateHistory(
        _.addScaffolding(ScaffoldingResult[String, MessengerModel]("", status, FeedbackEntity.TestEntity, None, at.plusSeconds(offset))),
        ExecutionContextInformation.default)
      hint(ScaffoldingStatus.ScaffoldingRequestedButNotFinished(), 1)
      assertEquals(report.now().scaffolding.status, ScaffoldingReport.Status.Pending)
      assert(report.now().scaffolding.feedback.isEmpty)
      hint(ScaffoldingStatus.ScaffoldingFeedback(LanguageMap.universalMap[HumanLanguage]("hint")), 2)
      assertEquals(report.now().scaffolding.status, ScaffoldingReport.Status.Ready)
      element.interactionVariable.updateHistory(_.addState(InteractionVariableState("new", UpdateImportance.MAJOR, at.plusSeconds(3))), ExecutionContextInformation.default)
      hint(ScaffoldingStatus.ScaffoldingFeedback(LanguageMap.universalMap[HumanLanguage]("late")), 4)
      assertEquals(report.now().scaffolding.status, ScaffoldingReport.Status.NotRequested)
      assert(report.now().scaffolding.feedback.isEmpty)
    } finally owner.killSubscriptions()
  }

  test("sync classification ignores obsolete confirmations and shares its snapshot") {
    import SyncReporter.Status
    assertEquals(SyncReporter.summarize[String](None).status, Status.Disabled)
    assertEquals(SyncReporter.summarize(Some(InteractionVariableSyncReport(Set.empty[String], Set("obsolete")))).status, Status.Disabled)
    assertEquals(SyncReporter.summarize(Some(InteractionVariableSyncReport(Set("a", "b"), Set.empty[String]))).status, Status.Unsynced)
    assertEquals(SyncReporter.summarize(Some(InteractionVariableSyncReport(Set("a", "b"), Set("a", "obsolete")))).status, Status.Partial)
    assertEquals(SyncReporter.summarize(Some(InteractionVariableSyncReport(Set("a", "b"), Set("a", "b")))).status, Status.Synced)
    val owner = new ManualOwner
    try {
      val source = Var(Option.empty[InteractionVariableSyncReport[it.evadid.workbook.interaction.sync.SyncInformationWithContext]])
      val reporter = new SyncReporter(source.signal)
      val first = reporter.report.observe(using owner)
      val second = reporter.report.observe(using owner)
      assert(first.now() eq second.now())
      assertEquals(first.now().status, Status.Disabled)
    } finally owner.killSubscriptions()
  }
}
