package it.evadid.workbook.elements.interactionElements.programming.state.snap

import it.evadid.vm.parsing.source.SourceParsing.SourceLanguage
import it.evadid.vm.parsing.source.SourceParsing
import it.evadid.workbook.elements.interactionElements.programming.state.snap.SnapScalaModel.*
import it.evadid.workbook.elements.interactionElements.programming.state.snap.SnapVocabulary.*
import munit.FunSuite

class SnapScalaModelViewsSpec extends FunSuite {
  private val xml =
    """<project name="demo" app="Snap! 10" version="2"><notes>Read me</notes><thumbnail>data:png</thumbnail>""" +
      """<origCreator>alice</origCreator><origName>first</origName><creator>bob</creator>""" +
      """<scenes select="1"><scene name="main" role="learner" palette="single" categories="false" buttons="true" clickrun="true" dragdata="false" colormodel="hsl" strict="true">""" +
      """<primitives><block-definition s="forward %n" type="command" category="motion" selector="forward" primitive="forward" helper="false" space="true" reports="number" strict="false" semantics="wide"><inputs><input type="%n" readonly="true" irreplaceable="false" separator="," collapse="c" expand="e" initial="2" min="0" max="9">10</input></inputs><script><block s="forward"><l>1</l></block></script></block-definition></primitives>""" +
      """<stage name="Stage" width="480" height="360" costume="1" color="255,255,255,1" tempo="60" threadsafe="false" penlog="true" volume="80" pan="5" lines="round" ternary="false" hyperops="true" codify="false" inheritance="true" sublistIDs="false" instrument="3">""" +
      """<costumes><list struct="atomic" id="7"><item><costume name="bg" center-x="1" center-y="2" image="data:a" embed="x"/></item></list></costumes>""" +
      """<sounds><list><item><sound name="beep" sound="data:s"/></item></list></sounds>""" +
      """<sprites select="1"><sprite name="Turtle" idx="1" x="10" y="-20" heading="90" scale="0.5" volume="70" pan="-3" rotation="1" draggable="true" hidden="false" costume="0" color="0,128,0,1" pen="tip" instrument="2">""" +
      """<variables><variable name="count" hidden="true" transient="false"><l>5</l></variable></variables>""" +
      """<scripts><script x="12" y="34"><block s="receiveGo"/><block var="count"/><custom-block s="square %n" scope="local"><l>3</l></custom-block>""" +
      """<block s="setColor"><color>1,2,3,1</color></block><block s="doIf"><l><bool>true</bool></l><l><option>random</option></l></block>""" +
      """<block s="reportNewList"><list linked="linked"><item><l>a</l></item><list struct="atomic">1,2</list></list></block></script>""" +
      """<comment x="1" y="2" w="90" collapsed="false">note</comment></scripts></sprite>""" +
      """<watcher var="count" scope="Turtle" style="normal" x="3" y="4" min="0" max="10" hidden="true"/></sprites></stage></scene></scenes></project>"""

  private val document = SourceParsing.parse(xml, SourceLanguage.Snap).syntax.fold(message => fail(message), identity)
  private val project = document.project.get
  private val scene = project.scenes.head
  private val stage = scene.stage.get
  private val sprite = stage.sprites.head

  test("project and scene views expose metadata and settings") {
    assertEquals((project.name, project.app, project.version), (Some("demo"), Some("Snap! 10"), Some("2")))
    assertEquals((project.notes, project.thumbnail, project.creator), (Some("Read me"), Some("data:png"), Some("bob")))
    assertEquals((project.originalCreator, project.originalName), (Some("alice"), Some("first")))
    assertEquals((project.selectedScene, project.legacyStage), (Some(1), None))
    assertEquals((scene.role, scene.unifiedPalette, scene.colorModel), (Some("learner"), true, Some(ColorModel.Hsl)))
    assertEquals((scene.showCategories, scene.showButtons, scene.clickRun, scene.dragData, scene.strict),
      (Some(false), Some(true), Some(true), Some(false), Some(true)))
  }

  test("primitive definitions expose their signature, inputs and body") {
    val definition = scene.primitiveDefinitions.head
    assertEquals((definition.spec, definition.blockType, definition.category), (Some("forward %n"), Some(BlockType.Command), Some(BlockCategory.Motion)))
    assertEquals((definition.primitiveSelector, definition.primitive, definition.reports, definition.isRuleHat),
      (Some("forward"), Some("forward"), Some("number"), false))
    assertEquals((definition.helper, definition.spaceAbove, definition.strict), (Some(false), Some(true), Some(false)))
    assertEquals(definition.body.head.blocks.flatMap(_.selector), Vector("forward"))
    val input = definition.inputs.head
    assertEquals((input.inputType, input.readonly, input.irreplaceable, input.separator), (Some("%n"), Some(true), Some(false), Some(",")))
    assertEquals((input.collapse, input.expand, input.initial, input.minimum, input.maximum), (Some("c"), Some("e"), Some(2), Some(0.0), Some(9.0)))
  }

  test("stage views decode numbers, flags, colors, media and watchers") {
    assertEquals((stage.width, stage.height, stage.tempo, stage.volume, stage.pan), (Some(480.0), Some(360.0), Some(60.0), Some(80.0), Some(5.0)))
    assertEquals((stage.costumeIndex, stage.instrument, stage.lineEnds), (Some(1), Some(3), Some(LineEnds.Round)))
    assertEquals((stage.penLog, stage.ternary, stage.hyperops, stage.codify, stage.inheritanceEnabled, stage.sublistIDs, stage.threadSafe),
      (Some(true), Some(false), Some(true), Some(false), Some(true), Some(false), Some(false)))
    assertEquals(stage.color, Some(Rgba(255, 255, 255, 1)))
    assertEquals(stage.selectedSprite, Some(1))
    val costume = stage.costumes.head
    assertEquals((costume.name, costume.centerX, costume.centerY, costume.image, costume.embed), (Some("bg"), Some(1.0), Some(2.0), Some("data:a"), Some("x")))
    assertEquals(stage.sounds.map(_.data), Vector(Some("data:s")))
    val watcher = stage.watchers.head
    assertEquals((watcher.variable, watcher.scope, watcher.style, watcher.selector), (Some("count"), Some("Turtle"), Some(WatcherStyle.Normal), None))
    assertEquals((watcher.x, watcher.y, watcher.minimum, watcher.maximum, watcher.hidden), (Some(3.0), Some(4.0), Some(0.0), Some(10.0), Some(true)))
  }

  test("sprite, variable and script views expose their content") {
    assertEquals((sprite.index, sprite.x, sprite.y, sprite.heading, sprite.scale), (Some(1), Some(10.0), Some(-20.0), Some(90.0), Some(0.5)))
    assertEquals((sprite.volume, sprite.pan, sprite.rotationStyle, sprite.instrument, sprite.costumeIndex, sprite.penPoint),
      (Some(70.0), Some(-3.0), Some(RotationStyle.Free), Some(2), Some(0), Some(PenPoint.Tip)))
    assertEquals((sprite.hidden, sprite.draggable, sprite.color), (Some(false), Some(true), Some(Rgba(0, 128, 0, 1))))
    val variable = sprite.variables.head
    assertEquals((variable.name, variable.hidden, variable.transient), (Some("count"), Some(true), Some(false)))
    assertEquals(variable.value.map(_.text), Some("5"))
    val script = sprite.scripts.head
    assertEquals((script.x, script.y), (Some(12.0), Some(34.0)))
    val blocks = script.blocks
    assertEquals(blocks.map(_.selector), Vector(Some("receiveGo"), None, Some("square %n"), Some("setColor"), Some("doIf"), Some("reportNewList")))
    assertEquals(blocks(1).asInstanceOf[Block].variable, Some("count"))
    assert(blocks(2).asInstanceOf[CustomBlock].isLocal)
    assertEquals(blocks(2).inputs.collect { case literal: Literal => literal.number }, Vector(Some(3.0)))
    assertEquals(blocks(3).inputs.collect { case color: Color => color.value }, Vector(Some(Rgba(1, 2, 3, 1))))
    val Vector(flag: Literal, option: Literal) = blocks(4).inputs: @unchecked
    assertEquals(flag.boolean.flatMap(_.value), Some(true))
    assertEquals(option.option.map(_.text), Some("random"))
    assertEquals((flag.wish, flag.number), (None, None))
    val Vector(list: ListValue) = blocks(5).inputs: @unchecked
    assert(list.linked)
    assertEquals(list.items.flatMap(_.value).map(_.text), Vector("a"))
    assertEquals(list.tail.flatMap(_.atomicCsv), Some("1,2"))
    assertEquals(list.atomicCsv, None)
    val comment = sprite.element.named("scripts").head.element.named("comment").head.asInstanceOf[Comment]
    assertEquals((comment.x, comment.y, comment.width, comment.collapsed, comment.text), (Some(1.0), Some(2.0), Some(90.0), Some(false), "note"))
  }

  test("attribute edits keep other attributes, escape values and reclassify the element") {
    val renamed = sprite.withAttribute("name", "A&B").withAttribute("extra", "<1>")
    assert(renamed.isInstanceOf[Sprite])
    assertEquals(renamed.name, Some("A&B"))
    assertEquals(renamed.element.attributes.last.rawValue, "&lt;1&gt;")
    assertEquals(renamed.element.attribute("idx"), Some("1"))
    assertEquals(Element("x", Vector(Attribute("flag", "maybe"))).boolean("flag"), None)
    assertEquals(Rgba.parse("1,2,3"), None)
    assertEquals(BooleanValue(Element("bool", children = Vector(Text("maybe")))).value, None)
  }
}
