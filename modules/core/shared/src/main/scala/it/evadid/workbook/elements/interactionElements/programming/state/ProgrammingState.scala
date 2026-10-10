package it.evadid.workbook.elements.interactionElements.programming.state

import it.evadid.core.datastructures.language.AppLanguage.{English, Java}
import it.evadid.core.datastructures.vectorShapes.svg.BeExpressionToTurtleCommands
import it.evadid.core.datastructures.vectorShapes.svg.TurtlePathBuilder.TurtleCommand
import it.evadid.vm.BeProgram
import it.evadid.vm.code.abstractions.BeExpression
import it.evadid.vm.parsing.generic.abstractions.GenericAST
import it.evadid.vm.parsing.java.clean.JavaParser
import it.evadid.vm.parsing.java.clean.model.JavaAST.{JavaClassDef, JavaUnparsableStatement}
import it.evadid.vm.parsing.java.turtle.{JavaTurtleInputLimits, JavaTurtleSource, JavaTurtleVmPrograms}
import it.evadid.workbook.elements.interactionElements.programming.*
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingState.ProgrammingState.{ProgrammingStateJavaString, ProgrammingStatePythonString, ProgrammingStateSnapXml}
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingState.ProgrammingStateBeExpression
import it.evadid.workbook.elements.interactionElements.programming.state.snap.{ProgrammingStateSnapXmlHelper, SnapTurtlePythonBridge}
import upickle.default.*
import scala.reflect.ClassTag

/** The source representation currently edited by a programming exercise. */
sealed trait ProgrammingState {
  final def toBeExpression: ProgrammingStateBeExpression = toBeExpressionState
  def toBeExpressionState: ProgrammingStateBeExpression

  def toSnapXml: ProgrammingStateSnapXml

  def toPython: ProgrammingStatePythonString

  def toJava: ProgrammingStateJavaString

  def fingerprint(): String = this match
    case ProgrammingStateBeExpression(expression) => s"expression:${expression.toString}"
    case snap: ProgrammingStateSnapXml if snap.hasLegacyFloatingObjects =>
      s"snap-legacy:${write((snap.snapXml, snap.legacyFloatingObjects))}"
    case ProgrammingStateSnapXml(xml) => s"snap:$xml"
    case ProgrammingStatePythonString(code) => s"python:$code"
    case ProgrammingStateJavaString(code) => s"java:$code"
}

object ProgrammingState {
  export ProgrammingState.{ProgrammingStateJavaString, ProgrammingStatePythonString, ProgrammingStateSnapXml}

  private val legacySnapTag = "ProgrammingStateSnapXMLWithAdditionalFloatingObjects"
  private val tagPrefixes = List(
    "it.evadid.workbook.elements.interactionElements.programming.",
    "it.evadid.workbook.elements.interactionElements.programming.state.",
    "it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingState.",
    "it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingState.ProgrammingState."
  )
  private val stateTags = Set("ProgrammingStateBeExpression", "ProgrammingStateSnapXml",
    "ProgrammingStatePythonString", "ProgrammingStateJavaString", legacySnapTag)

  private[programming] def readLegacyFloatingObjects(value: ujson.Value): List[String] =
    value.arr.toList.map(_.str)

  given derived$ReadWriter: ReadWriter[it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingState] =
    readwriter[ujson.Value].bimap(
      {
        case snap: ProgrammingStateSnapXml if snap.hasLegacyFloatingObjects =>
          ujson.Obj("$type" -> legacySnapTag, "snapXml" -> snap.snapXml,
            "additionalFloatingObjects" -> writeJs(snap.legacyFloatingObjects))
        case snap: ProgrammingStateSnapXml =>
          ujson.Obj("$type" -> "ProgrammingStateSnapXml", "snapXml" -> snap.snapXml)
        case ProgrammingStatePythonString(code) =>
          ujson.Obj("$type" -> "ProgrammingStatePythonString", "code" -> code)
        case ProgrammingStateJavaString(code) =>
          ujson.Obj("$type" -> "ProgrammingStateJavaString", "code" -> code)
        case ProgrammingStateBeExpression(expression) =>
          ujson.Obj("$type" -> "ProgrammingStateBeExpression", "expression" -> writeJs(expression))
      },
      value => {
        val payload = ujson.Obj.from(value.obj)
        payload.obj.get("$type").foreach { raw =>
          val tag = raw.str
          val name = tagPrefixes.iterator.filter(tag.startsWith).map(tag.stripPrefix).find(stateTags.contains)
            .getOrElse(tag)
          payload.obj("$type") = ujson.Str(name)
        }
        payload("$type").str match {
          case `legacySnapTag` =>
            ProgrammingStateSnapXml(payload("snapXml").str, readLegacyFloatingObjects(payload("additionalFloatingObjects")))
          case "ProgrammingStateSnapXml" =>
            ProgrammingStateSnapXml(payload("snapXml").str,
              payload.obj.get("legacyFloatingObjects").map(readLegacyFloatingObjects).getOrElse(Nil))
          case "ProgrammingStatePythonString" => ProgrammingStatePythonString(payload("code").str)
          case "ProgrammingStateJavaString" => ProgrammingStateJavaString(payload("code").str)
          case "ProgrammingStateBeExpression" => ProgrammingStateBeExpression(read[BeExpression](payload("expression")))
          case other => throw IllegalArgumentException(s"Unknown programming state: $other")
        }
      }
    )

  private def subtypeReadWriter[T <: it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingState: ClassTag]: ReadWriter[T] =
    derived$ReadWriter.bimap[T](identity, state => summon[ClassTag[T]].unapply(state)
      .getOrElse(throw IllegalArgumentException("Unexpected programming state type.")))

  def fingerprint(state: it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingState): String =
    state.fingerprint()

  final case class ProgrammingStateBeExpression(expression: BeExpression) extends ProgrammingState {
    override def toBeExpressionState: ProgrammingStateBeExpression = this

    override def toSnapXml: ProgrammingStateSnapXml = ProgrammingStateSnapXmlHelper.fromProgram(BeProgram(expression))

    override def toPython: ProgrammingStatePythonString =
      ProgrammingStatePythonString(SnapTurtlePythonBridge.printedPython(expression))

    override def toJava: ProgrammingStateJavaString =
      ProgrammingStateJavaString(expression.structureInfo.toStringInLanguage(Java, English, false))

    def deriveTurtleCommands: List[TurtleCommand[Double]] = BeExpressionToTurtleCommands(expression)

    def executedTurtleCommands(): List[TurtleCommand[Double]] = deriveTurtleCommands

    override val toString: String = s"ProgrammingStateBeExpression(${expression.toString.take(300)})"

  }

  object ProgrammingStateBeExpression {
    given ReadWriter[ProgrammingStateBeExpression] = subtypeReadWriter[ProgrammingStateBeExpression]
  }

  object ProgrammingState {

    final case class ProgrammingStateSnapXml(snapXml: String, legacyFloatingObjects: List[String] = Nil) extends ProgrammingState {
      require(legacyFloatingObjects != null && legacyFloatingObjects.forall(_ != null), "Invalid legacy Snap data.")

      def hasLegacyFloatingObjects: Boolean = legacyFloatingObjects.nonEmpty

      def withProjectXml(xml: String): ProgrammingStateSnapXml = copy(snapXml = xml)

      /** Drop regenerated preview/pen images without changing scripts or authored costumes. */
      def removeBloatFromXml: ProgrammingStateSnapXml = {
        val cleaned = ProgrammingStateSnapXmlHelper.removeGeneratedImages(snapXml)
        if cleaned == snapXml then this else copy(snapXml = cleaned)
      }

      override def toBeExpressionState: ProgrammingStateBeExpression =
        ProgrammingStateBeExpression(SnapStateConversion.expressionFromXml(snapXml))

      override def toSnapXml: ProgrammingStateSnapXml = this

      private def requireTextConversion(): Unit =
        if hasLegacyFloatingObjects then
          throw IllegalArgumentException("This project contains legacy extra blocks. Keep editing it in Snap until they can be migrated.")

      override def toPython: ProgrammingStatePythonString = { requireTextConversion(); toBeExpressionState.toPython }

      override def toJava: ProgrammingStateJavaString = { requireTextConversion(); toBeExpressionState.toJava }

      override val toString: String = s"ProgrammingStateSnapXml(${snapXml.toString.take(300)})"
    }

    object ProgrammingStateSnapXml {
      given ReadWriter[ProgrammingStateSnapXml] = subtypeReadWriter[ProgrammingStateSnapXml]
      def unapply(state: ProgrammingStateSnapXml): Some[String] = Some(state.snapXml)
      def fromProgram(program: BeProgram, canvasLayout: snap.SnapCanvasLayout = snap.SnapCanvasLayout.empty,
          previousXml: String = ""): ProgrammingStateSnapXml =
        ProgrammingStateSnapXmlHelper.fromProgram(program, canvasLayout, previousXml)
      def mini: ProgrammingStateSnapXml = ProgrammingStateSnapXmlHelper.mini
      def empty: ProgrammingStateSnapXml = ProgrammingStateSnapXmlHelper.empty
      def fingerprint(state: ProgrammingStateSnapXml): String =
        if state.hasLegacyFloatingObjects then state.fingerprint() else state.snapXml
    }

    final case class ProgrammingStatePythonString(code: String) extends ProgrammingState {
      override def toBeExpressionState: ProgrammingStateBeExpression =
        ProgrammingStateBeExpression(BeProgram.fromPythonString(code).fullProgram)

      override def toSnapXml: ProgrammingStateSnapXml =
        SnapTurtlePythonBridge.applyPython(code).fold(message => throw IllegalArgumentException(message), identity)

      override def toPython: ProgrammingStatePythonString = this

      override def toJava: ProgrammingStateJavaString = toBeExpressionState.toJava

      override val toString: String = s"ProgrammingStatePythonString(${code.toString.take(300)})"
    }

    object ProgrammingStatePythonString {
      given ReadWriter[ProgrammingStatePythonString] = subtypeReadWriter[ProgrammingStatePythonString]
    }

    final case class ProgrammingStateJavaString(code: String) extends ProgrammingState {
      def isClassProgram: Boolean = {
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
        while pending.nonEmpty do {
          val node = pending.head
          pending = pending.tail
          node match {
            case _: JavaUnparsableStatement =>
              throw IllegalArgumentException("This Java source contains unsupported or unfinished syntax.")
            case _: JavaClassDef => hasClass = true
            case _ => ()
          }
          pending = node.getChildren().toList ::: pending
        }
        hasClass
      }

      def toLegacyTurtleCommands: List[TurtleCommand[Double]] = {
        if isClassProgram then throw IllegalArgumentException("Full Java classes require the checked Java runner.")
        toBeExpressionState.deriveTurtleCommands
      }

      def toJavaVmProgram: Either[JavaTurtleSource.Diagnostic, JavaTurtleVmPrograms.Program] =
        JavaTurtleVmPrograms.compile(code)

      override def toBeExpressionState: ProgrammingStateBeExpression =
        ProgrammingStateBeExpression(JavaToBeExpressionParser.parse(code))

      override def toSnapXml: ProgrammingStateSnapXml = {
        if isClassProgram then throw IllegalArgumentException("Full Java classes cannot be converted to Snap yet.")
        toBeExpressionState.toSnapXml
      }

      override def toPython: ProgrammingStatePythonString = toBeExpressionState.toPython

      override def toJava: ProgrammingStateJavaString = this

      override val toString: String = s"ProgrammingStateJavaString(${code.toString.take(300)})"
    }

    object ProgrammingStateJavaString {
      given ReadWriter[ProgrammingStateJavaString] = subtypeReadWriter[ProgrammingStateJavaString]
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
