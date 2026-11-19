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
    Map(Python -> "import turtle", Java -> JavaTurtleLibrary.source))

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

  private val byIntrinsic: Seq[(BeIntrinsic, BeDependency)] = Seq(BeIntrinsic.Turtle -> turtle, BeIntrinsic.FetchText -> fetchText)

  val all: Seq[BeDependency] = byIntrinsic.map(_._2)

  /** The dependencies a program calls into, in registry order. */
  def requiredBy(expression: BeExpression): Seq[BeDependency] = {
    val used = ModuleLayout.nodes(expression).collect { case call: BeFunctionCall => call.funcDef.intrinsic }.flatten.toSet
    byIntrinsic.collect { case (intrinsic, dependency) if used(intrinsic) => dependency }
  }

  def definitionsFor(expression: BeExpression, language: ProgrammingLanguage): Seq[String] =
    requiredBy(expression).flatMap(_.definition(language))

  private def compact(text: String): String = text.filterNot(_.isWhitespace)

  /** Source without the dependency definitions it contains verbatim, ignoring layout. */
  def withoutDefinitions(source: String, language: ProgrammingLanguage): String = language match {
    case Java =>
      val definitions = all.flatMap(_.definition(Java)).map(compact).toSet
      val matches = SourceStructure.javaClasses(source).filter(block => definitions(compact(block.source))).map(block => block.start -> block.end)
      if matches.isEmpty then source else SourceStructure.remove(source, matches)
    case Python =>
      all.flatMap(_.definition(Python)).foldLeft(source)(withoutLines)
    case _ => source
  }

  /** Removes the first run of lines equal to `definition`, ignoring indentation and blank lines. */
  private def withoutLines(source: String, definition: String): String = {
    val wanted = definition.linesIterator.map(_.trim).filter(_.nonEmpty).toVector
    val lines = source.split("\n", -1).toVector
    val code = lines.indices.filter(index => lines(index).trim.nonEmpty).toVector
    code.indices.find(start => code.slice(start, start + wanted.size).map(index => lines(index).trim) == wanted) match {
      case Some(start) =>
        val (first, last) = (code(start), code(start + wanted.size - 1))
        (lines.take(first) ++ lines.drop(last + 1)).mkString("\n")
      case None => source
    }
  }
}
