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

  test("data URLs are nameless, never parse their payload as a path and declare their RFC 2397 media type") {
    val png = "data:image/png;base64,iVBORw0KGgo/AAA+b.c/d.e"
    assertEquals(parsePathStructure(png), PathStructure(LocationStructure(Some("data"), Nil, None, Nil), FilenameStructure("", None)))
    assertEquals(parsePathStructure("  DATA:text/csv,a/b.c").filenameWithExtension, "")
    assertEquals(dataUrlMediaType(png), Some("image/png"))
    assertEquals(dataUrlMediaType(" Data:text/plain; charset=UTF-8 ;base64,SGk="), Some("text/plain;charset=UTF-8"))
    assertEquals(dataUrlMediaType("data:image/svg+xml;charset=utf-8,%3Csvg%2F%3E"), Some("image/svg+xml;charset=utf-8"))
    assertEquals(dataUrlMediaType("data:,Hello"), Some("text/plain;charset=US-ASCII"))
    assertEquals(dataUrlMediaType("data:;base64,SGk="), Some("text/plain;charset=US-ASCII"))
    assertEquals(dataUrlMediaType("data:;charset=utf-8,Hi"), Some("text/plain;charset=utf-8"))
    for (other <- List("https://example.org/data:x", "report.pdf", "", "dat:a")) assertEquals(dataUrlMediaType(other), None)
  }

  test("a data URL description is a nameless file rather than a directory; other locations declare no media type") {
    def description(url: String): FileDescription = new FileDescription {
      def copyrightInfo = unknownCopyrightInfo
      def asUrlString = url
      def loadData(): Future[LoadedFile] = Future.failed(UnsupportedOperationException())
      def getChildrenFile(name: String, copyright: CopyrightInfo): Option[FileDescription] = None
    }
    val inline = description("data:application/pdf;base64,JVBERg==")
    assertEquals(inline.mediaType, Some("application/pdf"))
    assertEquals(inline.filenameWithExtension, "")
    assert(!inline.isDirectory)
    assertEquals(description("https://example.org/assets").mediaType, None)
    assert(description("https://example.org/assets").isDirectory)
    assert(!description("assets/logo.png").isDirectory)
  }

  test("a named data file describes and loads itself with its declared media type") {
    val bytes = Array[Byte](1, 2, 3)
    val file = NamedDataFileDescription("dir/report.pdf", bytes, CopyrightInfo.fromAi, Some("application/pdf"))
    assert(file.description eq file)
    assertEquals(file.filenameWithExtension, "report.pdf")
    assertEquals(file.asUrlString, "dir/report.pdf")
    assertEquals(file.mediaType, Some("application/pdf"))
    assertEquals(file.copyrightInfo, CopyrightInfo.fromAi)
    assertEquals(file.getChildrenFile("child"), None)
    assertEquals(file.toString, "NamedDataFileDescription(dir/report.pdf: 3 bytes)")
    val nameless = NamedDataFileDescription("", bytes, unknownCopyrightInfo, Some("image/png"))
    assert(!nameless.isDirectory)
    assertEquals(nameless.mediaType, Some("image/png"))
    import scala.concurrent.ExecutionContext.Implicits.global
    file.loadData().map(loaded => assert(loaded eq file))
  }
}
