package contentmanagement.model.vm.code.defining

import contentmanagement.model.language.AppLanguage.{Java, JavaScript, Lisp, Python, Rust}
import contentmanagement.model.language.{HumanLanguage, LanguageMap, ProgrammingLanguage}
import contentmanagement.model.vm.code.{BeDefineStructure, BeExpression}
import contentmanagement.model.vm.types.{BeChildPosition, BeChildRole, BeDataType, BeInfo}
import interactionPlugins.blockEnvironment.config.BeDisplayConfig
import interactionPlugins.blockEnvironment.programming.blocks.BeBlock
import util.CodeStringBuilder

case class BeDefineClass(name: LanguageMap[HumanLanguage], attributes: List[BeDefineVariable], methods: List[BeDefineFunction]) extends BeDefineStructure {

  override def definedClasses: List[BeDefineClass] = List(this)

  override def getInLanguage(programmingLanguage: ProgrammingLanguage, humanLanguage: HumanLanguage): String = {
    val className = name.getInLanguage(humanLanguage)
    val attributeStrings = attributes.map(attr => (attr, formatAttribute(programmingLanguage, humanLanguage, attr)))
    val methodStrings = methods.map(_.getInLanguage(programmingLanguage, humanLanguage))
    programmingLanguage match {
      case Python =>
        val builder = CodeStringBuilder()
          .appendNextLine(s"class $className:")
          .changeIntLevel(1)
        if (attributeStrings.nonEmpty)
          attributeStrings.foreach { case (_, attrLine) => builder.appendNextLine(attrLine) }
        methodStrings.foreach(builder.appendAsLines)
        if (attributeStrings.isEmpty && methodStrings.isEmpty) builder.appendNextLine("pass")
        builder.toString
      case Java =>
        val builder = CodeStringBuilder()
          .appendNextLine(s"class $className {")
          .changeIntLevel(1)
        attributeStrings.foreach { case (_, attrLine) => builder.appendNextLine(attrLine) }
        methodStrings.foreach(builder.appendAsLines)
        builder.changeIntLevel(-1)
          .appendNextLine("}")
          .toString
      case JavaScript =>
        val builder = CodeStringBuilder()
          .appendNextLine(s"class $className {")
          .changeIntLevel(1)
        if (attributeStrings.nonEmpty) {
          builder.appendNextLine("constructor() {")
            .changeIntLevel(1)
          attributeStrings.foreach { case (attr, _) =>
            builder.appendNextLine(s"this.${attr.name.getInLanguage(humanLanguage)} = null;")
          }
          builder.changeIntLevel(-1)
            .appendNextLine("}")
        }
        methodStrings.foreach { methodString => builder.appendAsLines(stripJavaScriptFunctionKeyword(methodString)) }
        if (attributeStrings.isEmpty && methodStrings.isEmpty) builder.appendNextLine("constructor() {}")
        builder.changeIntLevel(-1)
          .appendNextLine("}")
          .toString
      case Rust =>
        val structBuilder = CodeStringBuilder()
          .appendNextLine(s"struct ${sanitizeRustName(className)} {")
          .changeIntLevel(1)
        attributeStrings.foreach { case (_, attrLine) => structBuilder.appendNextLine(attrLine) }
        structBuilder.changeIntLevel(-1)
          .appendNextLine("}")
        val implBuilder = CodeStringBuilder()
          .appendNextLine(s"impl ${sanitizeRustName(className)} {")
          .changeIntLevel(1)
        methodStrings.foreach(implBuilder.appendAsLines)
        implBuilder.changeIntLevel(-1)
          .appendNextLine("}")
        Seq(structBuilder.toString, implBuilder.toString).mkString("\n")
      case Lisp =>
        val slots = if (attributeStrings.isEmpty) "()" else attributeStrings.map(_._2).mkString("(", " ", ")")
        ((s"(defclass ${className.toLowerCase} () $slots)" +: methodStrings)).mkString("\n")
      case _ =>
        val builder = CodeStringBuilder(s"BeDefineClass($className)")
          .changeIntLevel(1)
        attributeStrings.foreach { case (_, attrLine) => builder.appendNextLine(attrLine) }
        methodStrings.foreach(builder.appendAsLines)
        builder.changeIntLevel(-1)
          .appendNextLine(")")
          .toString
    }
  }

  override def getSyntaxErrors: Seq[BeInfo] = {

    methods.flatMap(curMethod => {
      val inClass = curMethod.functionTypeInfo.isMethodInClass
      if (inClass.isEmpty)
        Some(BeInfo(LanguageMap.universalMap("Method must have an object it´s called on!"), BeInfo.SyntaxError.StructureMismatch))
      else if (inClass.get != this)
        Some(BeInfo(LanguageMap.universalMap("Method must live in the class its defined in!"), BeInfo.SyntaxError.StructureMismatch))
      else None
    })
  }

  override def createBlock(config: BeDisplayConfig, parentPos: BeChildPosition): BeBlock =
    throw new NotImplementedError("Block rendering is not implemented for BeDefineClass")

  override def getChildren: List[(BeChildRole, BeExpression)] = List()

  private def formatAttribute(programmingLanguage: ProgrammingLanguage, humanLanguage: HumanLanguage, variable: BeDefineVariable): String = {
    val typeLabel = programmingLanguage match {
      case Java => languageSpecificType(programmingLanguage, variable.canEvaluateTo)
      case Rust => languageSpecificType(programmingLanguage, variable.canEvaluateTo)
      case _ => ""
    }
    programmingLanguage match {
      case Java => s"$typeLabel ${variable.name.getInLanguage(humanLanguage)};"
      case Rust => s"${variable.name.getInLanguage(humanLanguage)}: $typeLabel,".replaceAll(",,", ",")
      case Lisp => variable.name.getInLanguage(humanLanguage)
      case _ => variable.name.getInLanguage(humanLanguage)
    }
  }

  private def languageSpecificType(language: ProgrammingLanguage, possibleTypes: Set[BeDataType]): String = {
    val resolvedType = possibleTypes.find(_ != BeDataType.Unit).orElse(possibleTypes.headOption).getOrElse(BeDataType.Unit)
    language match {
      case Java =>
        resolvedType match {
          case BeDataType.Numeric => "double"
          case BeDataType.Boolean => "boolean"
          case BeDataType.String => "String"
          case BeDataType.Date => "java.time.LocalDate"
          case BeDataType.Unit => "void"
          case _ => "Object"
        }
      case Rust =>
        resolvedType match {
          case BeDataType.Numeric => "f64"
          case BeDataType.Boolean => "bool"
          case BeDataType.String => "String"
          case BeDataType.Date => "chrono::NaiveDate"
          case BeDataType.Unit => "()"
          case _ => "()"
        }
      case _ => resolvedType.toString
    }
  }

  private def stripJavaScriptFunctionKeyword(method: String): String = {
    val lines = method.split("\n", -1).toList
    lines match {
      case head :: tail if head.trim.startsWith("function ") =>
        val leadingWhitespace = head.takeWhile(_.isWhitespace)
        val withoutKeyword = head.trim.stripPrefix("function ")
        val updatedHead = leadingWhitespace + withoutKeyword
        (updatedHead :: tail).mkString("\n")
      case _ => method
    }
  }

  private def sanitizeRustName(name: String): String =
    if (name.nonEmpty && name.head.isUpper) name.head.toLower + name.tail else name
}
