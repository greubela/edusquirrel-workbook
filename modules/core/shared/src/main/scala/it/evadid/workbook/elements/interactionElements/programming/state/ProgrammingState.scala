package it.evadid.workbook.elements.interactionElements.programming.state

import it.evadid.workbook.elements.interactionElements.programming.state.snap.SnapScalaModel

import it.evadid.vm.conversion.expressionsnap.SnapProjectXml
import it.evadid.vm.conversion.javaexpression.checked.JavaTurtleResolution

import it.evadid.core.datastructures.language.AppLanguage.Java
import it.evadid.core.datastructures.vectorShapes.svg.BeExpressionToTurtleCommands
import it.evadid.core.datastructures.vectorShapes.svg.TurtleCommand
import it.evadid.vm.code.abstractions.BeExpression
import it.evadid.vm.conversion.javaexpression.checked.JavaTurtleVmPrograms
import it.evadid.vm.simulation.java.JavaTurtleRuntime
import it.evadid.workbook.elements.interactionElements.programming.*
import it.evadid.workbook.elements.interactionElements.programming.state.snap.SnapGeneratedImages
import it.evadid.vm.conversion.ConversionResult
import it.evadid.vm.conversion.snapmodelxml.SnapModelToXml.*
import upickle.default.*

/** The source representation currently edited by a programming exercise. */
sealed trait ProgrammingState {
  def associatedLanguage: it.evadid.core.datastructures.language.AppLanguage.ProgrammingLanguage = this match {
    case _: ProgrammingStateSnapXml | _: ProgrammingStateSnapScalaModel => it.evadid.core.datastructures.language.AppLanguage.SnapLanguage
    case _: ProgrammingStateJavaString => Java
    case _: ProgrammingStatePythonString => it.evadid.core.datastructures.language.AppLanguage.Python
    case _ => it.evadid.core.datastructures.language.AppLanguage.SnapLanguage
  }

  def fingerprint(): String = this match
    case ProgrammingStateBeExpression(root: JavaTurtleVmPrograms.Root) => s"java:${root.bindings.source.source}"
    case ProgrammingStateBeExpression(expression) => s"expression:${expression.toString}"
    case ProgrammingStateSnapXml(xml) => s"snap:$xml"
    case snap: ProgrammingStateSnapScalaModel => s"snap:${snap.model.toXml}"
    case ProgrammingStatePythonString(code) => s"python:$code"
    case ProgrammingStateJavaString(code) => s"java:$code"
}

object ProgrammingState {

  // One current wire schema, derived from typed payloads. No historical tags or migration paths.
  private enum Payload derives ReadWriter {
    case Xml(value: ProgrammingStateSnapXml)
    case SnapModel(value: ProgrammingStateSnapScalaModel)
    case PythonSource(value: ProgrammingStatePythonString)
    case JavaSource(value: ProgrammingStateJavaString)
    case Expression(value: ProgrammingStateBeExpression)
  }
  given ReadWriter[ProgrammingState] =
    readwriter[Payload].bimap({
      case value: ProgrammingStateSnapXml => Payload.Xml(value)
      case value: ProgrammingStateSnapScalaModel => Payload.SnapModel(value)
      case value: ProgrammingStatePythonString => Payload.PythonSource(value)
      case value: ProgrammingStateJavaString => Payload.JavaSource(value)
      case value: ProgrammingStateBeExpression => Payload.Expression(value)
    }, {
      case Payload.Xml(value) => value
      case Payload.SnapModel(value) => value
      case Payload.PythonSource(value) => value
      case Payload.JavaSource(value) => value
      case Payload.Expression(value) => value
    })

  lazy val serializer: it.evadid.core.util.io.Serializer[ProgrammingState] =
    it.evadid.core.util.io.Serializer.fromImplicitRW

}

final case class ProgrammingStateBeExpression(expression: BeExpression) extends ProgrammingState {
  def deriveTurtleCommands: List[TurtleCommand[Double]] = expression match {
    case root: JavaTurtleVmPrograms.Root =>
      val execution = JavaTurtleRuntime.runVm(JavaTurtleVmPrograms.fromRoot(root))
      if execution.status != JavaTurtleRuntime.Status.Completed then
        throw IllegalArgumentException(s"Java execution did not complete: ${execution.status}.")
      execution.commands.toList.map { command =>
        if !command.value.isFinite then throw IllegalArgumentException("A drawing command needs a finite number.")
        command.command match {
          case it.evadid.vm.conversion.javaexpression.checked.JavaTurtleResolution.TurtleCommand.Forward => TurtleCommand.Forward(command.value)
          case it.evadid.vm.conversion.javaexpression.checked.JavaTurtleResolution.TurtleCommand.TurnRight => TurtleCommand.Right(command.value)
        }
      }
    case _ => BeExpressionToTurtleCommands(expression)
  }

  override val toString: String = s"ProgrammingStateBeExpression(${expression.toString.take(300)})"

}

object ProgrammingStateBeExpression {
  // Checked VM nodes contain runtime bindings rather than a persistent BeExpression tree.
  // Store their source and recompile it on read; all other expressions use their tree codec.
  private enum Content derives ReadWriter {
    case Tree(expression: BeExpression)
    case CheckedJava(source: String)
  }
  given ReadWriter[ProgrammingStateBeExpression] = readwriter[Content].bimap(
    state => state.expression match {
      case root: JavaTurtleVmPrograms.Root => Content.CheckedJava(root.bindings.source.source)
      case expression => Content.Tree(expression)
    }, {
      case Content.Tree(expression) => ProgrammingStateBeExpression(expression)
      case Content.CheckedJava(source) =>
        val program = JavaTurtleVmPrograms.compile(source).fold(
          diagnostic => ConversionResult.rejectAt(diagnostic.message, source, diagnostic.range.map(_.start)), identity)
        ProgrammingStateBeExpression(program.root)
    })
}

final case class ProgrammingStateSnapXml(snapXml: String) extends ProgrammingState derives ReadWriter {

  /** Drop regenerated preview/pen images without changing scripts or authored costumes. */
  def removeBloatFromXml: ProgrammingStateSnapXml = {
    val cleaned = SnapGeneratedImages.remove(snapXml)
    if cleaned == snapXml then this else copy(snapXml = cleaned)
  }

  override val toString: String = s"ProgrammingStateSnapXml(${snapXml.toString.take(300)})"
}

object ProgrammingStateSnapXml {
  def unapply(state: ProgrammingStateSnapXml): Some[String] = Some(state.snapXml)
  def mini: ProgrammingStateSnapXml = ProgrammingStateSnapXml(SnapProjectXml.mini)
  def empty: ProgrammingStateSnapXml = ProgrammingStateSnapXml(SnapProjectXml.empty)
}

final case class ProgrammingStatePythonString(code: String) extends ProgrammingState derives ReadWriter {
  override val toString: String = s"ProgrammingStatePythonString(${code.toString.take(300)})"
}

final case class ProgrammingStateJavaString(code: String) extends ProgrammingState derives ReadWriter {
  override val toString: String = s"ProgrammingStateJavaString(${code.toString.take(300)})"
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
  private case class Saved(model: snap.SnapScalaModel.Document) derives ReadWriter
  given ReadWriter[ProgrammingStateSnapScalaModel] = upickle.default.readwriter[Saved].bimap(
    state => Saved(state.model.withoutGeneratedImages),
    saved => ProgrammingStateSnapScalaModel(saved.model.withoutGeneratedImages))
}
