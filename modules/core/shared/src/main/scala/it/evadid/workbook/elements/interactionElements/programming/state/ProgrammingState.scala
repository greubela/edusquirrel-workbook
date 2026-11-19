package it.evadid.workbook.elements.interactionElements.programming.state

import it.evadid.workbook.elements.interactionElements.programming.state.snap.SnapScalaModel

import it.evadid.vm.conversion.expressionsnap.SnapProjectXml

import it.evadid.core.datastructures.language.AppLanguage.Java
import it.evadid.vm.simulation.turtle.{TurtleProgramAnalysis, TurtleCommandExecutionInfo}
import it.evadid.vm.code.abstractions.BeExpression
import it.evadid.vm.conversion.javaexpression.checked.JavaTurtleVmPrograms
import it.evadid.workbook.elements.interactionElements.programming.*
import it.evadid.workbook.elements.interactionElements.programming.state.snap.SnapGeneratedImages
import it.evadid.vm.conversion.ConversionResult
import it.evadid.vm.conversion.snapmodelxml.SnapModelToXml.*
import upickle.default.*
import it.evadid.vm.naming.BeEntityName
import it.evadid.vm.conversion.structured.{SourceStructure, StructuredProgramConversions}

/** The source representation currently edited by a programming exercise. */
sealed trait ProgrammingState {
  def associatedLanguage: it.evadid.core.datastructures.language.AppLanguage.ProgrammingLanguage = this match {
    case _: ProgrammingStateSnapXml | _: ProgrammingStateSnapScalaModel => it.evadid.core.datastructures.language.AppLanguage.SnapLanguage
    case _: ProgrammingStateJavaString | _: ProgrammingStateJavaStructured => Java
    case _: ProgrammingStatePythonString | _: ProgrammingStatePythonStructured => it.evadid.core.datastructures.language.AppLanguage.Python
    case _ => it.evadid.core.datastructures.language.AppLanguage.SnapLanguage
  }

  def fingerprint(): String = this match
    case ProgrammingStateBeExpression(expression) => s"expression:${expression.toString}"
    case ProgrammingStateSnapXml(xml) => s"snap:$xml"
    case snap: ProgrammingStateSnapScalaModel => s"snap:${snap.model.toXml}"
    case ProgrammingStatePythonString(code) => s"python:$code"
    case ProgrammingStateJavaString(code) => s"java:$code"
    case state: ProgrammingStateJavaStructured => s"java-structured:${write(state)}"
    case state: ProgrammingStatePythonStructured => s"python-structured:${write(state)}"
}

object ProgrammingState {

  // One current wire schema, derived from typed payloads. No historical tags or migration paths.
  private enum Payload derives ReadWriter {
    case Xml(value: ProgrammingStateSnapXml)
    case SnapModel(value: ProgrammingStateSnapScalaModel)
    case PythonSource(value: ProgrammingStatePythonString)
    case JavaSource(value: ProgrammingStateJavaString)
    case JavaStructured(value: ProgrammingStateJavaStructured)
    case PythonStructured(value: ProgrammingStatePythonStructured)
    case Expression(value: ProgrammingStateBeExpression)
  }
  given ReadWriter[ProgrammingState] =
    readwriter[Payload].bimap({
      case value: ProgrammingStateSnapXml => Payload.Xml(value)
      case value: ProgrammingStateSnapScalaModel => Payload.SnapModel(value)
      case value: ProgrammingStatePythonString => Payload.PythonSource(value)
      case value: ProgrammingStateJavaString => Payload.JavaSource(value)
      case value: ProgrammingStateJavaStructured => Payload.JavaStructured(value)
      case value: ProgrammingStatePythonStructured => Payload.PythonStructured(value)
      case value: ProgrammingStateBeExpression => Payload.Expression(value)
    }, {
      case Payload.Xml(value) => value
      case Payload.SnapModel(value) => value
      case Payload.PythonSource(value) => value
      case Payload.JavaSource(value) => value
      case Payload.JavaStructured(value) => value
      case Payload.PythonStructured(value) => value
      case Payload.Expression(value) => value
    })

  lazy val serializer: it.evadid.core.util.io.Serializer[ProgrammingState] =
    it.evadid.core.util.io.Serializer.fromImplicitRW

}

final case class ProgrammingStateBeExpression(expression: BeExpression) extends ProgrammingState derives ReadWriter {
  def turtleCommandExecutionInfo: TurtleCommandExecutionInfo[Double] = TurtleProgramAnalysis(expression)

  override val toString: String = s"ProgrammingStateBeExpression(${expression.toString.take(300)})"

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

/** Class values are full source declarations, including bodies that the VM cannot yet parse. */
final case class ProgrammingStateJavaStructured(definedClasses: Map[BeEntityName, String]) extends ProgrammingState derives ReadWriter {
  override def toString: String = definedClasses.values.mkString("\n\n")
}
object ProgrammingStateJavaStructured {
  def apply(source: String): ProgrammingStateJavaStructured = {
    val parts = SourceStructure.java(source)
    if parts.remaining.isEmpty then new ProgrammingStateJavaStructured(parts.definedClasses)
    else StructuredProgramConversions.javaWithLooseCode(parts.definedClasses, parts.remaining)
  }
}

/** Classes precede module-level variables, functions and startup code when printed. */
final case class ProgrammingStatePythonStructured(definedClasses: Map[BeEntityName, String], mainScript: String) extends ProgrammingState derives ReadWriter {
  override def toString: String = (definedClasses.values.toList :+ mainScript).filter(_.nonEmpty).mkString("\n\n")
}
object ProgrammingStatePythonStructured {
  def apply(source: String): ProgrammingStatePythonStructured = {
    val parts = SourceStructure.python(source)
    new ProgrammingStatePythonStructured(parts.definedClasses, parts.remaining)
  }
}
