package it.evadid.homepage.webElements.facades

import com.raquo.airstream.state.Var
import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.facades.Snap
import munit.FunSuite
import scala.scalajs.js

class SnapLanguageSpec extends FunSuite {
  test("every workbook language maps to its bundled Snap locale") {
    val expected = Map[HumanLanguage, String](English -> "en", German -> "de", French -> "fr",
      Ukrainian -> "ua", Russian -> "ru", Turkish -> "tr", Danish -> "dk", Spanish -> "es")
    assertEquals(expected.keySet, humanLanguages)
    expected.foreach { (language, code) => assertEquals(Snap.languageCode(language), code) }
  }

  test("switching locales refreshes block specs once and retains other loaded dictionaries") {
    val global = js.Dynamic.global.globalThis
    val previousTranslator = global.selectDynamic("SnapTranslator")
    val previousSprite = global.selectDynamic("SpriteMorph")
    val refreshed = Var(List.empty[String])
    val dictionary = js.Dynamic.literal(en = js.Dynamic.literal(), de = js.Dynamic.literal(), ru = js.Dynamic.literal())
    val translator = js.Dynamic.literal(language = "en", dict = dictionary)
    val prototype = js.Dynamic.literal(initBlocks = (() => {
      refreshed.update(_ :+ translator.language.asInstanceOf[String])
    }): js.Function0[Unit])
    global.updateDynamic("SnapTranslator")(translator)
    global.updateDynamic("SpriteMorph")(js.Dynamic.literal(prototype = prototype))
    try {
      Snap.useLanguage(German)
      Snap.useLanguage(German)
      Snap.useLanguage(Russian)
      Snap.useLanguage(English)
      assertEquals(refreshed.now(), List("de", "ru", "en"))
      assert(translator.dict == dictionary)
      assert(!js.isUndefined(dictionary.de))
      intercept[IllegalArgumentException](Snap.useLanguage(French))
      assertEquals(translator.language.asInstanceOf[String], "en")
    } finally {
      global.updateDynamic("SnapTranslator")(previousTranslator)
      global.updateDynamic("SpriteMorph")(previousSprite)
    }
  }
}
