package it.evadid.workbook.elements.interactionElements.programming

import munit.FunSuite
import upickle.default.*
import it.evadid.workbook.elements.interactionElements.programming.state.*
import it.evadid.workbook.elements.interactionElements.programming.state.snap.*
import it.evadid.workbook.elements.interactionElements.programming.state.snap.SnapScalaModel.*
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseRegular.ProgrammingExercise

class ProgrammingStateSnapScalaModelSpec extends FunSuite {
  // Snap 11 / TurtleStitch serializer vocabulary, including fields the legacy client mapper drops.
  private val fullProject = """<?xml version="1.0"?>
<project name='Unicode &#x1F422; &amp; &quot;project&quot;' app="Snap!" version="2" temp="true" xmlns:ext="urn:test">
<notes>hello &lt;stage&gt; &#10; &amp;lt;<![CDATA[<literal>]]></notes><thumbnail>data:image/png;base64,AA==</thumbnail>
<scenes select="2"><scene name="First" role="tutorial" strict="true" palette="single" buttons="false">
<palette><category name="custom" color="10,20,30,1"/></palette><hidden>forward turn</hidden>
<headers><forward>header &amp; body</forward></headers><code><forward>move(#1)</forward></code>
<template version="11" lang="de"><primitives/><custom/><variables/></template>
<blocks><block-definition s="say %'word'" type="command" category="custom" helper="true" space="true" reports="%s" strict="true" semantics="rule">
<comment w="120" collapsed="false">block note</comment><variables><list struct="atomic">a,b</list></variables>
<header>import turtle</header><code>print(#1)</code><translations>de:sagen</translations>
<inputs><input type="%s" readonly="true" irreplaceable="true" separator="," collapse="less" expand="more" initial="2" min="1" max="9">hello<options>one
two</options></input></inputs>
<script><block s="doReport"><l><bool>true</bool></l></block></script><scripts><script x="20" y="30"><block s="forward"><l>20</l></block></script></scripts>
</block-definition></blocks><primitives><block-definition s="primitive" type="reporter" category="other" selector="reportSum" primitive="reportSum"/></primitives>
<stage name="Stage" width="480" height="360" costume="0" color="255,255,255,1" tempo="60" threadsafe="true" id="stage-id" sublistIDs="true">
<pentrails>data:image/png;base64,TRAILS</pentrails><wear><costume name="worn" image="data:image/svg+xml,&lt;svg/&gt;"/></wear>
<costumes><list id="costumes"><item><costume id="c1" name="authored" center-x="1.5" center-y="2" image="data:image/png;base64,IMAGE" embed="metadata"/></item></list></costumes>
<sounds><list><item><sound id="sound1" name="sound" sound="data:audio/wav;base64,AUDIO"/></item></list></sounds>
<variables><variable name="cycle"><list id="cyclic"><item><l>text</l></item><item><bool>false</bool></item><item><ref id="cyclic"/></item></list></variable>
<variable name="linked"><list linked="linked"><item><l>head</l></item><list linked="linked"/></list></variable>
<variable name="atomic"><list struct="atomic">"a,b",c
1,2</list></variable><variable name="closure"><context id="ctx"><inputs><input>arg</input></inputs><variables/>
<remark>closure remark</remark><script><block s="forward"><l>2</l></block></script><receiver><ref id="sprite1"/></receiver><origin><ref id="stage-id"/></origin><context><inputs/><variables/><receiver/></context></context></variable></variables>
<blocks/><scripts><comment x="10" y="20" w="100" collapsed="true">free note</comment></scripts>
<sprites select="1"><sprite id="sprite1" name="Turtle" idx="1" x="-2.5" y="3" heading="90" scale="0.1" volume="80" rotation="1" hidden="false" draggable="true" color="0,0,0,1">
<solution><sprite name="answer"><scripts/></sprite></solution><inherit exemplar="parent"><list struct="atomic">scripts,costumes</list></inherit><nest anchor="parent" synch="true" scale="0.5"/>
<costumes/><sounds/><blocks/><variables/><dispatches><block-definition s="override" type="command" category="other"/></dispatches>
<scripts><script x="156" y="66"><block s="receiveGo"/><custom-block s="say %s" scope="Turtle"><l>hi</l><variables/><comment w="30">call</comment></custom-block>
<block s="doRepeat"><l>3</l><script><block s="forward"><l>10</l></block></script></block>
<block s="reportNewList"><list><l><option>last</option></l><color>1,2,3,0.5</color><autolambda><block var="x"/></autolambda><l><wish s="forward %n" type="command" category="motion">de:vor</wish></l></list></block></script></scripts></sprite>
<watcher var="cycle" scope="Stage" style="slider" min="0" max="10" x="5" y="6" hidden="false"/><watcher s="getTimer" style="normal"/></sprites></stage><variables/>
</scene><scene name="Second"><stage name="Stage 2"><sprites/><scripts/></stage><variables/></scene></scenes>
<creator>author</creator><origCreator>first author</origCreator><origName>original name</origName>
<!-- <block s="not-code"/> --><?extension key="value"?><ext:future enabled='yes'>mixed<ext:child/>tail</ext:future>
</project>
"""

  private def converted(xml: String, floating: List[String] = Nil): ProgrammingStateSnapScalaModel =
    ProgrammingStateSnapXml(xml, floating).toSnapScalaModel.fold(message => fail(message), identity)

  private def assertRoundTrip(xml: String): Unit = {
    val source = ProgrammingStateSnapXml(xml, List("watcher\nopaque", "", "unicode 🐢"))
    val typed = source.toSnapScalaModel.toOption.get
    assertEquals(typed.toSnapXml, source)
    assertEquals(converted(typed.toSnapXml.snapXml, typed.legacyFloatingObjects), typed)
    assertEquals(read[ProgrammingStateSnapScalaModel](write(typed)), typed)
    assertEquals(readBinary[ProgrammingStateSnapScalaModel](writeBinary(typed)), typed)
    assertEquals(read[ProgrammingState](write[ProgrammingState](typed)), typed)
    assertEquals(readBinary[ProgrammingState](writeBinary[ProgrammingState](typed)), typed)
    assertEquals(ProgrammingExercise.StateSerializer.deserialize(ProgrammingExercise.StateSerializer.serialize(typed)), typed)
    assertEquals(ProgrammingExercise.StateSerializer.deserialize(ProgrammingExercise.StateSerializer.serialize(source)), source)
    assertEquals(read[ProgrammingStateSnapXml](write(source)), source)
    assertEquals(typed.fingerprint(), source.fingerprint())
  }

  test("full project survives XML, JSON, MessagePack and workbook-state round trips") {
    assertRoundTrip(fullProject)
  }
  test("typed views expose multi-scenes, stage geometry, sprite state, blocks, assets and cyclic data") {
    val project = converted(fullProject).model.project.get
    assertEquals(project.name, Some("Unicode 🐢 & \"project\""))
    assertEquals(project.selectedScene, Some(2))
    assertEquals(project.scenes.size, 2)
    assertEquals(project.notes, Some("hello <stage> \n &lt;<literal>"))
    assertEquals(project.creator, Some("author"))
    val scene = project.scenes.head
    assertEquals(scene.strict, Some(true))
    assertEquals(scene.definitions.head.inputs.head.readonly, Some(true))
    assertEquals(scene.primitiveDefinitions.head.primitiveSelector, Some("reportSum"))
    val stage = scene.stage.get
    assertEquals(stage.width, Some(480.0))
    assertEquals(stage.threadSafe, Some(true))
    assertEquals(stage.color, Some(Rgba(255, 255, 255, 1)))
    assertEquals(stage.costumes.head.image, Some("data:image/png;base64,IMAGE"))
    assertEquals(stage.sounds.head.data, Some("data:audio/wav;base64,AUDIO"))
    val sprite = stage.sprites.head
    assertEquals(sprite.x, Some(-2.5))
    assertEquals(sprite.hidden, Some(false))
    assertEquals(sprite.scripts.head.blocks.size, 4)
    assert(sprite.scripts.head.blocks(1).isInstanceOf[CustomBlock])
    assertEquals(sprite.scripts.head.blocks(1).inputs.size, 1)
    assertEquals(stage.watchers.head.maximum, Some(10.0))
    val cycle = stage.variables.head.value.get.asInstanceOf[ListValue]
    assertEquals(cycle.items.last.value.get.asInstanceOf[Reference].target, cycle.id)
    assertEquals(stage.variables(2).value.get.asInstanceOf[ListValue].atomicCsv, Some("\"a,b\",c\n1,2"))
    assert(stage.variables.last.value.get.isInstanceOf[Context])
    assert(project.descendants.exists {
      case e: Extension => e.element.tag == "ext:future"
      case _ => false
    })
  }
  test("legacy single-stage projects, block libraries and separate media modules survive") {
    List("<project><stage name='legacy'><sprites/></stage></project>",
      "<blocks><block-definition s='block' type='command'/></blocks>",
      "<snapdata><project><scenes/></project><media><costume mediaID='c' image='payload'/><sound mediaID='s' sound='audio'/></media></snapdata>")
      .foreach(assertRoundTrip)
    assert(converted("<project><stage/></project>").model.project.get.legacyStage.nonEmpty)
    assert(converted("<snapdata><project/><media/></snapdata>").model.project.nonEmpty)
  }
  test("existing canonical projects convert without depending on the executable subset") {
    List(ProgrammingStateSnapXmlHelper.empty, ProgrammingStateSnapXmlHelper.mini,
      ProgrammingStatePythonString("forward(10)\nright(90)\n").toSnapXml).foreach(s => assertRoundTrip(s.snapXml))
    val unsupported = "<project><stage><scripts><script><block s='newUnknownPrimitive'><l>opaque</l></block></script></scripts></stage></project>"
    assertRoundTrip(unsupported)
  }
  test("typed immutable edits preserve unrelated scenes, assets, unknown attributes and metadata") {
    val state = converted(fullProject, List("legacy"))
    val edited = state.copy(model = state.model.transform {
      case sprite: Sprite if sprite.id.contains("sprite1") => sprite.withAttribute("x", "12.25")
      case node => node
    })
    assertEquals(edited.model.project.get.scenes.head.stage.get.sprites.head.x, Some(12.25))
    assertEquals(edited.toSnapXml.legacyFloatingObjects, List("legacy"))
    assertEquals(edited.toSnapXml.snapXml, fullProject.replace("x=\"-2.5\"", "x=\"12.25\""))
    assertEquals(state.toSnapXml.snapXml, fullProject)
    assertRoundTrip(edited.toSnapXml.snapXml)
  }
  test("entity escaping is single-pass, handles supplementary Unicode and safe edited attributes") {
    val document = converted("<project name='&amp;lt; &#128034; &#x1f422;'><notes>&amp;amp;</notes></project>").model
    assertEquals(document.root.name, Some("&lt; 🐢 🐢"))
    assertEquals(document.project.get.notes, Some("&amp;"))
    val edited = document.transform {
      case p: Project => p.withAttribute("name", "\"&<>'")
      case node => node
    }
    assertEquals(SnapScalaModel.parse(edited.toXml).toOption.get.root.name, Some("\"&<>'"))
    assertRoundTrip(edited.toXml)
  }
  test("DOCTYPE entities remain opaque and external resources are never resolved") {
    val xml = "<!DOCTYPE project [<!ENTITY external SYSTEM 'https://example.invalid/secret'>]><project><notes>&external;</notes></project>"
    assertRoundTrip(xml)
    assertEquals(converted(xml).model.project.get.notes, Some("&external;"))
  }
  test("mixed XML constructs and whitespace in content are retained") {
    assertRoundTrip("\ufeff<?xml version='1.0'?>\r\n<!-- before --><project  name = 'a > b' ><notes>  a\r\nb  <![CDATA[<block/>]]><!-- mid -->tail</notes><scenes /></project   >\n<!-- after -->")
  }
  test("malformed and deeply nested projects fail explicitly rather than discarding content") {
    List("", "<project>", "<project><scene></project>", "<project/><project/>", "<project name='unclosed>",
      "<project name='a' name='b'/>", "<project><!-- unclosed</project>", "<project><![CDATA[unclosed</project>",
      "<project a='<'/>", "<project>&#x110000;</project>", "<project>&#0;</project>",
      "<project>" + "<list>" * 260 + "</list>" * 260 + "</project>")
      .foreach(xml => assert(ProgrammingStateSnapXml(xml).toSnapScalaModel.isLeft, xml.take(100)))
  }
  test("newly authored typed trees serialize, and editing a self-closing node creates a closing tag") {
    val empty = Project(Element("project", selfClosing = true))
    val withNotes = Project(empty.element.copy(children = Vector(Notes(Element("notes", children = Vector(Text("new story")))))))
    val state = ProgrammingStateSnapScalaModel(Document(Vector(withNotes)))
    assertEquals(state.toSnapXml.snapXml, "<project><notes>new story</notes></project>")
    assertRoundTrip(state.toSnapXml.snapXml)
  }
}
