package it.evadid.workbook.elements.interactionElements.programming.state

import it.evadid.core.datastructures.language.AppLanguage.{English, Java}
import it.evadid.core.datastructures.vectorShapes.svg.BeExpressionToTurtleCommands
import it.evadid.core.datastructures.vectorShapes.svg.TurtlePathBuilder.TurtleCommand
import it.evadid.vm.BeProgram
import it.evadid.vm.code.abstractions.BeExpression
import it.evadid.vm.code.errors.{BeExpressionUnparsable, BeExpressionUnsupported, ProgrammingConversionException, ProgrammingConversionLocation}
import it.evadid.vm.code.tree.BeExpressionReference
import it.evadid.vm.parsing.generic.abstractions.GenericAST
import it.evadid.vm.parsing.java.clean.JavaParser
import it.evadid.vm.parsing.java.clean.model.JavaAST.{JavaAttributeAccess, JavaCallExpression, JavaClassDef, JavaFunctionCall, JavaMethodDef, JavaTarget, JavaUnparsableStatement}
import it.evadid.vm.parsing.java.turtle.{JavaTurtleInputLimits, JavaTurtleSource, JavaTurtleVmPrograms}
import it.evadid.vm.simulation.java.JavaTurtleRuntime
import it.evadid.vm.types.{BeChildInfo, BeChildRole, BeScope}
import it.evadid.workbook.elements.interactionElements.programming.*
import it.evadid.workbook.elements.interactionElements.programming.state.snap.{JavaTurtleEditingBridge, SnapGeneratedImages, SnapTurtlePythonBridge}
import upickle.default.*

/** The source representation currently edited by a programming exercise. */
sealed trait ProgrammingState {
  def associatedLanguage: it.evadid.core.datastructures.language.AppLanguage.ProgrammingLanguage = this match {
    case _: ProgrammingStateSnapXml | _: ProgrammingStateSnapScalaModel => it.evadid.core.datastructures.language.AppLanguage.SnapLanguage
    case _: ProgrammingStateJavaString => Java
    case _: ProgrammingStatePythonString => it.evadid.core.datastructures.language.AppLanguage.Python
    case _ => it.evadid.core.datastructures.language.AppLanguage.SnapLanguage
  }

  def toLanguage(language: it.evadid.core.datastructures.language.AppLanguage.ProgrammingLanguage): ProgrammingState = language match {
    case it.evadid.core.datastructures.language.AppLanguage.SnapLanguage => toSnapXml
    case Java => toJava
    case it.evadid.core.datastructures.language.AppLanguage.Python => toPython
    case _ => throw new UnsupportedOperationException(s"Programming language '$language' cannot be converted.")
  }

  def toBeExpression: ProgrammingStateBeExpression

  def toSnapXml: ProgrammingStateSnapXml

  def toPython: ProgrammingStatePythonString

  def toJava: ProgrammingStateJavaString

  def fingerprint(): String = this match
    case ProgrammingStateBeExpression(root: JavaTurtleVmPrograms.Root) => s"java:${root.bindings.source.source}"
    case ProgrammingStateBeExpression(expression) => s"expression:${expression.toString}"
    case snap: ProgrammingStateSnapXml if snap.hasLegacyFloatingObjects =>
      s"snap-legacy:${write((snap.snapXml, snap.legacyFloatingObjects))}"
    case ProgrammingStateSnapXml(xml) => s"snap:$xml"
    case snap: ProgrammingStateSnapScalaModel => snap.toSnapXml.fingerprint()
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

private def converted[A](result: Either[String, A]): A =
  result.fold(message => throw ProgrammingConversionException.unsupported(message), identity)

private def rejectAt(message: String, source: String, start: Option[Int]): Nothing = {
  val position = start.map(ProgrammingConversionLocation.lineColumn(source, _))
  throw ProgrammingConversionException.unparsable(message, position.map(_._1), position.map(_._2))
}

final case class ProgrammingStateBeExpression(expression: BeExpression) extends ProgrammingState {
  override def toBeExpression: ProgrammingStateBeExpression = this

  override def toSnapXml: ProgrammingStateSnapXml = expression match {
    case root: JavaTurtleVmPrograms.Root => converted(JavaTurtleEditingBridge.toSnap(JavaTurtleVmPrograms.fromRoot(root)))
    case _ => converted(SnapTurtlePythonBridge.validateSubset(expression).map(_ =>
      ProgrammingStateSnapXml.fromProgram(BeProgram(expression))))
  }

  override def toPython: ProgrammingStatePythonString = expression match {
    case root: JavaTurtleVmPrograms.Root => converted(JavaTurtleEditingBridge.toPython(JavaTurtleVmPrograms.fromRoot(root)))
    case _ => ProgrammingStatePythonString(SnapTurtlePythonBridge.printedPython(expression))
  }

  override def toJava: ProgrammingStateJavaString = expression match {
    case root: JavaTurtleVmPrograms.Root => ProgrammingStateJavaString(root.bindings.source.source)
    case _ => ProgrammingStateJavaString(expression.structureInfo.toStringInLanguage(Java, English, false))
  }

  def deriveTurtleCommands: List[TurtleCommand[Double]] = expression match {
    case root: JavaTurtleVmPrograms.Root =>
      val execution = JavaTurtleRuntime.runVm(JavaTurtleVmPrograms.fromRoot(root))
      if execution.status != JavaTurtleRuntime.Status.Completed then
        throw IllegalArgumentException(s"Java execution did not complete: ${execution.status}.")
      execution.commands.toList.map { command =>
        if !command.value.isFinite then throw IllegalArgumentException("A drawing command needs a finite number.")
        val name = command.command match {
          case it.evadid.vm.parsing.java.turtle.JavaTurtleResolution.TurtleCommand.Forward => "forward"
          case it.evadid.vm.parsing.java.turtle.JavaTurtleResolution.TurtleCommand.TurnRight => "right"
        }
        TurtleCommand[Double](name, List(command.value))
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
          diagnostic => rejectAt(diagnostic.message, source, diagnostic.range.map(_.start)), identity)
        ProgrammingStateBeExpression(program.root)
    })
}

final case class ProgrammingStateSnapXml(snapXml: String, legacyFloatingObjects: List[String] = Nil) extends ProgrammingState derives ReadWriter {
  require(legacyFloatingObjects != null && legacyFloatingObjects.forall(_ != null), "Invalid legacy Snap data.")

  def hasLegacyFloatingObjects: Boolean = legacyFloatingObjects.nonEmpty

  /** Lossless full-project conversion, independent of the executable Turtle subset. */
  def toSnapScalaModel: Either[String, ProgrammingStateSnapScalaModel] =
    snap.SnapScalaModel.parse(snapXml).map(ProgrammingStateSnapScalaModel(_, legacyFloatingObjects))

  /** Drop regenerated preview/pen images without changing scripts or authored costumes. */
  def removeBloatFromXml: ProgrammingStateSnapXml = {
    val cleaned = SnapGeneratedImages.remove(snapXml)
    if cleaned == snapXml then this else copy(snapXml = cleaned)
  }

  override def toBeExpression: ProgrammingStateBeExpression =
    if JavaTurtleEditingBridge.hasSnapMetadata(snapXml) then toJava.toBeExpression
    else ProgrammingStateBeExpression(SnapStateConversion.expressionFromXml(snapXml))

  override def toSnapXml: ProgrammingStateSnapXml = this

  private def requireTextConversion(): Unit =
    if hasLegacyFloatingObjects then
      throw IllegalArgumentException("This project contains legacy extra blocks. Keep editing it in Snap until they can be migrated.")

  override def toPython: ProgrammingStatePythonString = { requireTextConversion(); toBeExpression.toPython }

  override def toJava: ProgrammingStateJavaString = {
    requireTextConversion()
    if JavaTurtleEditingBridge.hasSnapMetadata(snapXml) then converted(JavaTurtleEditingBridge.fromSnap(this))
    else toBeExpression.toJava
  }

  override val toString: String = s"ProgrammingStateSnapXml(${snapXml.toString.take(300)})"
}

object ProgrammingStateSnapXml {
  def unapply(state: ProgrammingStateSnapXml): Some[String] = Some(state.snapXml)
  def fromProgram(program: BeProgram, canvasLayout: snap.SnapCanvasLayout = snap.SnapCanvasLayout.empty,
      previousXml: String = ""): ProgrammingStateSnapXml =
    ProgrammingStateSnapXml(snap.SnapCustomBlockMerge.applyProgram(program, canvasLayout, previousXml))
  def mini: ProgrammingStateSnapXml = ProgrammingStateSnapXml(snap.SnapProjectXml.mini)
  def empty: ProgrammingStateSnapXml = ProgrammingStateSnapXml(snap.SnapProjectXml.empty)
}

final case class ProgrammingStatePythonString(code: String) extends ProgrammingState derives ReadWriter {
  override def toBeExpression: ProgrammingStateBeExpression =
    if JavaTurtleEditingBridge.hasPythonMetadata(code) then toJava.toBeExpression
    else ProgrammingStateBeExpression(BeProgram.fromPythonString(code).fullProgram)

  override def toSnapXml: ProgrammingStateSnapXml =
    if JavaTurtleEditingBridge.hasPythonMetadata(code) then toJava.toSnapXml
    else SnapTurtlePythonBridge.applyPython(code).fold(
      message => throw ProgrammingConversionException.unsupported(message), identity)

  override def toPython: ProgrammingStatePythonString = this

  override def toJava: ProgrammingStateJavaString =
    if JavaTurtleEditingBridge.hasPythonMetadata(code) then converted(JavaTurtleEditingBridge.fromPython(code))
    else toBeExpression.toJava

  override val toString: String = s"ProgrammingStatePythonString(${code.toString.take(300)})"
}

final case class ProgrammingStateJavaString(code: String) extends ProgrammingState derives ReadWriter {
  private lazy val classDetails: (Boolean, Boolean, Boolean) = {
    JavaTurtleInputLimits.checkParserInput(code).fold(
      diagnostic => rejectAt(diagnostic.message, code, diagnostic.range.map(_.start)), identity)
    val sourceForAst = JavaToBeExpressionParser.withoutEntityHints(code)
    JavaTurtleInputLimits.checkParserInput(sourceForAst).fold(
      diagnostic => rejectAt(diagnostic.message, sourceForAst, diagnostic.range.map(_.start)), identity)
    val parsed = JavaParser.parse(sourceForAst).fold(
      {
        case conversion: ProgrammingConversionException =>
          throw ProgrammingConversionException.unparsable(
            "This Java source cannot be read as a complete program or fragment.",
            conversion.error.line, conversion.error.column)
        case error =>
          throw IllegalArgumentException("This Java source cannot be read as a complete program or fragment.", error)
      },
      identity)
    var pending: List[GenericAST] = List(parsed)
    var hasClass = false
    var hasStaticMain = false
    var hasTurtleCalls = false
    while pending.nonEmpty do {
      val node = pending.head
      pending = pending.tail
      node match {
        case statement: JavaUnparsableStatement =>
          val line = {
            val index = code.indexOf(statement.source)
            if index >= 0 then Some(ProgrammingConversionLocation.lineColumn(code, index)._1) else None
          }
          throw ProgrammingConversionException.unparsable(
            "This Java source contains unsupported or unfinished syntax.", line)
        case _: JavaClassDef => hasClass = true
        case method: JavaMethodDef if method.name == "main" && method.modifiers.contains("static") => hasStaticMain = true
        case JavaFunctionCall(JavaTarget(_, Seq("Turtle"), _), _) => hasTurtleCalls = true
        case JavaCallExpression(JavaAttributeAccess(JavaTarget("Turtle", locations, _), _), _) if locations.isEmpty => hasTurtleCalls = true
        case _ => ()
      }
      pending = node.getChildren().toList ::: pending
    }
    (hasClass, hasStaticMain, hasTurtleCalls)
  }

  def isClassProgram: Boolean = classDetails._1

  private def legacyPrinterExpression(): ProgrammingStateBeExpression = {
    val expression = JavaToBeExpressionParser.parse(code)
    val nodes = expression.recToTree(false, BeChildInfo(BeChildRole.NoRole, BeScope.GlobalScope())).values
    val unsupported = nodes.exists {
      case BeExpressionReference(_, _: BeExpressionUnsupported) => true
      case BeExpressionReference(_, _: BeExpressionUnparsable) => true
      case _ => false
    }
    if unsupported then throw IllegalArgumentException("This legacy Java class cannot be converted completely.")
    ProgrammingStateBeExpression(expression)
  }

  def toLegacyTurtleCommands: List[TurtleCommand[Double]] = {
    if isClassProgram then throw IllegalArgumentException("Full Java classes require the checked Java runner.")
    toBeExpression.deriveTurtleCommands
  }

  def toJavaVmProgram: Either[JavaTurtleSource.Diagnostic, JavaTurtleVmPrograms.Program] =
    JavaTurtleVmPrograms.compile(code)

  override def toBeExpression: ProgrammingStateBeExpression =
    if !isClassProgram then ProgrammingStateBeExpression(JavaToBeExpressionParser.parse(code))
    else toJavaVmProgram match {
      case Right(program) => ProgrammingStateBeExpression(program.root)
      case Left(_) if JavaToBeExpressionParser.withoutEntityHints(code) != code && !classDetails._2 && !classDetails._3 =>
        legacyPrinterExpression()
      case Left(diagnostic) => rejectAt(diagnostic.message, code, diagnostic.range.map(_.start))
    }

  /** Snap view for Eva tab switching: a Java class becomes method custom blocks (not normal turtle blocks).
    * For an empty beginner Snap start, use `SnapProjectXml.mini` instead of calling this. */
  override def toSnapXml: ProgrammingStateSnapXml =
    if isClassProgram then converted(toJavaVmProgram.left.map(_.message).flatMap(JavaTurtleEditingBridge.toSnap))
    else toBeExpression.toSnapXml

  override def toPython: ProgrammingStatePythonString = toBeExpression.toPython

  override def toJava: ProgrammingStateJavaString = this

  override val toString: String = s"ProgrammingStateJavaString(${code.toString.take(300)})"
}

/** Full Snap project state. Converting representations preserves XML and opaque legacy data.
  * Executing or translating this state still uses the existing checked subset converters.
  */
final case class ProgrammingStateSnapScalaModel(
    model: snap.SnapScalaModel.Document,
    legacyFloatingObjects: List[String] = Nil
) extends ProgrammingState {
  require(legacyFloatingObjects != null && legacyFloatingObjects.forall(_ != null), "Invalid legacy Snap data.")
  def persistent: ProgrammingStateSnapScalaModel = copy(model = model.withoutGeneratedImages)
  def meaningfullyEquals(other: ProgrammingStateSnapScalaModel): Boolean =
    legacyFloatingObjects == other.legacyFloatingObjects && model.meaningfullyEquals(other.model)
  override def toSnapXml: ProgrammingStateSnapXml = ProgrammingStateSnapXml(model.toXml, legacyFloatingObjects)
  override def toBeExpression: ProgrammingStateBeExpression = toSnapXml.toBeExpression
  override def toPython: ProgrammingStatePythonString = toSnapXml.toPython
  override def toJava: ProgrammingStateJavaString = toSnapXml.toJava
}

object ProgrammingStateSnapScalaModel {
  private case class Saved(model: snap.SnapScalaModel.Document, legacyFloatingObjects: List[String] = Nil) derives ReadWriter
  given ReadWriter[ProgrammingStateSnapScalaModel] = upickle.default.readwriter[Saved].bimap(
    state => Saved(state.model.withoutGeneratedImages, state.legacyFloatingObjects),
    saved => ProgrammingStateSnapScalaModel(saved.model.withoutGeneratedImages, saved.legacyFloatingObjects))
}
