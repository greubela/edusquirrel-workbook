package it.evadid.workbook.elements.interactionElements.programming

import munit.FunSuite

class SnapCustomBlockRulesSpec extends FunSuite {

  private def project(blocks: String, scripts: String, palette: String = ""): String =
    s"""<project name="t" app="TurtleStitch 2.11, http://www.turtlestitch.org" version="2"><notes></notes><scenes select="1"><scene name="t"><notes></notes>$palette<hidden></hidden><headers></headers><code></code><blocks>$blocks</blocks><primitives></primitives><stage name="Stage" width="480" height="360"><blocks></blocks><scripts></scripts><sprites select="1"><sprite name="Sprite" idx="1"><blocks></blocks><variables></variables><scripts>$scripts</scripts></sprite></sprites></stage><variables></variables></scene></scenes></project>"""

  private def definition(spec: String, inputs: String, body: String = "", category: String = "Variables"): String =
    s"""<block-definition s="$spec" type="command" category="$category"><header></header><code></code><translations></translations><inputs>$inputs</inputs><script>$body</script></block-definition>"""

  private def script(blocks: String): String =
    s"""<script x="156" y="66">$blocks</script>"""

  test("parseSpec splits on unquoted spaces and keeps quoted label text") {
    assertEquals(
      SnapCustomBlockRules.parseSpec("draw square %size"),
      List("draw", "square", "%size")
    )
    assertEquals(
      SnapCustomBlockRules.parseSpec("say 'hello world' %n"),
      List("say", "hello world", "%n")
    )
  }

  test("slotNames reads input names from the definition spec") {
    assertEquals(SnapCustomBlockRules.slotNames("draw square %size %times"), List("size", "times"))
    assertEquals(SnapCustomBlockRules.slotNames("clear all"), Nil)
  }

  test("rewriteSpecSlots keeps label words and replaces slot names") {
    assertEquals(
      SnapCustomBlockRules.rewriteSpecSlots("draw square %size", List("laenge")),
      "draw square %laenge"
    )
    assertEquals(
      SnapCustomBlockRules.rewriteSpecSlots("circ %'dist'", List("radius")),
      "circ %radius"
    )
    assertEquals(
      SnapCustomBlockRules.rewriteSpecSlots("say 'hello world' %n", List("msg")),
      "say 'hello world' %msg"
    )
  }

  test("blockSpec keeps label words and substitutes declared slot types") {
    val xml = project(definition("draw square %size", """<input type="%n"></input>"""), "")
    val defn = SnapCustomBlockRules.globalDefinitions(xml).head
    assertEquals(defn.blockSpec, "draw square %n")
    assertEquals(defn.labelWords, List("draw", "square"))
    assertEquals(defn.arity, 1)
  }

  test("blockSpec falls back to %s for undeclared slots, matching Snap's Block Editor default") {
    val xml = project(definition("square %size", ""), "")
    val defn = SnapCustomBlockRules.globalDefinitions(xml).head
    assertEquals(defn.typeOf("size"), "%s")
    assertEquals(defn.blockSpec, "square %s")
  }

  test("a call using blockSpec() resolves, whatever the declared slot types are") {
    val cases = List("%n", "%s", "%b", "%txt", "%anyUE")
    cases.foreach { slotType =>
      val defs = definition("draw square %size", s"""<input type="$slotType"></input>""")
      val xml = project(defs, script(s"""<custom-block s="draw square $slotType"><l>10</l></custom-block>"""))
      assertEquals(SnapCustomBlockRules.obsoleteCalls(xml), Nil, clue = slotType)
    }
  }

  test("dropping label words from the call spec is exactly what makes Snap show Undefined!") {
    val xml = project(
      definition("draw square %size", """<input type="%n"></input>"""),
      script("""<custom-block s="draw %n"><l>10</l></custom-block>""")
    )
    val obsolete = SnapCustomBlockRules.obsoleteCalls(xml)
    assertEquals(obsolete.map(_.spec), List("draw %n"))
    assert(obsolete.head.reason.contains("draw square %n"), clue = obsolete.head.reason)
  }

  test("assuming %n for a slot Snap declared as %s also yields Undefined!") {
    val xml = project(
      definition("square %size", """<input type="%s"></input>"""),
      script("""<custom-block s="square %n"><l>10</l></custom-block>""")
    )
    assertEquals(SnapCustomBlockRules.obsoleteCalls(xml).map(_.spec), List("square %n"))
  }

  test("two blocks sharing a first label word stay distinguishable by blockSpec") {
    val defs =
      definition("draw square %size", """<input type="%n"></input>""") +
        definition("draw circle %radius", """<input type="%n"></input>""")
    val xml = project(
      defs,
      script(
        """<custom-block s="draw square %n"><l>10</l></custom-block><custom-block s="draw circle %n"><l>20</l></custom-block>"""
      )
    )
    assertEquals(SnapCustomBlockRules.obsoleteCalls(xml), Nil)
    assertEquals(SnapCustomBlockRules.globalDefinitions(xml).map(_.blockSpec), List("draw square %n", "draw circle %n"))
  }

  test("zero-arity definitions and calls resolve") {
    val xml = project(
      definition("clear all", ""),
      script("""<custom-block s="clear all"></custom-block>""")
    )
    assertEquals(SnapCustomBlockRules.obsoleteCalls(xml), Nil)
  }

  test("calls nested in a repeat and recursive calls in the definition body resolve") {
    val recursive =
      definition(
        "square %size",
        """<input type="%n"></input>""",
        """<block s="forward"><block var="size"/></block><custom-block s="square %n"><block var="size"/></custom-block>"""
      )
    val xml = project(
      recursive,
      script("""<block s="doRepeat"><l>4</l><script><custom-block s="square %n"><l>50</l></custom-block></script></block>""")
    )
    assertEquals(SnapCustomBlockRules.obsoleteCalls(xml), Nil)
  }

  test("a definition with an empty body resolves") {
    val xml = project(
      s"""<block-definition s="square %size" type="command" category="Variables"><header></header><code></code><translations></translations><inputs><input type="%n"></input></inputs></block-definition>""",
      script("""<custom-block s="square %n"><l>50</l></custom-block>""")
    )
    assertEquals(SnapCustomBlockRules.obsoleteCalls(xml), Nil)
  }

  test("sprite-local definitions are not visible to unscoped calls") {
    val local = definition("square %size", """<input type="%n"></input>""")
    val xml =
      s"""<project name="t" version="2"><scenes select="1"><scene name="t"><blocks></blocks><stage name="Stage"><blocks></blocks><sprites select="1"><sprite name="Sprite"><blocks>$local</blocks><scripts>${script("""<custom-block s="square %n"><l>1</l></custom-block>""")}</scripts></sprite></sprites></stage></scene></scenes></project>"""
    assertEquals(SnapCustomBlockRules.localDefinitions(xml).map(_.spec), List("square %size"))
    assertEquals(SnapCustomBlockRules.globalDefinitions(xml), Nil)
    assertEquals(SnapCustomBlockRules.obsoleteCalls(xml).map(_.spec), List("square %n"))
  }

  test("allCategories combines built-ins, palette entries and host-registered tabs") {
    val xml = project(
      definition("square %size", """<input type="%n"></input>"""),
      "",
      palette = """<palette><category name="Variables" color="243,118,29,1"/></palette>"""
    )
    assertEquals(SnapCustomBlockRules.paletteCategories(xml), List("Variables"))
    assert(SnapCustomBlockRules.allCategories(xml).contains("Variables"))
    assert(SnapCustomBlockRules.allCategories(xml, List("Blocks")).contains("Blocks"))
    assertEquals(SnapCustomBlockRules.effectiveCategory("Unknown", SnapCustomBlockRules.allCategories(xml)), "other")
    assertEquals(SnapCustomBlockRules.effectiveCategory("Variables", SnapCustomBlockRules.allCategories(xml)), "Variables")
  }

  test("non-command, sprite-local and non-scalar slots are reported as Python-incompatible") {
    val reporter =
      s"""<block-definition s="area %size" type="reporter" category="Variables"><inputs><input type="%n"></input></inputs></block-definition>"""
    assert(SnapCustomBlockRules.allDefinitions(reporter).head.pythonIncompatibility.isDefined)

    val scripted =
      s"""<block-definition s="twice %action" type="command" category="Variables"><inputs><input type="%cs"></input></inputs></block-definition>"""
    val message = SnapCustomBlockRules.allDefinitions(scripted).head.pythonIncompatibility
    assert(message.exists(_.contains("%cs")), clue = message)

    val variadic =
      s"""<block-definition s="sum %values" type="command" category="Variables"><inputs><input type="%mult%n"></input></inputs></block-definition>"""
    assert(SnapCustomBlockRules.allDefinitions(variadic).head.pythonIncompatibility.isDefined)

    val plain = definition("square %size", """<input type="%n"></input>""")
    assert(SnapCustomBlockRules.allDefinitions(plain).head.isPythonCompatible)
  }

  test("python apply output resolves in Snap for plain command blocks") {
    val sources = List(
      "def square(n):\n    forward(n)\n\nsquare(50)\n",
      "def setup():\n    clear()\n\nsetup()\n",
      "def square(n):\n    pass\n\nsquare(50)\n",
      "def spiral(n):\n    forward(n)\n    spiral(n)\n\nspiral(10)\n",
      "def square(n):\n    forward(n)\n\nfor _ in range(4):\n    square(50)\n"
    )
    sources.foreach { source =>
      val result = SnapTurtlePythonBridge.applyPython(source)
      assert(result.isRight, clue = s"$source -> $result")
      val xml = result.toOption.get.snapXml
      assertEquals(SnapCustomBlockRules.obsoleteCalls(xml), Nil, clue = s"$source -> $xml")
    }
  }
}
