package it.evadid.workbook.elements.interactionElements.programming

import it.evadid.core.datastructures.language.AppLanguage.{Python, SnapLanguage}
import it.evadid.vm.analysis.ProgramAnalysis
import it.evadid.vm.conversion.dependencies.ProgrammingEnvironment
import it.evadid.vm.conversion.snapexpression.SnapCustomBlockRules
import it.evadid.vm.conversion.snapmodelxml.SnapModelToXml.*
import it.evadid.workbook.elements.interactionElements.programming.state.*
import it.evadid.workbook.elements.interactionElements.programming.state.snap.SnapScalaModel
import it.evadid.workbook.elements.interactionElements.programming.state.snap.SnapScalaModel.*
import it.evadid.workbook.elements.interactionElements.programming.state.snap.SnapVocabulary.*
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import munit.FunSuite
import upickle.default.{read, write}

/** Projects saved by the bundled TurtleStitch editor read losslessly into the typed Snap model, every
  * attribute of a closed vocabulary has its typed value, and what our writer produces means the same
  * program after native Snap has loaded and saved it. See [[NativeSnapFixtures]] for regeneration.
  */
class NativeSnapFormatSpec extends FunSuite {
  private def model(name: String): Document =
    ProgrammingStateRaw(SnapLanguage, NativeSnapFixtures.read(name)).toSnapScalaModel.fold(error => fail(s"$name: $error"), _.model)
  private def nodes(document: Document): Vector[SnapElement] = document.root +: document.root.descendants
  private def python(state: ProgrammingState): String = state.toPythonStructured.toString

  NativeSnapFixtures.all.foreach { name =>
    test(s"$name: native XML reads losslessly and its history codec round-trips") {
      val xml = NativeSnapFixtures.read(name)
      val document = model(name)
      assertEquals(document.toXml, xml, "the model writes back exactly what the editor saved")
      assertEquals(read[Document](write(document)), document)
      val state = ProgrammingStateSnapScalaModel(document)
      assertEquals(read[ProgrammingStateSnapScalaModel](write(state)).toSnapXml, ProgrammingStateRaw(SnapLanguage, xml).removeBloatFromXml)
      assert(document.project.exists(_.app.exists(_.startsWith("Snap!"))), "saved by the bundled editor")
    }

    test(s"$name: every native element has a typed node and every closed-vocabulary attribute a typed value") {
      val document = model(name)
      assertEquals(nodes(document).collect { case e: Extension => e.element.tag }.distinct, Vector.empty[String])
      def readable[A](present: SnapElement => Boolean, typed: SnapElement => Option[A], label: String): Unit =
        nodes(document).filter(present).foreach(node => assert(typed(node).nonEmpty, s"$label of ${node.element.tag}: ${node.element.attributes}"))
      def has(attribute: String)(node: SnapElement) = node.element.attribute(attribute).nonEmpty
      readable(n => n.isInstanceOf[Sprite] && has("pen")(n), { case s: Sprite => s.penPoint; case _ => None }, "pen")
      readable(n => n.isInstanceOf[Sprite] && has("rotation")(n), { case s: Sprite => s.rotationStyle; case _ => None }, "rotation")
      readable(n => n.isInstanceOf[Sprite] && has("color")(n), { case s: Sprite => s.color; case _ => None }, "sprite color")
      readable(n => n.isInstanceOf[Stage] && has("lines")(n), { case s: Stage => s.lineEnds; case _ => None }, "lines")
      readable(n => n.isInstanceOf[Stage] && has("color")(n), { case s: Stage => s.color; case _ => None }, "stage color")
      readable(_.isInstanceOf[Scene], { case s: Scene => s.colorModel; case _ => None }, "colormodel")
      readable(n => n.isInstanceOf[BlockDefinition], { case d: BlockDefinition => d.blockType; case _ => None }, "type")
      readable(n => n.isInstanceOf[BlockDefinition], { case d: BlockDefinition => d.category; case _ => None }, "category")
      readable(n => n.isInstanceOf[Watcher] && has("style")(n), { case w: Watcher => w.style; case _ => None }, "style")
      List("x", "y", "heading", "scale", "volume", "pan").foreach(attribute =>
        readable(n => n.isInstanceOf[Sprite] && has(attribute)(n), n => n.element.number(attribute), attribute))
    }
  }

  test("our writer's programs mean the same program after the native editor saved them") {
    NativeSnapFixtures.programs.foreach { (name, program) =>
      val ours = program.toSnapScalaModel.fold(error => fail(s"$name: $error"), identity)
      val native = ProgrammingStateRaw(SnapLanguage, NativeSnapFixtures.read(s"$name.xml"))
      assertEquals(python(native), python(ours), name)
      assertEquals(SnapCustomBlockRules.obsoleteCalls(native.code), Nil, s"$name: every call resolves natively")
    }
  }

  test("every native export draws the same turtle commands through its Python conversion") {
    def commands(state: ProgrammingStateRaw) =
      ProgramAnalysis.of(state, ProgrammingEnvironment.all).turtle.map(_.commands)
    NativeSnapFixtures.all.foreach { name =>
      val native = ProgrammingStateRaw(SnapLanguage, NativeSnapFixtures.read(name))
      val analysis = ProgramAnalysis.of(native, ProgrammingEnvironment.all)
      analysis.python.foreach { code =>
        assertEquals(commands(ProgrammingStateRaw(Python, code)), analysis.turtle.map(_.commands), s"$name via Python:\n$code")
      }
    }
  }

  test("published projects mean the same program before and after the native editor saved them") {
    val published = Files.list(NativeSnapFixtures.embroideryProjects)
    val names = try scala.jdk.CollectionConverters.IteratorHasAsScala(published.iterator()).asScala
      .map(_.getFileName.toString).filter(_.endsWith(".xml")).toVector finally published.close()
    names.foreach { name =>
      val original = ProgrammingStateRaw(SnapLanguage, Files.readString(NativeSnapFixtures.embroideryProjects.resolve(name), StandardCharsets.UTF_8))
      assertEquals(python(ProgrammingStateRaw(SnapLanguage, NativeSnapFixtures.read(name))), python(original), name)
    }
  }

  test("the editor's wider vocabulary reads into typed values") {
    val document = model("features.xml")
    val scene = nodes(document).collectFirst { case s: Scene => s }.get
    assert(scene.unifiedPalette)
    assertEquals(scene.colorModel, Some(ColorModel.Hsv))
    val stage = scene.stage.get
    assertEquals(stage.lineEnds, Some(LineEnds.Flat))
    assertEquals(stage.sprites.map(s => (s.name.get, s.penPoint, s.rotationStyle, s.draggable)), Vector(
      ("Sprite", Some(PenPoint.Center), Some(RotationStyle.LeftRight), Some(false)),
      ("Helper", Some(PenPoint.Tip), Some(RotationStyle.DontRotate), Some(true))))
    val definitions = nodes(document).collect { case d: BlockDefinition => d }
    assertEquals(definitions.map(d => (d.spec.get, d.blockType, d.category, d.isRuleHat)), Vector(
      ("double %n", Some(BlockType.Reporter), Some(BlockCategory.Operators), false),
      ("is big %x", Some(BlockType.Predicate), Some(BlockCategory.Other), false),
      ("when ready", Some(BlockType.Hat), Some(BlockCategory.Control), true),
      ("stitch twice", Some(BlockType.Command), Some(BlockCategory.Embroidery), false)))
    assertEquals(definitions(1).inputs.map(input => (input.inputType, input.readonly)), Vector((Some("%s"), Some(true))))
    val lists = nodes(document).collect { case l: ListValue => l }
    assert(lists.exists(l => l.linked && l.isAtomic && l.atomicCsv.contains("head,tail")))
    assert(lists.exists(l => !l.isAtomic && l.items.size == 3))
    assertEquals(stage.watchers.map(w => (w.variable, w.style)), Vector((Some("score"), Some(WatcherStyle.Normal))))
    assertEquals(nodes(document).collect { case c: Comment => (c.text, c.collapsed) }, Vector(("A note", Some(false))))
  }

  test("definitions saved in a sprite are sprite-local, and the Python writer does not claim them") {
    val xml = NativeSnapFixtures.read("features.xml")
    assertEquals(SnapCustomBlockRules.globalDefinitions(xml).map(_.spec), List("double %n", "is big %x", "when ready"))
    val local = SnapCustomBlockRules.localDefinitions(xml)
    assertEquals(local.map(block => (block.spec, block.isGlobal, block.category)), List(("stitch twice", false, BlockCategory.Embroidery)))
    assert(local.head.pythonIncompatibility.exists(_.contains("sprite-local")), local.head.pythonIncompatibility.toString)
    assertEquals(SnapCustomBlockRules.BuiltInCategories.last, "embroidery", "TurtleStitch's own category is built in")
  }
}
