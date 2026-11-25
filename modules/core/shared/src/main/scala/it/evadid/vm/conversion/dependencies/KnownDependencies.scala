package it.evadid.vm.conversion.dependencies

import it.evadid.core.datastructures.language.AppLanguage.{Java, ProgrammingLanguage, Python}
import it.evadid.vm.code.abstractions.BeExpression
import it.evadid.vm.code.defining.{BeDependency, BeIntrinsic}
import it.evadid.vm.code.usage.BeFunctionCall
import it.evadid.vm.conversion.printing.ModuleLayout
import it.evadid.vm.naming.BeEntityName

/** The libraries printed programs may need, and how printers and parsers treat their definitions.
  *
  * A dependency is required when the program calls a function with its intrinsic. Printers add the
  * required definitions; the parsing pipeline's first step masks a definition only when it matches exactly
  * (ignoring layout), so an edited copy stays an ordinary user definition.
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


  def named(name: BeEntityName): Option[BeDependency] = all.find(_.structureName == name)

  /** Whether `classSource` is a dependency's Java definition verbatim, ignoring layout. */
  def isJavaDefinition(classSource: String): Boolean = javaDefinitions(it.evadid.vm.parsing.source.SourceText.compact(classSource))

  private lazy val javaDefinitions: Set[String] = all.flatMap(_.definition(Java)).map(it.evadid.vm.parsing.source.SourceText.compact).toSet
}
