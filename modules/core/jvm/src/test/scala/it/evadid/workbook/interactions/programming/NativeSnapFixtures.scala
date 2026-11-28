package it.evadid.workbook.interactions.programming

import it.evadid.core.datastructures.language.AppLanguage.{Java, Python}
import it.evadid.workbook.interactions.programming.state.ProgrammingStateRaw
import it.evadid.workbook.interactions.programming.state.snap.SnapTurtleCatalog
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, StandardCopyOption}
import scala.jdk.CollectionConverters.*

/** Native TurtleStitch exports in `src/test/resources/snap-native`: the bundled editor loaded each project and saved it.
  *
  * Regenerate after changing the Snap writer or upgrading the bundled editor:
  * {{{
  * sbt 'coreJVM/Test/runMain it.evadid.workbook.interactions.programming.NativeSnapFixtures tmp/work/snap-native/inputs'
  * node tools/dev/generate-native-snap-fixtures.mjs tmp/work/snap-native/inputs modules/core/jvm/src/test/resources/snap-native
  * }}}
  */
object NativeSnapFixtures {
  val directory: Path = Path.of(sys.props("user.dir"), "modules", "core", "jvm", "src", "test", "resources", "snap-native")
  val embroideryProjects: Path = Path.of(sys.props("user.dir"), "resources", "workbookresources", "embroidery", "existingProjects")

  /** Programs written by our Snap writer; the native export of each must mean the same program. */
  val programs: List[(String, ProgrammingStateRaw)] = List(
    "py-repeat" -> ProgrammingStateRaw(Python, "for _ in range(4):\n    forward(50)\n    right(90)\n"),
    "py-variables" -> ProgrammingStateRaw(Python, "n = 3\nsize = 10\nfor _ in range(n * 2):\n    forward(size)\n    size = size + 5\n    left(360 / n)\n"),
    "py-counting" -> ProgrammingStateRaw(Python, "for i in range(1, 6):\n    forward(i * 10)\n    right(72)\nfor j in range(5, 0, -1):\n    back(j)\n"),
    "py-if-while" -> ProgrammingStateRaw(Python, "x = 0\nwhile x < 100:\n    if x % 20 == 0:\n        pen_up()\n    else:\n        pen_down()\n    forward(10)\n    x = x + 10\n"),
    "py-function" -> ProgrammingStateRaw(Python, "def square(side):\n    for _ in range(4):\n        forward(side)\n        right(90)\n\nsquare(30)\nsquare(60)\n"),
    "py-power" -> ProgrammingStateRaw(Python, "side = 2\nforward(side ** 3)\nright(90)\n"),
    "py-reporters" -> ProgrammingStateRaw(Python, "def double(x: float) -> float:\n    return x * 2\n\ndef big(n: float) -> bool:\n    return n > 10\n\ndef fact(n: float) -> float:\n    if n <= 1:\n        return 1\n    return n * fact(n - 1)\n\nif big(double(fact(3))):\n    forward(double(5))\n"),
    "py-print" -> ProgrammingStateRaw(Python, "name = \"Ada\"\nprint(\"Hello \" + name)\n"),
    "java-loop" -> ProgrammingStateRaw(Java, "public class Main {\n  public static void main(String[] args) {\n    for (int i = 0; i < 4; i++) {\n      forward(40);\n      right(90);\n    }\n  }\n}\n"))

  def read(name: String): String = Files.readString(directory.resolve(name), StandardCharsets.UTF_8)
  def all: Vector[String] = {
    val files = Files.list(directory)
    try files.iterator().asScala.map(_.getFileName.toString).filter(_.endsWith(".xml")).toVector.sorted finally files.close()
  }

  /** Writes the generator's inputs: the published embroidery projects, our writer's output for [[programs]]
    * and the editor's extra primitives.
    */
  def main(args: Array[String]): Unit = {
    val target = Path.of(args.headOption.getOrElse("tmp/work/snap-native/inputs"))
    Files.createDirectories(target)
    val published = Files.list(embroideryProjects)
    try published.iterator().asScala.filter(_.toString.endsWith(".xml")).foreach(path =>
      Files.copy(path, target.resolve(path.getFileName), StandardCopyOption.REPLACE_EXISTING)) finally published.close()
    programs.foreach { (name, program) =>
      val xml = program.toSnapScalaModel.fold(error => throw IllegalStateException(s"$name: $error"), _.toSnapXml.code)
      Files.writeString(target.resolve(s"$name.xml"), xml, StandardCharsets.UTF_8)
    }
    // The editor injects these primitives into the bundled TurtleStitch; the generator does the same.
    val extras = ujson.Arr.from(SnapTurtleCatalog.ExtraPrimitives.flatMap(primitive => primitive.extraSpec.map(spec => ujson.Obj(
      "selector" -> primitive.snapSelector, "category" -> spec.category, "spec" -> spec.spec, "defaults" -> ujson.Arr.from(spec.defaults)))))
    Files.writeString(target.resolve("extra-primitives.json"), ujson.write(extras, 2), StandardCharsets.UTF_8)
  }
}
