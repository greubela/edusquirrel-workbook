package it.evadid.vm.conversion.dependencies

import it.evadid.core.datastructures.language.AppLanguage.{Java, ProgrammingLanguage, Python}
import it.evadid.vm.code.abstractions.BeExpression
import it.evadid.vm.code.defining.{BeDependency, BeIntrinsic}
import it.evadid.vm.code.usage.{BeFunctionCall, BeUseValue}
import it.evadid.vm.types.BeDataValueLiteral
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

  /** Python imports `sqrt` and `random`; Java has them in `java.lang.Math`, Snap as reporters. */
  val squareRoot: BeDependency = BeDependency(BeEntityName.fromUniversalNameInParts("sqrt"), Map(Python -> "from math import sqrt"))
  val random: BeDependency = BeDependency(BeEntityName.fromUniversalNameInParts("random"), Map(Python -> "from random import random"))

  val arithmeticMethods: Map[String, String] = Map("modulo" -> "%", "signedRemainder" -> "fmod", "divide" -> "/", "floorDivide" -> "//")
  def javaArithmeticMethod(call: BeFunctionCall): Option[String] = {
    val symbol = call.funcDef.functionTypeInfo.displayName.universalInterpretation()
    val hasNonzeroLiteral = call.arguments.lift(1).exists {
      case BeUseValue(BeDataValueLiteral(raw), _) => raw.toDoubleOption.exists(value => value.isFinite && value != 0)
      case _ => false
    }
    Option.unless(symbol == "/" && hasNonzeroLiteral)(arithmeticMethods.collectFirst { case (method, `symbol`) => method }).flatten
  }
  val ieeeDivide: BeDependency = BeDependency(BeEntityName.fromCodeString("ieeeDivide"), Map(Python ->
    """def ieee_divide(value, divisor):
      |    import math
      |    if divisor != 0:
      |        return value / divisor
      |    if value == 0 or math.isnan(value):
      |        return math.nan
      |    return math.copysign(math.inf, math.copysign(1.0, value) * math.copysign(1.0, divisor))""".stripMargin))

  val signedRemainderImport: BeDependency = BeDependency(BeEntityName.fromCodeString("math"), Map(Python -> "import math"))

  val modulo: BeDependency = BeDependency(BeEntityName.fromCodeString("WorkbookMath"), Map(Java ->
    """class WorkbookMath {
      |    static double divide(double value, double divisor) {
      |        if (divisor == 0) throw new ArithmeticException("Division by zero.");
      |        return value / divisor;
      |    }
      |    static double floorDivide(double value, double divisor) {
      |        double rest = signedRemainder(value, divisor);
      |        double quotient = (value - rest) / divisor;
      |        if (rest != 0 && (rest < 0) != (divisor < 0)) quotient--;
      |        if (quotient == 0) return Math.copySign(0.0, value / divisor);
      |        double floor = Math.floor(quotient);
      |        return quotient - floor > 0.5 ? floor + 1 : floor;
      |    }
      |    static double signedRemainder(double value, double divisor) {
      |        if (divisor == 0) throw new ArithmeticException("Remainder by zero.");
      |        return value % divisor;
      |    }
      |    static double modulo(double value, double divisor) {
      |        if (divisor == 0) throw new ArithmeticException("Modulo by zero.");
      |        double rest = value % divisor;
      |        if (rest == 0) return Math.copySign(0.0, divisor);
      |        return (rest < 0) == (divisor < 0) ? rest : rest + divisor;
      |    }
      |}""".stripMargin))

  private val byIntrinsic: Seq[(BeIntrinsic, BeDependency)] = Seq(BeIntrinsic.Turtle -> turtle, BeIntrinsic.FetchText -> fetchText,
    BeIntrinsic.Print -> system, BeIntrinsic.PrintErr -> systemErrors, BeIntrinsic.SquareRoot -> squareRoot, BeIntrinsic.Random -> random)

  /** Python modules that provide a dependency: importing one in any form (`import turtle`,
    * `from turtle import *`, `from turtle import forward`) means that library, not user code.
    */
  private val pythonModules: Map[String, BeDependency] = Map("turtle" -> turtle, "math" -> squareRoot, "random" -> random)

  def providedByPythonModule(module: String): Option[BeDependency] = pythonModules.get(module)

  /** Java calls a library function as a static method of the class its definition adds. */
  private val javaFunctionCalls: Map[BeIntrinsic, Seq[String]] = Map(BeIntrinsic.FetchText -> Seq("Web", "fetchText"))

  def javaCall(intrinsic: BeIntrinsic): Option[String] = javaFunctionCalls.get(intrinsic).map(_.mkString("."))

  /** The shared function name a qualified Java call to a library function stands for. */
  def functionForJavaCall(path: Seq[String]): Option[String] = javaFunctionCalls.collectFirst {
    case (intrinsic, `path`) => byIntrinsic.collectFirst { case (`intrinsic`, dependency) => dependency.structureName.universalInterpretation() }.get
  }

  val all: Seq[BeDependency] = byIntrinsic.map(_._2).distinct ++ Seq(modulo, ieeeDivide)

  /** The dependencies a program calls into, in registry order. */
  def requiredBy(expression: BeExpression): Seq[BeDependency] = {
    val calls = expression.subtree.collect { case call: BeFunctionCall => call }
    val used = calls.flatMap(_.funcDef.intrinsic).toSet
    val operators = calls.filter(_.isOperator).map(_.funcDef.functionTypeInfo.displayName.universalInterpretation()).toSet
    byIntrinsic.collect { case (intrinsic, dependency) if used(intrinsic) => dependency }.distinct ++
      Option.when(calls.exists(call => call.isOperator && javaArithmeticMethod(call).isDefined))(modulo) ++ Option.when(operators("fmod"))(signedRemainderImport) ++
      Option.when(operators("quotient"))(ieeeDivide)
  }

  def definitionsFor(expression: BeExpression, language: ProgrammingLanguage): Seq[String] =
    requiredBy(expression).flatMap(_.definition(language))


  def named(name: BeEntityName): Option[BeDependency] = all.find(_.structureName == name)

  /** Whether `classSource` is a dependency's Java definition verbatim, ignoring layout. */
  def isJavaDefinition(classSource: String): Boolean = javaDefinitions(it.evadid.vm.parsing.source.SourceText.compact(classSource))

  private lazy val javaDefinitions: Set[String] = all.flatMap(_.definition(Java)).map(it.evadid.vm.parsing.source.SourceText.compact).toSet
}
