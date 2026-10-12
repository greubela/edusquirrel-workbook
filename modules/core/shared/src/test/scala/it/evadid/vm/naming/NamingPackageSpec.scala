package it.evadid.vm.naming

import it.evadid.core.datastructures.language.AppLanguage.*
import munit.FunSuite
import upickle.default.*

class NamingPackageSpec extends FunSuite {
  test("all naming styles handle empty names") {
    List(NamingStyle.CamelCase, NamingStyle.SnakeCase, NamingStyle.AllcapsSchool).foreach { style =>
      assertEquals(style.applyStyle(Nil), "")
      assertEquals(style.stringToParts(""), Nil)
    }
  }
  test("styles convert words and mixed notation consistently") {
    val words = List("draw", "circle", "size")
    assertEquals(NamingStyle.CamelCase.applyStyle(words), "drawCircleSize")
    assertEquals(NamingStyle.SnakeCase.applyStyle(words), "draw_circle_size")
    assertEquals(NamingStyle.AllcapsSchool.applyStyle(words), "DRAW_CIRCLE_SIZE")
    assertEquals(NamingStyle.fromAnyNotationToParts("  drawCircle__size  "), words)
  }
  test("parts-based names adapt to language and style while literals remain literal") {
    val name = BeEntityName.fromMapInCodeNotation(Map(English -> "draw_circle", German -> "zeichne_kreis"))
    assertEquals(name.getNameIn(German, NamingStyle.CamelCase), "zeichneKreis")
    assertEquals(name.universalInterpretation(), "draw_circle")
    assertEquals(BeEntityName.fromLiteral("Keep THIS").getNameIn(German, NamingStyle.SnakeCase), "Keep THIS")
    assertEquals(BeEntityName.fromCodeString("drawCircle").universalInterpretation(), "draw_circle")
    assertEquals(BeEntityName.fromUniversalNameInParts("draw_circle").asLanguageMap(NamingStyle.CamelCase).getInLanguage(German), "drawCircle")
  }
  test("configuration, styles and both entity name variants have default codecs") {
    for (style <- List[NamingStyle](NamingStyle.CamelCase, NamingStyle.SnakeCase, NamingStyle.AllcapsSchool)) {
      assertEquals(read[NamingStyle](write(style)), style)
      val config = CodeRepresentationConfig(Python, German, style, skipUnparsable = true)
      assertEquals(read[CodeRepresentationConfig](write(config)), config)
    }
    List(BeEntityName.fromLiteral("raw"), BeEntityName.fromCodeString("drawCircle")).foreach { name =>
      assertEquals(read[BeEntityName](write(name)), name)
    }
  }
}
