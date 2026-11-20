package it.evadid.workbook.elements.interactionElements.programming.state

import it.evadid.workbook.elements.interactionElements.programming.state.snap.SnapScalaModel

import it.evadid.vm.conversion.expressionsnap.SnapProjectXml

import it.evadid.core.datastructures.language.AppLanguage.{BlockDisplay, Java, Python, SnapLanguage, ProgrammingLanguage}
import it.evadid.vm.simulation.turtle.{TurtleProgramAnalysis, TurtleCommandExecutionInfo}
import it.evadid.vm.code.abstractions.BeExpression
import it.evadid.workbook.elements.interactionElements.programming.*
import it.evadid.workbook.elements.interactionElements.programming.state.snap.SnapGeneratedImages
import it.evadid.vm.conversion.snapmodelxml.SnapModelToXml.*
import it.evadid.distribution.clients.ExecutionClient
import it.evadid.distribution.command.ExecutionCommand
import it.evadid.distribution.commandTypes.WorkerCommands
import it.evadid.distribution.commandTypes.WorkerCommands.RunPythonResult
import it.evadid.util.logging.BasicLogger
import scala.concurrent.{ExecutionContext, Future}
import upickle.default.*
import it.evadid.vm.naming.BeEntityName
import it.evadid.vm.conversion.structured.{SourceStructure, StructuredProgramConversions}

/** The source representation currently edited by a programming exercise. */
sealed trait ProgrammingState {
  lazy val derivations: it.evadid.vm.conversion.ProgrammingStateDerivations =
    new it.evadid.vm.conversion.ProgrammingStateDerivations(this)
  def convertToLanguage(language: it.evadid.core.datastructures.language.AppLanguage.ProgrammingLanguage): it.evadid.vm.conversion.ConversionResult = derivations.convertToLanguage(language)
  def toBeExpression: ProgrammingStateBeExpression = derivations.expression
  def toJava: ProgrammingStateRaw = derivations.java
  def toPython: ProgrammingStateRaw = derivations.python
  def toSnapXml: ProgrammingStateRaw = derivations.snap
  def toJavaStructured: ProgrammingStateJavaStructured = derivations.javaStructured
  def toPythonStructured: ProgrammingStatePythonStructured = derivations.pythonStructured

  def associatedLanguage: ProgrammingLanguage = this match {
    case ProgrammingStateRaw(language, _) => language
    case _: ProgrammingStateSnapScalaModel => SnapLanguage
    case _: ProgrammingStateJavaStructured => Java
    case _: ProgrammingStatePythonStructured => Python
    case _: ProgrammingStateBeExpression => BlockDisplay
  }

  def fingerprint(): String = this match {
    case ProgrammingStateBeExpression(expression) => s"expression:${expression.toString}"
    case ProgrammingStateRaw(language, code) => s"${language.name.toLowerCase}:$code"
    case snap: ProgrammingStateSnapScalaModel => s"snap:${snap.model.toXml}"
    case state: ProgrammingStateJavaStructured => s"java:${state.toString}"
    case state: ProgrammingStatePythonStructured => s"python:${state.toString}"
  }
}

object ProgrammingState {

  // One current wire schema, derived from typed payloads. No historical tags or migration paths.
  private enum Payload derives ReadWriter {
    case Raw(value: ProgrammingStateRaw)
    case SnapModel(value: ProgrammingStateSnapScalaModel)
    case JavaStructured(value: ProgrammingStateJavaStructured)
    case PythonStructured(value: ProgrammingStatePythonStructured)
    case Expression(value: ProgrammingStateBeExpression)
  }
  given ReadWriter[ProgrammingState] =
    readwriter[Payload].bimap({
      case value: ProgrammingStateRaw => Payload.Raw(value)
      case value: ProgrammingStateSnapScalaModel => Payload.SnapModel(value)
      case value: ProgrammingStateJavaStructured => Payload.JavaStructured(value)
      case value: ProgrammingStatePythonStructured => Payload.PythonStructured(value)
      case value: ProgrammingStateBeExpression => Payload.Expression(value)
    }, {
      case Payload.Raw(value) => value
      case Payload.SnapModel(value) => value
      case Payload.JavaStructured(value) => value
      case Payload.PythonStructured(value) => value
      case Payload.Expression(value) => value
    })

  lazy val serializer: it.evadid.core.util.io.Serializer[ProgrammingState] =
    it.evadid.core.util.io.Serializer.fromImplicitRW

}

final case class ProgrammingStateBeExpression(expression: BeExpression) extends ProgrammingState derives ReadWriter {
  lazy val turtleCommandSites = TurtleProgramAnalysis.commandSites(expression)
  lazy val turtleCommandExecutionInfo: TurtleCommandExecutionInfo[Double] = TurtleProgramAnalysis(expression)
  /** Startup run on the default list and sudoku; its list and sudoku commands are extracted like turtle commands. */
  lazy val programExecution: it.evadid.vm.simulation.ProgramExecution = execute()

  def execute(world: it.evadid.vm.simulation.data.DataWorld = it.evadid.vm.simulation.data.DataWorld(),
      entry: it.evadid.vm.simulation.ProgramEntry = it.evadid.vm.simulation.ProgramEntry.Startup,
      limits: it.evadid.vm.simulation.turtle.TurtleExecutionLimits = it.evadid.vm.simulation.turtle.TurtleExecutionLimits()
  ): it.evadid.vm.simulation.ProgramExecution =
    it.evadid.vm.simulation.BeProgramInterpreter.run(expression, world, entry, limits)

  override val toString: String = s"ProgrammingStateBeExpression(${expression.toString.take(300)})"

}


/** Transport and in-progress source. Prefer the structured language states for saved answers. */
final case class ProgrammingStateRaw(language: ProgrammingLanguage, code: String) extends ProgrammingState derives ReadWriter {
  def toSnapScalaModel: Either[String, ProgrammingStateSnapScalaModel] = derivations.snapModel

  def removeBloatFromXml: ProgrammingStateRaw = {
    val cleaned = if language == SnapLanguage then SnapGeneratedImages.remove(code) else code
    if cleaned == code then this else copy(code = cleaned)
  }
  def isClassProgram: Boolean = {
    require(language == Java, "Class-program validation requires Java source.")
    derivations.isJavaClass
  }
  def compileForExecution = {
    require(language == Java, "The checked Java runner requires Java source.")
    derivations.javaExecutionProgram
  }
  def toLegacyTurtleCommands: List[it.evadid.core.datastructures.vectorShapes.svg.TurtleCommand[Double]] = {
    require(!isClassProgram, "Full Java classes require the checked Java runner.")
    toBeExpression.turtleCommandExecutionInfo.completedCommands
  }
  override val toString: String = s"ProgrammingStateRaw($language, ${code.take(300)})"
}

/** Full Snap project state. Converting representations preserves native project XML.
  * Executing or translating this state still uses the existing checked subset converters.
  */
final case class ProgrammingStateSnapScalaModel(
    model: snap.SnapScalaModel.Document
) extends ProgrammingState {
  def persistent: ProgrammingStateSnapScalaModel = copy(model = model.withoutGeneratedImages)
  def meaningfullyEquals(other: ProgrammingStateSnapScalaModel): Boolean =
    model.meaningfullyEquals(other.model)

}

object ProgrammingStateSnapScalaModel {
  lazy val empty: ProgrammingStateSnapScalaModel = fromXml(SnapProjectXml.empty)
  lazy val mini: ProgrammingStateSnapScalaModel = fromXml(SnapProjectXml.mini)
  private def fromXml(xml: String): ProgrammingStateSnapScalaModel =
    it.evadid.vm.conversion.snapxmlmodel.SnapScalaXmlCodec.parse(xml)
      .fold(message => throw IllegalArgumentException(message), ProgrammingStateSnapScalaModel(_))

  private case class Saved(model: snap.SnapScalaModel.Document) derives ReadWriter
  given ReadWriter[ProgrammingStateSnapScalaModel] = upickle.default.readwriter[Saved].bimap(
    state => Saved(state.model.withoutGeneratedImages),
    saved => ProgrammingStateSnapScalaModel(saved.model.withoutGeneratedImages))
}

/** Class values are full source declarations, including bodies that the VM cannot yet parse. */
final case class ProgrammingStateJavaStructured(definedClasses: Map[BeEntityName, String]) extends ProgrammingState derives ReadWriter {
  override def toString: String = definedClasses.values.mkString("\n\n")
}
object ProgrammingStateJavaStructured {
  /** Loose code, including none at all, becomes `Main.main`: an empty program is a `Main` class with an empty entry point. */
  def apply(source: String): ProgrammingStateJavaStructured = {
    val parts = SourceStructure.java(source)
    if parts.remaining.isEmpty && parts.definedClasses.nonEmpty then new ProgrammingStateJavaStructured(parts.definedClasses)
    else StructuredProgramConversions.javaWithLooseCode(parts.definedClasses, parts.remaining)
  }
}

/** Classes precede module-level variables, functions and startup code when printed. */
final case class ProgrammingStatePythonStructured(definedClasses: Map[BeEntityName, String], mainScript: String) extends ProgrammingState derives ReadWriter {
  /** Execute this source on the supplied worker client, preserving stdout/stderr on the console.
    * Turtle operations print their calls; they do not create a drawing window.
    */
  def startExecution()(using client: ExecutionClient, ec: ExecutionContext): Future[RunPythonResult] = {
    val request = WorkerCommands.RunPythonRequest(toString, logTurtleCalls = true)
    val command = ExecutionCommand(WorkerCommands.runPython.name, upickle.default.writeJs(request))
    val logger = BasicLogger()
    client.executeResponse(logger, command).map { response =>
      response.result match {
        case Right(encoded) =>
          val result = encoded.getAs[RunPythonResult]
          print(result.stdout)
          Console.err.print(result.stderr)
          result
        case Left(error) =>
          print(response.additionalOutput.loggerOut)
          Console.err.print(response.additionalOutput.loggerError)
          throw error
      }
    }
  }

  override def toString: String = (definedClasses.values.toList :+ mainScript).filter(_.nonEmpty).mkString("\n\n")
}
object ProgrammingStatePythonStructured {
  /** The main program of an empty source: an explicit entry point, called at the end. */
  lazy val emptyMainScript: String =
    it.evadid.vm.conversion.expressionpython.ExpressionToPython.convert(it.evadid.vm.BeProgram.empty.fullProgram).code.trim
  def apply(source: String): ProgrammingStatePythonStructured = {
    val parts = SourceStructure.python(source)
    new ProgrammingStatePythonStructured(parts.definedClasses,
      if parts.remaining.isEmpty && parts.definedClasses.isEmpty then emptyMainScript else parts.remaining)
  }
}
