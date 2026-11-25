package it.evadid.vm.conversion

import it.evadid.core.datastructures.language.AppLanguage.{Java, Python, SnapLanguage}
import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.vm.code.errors.{BeExpressionUnparsable, BeExpressionUnsupported, ProgrammingConversionException, ProgrammingConversionKind}
import it.evadid.vm.conversion.expressionjava.ExpressionToJava
import it.evadid.vm.conversion.expressionpython.ExpressionToPython
import it.evadid.vm.conversion.expressionsnap.ExpressionToSnap
import it.evadid.vm.conversion.printing.ModuleLayout
import it.evadid.vm.conversion.structured.PreparedProgramSource
import it.evadid.vm.parsing.source.{JavaSource, PythonSource, SourceParsing}
import it.evadid.vm.parsing.source.SourceParsing.SourceLanguage
import it.evadid.vm.conversion.snapexpression.SnapToExpression
import it.evadid.vm.conversion.snapmodelxml.SnapModelToXml.*
import it.evadid.workbook.elements.interactionElements.programming.state.*

/** Owned by one immutable state and the environment it is read in. Views and graders share its parsing and
  * projections. Library calls bind only to the libraries `environment` provides ([[dependencies.ProgrammingEnvironment]]).
  */
final class ProgrammingStateDerivations private[evadid](source: ProgrammingState, environment: dependencies.ProgrammingEnvironment) {
  private lazy val preparedSource = source match {
    case ProgrammingStateRaw(Java, code) => PreparedProgramSource(code, Java)
    case value: ProgrammingStateJavaStructured => PreparedProgramSource(value.toString, Java)
    case ProgrammingStateRaw(Python, code) => PreparedProgramSource(code, Python)
    case value: ProgrammingStatePythonStructured => PreparedProgramSource(value.toString, Python)
    case _ => PreparedProgramSource("", Seq.empty)
  }

  /** The Java text of this state, read by the parsing pipeline ([[SourceParsing]]). */
  lazy val javaSource: JavaSource = SourceParsing.parse(source match {
    case ProgrammingStateRaw(Java, code) => code
    case value: ProgrammingStateJavaStructured => value.toString
    case _ => java.code
  }, SourceLanguage.Java)

  /** The Python text this state converts from: classes hoisted before module code. */
  private lazy val pythonSource: PythonSource = SourceParsing.parse(preparedSource.code, SourceLanguage.Python)

  private lazy val snapSource = SourceParsing.parse(source match {
    case ProgrammingStateRaw(SnapLanguage, code) => code
    case _ => snap.code
  }, SourceLanguage.Snap)

  lazy val expression: ProgrammingStateBeExpression = source match {
    case value: ProgrammingStateBeExpression => value
    case ProgrammingStateRaw(Java, _) | _: ProgrammingStateJavaStructured => javaSource.parsed(environment).fold(error => throw error, _.expression)
    case ProgrammingStateRaw(Python, _) | _: ProgrammingStatePythonStructured => ProgrammingStateBeExpression(pythonSource.parsed(environment).program.fullProgram)
    case ProgrammingStateRaw(SnapLanguage, xml) =>
      if xml.trim.nonEmpty then snapSource.syntax.left.foreach(message => throw ProgrammingConversionException.unparsable(message))
      ProgrammingStateBeExpression(snapSource.parsed.expression)
    case ProgrammingStateSnapScalaModel(model) => ProgrammingStateBeExpression(SnapToExpression.convert(model).expression)
    case ProgrammingStateRaw(language, _) => throw UnsupportedOperationException(s"Parsing $language source into expressions is not supported.")
  }
  lazy val python: ProgrammingStateRaw = source match {
    case value @ ProgrammingStateRaw(Python, _) => value
    case value: ProgrammingStatePythonStructured => ProgrammingStateRaw(Python, value.toString)
    case _ => ExpressionToPython.convert(expression.expression)
  }
  lazy val java: ProgrammingStateRaw = source match {
    case value @ ProgrammingStateRaw(Java, _) => value
    case value: ProgrammingStateJavaStructured => ProgrammingStateRaw(Java, value.toString)
    case _ => ExpressionToJava.convert(expression.expression)
  }
  lazy val snap: ProgrammingStateRaw = source match {
    case value @ ProgrammingStateRaw(SnapLanguage, xml) if xml.trim.nonEmpty => value
    case ProgrammingStateSnapScalaModel(model) => ProgrammingStateRaw(SnapLanguage, model.toXml)
    case _ => ExpressionToSnap.convert(expression.expression)
  }
  lazy val javaStructured: ProgrammingStateJavaStructured = source match {
    case value: ProgrammingStateJavaStructured => value
    case ProgrammingStateRaw(Java, _) => ProgrammingStateJavaStructured(preparedSource.code)
    case _ => ProgrammingStateJavaStructured(java.code)
  }
  lazy val pythonStructured: ProgrammingStatePythonStructured = source match {
    case value: ProgrammingStatePythonStructured => value
    case ProgrammingStateRaw(Python, _) => ProgrammingStatePythonStructured(preparedSource.code)
    case _ => ProgrammingStatePythonStructured(python.code)
  }
  lazy val snapModel: Either[String, ProgrammingStateSnapScalaModel] = source match {
    case value: ProgrammingStateSnapScalaModel => Right(value)
    case ProgrammingStateRaw(SnapLanguage, code) if code.trim.isEmpty =>
      SourceParsing.parse(snap.code, SourceLanguage.Snap).syntax.map(ProgrammingStateSnapScalaModel(_))
    case _ => snapSource.syntax.map(ProgrammingStateSnapScalaModel(_))
  }
  /** Whether the source declares classes; unreadable source has no answer and fails like its conversion. */
  lazy val isJavaClass: Boolean = javaSource.syntax
    .left.map(failure => ProgrammingConversionException.unparsable("This Java source cannot be read as a complete program or fragment.",
      Some(failure.line), Some(failure.column)))
    .flatMap(_.readable).fold(error => throw error,
      _.program.statements.exists(_.statement.isInstanceOf[it.evadid.vm.parsing.java.clean.model.JavaAST.JavaClassDef]))
  lazy val javaExecutionProgram = it.evadid.vm.compilation.java.turtle.JavaTurtleCompiler.compile(javaSource.source)

  def convertToLanguage(language: ProgrammingLanguage): ConversionResult = {
    try {
      if language != source.associatedLanguage then requireSharedForm(language)
      val target = inLanguage(language)
      // Integer declarations in the program itself; library classes, casts and repeat counters do not count.
      // Scala.js regular expressions support neither look-behind nor \p{L}: the optional group marks
      // repeat counters, and the letter range is explicit.
      val integerDeclaration = "(for\\s*\\(\\s*)?\\b(?:byte|short|int|long)\\s+[A-Za-z_$\\u00C0-\\uFFFF]".r
      val numericWarnings = Option.when(source.associatedLanguage == Java && language != Java &&
          integerDeclaration.findAllMatchIn(javaSource.structured.codeMasked).exists(_.group(1) == null)) {
        ConversionInfo("Java integer types were widened to shared numeric values; integer overflow and truncating division are not preserved.",
          it.evadid.util.logging.LoggingLevel.WARN, true)
      }.toSeq
      val remainderWarnings = Option.when((source.associatedLanguage == Java) != (language == Java) &&
          expression.expression.subtree.exists {
            case call: it.evadid.vm.code.usage.BeFunctionCall => call.isOperator && call.funcDef.functionTypeInfo.displayName.universalInterpretation() == "%"
            case _ => false
          }) {
        ConversionInfo("Java's % keeps the sign of the dividend, while Python's and Snap's keep the sign of the divisor; results differ for negative numbers.",
          it.evadid.util.logging.LoggingLevel.WARN, false)
      }.toSeq
      val droppedComments = Option.when(language == SnapLanguage && source.associatedLanguage != SnapLanguage &&
          expression.expression.subtree.exists(_.isInstanceOf[it.evadid.vm.code.errors.BeSingleLineComment])) {
        ConversionInfo("Snap scripts have no comment statements; comments were not converted.", it.evadid.util.logging.LoggingLevel.INFO, false)
      }.toSeq
      ConversionResult(source, Some(target), preparedSource.diagnostics ++ numericWarnings ++ remainderWarnings ++ droppedComments)
    }
    catch { case scala.util.control.NonFatal(error) => ConversionResult.failed(source, error) }
  }

  /** Unfinished or unsupported source keeps its text within its own language, but has no form in
    * another one. The first such part is reported with its kind and, for text sources, its position.
    */
  private def requireSharedForm(target: ProgrammingLanguage): Unit = {
    val text = source match {
      case ProgrammingStateRaw(Python | Java, _) | _: ProgrammingStateJavaStructured | _: ProgrammingStatePythonStructured => preparedSource.code
      case _ => ""
    }
    def firstLine(fragment: String) = fragment.trim.linesIterator.nextOption().getOrElse("").trim
    expression.expression.subtree.collectFirst {
      case BeExpressionUnparsable(fragment, reason, Some(origin)) if origin != target =>
        (ProgrammingConversionKind.Unparsable, fragment, s"$reason: ${firstLine(fragment)}")
      case BeExpressionUnsupported(fragment, Some(origin), reason) if origin != target =>
        (ProgrammingConversionKind.UnsupportedConstruct, fragment,
          if reason.nonEmpty then s"$reason: ${firstLine(fragment)}" else s"${firstLine(fragment)} has no form in $target.")
    }.foreach { (kind, fragment, message) =>
      throw ProgrammingConversionException.at(kind, message, text, Option(text.indexOf(fragment.trim)).filter(index => index >= 0 && fragment.trim.nonEmpty))
    }
  }

  private def inLanguage(language: ProgrammingLanguage): ProgrammingState = language match {
    case Python => pythonStructured
    case Java => javaStructured
    case SnapLanguage => snapModel.fold(message => throw ProgrammingConversionException.unparsable(message), _.persistent)
    case BlockDisplay => expression
    case _ => throw UnsupportedOperationException(s"Programming language '$language' cannot be converted.")
  }
}
