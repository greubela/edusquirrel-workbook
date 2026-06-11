package it.evadid.homepage.control

import it.evadid.core.datastructures.language.AppLanguage.{English, German, Spanish}
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.homepage.control.WorkbookContentStorage.{LanguageMapTripleStore, MapEntryTripel, UniversalMapEntry}
import munit.FunSuite

class WorkbookContentStorageSpec extends FunSuite {

  test("language map triples use universal entries as fallback for missing explicit languages") {
    val contentId = LanguageMapContentId("testmap", "image1url")
    val store = LanguageMapTripleStore(Set(
      UniversalMapEntry(contentId, "universal.png"),
      MapEntryTripel(contentId, English, "english.png")
    ))

    val languageMap = store.getMap(contentId).get

    assertEquals(languageMap.getInLanguage(English), "english.png")
    assertEquals(languageMap.getInLanguage(German), "universal.png")
    assertEquals(languageMap.getInLanguage(Spanish), "universal.png")
  }

  test("language map triples can be backed by only a universal entry") {
    val contentId = LanguageMapContentId("testmap", "sharedasset")
    val store = LanguageMapTripleStore(Set(
      UniversalMapEntry(contentId, "shared.png")
    ))

    val languageMap = store.getMap(contentId).get

    assertEquals(languageMap.getInLanguage(English), "shared.png")
    assertEquals(languageMap.getInLanguage(German), "shared.png")
  }
}
