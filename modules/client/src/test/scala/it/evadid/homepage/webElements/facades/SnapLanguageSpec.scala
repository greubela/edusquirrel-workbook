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
  test("project serialization uses English, preserves the receiver and restores the locale on failure") {
    val global = js.Dynamic.global.globalThis
    val previous = global.selectDynamic("SnapTranslator")
    val translator = js.Dynamic.literal(language = "de")
    global.updateDynamic("SnapTranslator")(translator)
    val failures = Var(false)
    val calls = Var(0)
    val payload = js.Dynamic.literal(value = 3)
    val libraryFlags = Var(List.empty[Option[Boolean]])
    val original: js.ThisFunction2[js.Dynamic, js.Any, js.UndefOr[Boolean], String] =
        (receiver: js.Dynamic, value: js.Any, library: js.UndefOr[Boolean]) => {
      libraryFlags.update(_ :+ library.toOption)
      assertEquals(translator.language.asInstanceOf[String], "en")
      assertEquals(receiver.name.asInstanceOf[String], "serializer")
      assert(value == payload)
      calls.update(_ + 1)
      if failures.now() then throw IllegalStateException("export failed")
      "<project/>"
    }
    val serializer = js.Dynamic.literal(name = "serializer", serialize = original)
    try {
      Snap.protectSerialization(serializer)
      val guarded = serializer.serialize
      Snap.protectSerialization(serializer)
      assert(serializer.serialize == guarded)
      assertEquals(serializer.serialize(payload).asInstanceOf[String], "<project/>")
      assertEquals(translator.language.asInstanceOf[String], "de")
      assertEquals(serializer.serialize(payload, true).asInstanceOf[String], "<project/>")
      assertEquals(libraryFlags.now(), List(None, Some(true)))
      failures.set(true)
      intercept[IllegalStateException](serializer.serialize(payload))
      assertEquals(translator.language.asInstanceOf[String], "de")
      assertEquals(calls.now(), 3)
    } finally global.updateDynamic("SnapTranslator")(previous)
  }

  test("native language refresh keeps the project and does not save standalone preferences") {
    val calls = Var(0)
    val ide = js.Dynamic.literal(loadNewProject = true)
    ide.updateDynamic("reflectLanguage")(((language: String, callback: js.Function0[Unit], noSave: Boolean) => {
      assertEquals(language, "ru")
      assert(!ide.loadNewProject.asInstanceOf[Boolean])
      assert(noSave)
      callback()
    }): js.Function3[String, js.Function0[Unit], Boolean, Unit])
    Snap.changeLanguage(ide.asInstanceOf[Snap.IDEMorph], Russian, () => calls.update(_ + 1))
    assertEquals(calls.now(), 1)
  }

}
