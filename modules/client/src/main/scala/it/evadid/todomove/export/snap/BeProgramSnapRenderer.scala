package it.evadid.todomove.`export`.snap

import it.evadid.core.datastructures.language.AppLanguage.{English, Python}
import it.evadid.vm.BeProgram
import it.evadid.vm.code.BeExpression
import it.evadid.vm.code.controlStructures.BeSequence
import it.evadid.vm.code.others.BeStartProgram
import org.scalajs.dom.html

/** Renders a [[BeProgram]] with Snap!/Morphic primitives.
  *
  * This renderer intentionally depends only on the core VM model and the
  * Snap! facades in this package. It does not use the legacy SVG BeBlock
  * rendering pipeline.
  */
class BeProgramSnapRenderer(program: BeProgram, canvas: html.Canvas):

  /** Snap!/Morphic world initialized for the supplied canvas. */
  val world: WorldMorph = new WorldMorph(canvas)

  /** Scripts area that receives the generated Snap! block morphs. */
  val scripts: ScriptsMorph = new ScriptsMorph()

  /** Render this renderer's program into the world and return the world. */
  def render(): WorldMorph =
    BeProgramSnapRenderer.renderInto(program, world, scripts)

object BeProgramSnapRenderer:

  /** Convenience entry point for callers that do not need to keep a renderer instance. */
  def render(program: BeProgram, canvas: html.Canvas): WorldMorph =
    new BeProgramSnapRenderer(program, canvas).render()

  private val BlockX = 20.0
  private val FirstBlockY = 20.0
  private val BlockGap = 8.0

  private def renderInto(program: BeProgram, world: WorldMorph, scripts: ScriptsMorph): WorldMorph =
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
