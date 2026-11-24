package it.evadid.vm.conversion.dependencies

import it.evadid.core.datastructures.language.AppLanguage.{Java, ProgrammingLanguage, Python}
import it.evadid.vm.code.abstractions.BeExpression
import it.evadid.vm.code.defining.{BeDependency, BeIntrinsic}
import it.evadid.vm.code.usage.BeFunctionCall
import it.evadid.vm.conversion.printing.ModuleLayout
import it.evadid.vm.conversion.structured.SourceStructure
import it.evadid.vm.naming.BeEntityName

/** The libraries printed programs may need, and how printers and parsers treat their definitions.
  *
  * A dependency is required when the program calls a function with its intrinsic. Printers add the
  * required definitions; parsers remove a definition only when it matches exactly (ignoring layout),
  * so an edited copy stays an ordinary user definition.
  */
object KnownDependencies {
  /** Python's `turtle` module; Java gets a generated class with the same static methods. */
  val turtle: BeDependency = BeDependency(BeEntityName.fromCodeString("Turtle"),
    Map(Python -> "import turtle", Java -> JavaTurtleLibrary.source), definedClasses = List(it.evadid.core.datastructures.vectorShapes.svg.TurtleCommand.turtleClass))

  /** Reading the text behind a URL; Snap's `url` reporter provides it natively. */
  val fetchText: BeDependency = BeDependency(BeEntityName.fromUniversalNameInParts("fetch_text"), Map(
    Python ->
      """import urllib.request
        |
        |def fetch_text(url: str) -> str:
        |    return urllib.request.urlopen(url).read().decode("utf-8")""".stripMargin,
    Java ->
      """class Web {
        |    // Reads the text behind a URL.
        |    static String fetchText(String url) {
        |        try {
        |            java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
        |            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder(java.net.URI.create(url)).build();
        |            return client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString()).body();
        |        } catch (java.io.IOException | InterruptedException error) {
        |            throw new IllegalStateException("Cannot read " + url, error);
        |        }
        |    }
        |}""".stripMargin))

  val system: BeDependency = BeDependency(BeEntityName.fromLiteral("SystemFunctionality"), Map.empty,
    definedClasses = List(it.evadid.vm.modules.SystemFunctionality.library))

  /** Python prints to the error stream with `print(..., file=sys.stderr)`. */
  val systemErrors: BeDependency = system.copy(addDefinitions = Map(Python -> "import sys"))

  private val byIntrinsic: Seq[(BeIntrinsic, BeDependency)] = Seq(BeIntrinsic.Turtle -> turtle, BeIntrinsic.FetchText -> fetchText, BeIntrinsic.Print -> system, BeIntrinsic.PrintErr -> systemErrors)

  /** Python modules that provide a dependency: importing one in any form (`import turtle`,
    * `from turtle import *`, `from turtle import forward`) means that library, not user code.
    */
  private val pythonModules: Map[String, BeDependency] = Map("turtle" -> turtle)

  def providedByPythonModule(module: String): Option[BeDependency] = pythonModules.get(module)

  /** Java calls a library function as a static method of the class its definition adds. */
  private val javaFunctionCalls: Map[BeIntrinsic, Seq[String]] = Map(BeIntrinsic.FetchText -> Seq("Web", "fetchText"))

  def javaCall(intrinsic: BeIntrinsic): Option[String] = javaFunctionCalls.get(intrinsic).map(_.mkString("."))

  /** The shared function name a qualified Java call to a library function stands for. */
  def functionForJavaCall(path: Seq[String]): Option[String] = javaFunctionCalls.collectFirst {
    case (intrinsic, `path`) => byIntrinsic.collectFirst { case (`intrinsic`, dependency) => dependency.structureName.universalInterpretation() }.get
  }

  val all: Seq[BeDependency] = byIntrinsic.map(_._2).distinct

  /** The dependencies a program calls into, in registry order. */
  def requiredBy(expression: BeExpression): Seq[BeDependency] = {
    val used = expression.subtree.collect { case call: BeFunctionCall => call.funcDef.intrinsic }.flatten.toSet
    byIntrinsic.collect { case (intrinsic, dependency) if used(intrinsic) => dependency }.distinct
  }

  def definitionsFor(expression: BeExpression, language: ProgrammingLanguage): Seq[String] =
    requiredBy(expression).flatMap(_.definition(language))

  private def compact(text: String): String = text.filterNot(_.isWhitespace)

  /** Where `source` contains a dependency definition verbatim, ignoring layout, as `(start, end)` offsets. */
  def definitionRanges(source: String, language: ProgrammingLanguage): Seq[(Int, Int)] = language match {
    case Java =>
      val definitions = all.flatMap(_.definition(Java)).map(compact).toSet
      SourceStructure.javaClasses(source).filter(block => definitions(compact(block.source))).map(block => block.start -> block.end)
    case Python => all.flatMap(_.definition(Python)).flatMap(lineRun(source, _))
    case _ => Nil
  }

  /** Source with its dependency definitions blanked out, keeping line breaks, so that offsets in the
    * remaining code stay valid for diagnostics and tracing. Parsers and the checked Java runner read
    * this instead of the definitions.
    */
  def maskDefinitions(source: String, language: ProgrammingLanguage): String =
    definitionRanges(source, language).foldLeft(source) { case (text, (start, end)) =>
      text.take(start) + text.slice(start, end).map(char => if char == '\n' || char == '\r' then char else ' ') + text.drop(end)
    }

  /** The first run of whole lines equal to `definition`, ignoring indentation and blank lines. */
  private def lineRun(source: String, definition: String): Option[(Int, Int)] = {
    val wanted = definition.linesIterator.map(_.trim).filter(_.nonEmpty).toVector
    val lines = (0 +: source.indices.filter(source(_) == '\n').map(_ + 1)).map { start =>
      val end = source.indexOf('\n', start)
      start -> (if end < 0 then source.length else end)
    }
    val code = lines.filter((start, end) => source.substring(start, end).trim.nonEmpty)
    code.indices.find(first => code.slice(first, first + wanted.size).map((start, end) => source.substring(start, end).trim) == wanted)
      .map(first => code(first)._1 -> math.min(source.length, code(first + wanted.size - 1)._2 + 1))
  }
}
