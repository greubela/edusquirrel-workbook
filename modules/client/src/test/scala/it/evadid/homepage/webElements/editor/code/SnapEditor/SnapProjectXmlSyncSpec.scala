package it.evadid.homepage.webElements.editor.code.SnapEditor

import munit.FunSuite
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingStateJavaString

class SnapProjectXmlSyncSpec extends FunSuite {
  private def xml(code: String, image: String): String =
    s"<project><thumbnail>$image</thumbnail><stage><pentrails>$image</pentrails><scripts>$code</scripts></stage></project>"
  private def clean(code: String) = s"<project><stage><scripts>$code</scripts></stage></project>"

  test("initial snapshots and load comparisons ignore regenerated image data") {
    val sync = new SnapProjectXmlSync
    sync.markLoaded(xml("program", "first"))
    sync.resetSnapshot(xml("program", "second"))
    assert(sync.isLoaded(clean("program")))
    assert(sync.isLoaded(xml("program", "third")))
    for (image <- 1 to 20) assertEquals(sync.changedSnapshot(xml("program", image.toString)), None)
  }

  test("real edits publish a cleaned snapshot once and its acknowledgment suppresses reloads") {
    val sync = new SnapProjectXmlSync
    sync.markLoaded(clean("old"))
    sync.resetSnapshot(xml("old", "image"))
    assert(!sync.isLoaded(clean("new")))
    val changed = sync.changedSnapshot(xml("new", "large image")).get
    assertEquals(changed, clean("new"))
    sync.markLoaded(changed)
    for (image <- 1 to 20) {
      assert(sync.isLoaded(xml("new", image.toString)))
      assertEquals(sync.changedSnapshot(xml("new", image.toString)), None)
    }
  }

  test("external restores establish a fresh baseline and layout edits are retained") {
    val sync = new SnapProjectXmlSync
    sync.markLoaded(clean("old"))
    val restored = xml("<script x=\"1\"/>", "first")
    assert(!sync.isLoaded(restored))
    sync.markLoaded(restored)
    sync.resetSnapshot(xml("<script x=\"1\"/>", "regenerated"))
    assertEquals(sync.changedSnapshot(restored), None)
    assertEquals(sync.changedSnapshot(xml("<script x=\"2\"/>", "regenerated")), Some(clean("<script x=\"2\"/>")))
  }

  test("clearing a destroyed session forgets both load and snapshot baselines") {
    val sync = new SnapProjectXmlSync
    sync.markLoaded(clean("program"))
    sync.resetSnapshot(clean("program"))
    sync.clear()
    assert(!sync.isLoaded(clean("program")))
    assertEquals(sync.changedSnapshot(xml("program", "image")), Some(clean("program")))
  }

  test("running a typed Java project does not publish its sprite pose as a source edit") {
    val source = ProgrammingStateJavaString("class Drawing { public static void main(String[] args) { Turtle.forward(12); } }").toSnapXml.snapXml
    val moved = source.replace("x=\"0\" y=\"0\" heading=\"90\"", "x=\"12\" y=\"3\" heading=\"180\"")
    assert(moved != source)
    val sync = new SnapProjectXmlSync
    sync.markLoaded(source)
    sync.resetSnapshot(source)
    assertEquals(sync.changedSnapshot(moved), None)
    assert(sync.isLoaded(source))
    assert(!sync.isLoaded(moved))
    val edited = moved.replace("<l>12</l>", "<l>23</l>")
    assert(edited != moved)
    assertEquals(sync.changedSnapshot(edited), Some(edited))
    assertEquals(sync.changedSnapshot(edited), None)
    val layout = edited.replace("x=\"156\" y=\"66\"", "x=\"160\" y=\"70\"")
    assert(layout != edited)
    assertEquals(sync.changedSnapshot(layout), Some(layout))
  }

  test("ordinary Snap projects continue to retain sprite pose changes") {
    val source = "<project><notes>ordinary</notes><sprite x=\"0\" y=\"0\" heading=\"90\"><scripts/></sprite></project>"
    val moved = source.replace("x=\"0\"", "x=\"12\"")
    val sync = new SnapProjectXmlSync
    sync.resetSnapshot(source)
    assertEquals(sync.changedSnapshot(moved), Some(moved))
  }
}
