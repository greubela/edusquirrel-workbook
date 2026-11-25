package it.evadid.homepage.webElements.editor.code.SnapEditor

import com.raquo.airstream.ownership.ManualOwner
import com.raquo.airstream.state.Var
import it.evadid.core.datastructures.language.AppLanguage.Java
import it.evadid.workbook.elements.interactionElements.programming.state.{ProgrammingStateRaw, ProgrammingStateSnapScalaModel}
import munit.FunSuite
import SnapProjectBindingSpec.FakeNative

class SnapProjectBindingSpec extends FunSuite {
  private def model(xml: String) = SnapProjectBinding.typed(xml)
  private def xml(code: String, image: String): String =
    s"<project><thumbnail>$image</thumbnail><stage><pentrails>$image</pentrails><scripts>$code</scripts></stage></project>"
  private def clean(code: String) = s"<project><stage><scripts>$code</scripts></stage></project>"

  /** A binding whose native editor shows `answer` as `native` (Snap's serialization of it). */
  private def opened(answer: String, native: String): (Var[Option[ProgrammingStateSnapScalaModel]], FakeNative, SnapProjectBinding) = {
    val project = Var(Option(model(answer)))
    val editor = FakeNative()
    editor.xml = Some(native)
    val binding = SnapProjectBinding(project, editor)
    binding.opened(model(answer))
    (project, editor, binding)
  }

  test("an opened answer is not written back in Snap's spelling; edits are published once") {
    val project = Var(Option(model("<project>start</project>")))
    val native = FakeNative()
    val binding = SnapProjectBinding(project, native)
    val owner = new ManualOwner
    var published = List.empty[ProgrammingStateSnapScalaModel]
    project.signal.changes.foreach(value => published = published ++ value)(using owner)
    try {
      native.open(project.now().get.toSnapXml)
      binding.opened(project.now().get)
      binding.observe(owner)
      binding.publish()
      assertEquals(published, Nil, "Snap's normalization of the opened answer is not an edit")
      native.xml = Some("<project app=\"Snap\">edited</project>")
      binding.publish()
      binding.publish()
      assertEquals(published, List(model("<project app=\"Snap\">edited</project>")))
      assertEquals(native.opened.size, 1, "the editor's own edit is not reopened")
    } finally owner.killSubscriptions()
  }

  test("answers from elsewhere are opened once, and skipped while a slot is edited") {
    val project = Var(Option(model("<project>start</project>")))
    val native = FakeNative()
    val binding = SnapProjectBinding(project, native)
    val owner = new ManualOwner
    try {
      binding.opened(project.now().get)
      binding.observe(owner)
      val restored = model("<project>restored</project>")
      project.set(Some(restored))
      project.set(Some(restored))
      assertEquals(native.opened, List(restored.toSnapXml.code))
      binding.publish()
      assertEquals(project.now(), Some(restored), "the reopened answer's normalization is not an edit")
      native.editing = true
      project.set(Some(model("<project>while editing</project>")))
      assertEquals(native.opened.size, 1)
      project.set(None)
      assertEquals(native.opened.size, 1, "an answer not shown as Snap is never opened")
    } finally owner.killSubscriptions()
  }

  test("Snap image regeneration commits only cleaned code and is not an edit") {
    val project = Var(Option(model("<project><stage><scripts>start</scripts></stage></project>")))
    val native = FakeNative()
    val binding = SnapProjectBinding(project, native)
    def liveXml(code: String, image: Int) = s"<project><thumbnail>image-$image</thumbnail><stage><pentrails>trails-$image</pentrails><scripts>$code</scripts></stage></project>"
    native.xml = Some(liveXml("start", 0))
    binding.opened(project.now().get)
    for (image <- 1 to 20) {
      native.xml = Some(liveXml("start", image))
      binding.publish()
    }
    assertEquals(project.now(), Some(model("<project><stage><scripts>start</scripts></stage></project>")))
    native.xml = Some(liveXml("changed", 21))
    binding.publish()
    assertEquals(project.now(), Some(model("<project><stage><scripts>changed</scripts></stage></project>")))
  }

  test("a language change re-serializes the open project without an edit") {
    val project = Var(Option(model("<project>start</project>")))
    val native = FakeNative()
    val binding = SnapProjectBinding(project, native)
    binding.opened(project.now().get)
    native.xml = Some("<project lang=\"de\">start</project>")
    binding.reserialized()
    binding.publish()
    assertEquals(project.now(), Some(model("<project>start</project>")))
  }

  test("publishing is throttled") {
    val project = Var(Option(model("<project>start</project>")))
    val native = FakeNative()
    val binding = SnapProjectBinding(project, native)
    binding.opened(project.now().get)
    native.xml = Some("<project>one</project>")
    binding.publishIfDue(1000)
    native.xml = Some("<project>two</project>")
    binding.publishIfDue(1000 + SnapProjectBinding.checkIntervalMs / 2)
    assertEquals(project.now(), Some(model("<project>one</project>")))
    binding.publishIfDue(1000 + SnapProjectBinding.checkIntervalMs)
    assertEquals(project.now(), Some(model("<project>two</project>")))
  }

  test("a new serialization that already equals the answer is not written again") {
    val (project, native, binding) = opened("<project>start</project>", "<project app=\"Snap\">start</project>")
    val owner = new ManualOwner
    var writes = 0
    project.signal.changes.foreach(_ => writes += 1)(using owner)
    try {
      // Snap serializes differently than when the answer was opened, but the program is the answer itself.
      native.xml = Some("<project>start</project>")
      binding.publish()
      binding.publish()
      assertEquals(writes, 0)
      native.xml = Some("<project>edited</project>")
      binding.publish()
      assertEquals(writes, 1)
    } finally owner.killSubscriptions()
  }

  test("regenerated image data is neither an edit nor a different answer") {
    val (project, native, binding) = opened(xml("program", "first"), xml("program", "second"))
    val owner = new ManualOwner
    try {
      binding.observe(owner)
      project.set(Some(model(clean("program"))))
      project.set(Some(model(xml("program", "third"))))
      assertEquals(native.opened, Nil)
      for (image <- 1 to 20) {
        native.xml = Some(xml("program", image.toString))
        binding.publish()
      }
      assertEquals(project.now(), Some(model(xml("program", "third"))))
    } finally owner.killSubscriptions()
  }

  test("layout edits after a restore are published") {
    val (project, native, binding) = opened(clean("old"), clean("old"))
    native.xml = Some(xml("<script x=\"2\"/>", "regenerated"))
    binding.publish()
    assertEquals(project.now(), Some(model(clean("<script x=\"2\"/>"))))
  }

  test("converted projects and ordinary projects retain pose and source edits") {
    val source = ProgrammingStateRaw(Java, "class Drawing { public static void main(String[] args) { Turtle.forward(12); } }").toSnapXml.code
    val moved = source.replace("x=\"0\" y=\"0\" heading=\"90\"", "x=\"12\" y=\"3\" heading=\"180\"")
    assert(moved != source)
    val (project, native, binding) = opened(source, source)
    native.xml = Some(moved)
    binding.publish()
    assertEquals(project.now(), Some(model(moved)))
    val edited = moved.replace("<l>12</l>", "<l>23</l>")
    assert(edited != moved)
    native.xml = Some(edited)
    binding.publish()
    assertEquals(project.now(), Some(model(edited)))

    val ordinary = "<project><notes>ordinary</notes><sprite x=\"0\" y=\"0\" heading=\"90\"><scripts/></sprite></project>"
    val (other, otherNative, otherBinding) = opened(ordinary, ordinary)
    otherNative.xml = Some(ordinary.replace("x=\"0\"", "x=\"12\""))
    otherBinding.publish()
    assertEquals(other.now(), Some(model(ordinary.replace("x=\"0\"", "x=\"12\""))))
  }
}

object SnapProjectBindingSpec {
  /** A native editor that normalizes opened projects like Snap does (here: an added attribute). */
  final class FakeNative extends SnapProjectBinding.Native {
    var xml: Option[String] = Some("")
    var opened = List.empty[String]
    var editing = false
    override def serialized(): Option[String] = xml
    override def open(state: ProgrammingStateRaw): Boolean =
      !editing && {
        opened = opened :+ state.code
        xml = Some(state.code.replace("<project>", "<project app=\"Snap\">"))
        true
      }
  }
}
