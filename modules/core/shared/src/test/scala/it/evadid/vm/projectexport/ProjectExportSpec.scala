package it.evadid.vm.projectexport

import it.evadid.core.util.InfoUtil

import it.evadid.core.datastructures.language.AppLanguage.{Java, Python, SnapLanguage, Rust}
import it.evadid.vm.simulation.data.{DataWorld, SudokuGrid}
import it.evadid.workbook.interactions.programming.state.{ProgrammingStateJavaStructured, ProgrammingStatePythonStructured, ProgrammingStateRaw}
import munit.FunSuite

import java.nio.charset.StandardCharsets.UTF_8
import java.time.Instant

class ProjectExportSpec extends FunSuite {
  /** Reads an archive back through its central directory, checking every local header against it. */
  private def unzip(bytes: Array[Byte]): Seq[(String, String)] = {
    def u16(at: Int) = (bytes(at) & 0xFF) | ((bytes(at + 1) & 0xFF) << 8)
    def u32(at: Int) = u16(at) | (u16(at + 2) << 16)
    val end = bytes.length - 22
    assertEquals(u32(end), 0x06054b50, "end of central directory")
    val count = u16(end + 10)
    assertEquals(u16(end + 8), count)
    val directoryOffset = u32(end + 16)
    assertEquals(directoryOffset + u32(end + 12), end, "directory ends where the end record starts")
    (0 until count).foldLeft((directoryOffset, Vector.empty[(String, String)])) { case ((at, files), _) =>
      assertEquals(u32(at), 0x02014b50, "central header")
      assertEquals(u16(at + 8) & 0x0800, 0x0800, "UTF-8 names")
      val (crc, size, nameLength, local) = (u32(at + 16), u32(at + 24), u16(at + 28), u32(at + 42))
      val name = new String(bytes.slice(at + 46, at + 46 + nameLength), UTF_8)
      assertEquals(u32(local), 0x04034b50, s"local header of $name")
      assertEquals((u32(local + 14), u32(local + 22), u16(local + 26)), (crc, size, nameLength), name)
      val dataStart = local + 30 + nameLength
      val data = bytes.slice(dataStart, dataStart + size)
      assertEquals(ZipArchive.crc32(data), crc, s"checksum of $name")
      (at + 46 + nameLength, files :+ (name -> new String(data, UTF_8)))
    }._2
  }

  test("CRC-32 matches the standard check value and archives read back entry by entry") {
    assertEquals(ZipArchive.crc32("123456789".getBytes(UTF_8)), 0xCBF43926)
    assertEquals(ZipArchive.crc32(Array.emptyByteArray), 0)
    val archive = ZipArchive.write(Seq(ZipArchive.Entry("p/", Array.emptyByteArray), ZipArchive.Entry("p/Grüße.txt", "äöü\n".getBytes(UTF_8))))
    assertEquals(unzip(archive), Seq("p/" -> "", "p/Grüße.txt" -> "äöü\n"))
    intercept[IllegalArgumentException](ZipArchive.write(Seq(ZipArchive.Entry("a", Array(1)), ZipArchive.Entry("a", Array(2)))))
    intercept[IllegalArgumentException](ZipArchive.Entry("../escape", Array.emptyByteArray))
    intercept[IllegalArgumentException](ZipArchive.Entry("/absolute", Array.emptyByteArray))
  }

  test("archives store the modification time and are reproducible") {
    val entries = Seq(ZipArchive.Entry("a.txt", "a".getBytes(UTF_8)))
    val time = it.evadid.core.util.LocalZone.instantOf(java.time.LocalDateTime.of(2026, 11, 20, 13, 45, 30))
    val bytes = ZipArchive.write(entries, time)
    def u16(at: Int) = (bytes(at) & 0xFF) | ((bytes(at + 1) & 0xFF) << 8)
    assertEquals(u16(10), (13 << 11) | (45 << 5) | 15)
    assertEquals(u16(12), ((2026 - 1980) << 9) | (11 << 5) | 20)
    assertEquals(ZipArchive.write(entries, time).toSeq, bytes.toSeq)
    val early = ZipArchive.write(entries, java.time.LocalDateTime.of(1970, 1, 1, 0, 0).toInstant(java.time.ZoneOffset.UTC))
    assertEquals((early(12) & 0xFF) | ((early(13) & 0xFF) << 8), (1 << 5) | 1, "dates before 1980 become 1980-01-01")
  }

  test("project folders are portable and the archive unpacks into one folder") {
    assertEquals(InfoUtil.fileNameSafe("Sudoku Löser (GdP1)"), "Sudoku_Loeser_GdP1")
    assertEquals(InfoUtil.fileNameSafe("  ?? "), "project")
    assertEquals(InfoUtil.fileNameSafe("Æble/../x"), "Aeble_x")
    val project = ExportedProject("demo", Seq(ProjectFile("src/A.java", "class A {}"), ProjectFile("README.TXT", "hi")))
    assertEquals(project.fileName, "demo.zip")
    assertEquals(unzip(project.zip()).map(_._1), Seq("demo/", "demo/src/", "demo/src/A.java", "demo/README.TXT"))
    intercept[IllegalArgumentException](ProjectFile("../A.java", ""))
    intercept[IllegalArgumentException](ExportedProject("a/b", Nil))
  }

  private val multiClass = ProgrammingStateRaw(Java,
    """package workbook;
      |import java.util.List;
      |import java.util.ArrayList;
      |
      |public class Main {
      |    public static void main(String[] args) {
      |        List<Hund> hunde = new ArrayList<>();
      |        hunde.add(new Hund("Bello"));
      |    }
      |}
      |
      |// Ein Hund mit Namen.
      |class Hund {
      |    private final String name;
      |    Hund(String name) { this.name = name; }
      |}
      |""".stripMargin)

  private def files(project: ExportedProject): Map[String, String] = project.files.map(file => file.path -> file.content).toMap

  test("every class gets its own file with all imports, without a package declaration") {
    val project = JavaProjectExport.blueJ(multiClass, "Hunde").toOption.get
    val byPath = files(project)
    assertEquals(project.folder, "Hunde")
    assertEquals(project.files.map(_.path).toSet, Set("Main.java", "Hund.java", "package.bluej", "README.TXT"))
    Seq("Main.java", "Hund.java").foreach { path =>
      assert(byPath(path).startsWith("import java.util.List;\nimport java.util.ArrayList;"), byPath(path))
      assert(!byPath(path).contains("package workbook"), path)
    }
    assert(byPath("Main.java").contains("public class Main {"))
    assert(byPath("Hund.java").contains("class Hund {"), byPath("Hund.java"))
    // The structured Java state keeps comments between classes in the first class's preamble; they are not lost.
    assert(byPath("Main.java").contains("// Ein Hund mit Namen."), byPath("Main.java"))
    assert(!byPath("Main.java").contains("class Hund"), "classes are split, not repeated")
  }

  test("package.bluej lists every class as a target and README names the project") {
    val project = JavaProjectExport.blueJ(multiClass, "Hunde").toOption.get
    val bluej = files(project)("package.bluej")
    assert(bluej.startsWith("#BlueJ package file\n"))
    assert(bluej.contains("package.numTargets=2\n") && bluej.contains("project.charset=UTF-8"))
    assert(bluej.contains("target1.name=Main\ntarget1.type=ClassTarget") && bluej.contains("target2.name=Hund"), bluej)
    assertEquals(bluej.linesIterator.count(_.matches("""target\d+\.x=\d+""")), 2)
    assert(files(project)("README.TXT").contains("PROJECT TITLE: Hunde"))
    assert(!files(project)("README.TXT").contains("LIBRARIES"))
  }

  test("library classes are added for the libraries a program calls, with the exercise's data") {
    val sudoku = ProgrammingStateJavaStructured("public class Main {\n    public static void main(String[] args) {\n        Sudoku.set(0, 2, 4);\n    }\n}")
    val grid = SudokuGrid.parse("53.......", ".........", ".........", ".........", ".........", ".........", ".........", ".........", "........9")
    val withSudoku = files(JavaProjectExport.blueJ(sudoku, "s", DataWorld(sudoku = grid)).toOption.get)
    assert(withSudoku("Sudoku.java").contains("public class Sudoku {"))
    assert(withSudoku("Sudoku.java").contains("{5, 3, 0, 0, 0, 0, 0, 0, 0}"), withSudoku("Sudoku.java"))
    assert(withSudoku("README.TXT").contains("LIBRARIES: Sudoku"))
    assert(!withSudoku.contains("NumberList.java") && !withSudoku.contains("Turtle.java"))
    val list = ProgrammingStateRaw(Java, "public class Main {\n    public static void main(String[] args) {\n        NumberList.swap(0, 1);\n    }\n}")
    assert(files(JavaProjectExport.blueJ(list, "l", DataWorld(list = Vector(4, 2))).toOption.get)("NumberList.java").contains("{4, 2}"))
  }

  test("the turtle class answers to the spellings the program uses, and user classes win over libraries") {
    val turtle = ProgrammingStateRaw(Java, "public class Main {\n    public static void main(String[] args) {\n        Turtle.forward(100);\n        Turtle.right(90);\n        Turtle.fd(5);\n    }\n}")
    val library = files(JavaProjectExport.blueJ(turtle, "t").toOption.get)("Turtle.java")
    assert(library.contains("public static void right(double angle) {\n        turnRight(angle);"), library)
    assert(library.contains("public static void fd(double distance) {\n        forward(distance);"), library)
    assert(!library.contains("public static void left("), "only the spellings the program calls")
    val own = ProgrammingStateRaw(Java, "public class Main {\n    public static void main(String[] args) {\n        NumberList.length();\n    }\n}\n\nclass NumberList {\n    static int length() { return 0; }\n}")
    val exported = files(JavaProjectExport.blueJ(own, "own").toOption.get)
    assert(exported("NumberList.java").contains("return 0;"), "the student's own class is kept")
    assertEquals(exported.keySet.count(_.endsWith(".java")), 2)
  }

  private val pythonSort = ProgrammingStatePythonStructured(
    "def sortiere():\n    n = NumberList.length()\n    for i in range(n):\n        for j in range(n - 1 - i):\n            if NumberList.get(j) > NumberList.get(j + 1):\n                NumberList.swap(j, j + 1)\n\nsortiere()\n")

  test("Python programs export as main.py with their library classes, and convert to BlueJ projects") {
    val python = ProjectExport.forLanguage(pythonSort, Python, "Sortieren", DataWorld(list = Vector(3, 1))).toOption.get
    assertEquals(python.files.map(_.path), Seq("main.py"))
    val main = python.files.head.content
    assert(main.startsWith("class NumberList:") && main.contains("values = [3, 1]") && main.contains("def sortiere():"), main)
    val java = files(ProjectExport.forLanguage(pythonSort, Java, "Sortieren", DataWorld(list = Vector(3, 1))).toOption.get)
    assert(java.contains("Main.java") && java("NumberList.java").contains("{3, 1}"), java.keySet)
  }

  test("Snap exports the project XML; unfinished Java still exports; unsupported languages explain the problem") {
    val snap = ProjectExport.forLanguage(ProgrammingStateRaw(Python, "forward(10)\n"), SnapLanguage, "Zeichnung").toOption.get
    assertEquals(snap.files.map(_.path), Seq("Zeichnung.xml"))
    assert(snap.files.head.content.contains("<project"))
    assert(ProjectExport.forLanguage(pythonSort, Rust, "x").left.exists(_.contains("not supported")))
    // Unfinished code still exports, so the student can finish it in the IDE.
    val broken = JavaProjectExport.blueJ(ProgrammingStateRaw(Java, "public class Main { void broken( }"), "x")
    assert(broken.exists(project => files(project)("Main.java").contains("void broken(")), broken)
    assert(JavaProjectExport.blueJ(ProgrammingStateRaw(Java, ""), "x").exists(project => files(project).contains("Main.java")),
      "an empty program becomes an empty Main class")
  }
}
