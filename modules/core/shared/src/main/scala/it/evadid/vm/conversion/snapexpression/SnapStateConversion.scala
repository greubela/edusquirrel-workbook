package it.evadid.vm.conversion.snapexpression

import it.evadid.vm.conversion.snapxmlmodel.SnapScalaXmlCodec
import it.evadid.vm.code.abstractions.BeExpression
import it.evadid.workbook.elements.interactionElements.programming.state.snap.{SnapScalaModel, SnapTurtleCatalog}



/** Shared primitive decoding and the verified subset for Python unit-test execution. */
object SnapStateConversion {

  def expressionForExecution(xml: String): Either[String, BeExpression] = for {
    document <- SnapScalaXmlCodec.parse(xml)
    project <- document.project.filter(_ == document.root).toRight("Unit tests need a complete Snap project.")
    _ <- Either.cond(project.scenes.size <= 1 && project.element.named("scenes").size <= 1, (),
      "Unit tests currently support one Snap scene.")
    _ <- Either.cond(project.element.named("stage").isEmpty || project.element.named("scenes").isEmpty, (),
      "A Snap project cannot mix legacy and scene-based stages for unit tests.")
    stages = project.element.named("stage") ++ project.scenes.flatMap(_.element.named("stage"))
    _ <- Either.cond(stages.size == 1, (), "Unit tests currently support one Snap scene.")
    stage <- stages.head match {
      case value: SnapScalaModel.Stage => Right(value)
      case _ => Left("Unit tests need a Snap stage.")
    }
    _ <- Either.cond(stage.sprites.size <= 1, (), "Unit tests currently support one Snap sprite.")
    owners = Vector[SnapScalaModel.SnapElement](stage) ++ stage.sprites
    _ <- Either.cond(stage.element.named("sprites").size <= 1 && owners.forall(_.element.named("scripts").size <= 1), (),
      "Ambiguous Snap script containers cannot be executed by unit tests.")
    _ <- Either.cond(!document.root.descendants.exists(_.isInstanceOf[SnapScalaModel.Variable]), (),
      "Saved Snap variables cannot yet be included in unit-test execution.")
    _ <- Either.cond(project.definitions.isEmpty && project.scenes.forall(_.definitions.isEmpty) &&
      stage.definitions.isEmpty && stage.sprites.forall(_.definitions.isEmpty), (),
      "Custom Snap blocks are not yet supported by unit-test execution.")
    unsupported = SnapExpressionSupport.unsupportedSnapSelectors(xml)
    _ <- Either.cond(unsupported.isEmpty, (), s"Unsupported Snap blocks: ${unsupported.mkString(", ")}")
    scripts = owners.flatMap(_.scripts)
    started = scripts.filter(_.blocks.headOption.exists(_.selector.contains("receiveGo")))
    hats = document.root.descendants.collect { case block: SnapScalaModel.Block if block.selector.contains("receiveGo") => block }
    _ <- Either.cond(started.size <= 1 && hats.size == started.size, (),
      "Unit tests currently support one green-flag script.")
    _ <- Either.cond(scripts.forall(_.element.elements.forall {
      case _: SnapScalaModel.BlockLike | _: SnapScalaModel.Comment => true
      case _ => false
    }), (), "Unsupported content in a Snap script.")
    _ <- Either.cond(started.forall(_.blocks.forall(supportedExecutionBlock)), (),
      "These Snap blocks do not yet have a verified unit-test translation.")
  } yield SnapToExpression.parse(xml)

  private def supportedExecutionBlock(block: SnapScalaModel.BlockLike): Boolean = block match {
    case value: SnapScalaModel.Block => value.variable.isEmpty &&
      value.selector.flatMap(SnapTurtleCatalog.primitiveBySnapSelector.get).exists { primitive =>
      primitive.inputKinds.forall(_ == SnapTurtleCatalog.SnapInputKind.Numeric) &&
        value.element.elements.forall {
          case _: SnapScalaModel.Literal | _: SnapScalaModel.Comment => true
          case _ => false
        } &&
        value.inputs.size == primitive.arity && value.inputs.forall {
          case literal: SnapScalaModel.Literal => literal.element.elements.isEmpty &&
            literal.text.trim.matches("[-+]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)") && literal.number.exists(_.isFinite)
          case _ => false
        }
    }
    case _ => false
  }

}
