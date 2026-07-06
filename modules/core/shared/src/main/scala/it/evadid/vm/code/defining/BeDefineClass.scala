package it.evadid.vm.code.defining

import it.evadid.core.datastructures.language.*
import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.vm.code.controlStructures.BeSequence
import it.evadid.vm.code.{BeDefineStructure, BeExpression}
import it.evadid.vm.io.BeExpressionIO
import it.evadid.vm.naming.{BeEntityName, CodeRepresentationConfig}
import it.evadid.vm.static.BeExpressionStaticInformation
import it.evadid.vm.types.{BeDataType, BeInfo}

case class BeDefineClass(
                          name: BeEntityName,
                          attributes: List[BeDefineVariable],
                          methods: List[BeDefineFunction],
                          bodyExtras: List[BeExpression] = Nil
                        )
  extends BeDefineStructure {

  override def definedClasses: List[BeDefineClass] = List(this)

  override def expressionIO: BeExpressionIO = new BeExpressionIO() {

    override def toStringWithConfig(config: CodeRepresentationConfig): String = {
      import config.*
      val className = name.getNameIn(humanLanguage, config.namingStyle)

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
            val attributeName = attribute.name.getNameIn(humanLanguage, config.namingStyle)
            val typeHint = attribute.variableType.formatTypeForDisplay.getInLanguage(Python).trim
            val rendered = if (typeHint.nonEmpty) s"$attributeName: $typeHint" else attributeName
            Option.when(rendered.trim.nonEmpty)(indent + rendered)
          }

          val extraBlocks = bodyExtras
            .flatMap(extra => indentLines(splitLines(extra.expressionIO.toStringInLanguage(Python, humanLanguage, skipUnparsable)), indent))
            .filter(_.nonEmpty)

          val methodBlocks = methods
            .map(method => indentLines(splitLines(method.expressionIO.toStringInLanguage(Python, humanLanguage, skipUnparsable)), indent))
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
                val attributeName = attribute.name.getNameIn(humanLanguage, config.namingStyle)
                s"${indent}${indent}this.$attributeName = null;"
              }
              (s"${indent}constructor() {" :: assignments) :+ s"${indent}}"
            } else Nil

          val methodBlocks = methods
            .map { method =>
              val rendered = splitLines(method.expressionIO.toStringInLanguage(programmingLanguage, humanLanguage, skipUnparsable)) match {
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
            val attributeName = attribute.name.getNameIn(humanLanguage, config.namingStyle)
            val defaultType = if (programmingLanguage == Java) "Object" else "auto"
            val attributeType = ensureDefaultType(attribute.variableType, programmingLanguage, defaultType)
            val visibility = if (programmingLanguage == Java) "private " else ""
            s"$indent${visibility}$attributeType $attributeName;"
          }

          val methodBlocks = methods.map { method =>
            val methodName = method.functionTypeInfo.displayName.getNameIn(humanLanguage, config.namingStyle)
            val defaultReturnType = if (programmingLanguage == Java) "void" else "auto"
            val returnType = method.outputs.map(output => ensureDefaultType(output.variableType, programmingLanguage, defaultReturnType)).getOrElse(defaultReturnType)
            val parameters = method.inputs
              .map { input =>
                val paramType = ensureDefaultType(input.variableType, programmingLanguage, if (programmingLanguage == Java) "Object" else "auto")
                val paramName = input.name.getNameIn(humanLanguage, config.namingStyle)
                s"$paramType $paramName"
              }
              .mkString(", ")
            val visibility = if (programmingLanguage == Java) "public " else ""
            val header = s"$indent$visibility$returnType $methodName($parameters) {"
            val body = indentLines(splitLines(method.body.expressionIO.toStringInLanguage(programmingLanguage, humanLanguage, skipUnparsable)), indent + indent)
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
          val slotNames = attributes.map(_.name.getNameIn(humanLanguage, config.namingStyle).toLowerCase).mkString(" ")
          val classLine = s"(defclass ${className.toLowerCase} () ($slotNames))"
          val methodLines = methods.map(_.expressionIO.toStringInLanguage(programmingLanguage, humanLanguage, skipUnparsable)).filter(_.trim.nonEmpty)
          if (methodLines.isEmpty) classLine else classLine + "\n" + methodLines.mkString("\n")

        case _ => ""
      }
    }


  }

  override def staticInformationExpression: BeExpressionStaticInformation = new BeExpressionStaticInformation() {

    override def syntaxErrors: Seq[BeInfo] = methods.flatMap(curMethod => {
      val inClass = curMethod.functionTypeInfo.isMethodInClass
      if (inClass.isEmpty)
        Some(BeInfo(LanguageMap.universalMap("Method must have an object it´s called on!"), BeInfo.SyntaxError.StructureMismatch))
      else if (inClass.get.name.universalInterpretation() != BeDefineClass.this.name.universalInterpretation())
        Some(BeInfo(LanguageMap.universalMap("Method must live in the class its defined in!"), BeInfo.SyntaxError.StructureMismatch))
      else None
    })

    override def hasSideEffects: Boolean = true

  }


}

object BeDefineClass {
  case class MethodSignature(name: BeEntityName, inputs: List[BeDefineVariable], output: Option[BeDefineVariable])

  def apply(name: LanguageMap[HumanLanguage], attributes: List[BeDefineVariable], methods: List[BeDefineFunction], bodyExtras: List[BeExpression]): BeDefineClass =
    BeDefineClass(BeEntityName.fromMapInCodeNotation(name.asInstanceOf[LanguageMap[HumanLanguage]]), attributes, methods, bodyExtras)

  def fromLanguageMap(name: LanguageMap[HumanLanguage], attributes: List[BeDefineVariable], methods: List[BeDefineFunction], bodyExtras: List[BeExpression] = Nil): BeDefineClass =
    apply(name, attributes, methods, bodyExtras)

  def withMethods(name: BeEntityName, attributes: List[BeDefineVariable], methodSignatures: List[MethodSignature], bodyExtras: List[BeExpression] = Nil): BeDefineClass = {
    val ownerInfoClass = BeDefineClass(name, attributes, Nil, bodyExtras)
    val methods = methodSignatures.map { signature =>
      BeDefineFunction(
        signature.inputs,
        signature.output,
        BeSequence.optionalBody(List(BeExpression.pass)),
        BeDefineFunction.methodFunctionInfo(ownerInfoClass, signature.name)
      )
    }
    BeDefineClass(name, attributes, methods, bodyExtras)
  }
}
