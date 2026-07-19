package it.evadid.homepage.webElements.editor.code.SnapRenderer

import it.evadid.core.datastructures.language.AppLanguage.{English, Python}
import it.evadid.vm.BeProgram
import it.evadid.vm.code.BeExpression
import it.evadid.vm.code.controlStructures.BeSequence
import it.evadid.vm.code.others.BeStartProgram
import org.scalajs.dom.html.Canvas

class BeProgramSnapCustomRenderer extends BeProgramSnapRenderer {

  private val BlockX = 20.0
  private val FirstBlockY = 20.0
  private val BlockGap = 8.0

  override def renderInto(program: BeProgram, canvas: Canvas): Unit =

    val world: WorldMorph = WorldMorph(canvas)
    val scripts: ScriptsMorph = ScriptsMorph()

    world.add(scripts)
    scripts.setPosition(new SnapPoint(0, 0))

    val blocks = blockMorphsFor(program)
    var y = FirstBlockY
    blocks.foreach { block =>
      block.setPosition(new SnapPoint(BlockX, y))
      scripts.add(block)
      block.fixLayout()
      y = y + math.max(block.fullBounds().height(), block.height()) + BlockGap
    }

    scripts.fixLayout()
    scripts.changed()
    world.changed()
    world.doOneCycle()
    world

  private def blockMorphsFor(program: BeProgram): List[BlockMorph] =
    expressionsFor(program.fullProgram).map(blockForExpression)

  private def expressionsFor(expression: BeExpression): List[BeExpression] = expression match
    case BeStartProgram(Some(sequence)) => expressionsFor(sequence)
    case BeStartProgram(None) => Nil
    case sequence: BeSequence if sequence.body.nonEmpty => sequence.body
    case sequence: BeSequence => List(sequence)
    case other => List(other)

  private def blockForExpression(expression: BeExpression): BlockMorph =
    val block = new CommandBlockMorph()
    block.category = "other"
    block.setSpec(blockSpecFor(expression))
    block

  private def blockSpecFor(expression: BeExpression): String =
    val source = expression.expressionIO.toStringInLanguage(Python, English, skipUnparsable = true).trim
    if source.nonEmpty then source.replace("%", "%%") else expression.getClass.getSimpleName


}
