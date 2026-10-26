package it.evadid.workbook.elements.interactionElements.programming.state

import it.evadid.core.datastructures.language.AppLanguage.{English, Java}
import it.evadid.core.datastructures.vectorShapes.svg.BeExpressionToTurtleCommands
import it.evadid.core.datastructures.vectorShapes.svg.TurtlePathBuilder.TurtleCommand
import it.evadid.vm.BeProgram
import it.evadid.vm.code.abstractions.BeExpression
import it.evadid.vm.code.errors.{BeExpressionUnparsable, BeExpressionUnsupported}
import it.evadid.vm.code.tree.BeExpressionReference
import it.evadid.vm.parsing.generic.abstractions.GenericAST
import it.evadid.vm.parsing.java.clean.JavaParser
import it.evadid.vm.parsing.java.clean.model.JavaAST.{JavaAttributeAccess, JavaCallExpression, JavaClassDef, JavaFunctionCall, JavaMethodDef, JavaTarget, JavaUnparsableStatement}
import it.evadid.vm.parsing.java.turtle.{JavaTurtleInputLimits, JavaTurtleSource, JavaTurtleVmPrograms}
import it.evadid.vm.simulation.java.JavaTurtleRuntime
import it.evadid.vm.types.{BeChildInfo, BeChildRole, BeScope}
import it.evadid.workbook.elements.interactionElements.programming.*
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingState.ProgrammingState.{ProgrammingStateJavaString, ProgrammingStatePythonString, ProgrammingStateSnapXml}
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingState.ProgrammingStateBeExpression
import it.evadid.workbook.elements.interactionElements.programming.state.snap.{JavaTurtleEditingBridge, ProgrammingStateSnapXmlHelper, SnapTurtlePythonBridge}
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
    case _ => throw UnsupportedOperationException(s"Programming language '$language' cannot be converted.")
  }

  final def toBeExpression: ProgrammingStateBeExpression = toBeExpressionState
  def toBeExpressionState: ProgrammingStateBeExpression

  def toSnapXml: ProgrammingStateSnapXml

  def toPython: ProgrammingStatePythonString

  def toJava: ProgrammingStateJavaString

  /** Prepares Python source and optional entry-point metadata for the shared unit-test runner. */
  def prepareUnitTestProgram: Either[String, ProgrammingTestProgram]

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
  export ProgrammingState.{ProgrammingStateJavaString, ProgrammingStatePythonString, ProgrammingStateSnapXml}

  private def converted[A](result: Either[String, A]): A =
    result.fold(message => throw IllegalArgumentException(message), identity)

  // One current wire schema, derived from typed payloads. No historical tags or migration paths.
  private enum Payload derives ReadWriter {
    case Xml(value: ProgrammingStateSnapXml)
    case SnapModel(value: ProgrammingStateSnapScalaModel)
    case PythonSource(value: ProgrammingStatePythonString)
    case JavaSource(value: ProgrammingStateJavaString)
    case Expression(value: ProgrammingStateBeExpression)
  }
  given derived$ReadWriter: ReadWriter[it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingState] =
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

  lazy val serializer: it.evadid.core.util.io.Serializer[it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingState] =
    it.evadid.core.util.io.Serializer.fromImplicitRW

  def fingerprint(state: it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingState): String =
    state.fingerprint()

  final case class ProgrammingStateBeExpression(expression: BeExpression) extends ProgrammingState {
    override def prepareUnitTestProgram: Either[String, ProgrammingTestProgram] =
      ProgrammingTestProgram.converted(ProgrammingTestProgram.fromExpression(expression))

    override def toBeExpressionState: ProgrammingStateBeExpression = this

    override def toSnapXml: ProgrammingStateSnapXml = expression match {
      case root: JavaTurtleVmPrograms.Root => converted(JavaTurtleEditingBridge.toSnap(JavaTurtleVmPrograms.fromRoot(root)))
      case _ => converted(SnapTurtlePythonBridge.validateSubset(expression).map(_ =>
        ProgrammingStateSnapXmlHelper.fromProgram(BeProgram(expression))))
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

    def executedTurtleCommands(): List[TurtleCommand[Double]] = deriveTurtleCommands

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
            diagnostic => throw IllegalArgumentException(diagnostic.message), identity)
          ProgrammingStateBeExpression(program.root)
      })
  }

  object ProgrammingState {

    final case class ProgrammingStateSnapXml(snapXml: String, legacyFloatingObjects: List[String] = Nil) extends ProgrammingState derives ReadWriter {
      require(legacyFloatingObjects != null && legacyFloatingObjects.forall(_ != null), "Invalid legacy Snap data.")

      def hasLegacyFloatingObjects: Boolean = legacyFloatingObjects.nonEmpty

      override def prepareUnitTestProgram: Either[String, ProgrammingTestProgram] = ProgrammingTestProgram.converted {
        if hasLegacyFloatingObjects then Left("Legacy extra blocks cannot be included in unit-test execution.")
        else if JavaTurtleEditingBridge.hasSnapMetadata(snapXml) then
          JavaTurtleEditingBridge.fromSnap(this).flatMap(_.prepareUnitTestProgram)
        else SnapStateConversion.expressionForExecution(snapXml).flatMap(ProgrammingTestProgram.fromExpression)
      }

      def withProjectXml(xml: String): ProgrammingStateSnapXml = copy(snapXml = xml)

      /** Lossless full-project conversion, independent of the executable Turtle subset. */
      def toSnapScalaModel: Either[String, ProgrammingStateSnapScalaModel] =
        ProgrammingStateSnapScalaModel.fromXml(this)

      /** Drop regenerated preview/pen images without changing scripts or authored costumes. */
      def removeBloatFromXml: ProgrammingStateSnapXml = {
        val cleaned = ProgrammingStateSnapXmlHelper.removeGeneratedImages(snapXml)
        if cleaned == snapXml then this else copy(snapXml = cleaned)
      }

      override def toBeExpressionState: ProgrammingStateBeExpression =
        if JavaTurtleEditingBridge.hasSnapMetadata(snapXml) then toJava.toBeExpressionState
        else ProgrammingStateBeExpression(SnapStateConversion.expressionFromXml(snapXml))

      override def toSnapXml: ProgrammingStateSnapXml = this

      private def requireTextConversion(): Unit =
        if hasLegacyFloatingObjects then
          throw IllegalArgumentException("This project contains legacy extra blocks. Keep editing it in Snap until they can be migrated.")

      override def toPython: ProgrammingStatePythonString = { requireTextConversion(); toBeExpressionState.toPython }

      override def toJava: ProgrammingStateJavaString = {
        requireTextConversion()
        if JavaTurtleEditingBridge.hasSnapMetadata(snapXml) then converted(JavaTurtleEditingBridge.fromSnap(this))
        else toBeExpressionState.toJava
      }

      override val toString: String = s"ProgrammingStateSnapXml(${snapXml.toString.take(300)})"
    }

    object ProgrammingStateSnapXml {
      def unapply(state: ProgrammingStateSnapXml): Some[String] = Some(state.snapXml)
      def fromProgram(program: BeProgram, canvasLayout: snap.SnapCanvasLayout = snap.SnapCanvasLayout.empty,
          previousXml: String = ""): ProgrammingStateSnapXml =
        ProgrammingStateSnapXmlHelper.fromProgram(program, canvasLayout, previousXml)
      def mini: ProgrammingStateSnapXml = ProgrammingStateSnapXmlHelper.mini
      def empty: ProgrammingStateSnapXml = ProgrammingStateSnapXmlHelper.empty
      def fingerprint(state: ProgrammingStateSnapXml): String =
        if state.hasLegacyFloatingObjects then state.fingerprint() else state.snapXml
    }

    final case class ProgrammingStatePythonString(code: String) extends ProgrammingState derives ReadWriter {
      override def prepareUnitTestProgram: Either[String, ProgrammingTestProgram] = ProgrammingTestProgram.converted {
        if JavaTurtleEditingBridge.hasPythonMetadata(code) then
          JavaTurtleEditingBridge.fromPython(code).flatMap(_.prepareUnitTestProgram)
        else Right(ProgrammingTestProgram(code))
      }

      override def toBeExpressionState: ProgrammingStateBeExpression =
        if JavaTurtleEditingBridge.hasPythonMetadata(code) then toJava.toBeExpressionState
        else ProgrammingStateBeExpression(BeProgram.fromPythonString(code).fullProgram)

      override def toSnapXml: ProgrammingStateSnapXml =
        if JavaTurtleEditingBridge.hasPythonMetadata(code) then toJava.toSnapXml
        else converted(SnapTurtlePythonBridge.applyPython(code))

      override def toPython: ProgrammingStatePythonString = this

      override def toJava: ProgrammingStateJavaString =
        if JavaTurtleEditingBridge.hasPythonMetadata(code) then converted(JavaTurtleEditingBridge.fromPython(code))
        else toBeExpressionState.toJava

      override val toString: String = s"ProgrammingStatePythonString(${code.toString.take(300)})"
    }


    final case class ProgrammingStateJavaString(code: String) extends ProgrammingState derives ReadWriter {
      override def prepareUnitTestProgram: Either[String, ProgrammingTestProgram] = ProgrammingTestProgram.converted {
        toJavaVmProgram.left.map(_.message).flatMap(program => ProgrammingTestProgram.fromExpression(program.root))
      }

      private lazy val classDetails: (Boolean, Boolean, Boolean) = {
        JavaTurtleInputLimits.checkParserInput(code).fold(
          diagnostic => throw IllegalArgumentException(diagnostic.message), identity)
        val sourceForAst = JavaToBeExpressionParser.withoutEntityHints(code)
        JavaTurtleInputLimits.checkParserInput(sourceForAst).fold(
          diagnostic => throw IllegalArgumentException(diagnostic.message), identity)
        val parsed = JavaParser.parse(sourceForAst).fold(
          error => throw IllegalArgumentException("This Java source cannot be read as a complete program or fragment.", error),
          identity)
        var pending: List[GenericAST] = List(parsed)
        var hasClass = false
        var hasStaticMain = false
        var hasTurtleCalls = false
        while pending.nonEmpty do {
          val node = pending.head
          pending = pending.tail
          node match {
            case _: JavaUnparsableStatement =>
              throw IllegalArgumentException("This Java source contains unsupported or unfinished syntax.")
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
        toBeExpressionState.deriveTurtleCommands
      }

      def toJavaVmProgram: Either[JavaTurtleSource.Diagnostic, JavaTurtleVmPrograms.Program] =
        JavaTurtleVmPrograms.compile(code)

      override def toBeExpressionState: ProgrammingStateBeExpression =
        if !isClassProgram then ProgrammingStateBeExpression(JavaToBeExpressionParser.parse(code))
        else toJavaVmProgram match {
          case Right(program) => ProgrammingStateBeExpression(program.root)
          case Left(_) if JavaToBeExpressionParser.withoutEntityHints(code) != code && !classDetails._2 && !classDetails._3 =>
            legacyPrinterExpression()
          case Left(diagnostic) => throw IllegalArgumentException(diagnostic.message)
        }

      override def toSnapXml: ProgrammingStateSnapXml =
        if isClassProgram then converted(toJavaVmProgram.left.map(_.message).flatMap(JavaTurtleEditingBridge.toSnap))
        else toBeExpressionState.toSnapXml

      override def toPython: ProgrammingStatePythonString = toBeExpressionState.toPython

      override def toJava: ProgrammingStateJavaString = this

      override val toString: String = s"ProgrammingStateJavaString(${code.toString.take(300)})"
    }


  }

}

type ProgrammingStateBeExpression = ProgrammingState.ProgrammingStateBeExpression
val ProgrammingStateBeExpression = ProgrammingState.ProgrammingStateBeExpression
type ProgrammingStateJavaString = ProgrammingState.ProgrammingStateJavaString
val ProgrammingStateJavaString = ProgrammingState.ProgrammingStateJavaString
type ProgrammingStatePythonString = ProgrammingState.ProgrammingStatePythonString
val ProgrammingStatePythonString = ProgrammingState.ProgrammingStatePythonString
type ProgrammingStateSnapXml = ProgrammingState.ProgrammingStateSnapXml
val ProgrammingStateSnapXml = ProgrammingState.ProgrammingStateSnapXml

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
  override def toBeExpressionState: ProgrammingStateBeExpression = toSnapXml.toBeExpressionState
  override def toPython: ProgrammingStatePythonString = toSnapXml.toPython
  override def toJava: ProgrammingStateJavaString = toSnapXml.toJava
  override def prepareUnitTestProgram: Either[String, ProgrammingTestProgram] = toSnapXml.prepareUnitTestProgram
}

object ProgrammingStateSnapScalaModel {
  private case class Saved(model: snap.SnapScalaModel.Document, legacyFloatingObjects: List[String] = Nil) derives ReadWriter
  given ReadWriter[ProgrammingStateSnapScalaModel] = upickle.default.readwriter[Saved].bimap(
    state => Saved(state.model.withoutGeneratedImages, state.legacyFloatingObjects),
    saved => ProgrammingStateSnapScalaModel(saved.model.withoutGeneratedImages, saved.legacyFloatingObjects))
  def fromXml(state: ProgrammingStateSnapXml): Either[String, ProgrammingStateSnapScalaModel] =
    snap.SnapScalaModel.parse(state.snapXml).map(ProgrammingStateSnapScalaModel(_, state.legacyFloatingObjects))
}
