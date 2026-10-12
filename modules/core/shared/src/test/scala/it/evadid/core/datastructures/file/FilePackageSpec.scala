package it.evadid.core.datastructures.file

import FileDescription.*
import CopyrightInfo.*
import munit.FunSuite
import scala.concurrent.Future
import upickle.default.*

class FilePackageSpec extends FunSuite {
  test("URL parsing preserves all directories and separates protocol, host, port and filename") {
    val path = parsePathStructure("  https://files.example.org:8443/a/b/report.final.pdf?download=1#page2  ")
    assertEquals(path.protocol, Some("https"))
    assertEquals(path.domainElements, List("files", "example", "org"))
    assertEquals(path.port, Some(8443))
    assertEquals(path.dirElements, List("a", "b"))
    assertEquals(path.filenameWithoutExtension, "report.final")
    assertEquals(path.extension, Some("pdf"))
    assertEquals(path.filenameWithExtension, "report.final.pdf")
  }
  test("local paths keep their first directory and support Windows separators") {
    assertEquals(parsePathStructure("a/b/file.txt").dirElements, List("a", "b"))
    assertEquals(parsePathStructure("/a/b/file.txt").dirElements, List("a", "b"))
    assertEquals(parsePathStructure("C:\\a\\b\\file.txt").dirElements, List("C:", "a", "b"))
    assertEquals(parsePathStructure("a/file.txt").domainElements, Nil)
  }
  test("empty, directory, hidden and extensionless names parse without exceptions") {
    for (path <- List("", " ", "/", "https://example.org/")) assertEquals(parsePathStructure(path).filenameWithExtension, "")
    assertEquals(parsePathStructure("a/b/").dirElements, List("a", "b"))
    assertEquals(parsePathStructure(".gitignore").nameStructure, FilenameStructure(".gitignore", None))
    assertEquals(parsePathStructure("README").nameStructure, FilenameStructure("README", None))
  }
  test("filename and path values have default codecs and consistent extension helpers") {
    val filename = FilenameStructure("report", Some("pdf"))
    assertEquals(filename.extensionWithPointOrEmpty, ".pdf")
    assertEquals(filename.extensionOrEmpty, "pdf")
    assertEquals(FilenameStructure("README", None).extensionWithPointOrEmpty, "")
    val path = PathStructure(LocationStructure(Some("https"), List("example", "org"), Some(443), List("a")), filename)
    assertEquals(read[PathStructure](write(path)), path)
    assertEquals(read[FilenameStructure](write(filename)), filename)
    assertEquals(read[LocationStructure](write(path.locationStructure)), path.locationStructure)
  }
  test("copyright codecs preserve each licence and author variant") {
    for (licence <- List[LicenceInfo](UnknownLicence, CC_LICENCE); author <- List[AuthorInfo](UnknownAuthorInfo(), AuthorNameInfo("Ada"))) {
      val copyright = CopyrightInfo(licence, author)
      assertEquals(read[CopyrightInfo](write(copyright)), copyright)
      assertEquals(read[LicenceInfo](write(licence)), licence)
      assertEquals(read[AuthorInfo](write(author)), author)
    }
    assertEquals(plattformCopyrightInfo("Ada").authorInfo.getName, Some("Ada"))
    assertEquals(unknownCopyrightInfo.authorInfo.getName, None)
  }
  test("loaded files decode UTF-8 and retain their description and bytes") {
    val description = new FileDescription {
      def copyrightInfo = unknownCopyrightInfo
      def asUrlString = "hello.txt"
      def loadData(): Future[LoadedFile] = Future.successful(LoadedFile(this, "Grüße 🌳".getBytes("UTF-8")))
      def getChildrenFile(name: String, copyright: CopyrightInfo): Option[FileDescription] = None
    }
    assertEquals(description.filenameWithExtension, "hello.txt")
    assert(!description.isDirectory)
    import scala.concurrent.ExecutionContext.Implicits.global
    description.loadData().map { loaded =>
      assertEquals(loaded.fileDataAsUtf8String, "Grüße 🌳")
      assertEquals(loaded.description, description)
      assert(loaded.toString.contains(s"${loaded.data.length} bytes"))
    }
  }
}
