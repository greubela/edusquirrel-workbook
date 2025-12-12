package contentmanagement.model.vm.code.defining

import contentmanagement.model.language.AppLanguage.{Java, JavaScript, Python}
import contentmanagement.model.language.{HumanLanguage, LanguageMap, ProgrammingLanguage}
import contentmanagement.model.vm.code.tree.BeExpressionNode
import contentmanagement.model.vm.code.{BeDefineStructure, BeExpression}
import contentmanagement.model.vm.io.BeExpressionIO
import contentmanagement.model.vm.static.BeExpressionStaticInformation
import contentmanagement.model.vm.types.*
import interactionPlugins.blockEnvironment.programming.blockdisplay.BeBlock

case class BeDefineClass(name: LanguageMap[HumanLanguage], attributes: List[BeDefineVariable], methods: List[BeDefineFunction])
  extends BeDefineStructure {

  override def definedClasses: List[BeDefineClass] = List(this)

  override def expressionIO: BeExpressionIO = new BeExpressionIO() {

    override def getInLanguage(programmingLanguage: ProgrammingLanguage, humanLanguage: HumanLanguage): String = {
      val className = name.getInLanguage(humanLanguage)

      def splitLines(block: String): List[String] = block.split("\n", -1).toList

      def indentLines(lines: List[String], indent: String): List[String] =
        if (lines.forall(_.trim.isEmpty)) List.empty
        else {
          lines.map { line =>
            if (line.trim.isEmpty) ""
            else indent + line
          }
        }

      def ensureDefaultType(dataType: BeDataType, language: ProgrammingLanguage, default: String): String = {
        val rendered = dataType.formatTypeForDisplay.getInLanguage(language).trim
        if (rendered.nonEmpty) rendered else default
      }

      programmingLanguage match {
        case Python =>
          val indent = " " * 4
          val attributeLines = attributes.flatMap { attribute =>
            val attributeName = attribute.name.getInLanguage(humanLanguage)
            val typeHint = attribute.variableType.formatTypeForDisplay.getInLanguage(Python).trim
            val rendered = if (typeHint.nonEmpty) s"$attributeName: $typeHint" else attributeName
            Option.when(rendered.trim.nonEmpty)(indent + rendered)
          }

          val methodBlocks = methods
            .map(method => indentLines(splitLines(method.expressionIO.getInLanguage(Python, humanLanguage)), indent))
            .filter(_.nonEmpty)

          val bodyLines = scala.collection.mutable.ListBuffer[String]()
          bodyLines ++= attributeLines
          if (attributeLines.nonEmpty && methodBlocks.nonEmpty) {
            bodyLines += ""
          }
          methodBlocks.foreach { block =>
            if (bodyLines.nonEmpty && bodyLines.lastOption.exists(_.nonEmpty) && block.headOption.exists(_.nonEmpty)) {
              bodyLines += ""
            }
            bodyLines ++= block
          }

          val finalBody = if (bodyLines.isEmpty) List(indent + "pass") else bodyLines.toList
          (s"class $className:" :: finalBody).mkString("\n")

        case JavaScript =>
          val indent = "  "
          val constructorLines: List[String] =
            if (attributes.nonEmpty) {
              val assignments = attributes.map { attribute =>
                val attributeName = attribute.name.getInLanguage(humanLanguage)
                s"${indent}${indent}this.$attributeName = null;"
              }
              (s"${indent}constructor() {" :: assignments) :+ s"${indent}}"
            } else Nil

          val methodBlocks = methods
            .map { method =>
              val rendered = splitLines(method.expressionIO.getInLanguage(JavaScript, humanLanguage)) match {
                case head :: tail => head.replaceFirst("^function\\s+", "") :: tail
                case Nil => Nil
              }
              indentLines(rendered, indent)
            }
            .filter(_.nonEmpty)

          val bodyLines = scala.collection.mutable.ListBuffer[String]()
          bodyLines ++= constructorLines
          if (constructorLines.nonEmpty && methodBlocks.nonEmpty) {
            bodyLines += ""
          }
          methodBlocks.foreach { block =>
            if (bodyLines.nonEmpty && bodyLines.lastOption.exists(_.nonEmpty) && block.headOption.exists(_.nonEmpty)) {
              bodyLines += ""
            }
            bodyLines ++= block
          }

          val finalBody = if (bodyLines.isEmpty) List(s"$indent// TODO: add members") else bodyLines.toList
          ((s"class $className {" :: finalBody) :+ "}").mkString("\n")

        case Java =>
          val indent = "    "
          val fieldLines = attributes.map { attribute =>
            val attributeName = attribute.name.getInLanguage(humanLanguage)
            val attributeType = ensureDefaultType(attribute.variableType, Java, "Object")
            s"$indent private $attributeType $attributeName;"
          }

          val methodBlocks = methods.map { method =>
            val methodName = method.functionTypeInfo.displayName.getInLanguage(humanLanguage)
            val returnType = method.outputs.map(output => ensureDefaultType(output.variableType, Java, "void")).getOrElse("void")
            val parameters = method.inputs
              .map { input =>
                val paramType = ensureDefaultType(input.variableType, Java, "Object")
                val paramName = input.name.getInLanguage(humanLanguage)
                s"$paramType $paramName"
              }
              .mkString(", ")
            val header = s"$indent public $returnType $methodName($parameters) {"
            val body = indentLines(splitLines(method.body.expressionIO.getInLanguage(Java, humanLanguage)), indent + indent)
            val ensuredBody = if (body.nonEmpty) body else List(s"$indent${indent}// TODO: implement")
            (header :: ensuredBody) :+ s"$indent }"
          }

          val methodBlocksNonEmpty = methodBlocks.filter(_.nonEmpty)
          val bodyLines = scala.collection.mutable.ListBuffer[String]()
          bodyLines ++= fieldLines
          if (fieldLines.nonEmpty && methodBlocksNonEmpty.nonEmpty) {
            bodyLines += ""
          }
          methodBlocksNonEmpty.foreach { block =>
            if (bodyLines.nonEmpty && bodyLines.lastOption.exists(_.nonEmpty) && block.headOption.exists(_.nonEmpty)) {
              bodyLines += ""
            }
            bodyLines ++= block
          }

          val finalBody = if (bodyLines.isEmpty) List(s"$indent// TODO: add members") else bodyLines.toList
          ((s"public class $className {" :: finalBody) :+ "}").mkString("\n")

        case _ => ""
      }
    }

    override def createBlock(): BeBlock =
      throw new NotImplementedError("Block rendering is not implemented for BeDefineClass")

  }

  override def staticInformationExpression: BeExpressionStaticInformation = new BeExpressionStaticInformation() {

    override def syntaxErrors: Seq[BeInfo] = methods.flatMap(curMethod => {
      val inClass = curMethod.functionTypeInfo.isMethodInClass
      if (inClass.isEmpty)
        Some(BeInfo(LanguageMap.universalMap("Method must have an object it´s called on!"), BeInfo.SyntaxError.StructureMismatch))
      else if (inClass.get != this)
        Some(BeInfo(LanguageMap.universalMap("Method must live in the class its defined in!"), BeInfo.SyntaxError.StructureMismatch))
      else None
    })

    override def hasSideEffects: Boolean = true

  }



}
