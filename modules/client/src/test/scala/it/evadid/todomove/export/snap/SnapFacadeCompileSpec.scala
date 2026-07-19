package it.evadid.todomove.`export`.snap

import it.evadid.homepage.webElements.editor.code.SnapRenderer.{BlockLabelMorph, BlockSymbolMorph, BooleanSlotMorph, CSlotMorph, ColorSlotMorph, CommandBlockMorph, CommentMorph, IDEMorph, InputMorph, InputSlotMorph, MultiArgMorph, ReporterBlockMorph, ReporterSlotMorph, RingReporterSlotMorph, ScriptsMorph, SnapAttachTarget, SnapBlock, SnapCanvas, SnapColor, WorldMorph}
import munit.FunSuite

import scala.scalajs.js

class SnapFacadeCompileSpec extends FunSuite:
  test("Snap facade types expose strongly typed block and morph APIs"):
    val configureBlock: CommandBlockMorph => Unit = block =>
      block.selector = "forward"
      block.setSpec("move %n steps")
      val image: SnapCanvas = block.fullImage()
      assert(image != null)

    val consumeInputs: js.Array[InputMorph] => Int = _.length

    assertEquals(consumeInputs(new js.Array[InputMorph]()), 0)
    assert(configureBlock != null)

  test("blocks.js facades cover slot, label, script and comment classes"):
    val typedConstructors: js.Array[() => SnapBlock] = js.Array(
      () => new CommandBlockMorph(),
      () => new ReporterBlockMorph(false),
      () => new InputSlotMorph("10", true),
      () => new BooleanSlotMorph(false),
      () => new ColorSlotMorph(new SnapColor(255, 0, 0)),
      () => new CSlotMorph(),
      () => new MultiArgMorph("%n", "numbers"),
      () => new ReporterSlotMorph(false),
      () => new RingReporterSlotMorph(false)
    )

    val buildScriptArea: () => ScriptsMorph = () => new ScriptsMorph()
    val buildLabel: () => BlockLabelMorph = () => new BlockLabelMorph("move")
    val buildSymbol: () => BlockSymbolMorph = () => new BlockSymbolMorph("flag")
    val buildComment: () => CommentMorph = () => new CommentMorph("note")
    val attachTargets: js.Array[SnapAttachTarget] = new js.Array[SnapAttachTarget]()

    assertEquals(typedConstructors.length, 9)
    assert(buildScriptArea != null)
    assert(buildLabel != null)
    assert(buildSymbol != null)
    assert(buildComment != null)
    assertEquals(attachTargets.length, 0)

  test("IDE facade exposes the canonical project XML loading API"):
    val configureEditor: (IDEMorph, WorldMorph, String) => Unit = (editor, world, xml) =>
      editor.openIn(world)
      editor.loadProjectXML(xml)

    assert(configureEditor != null)
