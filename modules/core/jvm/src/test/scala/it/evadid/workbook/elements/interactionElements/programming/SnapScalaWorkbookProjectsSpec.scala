package it.evadid.workbook.elements.interactionElements.programming

import java.nio.file.{Files, Path}
import java.nio.charset.StandardCharsets
import scala.jdk.CollectionConverters.*
import munit.FunSuite
import upickle.default.*
import it.evadid.workbook.elements.interactionElements.programming.state.*

class SnapScalaWorkbookProjectsSpec extends FunSuite {
  test("published projects retain full XML while answer history excludes generated images") {
    val directory = Path.of(sys.props("user.dir"), "resources", "workbookresources", "embroidery", "existingProjects")
    val files = Files.list(directory)
    val projects = try files.iterator().asScala.filter(_.toString.endsWith(".xml")).toVector finally files.close()
    assert(projects.nonEmpty, "The published Snap project fixtures must be present.")
    projects.foreach { path =>
      val xml = Files.readString(path, StandardCharsets.UTF_8)
      val state = ProgrammingStateSnapXml(xml)
      val typed = state.toSnapScalaModel.fold(message => fail(s"$path: $message"), identity)
      assertEquals(typed.toSnapXml, state, path.toString)
      assert(read[it.evadid.workbook.elements.interactionElements.programming.state.snap.SnapScalaModel.Document](write(typed.model)).toXml == xml, path.toString)
      val saved = read[ProgrammingStateSnapScalaModel](write(typed))
      assert(saved.toSnapXml == state.removeBloatFromXml, path.toString)
      assert(typed.model.project.get.scenes.nonEmpty, path.toString)
    }
  }
}
