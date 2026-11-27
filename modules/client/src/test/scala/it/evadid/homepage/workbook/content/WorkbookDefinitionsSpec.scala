package it.evadid.homepage.workbook.content

import it.evadid.homepage.workbook.content.test.CreateTestWorkbook
import it.evadid.workbook.jsonFactory.WorkbookElementFactory
import it.evadid.workbook.elements.structureElements.Workbook
import upickle.default.*
import munit.FunSuite
import it.evadid.core.datastructures.language.AppLanguage
import it.evadid.core.datastructures.language.control.LanguageMapStorage
import it.evadid.core.datastructures.user.{AllUserInfo, UserConfig}
import it.evadid.workbook.session.{AllWorkbookInfo, SessionData}
import it.evadid.homepage.control.model.{AllDisplayInfo, HomepageInfo}
import it.evadid.util.logging.BasicLogger
import it.evadid.workbook.meta.ExecutionContextInformation
import it.evadid.workbook.interaction.sync.UpdateImportance

class WorkbookDefinitionsSpec extends FunSuite {
  private def submit[F <: it.evadid.workbook.abstractions.InteractionFormat](interaction: it.evadid.workbook.abstractions.WorkbookInteractionElement[F]): Unit =
    interaction.interactionVariable.setStateFromUserInteraction(interaction.defaultValue, UpdateImportance.MAJOR, context = ExecutionContextInformation.default)
  private val factories = DigitalWorkbookCatalog.entries.flatMap(entry => entry.native.map(native => entry.id -> native.factory)) :+
    ("design" -> CreateTestWorkbook.apply)

  test("catalogue identifiers and native entry points are unique") {
    val entries = DigitalWorkbookCatalog.entries
    assertEquals(entries.map(_.id).distinct.size, entries.size)
    val native = entries.flatMap(_.native)
    assertEquals(native.map(_.containerId).distinct.size, native.size)
    assertEquals(native.map(_.page).distinct.size, native.size)
    native.foreach(entry => assertEquals(DigitalWorkbookCatalog.nativeWorkbook(entry.containerId), Some(entry)))
    assertEquals(DigitalWorkbookCatalog.nativeWorkbook("unknown"), None)
  }

  factories.foreach { (name, factory) =>
    test(s"$name workbook definition has unique IDs and round-trips uPickle JSON and factory formats") {
      val workbook = factory(null).createWorkbook
      val ids = workbook.allChildrenFullSubtree.map(_.elementId)
      assert(ids.nonEmpty, workbook.workbookId)
      assertEquals(ids.distinct.size, ids.size, workbook.workbookId)
      assertEquals(read[Workbook](write[Workbook](workbook)), workbook, workbook.workbookId)
      for serializer <- List(WorkbookElementFactory.serializerRefBasedJson, WorkbookElementFactory.serializerConstructorLike) do
        assertEquals(serializer.deserialize(serializer.serialize(workbook)), workbook, workbook.workbookId)
    }
  }

  factories.foreach { (name, factory) =>
    test(s"$name workbook definition round-trips MessagePack and re-serializes to identical JSON") {
      val workbook = factory(null).createWorkbook
      assertEquals(readBinary[Workbook](writeBinary[Workbook](workbook)), workbook, workbook.workbookId)
      val json = write[Workbook](workbook)
      assertEquals(write[Workbook](read[Workbook](json)), json, "a stored definition is stable when saved again")
    }

    test(s"$name workbook info keeps its active section, which belongs to the restored workbook") {
      val info = factory(null).createEverything
      val restored = read[AllWorkbookInfo](write(info))
      assertEquals(restored, info)
      restored.config.activeSection.foreach(section => assert(restored.loadedWorkbook.sections.contains(section), section.elementId))
    }

    test(s"$name session file restores every interaction's answer into a fresh instance") {
      val studied = factory(null).createEverything
      val interactions = studied.loadedWorkbook.allContainedInteractionsById
      // Each interaction's own default answer, submitted, exercises its real state and history codecs.
      interactions.values.foreach(interaction => submit(interaction))
      val user = AllUserInfo.createNewUser("Student", "catalogue@example.invalid", UserConfig(Nil, false))
      val session = HomepageInfo(AppLanguage.English, Some(studied), Some(user),
        AllDisplayInfo(false, None), LanguageMapStorage.empty).toSessionData.get
      assertEquals(session.knownUsageData.size, interactions.size, "one history per interaction")
      val restored = read[SessionData](write(session))
      assertEquals(restored, session)
      val fresh = factory(null).createWorkbook
      fresh.restoreHistories(BasicLogger(), restored.knownUsageData, ExecutionContextInformation.default)
      fresh.allContainedInteractionsById.foreach { (id, interaction) =>
        def submitted(history: it.evadid.workbook.interaction.variable.InteractionVariableHistory[?]): Set[(Any, Any)] =
          history.allStates.map(event => (event.newState: Any) -> event.updateImportance)
        assertEquals(submitted(interaction.interactionVariable.currentHistory), submitted(interactions(id).interactionVariable.currentHistory), id)
        assert(interaction.interactionVariable.currentHistory.allStates.exists(_.updateImportance == UpdateImportance.MAJOR), id)
      }
    }
  }
}
