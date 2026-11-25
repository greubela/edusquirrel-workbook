package it.evadid.vm.conversion.turtlestitchexpression

import it.evadid.vm.parsing.source.SourceParsing
import it.evadid.vm.parsing.source.SourceParsing.SourceLanguage
import it.evadid.vm.conversion.turtlestitchexpression.TurtleStitchProgramModel.*
import munit.FunSuite

class TurtleStitchModelLoadingSpec extends FunSuite {
  private def projectXml(definitionBody: String, scripts: String, withDefinitions: Boolean = true) =
    """<project name="stitch" app="TurtleStitch 2.11" version="2"><notes>hello</notes><thumbnail>data:png</thumbnail>""" +
      """<scenes select="1"><scene name="main"><notes>scene notes</notes><hidden>forward turn</hidden><headers></headers><code></code>""" +
      (if withDefinitions then """<blocks><block-definition s="jump %'n'" type="command" category="motion"><header>h</header><code>c</code>""" +
        """<inputs><input type="%n" readonly="true">5</input></inputs><variables><variable name="local"/></variables>""" +
        s"""<comment x="1" y="2" w="80" collapse="false">explains</comment>$definitionBody</block-definition></blocks>""" else "") +
      """<stage name="Stage" width="480" height="360" costume="0" color="255,255,255,1" tempo="60" threadsafe="false" penlog="false" volume="100" pan="0" lines="round" ternary="false" hyperops="true" codify="false" inheritance="true" sublistIDs="false">""" +
      """<pentrails>data:trails</pentrails><wear><costume name="cloth" center-x="3" center-y="4" image="data:img"/></wear>""" +
      """<costumes><list struct="atomic"><item><costume name="bg" center-x="1" center-y="2" image="data:bg" id="9"/></item></list></costumes>""" +
      """<sprites select="1"><sprite name="Turtle" idx="1" x="5" y="-5" heading="90" scale="1" volume="100" pan="0" rotation="1" draggable="true" hidden="false" costume="0" color="0,0,255,1" pen="tip">""" +
      """<variables><variable name="size" transient="true"><l>10</l></variable><variable name="flag" hidden="true"><bool>true</bool></variable></variables>""" +
      s"""<scripts>$scripts</scripts></sprite></sprites></stage></scene></scenes>""" +
      """<creator>alice</creator><origCreator>bob</origCreator><origName>first</origName></project>"""

  private val script =
    """<script x="10" y="20"><block s="receiveGo"/><custom-block s="jump %n"><l>3</l></custom-block>""" +
      """<block s="setColor"><color>255,0,0,1</color></block><block s="doIf"><block s="reportBoolean"><l><bool>false</bool></l></block><script><block s="forward"><l>1</l></block></script></block>""" +
      """<block s="reportNewList"><list><l>a</l><l>b</l></list></block></script>"""

  test("a project without blocks keeps its metadata, scene settings, stage media and sprite variables") {
    val project = SourceParsing.parse(projectXml("", "", withDefinitions = false), SourceLanguage.Snap).project
    assertEquals((project.name, project.app, project.notes), ("stitch", "TurtleStitch 2.11", "hello"))
    assertEquals((project.thumbnail, project.creator, project.origCreator, project.origName), (Some("data:png"), Some("alice"), Some("bob"), Some("first")))
    val scene = project.scenes.head
    assertEquals(scene.hiddenPrimitives, Vector("forward", "turn"))
    assertEquals(scene.customBlocks, Vector.empty)
    val stage = scene.stage
    assertEquals(stage.pentrails, Some("data:trails"))
    assertEquals(stage.wear.map(_.name), Some("cloth"))
    assertEquals(stage.costumes.map(costume => (costume.name, costume.centerX, costume.id)), Vector(("bg", 1.0, Some("9"))))
    val sprite = stage.sprites.head
    assertEquals((sprite.name, sprite.x, sprite.y, sprite.color), ("Turtle", 5.0, -5.0, Rgba(0, 0, 255, 1.0)))
    assertEquals(sprite.variables.map(v => (v.name, v.value, v.transient, v.hidden)), Vector(
      ("size", Some(Literal("10")), true, false), ("flag", Some(BoolLiteral(true)), false, true)))
  }

  test("projects with blocks keep scripts, custom calls, colours, booleans, nested scripts and lists") {
    val project = SourceParsing.parse(projectXml("""<script><block s="forward"><block var="n"/></block></script>""", script), SourceLanguage.Snap).project
    val scene = project.scenes.head
    val definition = scene.customBlocks.head
    assertEquals((definition.spec, definition.blockType.xml, definition.category.xml), ("jump %'n'", "command", "motion"))
    assertEquals(definition.body.head.blocks.size, 1)
    assertEquals(scene.stage.pentrails, Some("data:trails"))
    val loaded = scene.stage.sprites.head.scripts.head
    assertEquals((loaded.x, loaded.y), (Some(10.0), Some(20.0)))
    val Vector(go, call: CustomBlockCall, color: PrimitiveBlock, branch: PrimitiveBlock, list: PrimitiveBlock) = loaded.blocks: @unchecked
    assertEquals(go, PrimitiveBlock(Some("receiveGo")))
    assertEquals((call.semanticSpec, call.inputs), ("jump %n", Vector(Literal("3"))))
    assertEquals(color.inputs, Vector(ColorLiteral(Rgba(255, 0, 0, 1.0))))
    branch.inputs match {
      case Vector(NestedBlock(PrimitiveBlock(Some("reportBoolean"), None, Vector(BoolLiteral(false)), None)), NestedScript(body)) =>
        assertEquals(body.blocks, Vector(PrimitiveBlock(Some("forward"), None, Vector(Literal("1")))))
      case other => fail(s"Unexpected inputs $other")
    }
    assertEquals(list.inputs, Vector(ListLiteral(Vector(Literal("a"), Literal("b")))))
  }

  test("unreadable XML is rejected as unparsable") {
    intercept[it.evadid.vm.code.errors.ProgrammingConversionException](SourceParsing.parse("not xml at all", SourceLanguage.Snap).project)
  }
}
