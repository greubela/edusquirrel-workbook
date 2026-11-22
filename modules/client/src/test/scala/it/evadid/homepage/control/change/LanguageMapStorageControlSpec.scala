package it.evadid.homepage.control.change

import it.evadid.core.datastructures.language.{AppLanguage, LanguageMapContentId}
import it.evadid.core.datastructures.language.control.LanguageMapStorage
import it.evadid.core.datastructures.language.serialization.LanguageMapInputSource
import it.evadid.core.datastructures.language.serialization.LanguageMapInputSource.EvaDirectorySource
import it.evadid.core.datastructures.state.StateHelper.*
import it.evadid.homepage.control.FakeStaticSite
import it.evadid.homepage.control.change.LanguageMapStorageControl.STARTUP_STRATEGY
import it.evadid.homepage.control.model.{AllDisplayInfo, FullInfo, HomepageInfo}
import it.evadid.homepage.webElements.editor.code.EditorDomSuite
import scala.concurrent.{ExecutionContext, Future}

/** Language maps load from the site's resource files into the page state, which the text signals then show. */
class LanguageMapStorageControlSpec extends EditorDomSuite {
  private given ExecutionContext = ExecutionContext.global
  private val site = FakeStaticSite()
  override def beforeEach(context: BeforeEach): Unit = site.install()
  override def afterEach(context: AfterEach): Unit = site.uninstall()
  private def page() = FullInfo(HomepageInfo(AppLanguage.English, None, None, AllDisplayInfo(false, None), LanguageMapStorage.empty))
  private def text(info: FullInfo, id: String): String =
    info.signals.stringFromLanguageMapId(LanguageMapContentId(id)).observe(using com.raquo.airstream.ownership.ManualOwner()).now()

  test("a full startup loads the default EVA maps and Snap translations, in every language they provide") {
    val info = page()
    assert(text(info, "emailSimulator/title").contains("Language data loading"))
    info.contentControl.languageStorage.ensureStartup(STARTUP_STRATEGY.CONTINUE_AFTER_FULL_LOAD).map { _ =>
      assertEquals(text(info, "emailSimulator/title"), "Mail Simulator")
      info.displayControl.changeLanguage(AppLanguage.German)
      assertEquals(text(info, "emailSimulator/title"), "Mail-Simulator")
      val requested = site.requested.now()
      assert(requested.contains("/resources/languageMaps/eva/emailSimulator/map-de.json"), requested.take(5).toString)
      assert(requested.exists(_.endsWith("/locale/lang-de.js")), "Snap's own translations are loaded too")
      assert(info.homepageInfoNow().languageMapStore.loadedSources.nonEmpty)
    }
  }

  test("sources already loaded are not fetched again; unreadable sources are skipped") {
    val info = page()
    val storage = info.contentControl.languageStorage
    val factory = info.contentControl.fileFactory
    def directory(name: String) = LanguageMapInputSource.forEvaLanguageMapFiles(Set(
      EvaDirectorySource(name, factory.relativeToTechnicalResources(s"/languageMaps/eva/$name"), Set("en"))))
    val login = directory("login")
    for {
      _ <- storage.ensureLanguageSourceLoaded(login)
      first = site.requested.now()
      _ <- storage.ensureLanguageSourceLoaded(login)
      second = site.requested.now()
      _ <- storage.ensureLanguageSourcesLoaded(List(directory("noSuchWorkbook")))
    } yield {
      assertEquals(first, Vector("/resources/languageMaps/eva/login/map-en.json"))
      assertEquals(second, first, "a loaded source is not requested again")
      assert(site.requested.now().last.contains("noSuchWorkbook"))
      assert(info.homepageInfoNow().languageMapStore.languageMaps.keys.exists(_.fullId.startsWith("login/")))
    }
  }
}
