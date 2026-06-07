package todomove.datastructures.core.vm.code.defining

import it.evadid.core.datastructures.language.AppLanguage.{Cpp, Java, JavaScript, Lisp, Python}

import it.evadid.core.datastructures.language.*
import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.programming.blockdisplay.BeBlock
import todomove.datastructures.core.vm.code.{BeDefineStructure, BeExpression}
import todomove.datastructures.core.vm.code.tree.BeExpressionNode
import todomove.datastructures.core.vm.io.BeExpressionIO
import todomove.datastructures.core.vm.static.BeExpressionStaticInformation
import todomove.datastructures.core.vm.types.{BeDataType, BeInfo}

case class BeDefineClass(
    name: LanguageMap[HumanLanguage],
    attributes: List[BeDefineVariable],
    methods: List[BeDefineFunction],
    bodyExtras: List[BeExpression] = Nil
)
  extends BeDefineStructure {

  override def definedClasses: List[BeDefineClass] = List(this)

  override def expressionIO: BeExpressionIO = new BeExpressionIO() {

    override def getInLanguage(programmingLanguage: ProgrammingLanguage, humanLanguage: HumanLanguage, skipUnparsable: Boolean = false): String = {
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

          val extraBlocks = bodyExtras
            .flatMap(extra => indentLines(splitLines(extra.expressionIO.getInLanguage(Python, humanLanguage, skipUnparsable)), indent))
            .filter(_.nonEmpty)

          val methodBlocks = methods
            .map(method => indentLines(splitLines(method.expressionIO.getInLanguage(Python, humanLanguage, skipUnparsable)), indent))
            .filter(_.nonEmpty)

          val bodyLines = scala.collection.mutable.ListBuffer[String]()
          bodyLines ++= attributeLines
          if (attributeLines.nonEmpty && (extraBlocks.nonEmpty || methodBlocks.nonEmpty)) {
            bodyLines += ""
          }
          bodyLines ++= extraBlocks
          if (bodyLines.nonEmpty && bodyLines.lastOption.exists(_.nonEmpty) && methodBlocks.nonEmpty) {
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
              val rendered = splitLines(method.expressionIO.getInLanguage(programmingLanguage, humanLanguage, skipUnparsable)) match {
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

        case Java | Cpp =>
          val indent = "    "
          val fieldLines = attributes.map { attribute =>
            val attributeName = attribute.name.getInLanguage(humanLanguage)
            val defaultType = if (programmingLanguage == Java) "Object" else "auto"
            val attributeType = ensureDefaultType(attribute.variableType, programmingLanguage, defaultType)
            val visibility = if (programmingLanguage == Java) "private " else ""
            s"$indent${visibility}$attributeType $attributeName;"
          }

          val methodBlocks = methods.map { method =>
            val methodName = method.functionTypeInfo.displayName.getInLanguage(humanLanguage)
            val defaultReturnType = if (programmingLanguage == Java) "void" else "auto"
            val returnType = method.outputs.map(output => ensureDefaultType(output.variableType, programmingLanguage, defaultReturnType)).getOrElse(defaultReturnType)
            val parameters = method.inputs
              .map { input =>
                val paramType = ensureDefaultType(input.variableType, programmingLanguage, if (programmingLanguage == Java) "Object" else "auto")
                val paramName = input.name.getInLanguage(humanLanguage)
                s"$paramType $paramName"
              }
              .mkString(", ")
            val visibility = if (programmingLanguage == Java) "public " else ""
            val header = s"$indent$visibility$returnType $methodName($parameters) {"
            val body = indentLines(splitLines(method.body.expressionIO.getInLanguage(programmingLanguage, humanLanguage, skipUnparsable)), indent + indent)
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
          val classHeader = if (programmingLanguage == Java) s"public class $className {" else s"class $className {"
          (classHeader :: (finalBody :+ "}")).mkString("\n")

        case Lisp =>
          val slotNames = attributes.map(_.name.getInLanguage(humanLanguage).toLowerCase).mkString(" ")
          val classLine = s"(defclass ${className.toLowerCase} () ($slotNames))"
          val methodLines = methods.map(_.expressionIO.getInLanguage(programmingLanguage, humanLanguage, skipUnparsable)).filter(_.trim.nonEmpty)
          if (methodLines.isEmpty) classLine else classLine + "\n" + methodLines.mkString("\n")

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
