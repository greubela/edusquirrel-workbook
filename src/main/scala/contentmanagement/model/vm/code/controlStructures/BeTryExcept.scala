package contentmanagement.model.vm.code.controlStructures

import contentmanagement.model.language.AppLanguage.{Java, JavaScript, Lisp, Python, Rust}
import contentmanagement.model.language.{HumanLanguage, LanguageMap, ProgrammingLanguage}
import contentmanagement.model.vm.code.{BeControlStructure, BeExpression}
import contentmanagement.model.vm.types.{BeChildPosition, BeChildRole, BeInfo}
import interactionPlugins.blockEnvironment.config.BeDisplayConfig
import interactionPlugins.blockEnvironment.programming.blocks.BeBlock
import util.CodeStringBuilder

case class BeTryExcept(tryBody: BeSequence,
                       exceptBlocks: List[BeTryExcept.ExceptBlock],
                       finallyBody: Option[BeSequence]) extends BeControlStructure {

  override def allPossibleBodies: List[BeExpression] =
    tryBody :: exceptBlocks.map(_.body) ++ finallyBody.toList

  override def getInLanguage(programmingLanguage: ProgrammingLanguage, humanLanguage: HumanLanguage): String = {
    val tryString = tryBody.getInLanguage(programmingLanguage, humanLanguage)
    val exceptStrings = exceptBlocks.map { block =>
      val condition = block.exception.map(_.getInLanguage(programmingLanguage, humanLanguage).replaceAll("\n", "")).getOrElse("")
      val bodyString = block.body.getInLanguage(programmingLanguage, humanLanguage)
      (condition, bodyString)
    }
    val finallyString = finallyBody.map(_.getInLanguage(programmingLanguage, humanLanguage))

    programmingLanguage match {
      case Python =>
        val builder = CodeStringBuilder()
          .appendNextLine("try:")
          .changeIntLevel(1)
          .appendAsLines(tryString)
          .changeIntLevel(-1)
        exceptStrings.foreach { case (cond, body) =>
          val header = if (cond.isEmpty) "except:" else s"except $cond:"
          builder.appendNextLine(header)
            .changeIntLevel(1)
            .appendAsLines(body)
            .changeIntLevel(-1)
        }
        finallyString.foreach { body =>
          builder.appendNextLine("finally:")
            .changeIntLevel(1)
            .appendAsLines(body)
            .changeIntLevel(-1)
        }
        builder.toString
      case Java =>
        val builder = CodeStringBuilder()
          .appendNextLine("try {")
          .changeIntLevel(1)
          .appendAsLines(tryString)
          .changeIntLevel(-1)
          .appendNextLine("}")
        exceptStrings.zipWithIndex.foreach { case ((cond, body), idx) =>
          val exceptionName = if (cond.isEmpty) s"Exception e$idx" else cond
          builder.appendNextLine(s"catch ($exceptionName) {")
            .changeIntLevel(1)
            .appendAsLines(body)
            .changeIntLevel(-1)
            .appendNextLine("}")
        }
        finallyString.foreach { body =>
          builder.appendNextLine("finally {")
            .changeIntLevel(1)
            .appendAsLines(body)
            .changeIntLevel(-1)
            .appendNextLine("}")
        }
        builder.toString
      case JavaScript =>
        val builder = CodeStringBuilder()
          .appendNextLine("try {")
          .changeIntLevel(1)
          .appendAsLines(tryString)
          .changeIntLevel(-1)
          .appendNextLine("}")
        exceptStrings.zipWithIndex.foreach { case ((cond, body), idx) =>
          val exceptionName = if (cond.isEmpty) s"err$idx" else cond
          builder.appendNextLine(s"catch ($exceptionName) {")
            .changeIntLevel(1)
            .appendAsLines(body)
            .changeIntLevel(-1)
            .appendNextLine("}")
        }
        finallyString.foreach { body =>
          builder.appendNextLine("finally {")
            .changeIntLevel(1)
            .appendAsLines(body)
            .changeIntLevel(-1)
            .appendNextLine("}")
        }
        builder.toString
      case Rust =>
        val builder = CodeStringBuilder()
          .appendNextLine("match std::panic::catch_unwind(|| {")
          .changeIntLevel(1)
          .appendAsLines(tryString)
          .changeIntLevel(-1)
          .appendNextLine("}) {")
          .changeIntLevel(1)
          .appendNextLine("Ok(_) => {} ,")
        exceptStrings.zipWithIndex.foreach { case ((cond, body), idx) =>
          val label = if (cond.isEmpty) s"Err(err$idx)" else s"Err($cond)"
          builder.appendNextLine(s"$label => {")
            .changeIntLevel(1)
            .appendAsLines(body)
            .changeIntLevel(-1)
            .appendNextLine("},")
        }
        finallyString match {
          case Some(body) =>
            builder.appendNextLine("_ => {")
              .changeIntLevel(1)
              .appendAsLines(body)
              .changeIntLevel(-1)
              .appendNextLine("}")
          case None =>
            builder.appendNextLine("_ => {}")
        }
        builder.changeIntLevel(-1)
          .appendNextLine("}")
          .toString
      case Lisp =>
        val trySection = tryString.split("\n").mkString("(progn ", " ", ")")
        val handlers = exceptStrings.map { case (cond, body) =>
          val handlerName = if (cond.isEmpty) "error" else cond.toLowerCase
          s"($handlerName () ${body.replace('\n', ' ')})"
        }
        val handlerCase = s"(handler-case $trySection ${handlers.mkString(" ")})"
        val lines = handlerCase :: finallyString.map(body => s"(unwind-protect (progn) ${body.replace('\n', ' ')})").toList
        lines.mkString("\n")
      case _ =>
        CodeStringBuilder("TRY/EXCEPT")
          .changeIntLevel(1)
          .appendAsLines(tryString)
          .changeForEach(exceptStrings, (builder, tuple) => builder.appendAsLines(tuple._2))
          .changeIntLevel(-1)
          .toString
    }
  }

  override def getSyntaxErrors: Seq[BeInfo] = {
    val baseErrors = tryBody.getSyntaxErrors ++ exceptBlocks.flatMap(block => block.body.getSyntaxErrors ++ block.exception.toSeq.flatMap(_.getSyntaxErrors)) ++ finallyBody.map(_.getSyntaxErrors).getOrElse(List())
    val structuralError =
      if (exceptBlocks.isEmpty && finallyBody.isEmpty)
        List(BeInfo(LanguageMap.universalMap("try/except requires at least one except block or a finally block"), BeInfo.SyntaxError.StructureMismatch))
      else
        List()
    baseErrors ++ structuralError
  }

  override def createBlock(config: BeDisplayConfig, childPos: BeChildPosition): BeBlock =
    throw new NotImplementedError("Block rendering is not implemented for BeTryExcept")

  override def getChildren: List[(BeChildRole, BeExpression)] = {
    val tryChild = List((BeChildRole.BodySequence(0), tryBody))
    val exceptChildren = exceptBlocks.zipWithIndex.flatMap { case (block, idx) =>
      val conditionChild = block.exception.map(expr => (BeChildRole.ExpressionInBody(idx), expr)).toList
      val bodyChild = (BeChildRole.BodySequence(idx + 1), block.body)
      conditionChild :+ bodyChild
    }
    val finallyChild = finallyBody.map(seq => (BeChildRole.BodySequence(exceptBlocks.length + 1), seq)).toList
    tryChild ++ exceptChildren ++ finallyChild
  }
}

object BeTryExcept {
  case class ExceptBlock(exception: Option[BeExpression], body: BeSequence)
}
