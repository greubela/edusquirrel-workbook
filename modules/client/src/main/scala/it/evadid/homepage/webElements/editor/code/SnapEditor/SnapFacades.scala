package it.evadid.homepage.webElements.editor.code.SnapEditor

import org.scalajs.dom

import scala.scalajs.js
import scala.scalajs.js.annotation.*

/** Strongly-typed Scala.js facades for the Snap! globals loaded from
  * resources/programs/20260704Snap.
  */
@js.native
@JSGlobal("Color")
class SnapColor(r0: Double = js.native, g0: Double = js.native, b0: Double = js.native, a0: Double = js.native) extends js.Object:
  var r: Double = js.native
  var g: Double = js.native
  var b: Double = js.native
  var a: Double = js.native
  def copy(): SnapColor = js.native
  def eq(color: SnapColor): Boolean = js.native
  def lighter(percent: Double): SnapColor = js.native
  def darker(percent: Double): SnapColor = js.native
  def solid(): SnapColor = js.native
  def mixed(proportion: Double, color: SnapColor): SnapColor = js.native
  def toRGBstring(): String = js.native

@js.native
@JSGlobal("Point")
class SnapPoint(x0: Double = js.native, y0: Double = js.native) extends js.Object:
  var x: Double = js.native
  var y: Double = js.native
  def copy(): SnapPoint = js.native
  def eq(point: SnapPoint): Boolean = js.native
  def add(point: SnapPoint): SnapPoint = js.native
  def subtract(point: SnapPoint): SnapPoint = js.native
  def multiplyBy(point: SnapPoint): SnapPoint = js.native
  def divideBy(point: SnapPoint): SnapPoint = js.native

@js.native
@JSGlobal("Rectangle")
class SnapRectangle(left: Double = js.native, top: Double = js.native, right: Double = js.native, bottom: Double = js.native) extends js.Object:
  var origin: SnapPoint = js.native
  var corner: SnapPoint = js.native
  def width(): Double = js.native
  def height(): Double = js.native
  def extent(): SnapPoint = js.native
  def center(): SnapPoint = js.native
  def merge(rectangle: SnapRectangle): SnapRectangle = js.native
  def setWidth(width: Double): Unit = js.native
  def setHeight(height: Double): Unit = js.native

@js.native
@JSGlobal("Morph")
class Morph() extends js.Object:
  var parent: Morph | Null = js.native
  var children: js.Array[Morph] = js.native
  var bounds: SnapRectangle = js.native
  var color: SnapColor = js.native
  var isVisible: Boolean = js.native
  var isDraggable: Boolean = js.native
  def add(morph: Morph): Unit = js.native
  def addBack(morph: Morph): Unit = js.native
  def removeChild(morph: Morph): Unit = js.native
  def position(): SnapPoint = js.native
  def setPosition(point: SnapPoint): Unit = js.native
  def width(): Double = js.native
  def height(): Double = js.native
  def extent(): SnapPoint = js.native
  def setExtent(point: SnapPoint): Unit = js.native
  def fullBounds(): SnapRectangle = js.native
  def fullImage(): dom.HTMLCanvasElement = js.native
  def drawNew(): Unit = js.native
  def rerender(): Unit = js.native
  def fixLayout(): Unit = js.native
  def changed(): Unit = js.native
  def fullChanged(): Unit = js.native
  def destroy(): Unit = js.native

@js.native
@JSGlobal("WorldMorph")
class WorldMorph(canvas0: dom.HTMLCanvasElement, fillPage: Boolean = js.native) extends Morph:
  /** The canvas to which Morphic attaches its input listeners. */
  var worldCanvas: dom.HTMLCanvasElement = js.native
  /** Hidden textarea through which Morphic receives keyboard and IME input. */
  var keyboardHandler: dom.HTMLTextAreaElement = js.native
  /** Active text cursor while editing an input slot; null when not typing. */
  var cursor: js.Any | Null = js.native
  def doOneCycle(): Unit = js.native
  /** Commit the active text cursor (writes textarea into the target morph). */
  def stopEditing(): Unit = js.native

@js.native
@JSGlobal("ThreadManager")
class ThreadManager() extends js.Object:
  var processes: js.Array[js.Any] = js.native

@js.native
@JSGlobal("StageMorph")
class StageMorph() extends Morph:
  var threads: ThreadManager = js.native
  var isFastTracked: Boolean = js.native
  def clearPenTrails(): Unit = js.native

@js.native
@JSGlobal("IDE_Morph")
class IDEMorph(config: js.Object = js.native) extends Morph:
  var currentSprite: SpriteMorph = js.native
  var stage: StageMorph = js.native
  var version: Double = js.native
  def openIn(world: WorldMorph): Unit = js.native
  def getProjectXML(): String = js.native
  def loadProjectXML(projectXML: String): Unit = js.native
  def rawOpenProjectString(projectXML: String): Unit = js.native
  def refreshPalette(shouldIgnorePosition: Boolean = js.native): Unit = js.native
  def createCategories(): Unit = js.native
  def runScripts(): Unit = js.native
  def stopAllScripts(): Unit = js.native

@js.native
@JSGlobal("SpriteMorph")
class SpriteMorph() extends Morph:
  var scripts: ScriptsMorph = js.native
  var paletteCache: js.Dictionary[js.Any] = js.native
  def blockForSelector(selector: String, setDefaults: Boolean = js.native): BlockMorph | Null = js.native

@js.native
@JSGlobal("BoxMorph")
class BoxMorph(edge: Double = js.native, border: Double = js.native, borderColor: SnapColor = js.native) extends Morph

@js.native
@JSGlobal("StringMorph")
class StringMorph(text0: String = js.native) extends Morph:
  var text: String = js.native
  def setText(text: String): Unit = js.native

@js.native
@JSGlobal("TextMorph")
class TextMorph(text0: String = js.native) extends StringMorph(text0)

@js.native
@JSGlobal("SymbolMorph")
class SymbolMorph(name0: String = js.native, size0: Double = js.native, color0: SnapColor = js.native, shadowOffset0: SnapPoint = js.native, shadowColor0: SnapColor = js.native) extends Morph:
  var name: String = js.native
  var size: Double = js.native

@js.native
@JSGlobal("AlignmentMorph")
class AlignmentMorph(orientation: String = js.native, padding: Double = js.native) extends Morph



// blocks.js hierarchy -------------------------------------------------------

@js.native
@JSGlobal("SyntaxElementMorph")
class SyntaxElementMorph() extends Morph:
  var selector: String = js.native
  var isStatic: Boolean = js.native
  var isTemplate: Boolean = js.native
  def inputs(): js.Array[InputMorph] = js.native
  def allInputs(): js.Array[InputMorph] = js.native
  def topBlock(): BlockMorph | Null = js.native
  def evaluate(): SnapInputValue = js.native
  def mappedCode(definitions: SnapDefinitionMap = js.native): String = js.native

@js.native
@JSGlobal("BlockLabelMorph")
class BlockLabelMorph(text0: String = js.native, fontSize: Double = js.native, fontStyle: String = js.native, bold: Boolean = js.native, italic: Boolean = js.native, isNumeric: Boolean = js.native, shadowOffset: SnapPoint = js.native, shadowColor: SnapColor = js.native, color: SnapColor = js.native) extends StringMorph(text0)

@js.native
@JSGlobal("BlockSymbolMorph")
class BlockSymbolMorph(name: String, size: Double = js.native, color: SnapColor = js.native, shadowOffset: SnapPoint = js.native, shadowColor: SnapColor = js.native) extends SymbolMorph(name, size, color, shadowOffset, shadowColor)

@js.native
@JSGlobal("BlockMorph")
class BlockMorph() extends SyntaxElementMorph:
  var blockSpec: String = js.native
  var category: String = js.native
  var comment: CommentMorph | Null = js.native
  def setSpec(spec: String): Unit = js.native
  def fixBlockColor(nearestBlock: BlockMorph | Null = js.native, isForced: Boolean = js.native): Unit = js.native
  def abstractBlockSpec(): String = js.native
  def parts(): js.Array[Morph] = js.native
  def scriptTarget(noError: Boolean = js.native): js.Any = js.native
  def thumbnail(scale: Double = js.native, clipWidth: Double = js.native): dom.HTMLCanvasElement = js.native
  def addHighlight(oldHighlight: BlockHighlightMorph = js.native): BlockHighlightMorph = js.native
  def removeHighlight(): Unit = js.native

@js.native
@JSGlobal("CommandBlockMorph")
class CommandBlockMorph() extends BlockMorph:
  def nextBlock(block: CommandBlockMorph = js.native): CommandBlockMorph | Null = js.native
  def blockSequence(forSyntax: Boolean = js.native): js.Array[BlockMorph] = js.native
  def attachTargets(): js.Array[SnapAttachTarget] = js.native

@js.native
@JSGlobal("HatBlockMorph")
class HatBlockMorph() extends CommandBlockMorph

@js.native
@JSGlobal("ReporterBlockMorph")
class ReporterBlockMorph(isPredicate0: Boolean = js.native) extends BlockMorph:
  var isPredicate: Boolean = js.native
  def blockSequence(): js.Array[ReporterBlockMorph] = js.native

@js.native
@JSGlobal("RingMorph")
class RingMorph() extends ReporterBlockMorph:
  def contents(): BlockMorph | Null = js.native
  def setContents(block: BlockMorph | Null): Unit = js.native
  def embed(block: BlockMorph, inputNames: js.Array[String] = js.native, noVanish: Boolean = js.native): Unit = js.native

@js.native
@JSGlobal("ScriptsMorph")
class ScriptsMorph() extends Morph:
  def scriptTarget(): js.Any = js.native
  def cleanUp(): Unit = js.native
  def scriptsPicture(): js.UndefOr[dom.HTMLCanvasElement] = js.native

@js.native
@JSGlobal("ArgMorph")
class ArgMorph(`type`: String = js.native) extends SyntaxElementMorph:
  var canBeEmpty: Boolean = js.native
  def getSpec(): String = js.native
  def setContents(value: SnapInputValue): Unit = js.native

@js.native
@JSGlobal("CommandSlotMorph")
class CommandSlotMorph() extends ArgMorph:
  def nestedBlock(block: CommandBlockMorph = js.native): CommandBlockMorph | Null = js.native
  def attachTargets(): js.Array[SnapAttachTarget] = js.native

@js.native
@JSGlobal("RingCommandSlotMorph")
class RingCommandSlotMorph() extends CommandSlotMorph

@js.native
@JSGlobal("CSlotMorph")
class CSlotMorph() extends CommandSlotMorph

@js.native
@JSGlobal("InputSlotMorph")
class InputSlotMorph(text0: String = js.native, isNumeric0: Boolean = js.native, choiceDict0: SnapChoiceDictionary = js.native, isReadOnly0: Boolean = js.native) extends ArgMorph:
  var isNumeric: Boolean = js.native
  var isReadOnly: Boolean = js.native
  def contents(): SnapInputValue = js.native
  override def setContents(value: SnapInputValue): Unit = js.native
  def setChoices(choices: SnapChoiceDictionary, readonly: Boolean = js.native): Unit = js.native
  def evaluateOption(): SnapInputValue = js.native

@js.native
@JSGlobal("InputSlotStringMorph")
class InputSlotStringMorph(text: String = js.native, fontSize: Double = js.native, fontStyle: String = js.native, bold: Boolean = js.native, italic: Boolean = js.native, isNumeric: Boolean = js.native, shadowOffset: SnapPoint = js.native, shadowColor: SnapColor = js.native, color: SnapColor = js.native) extends StringMorph(text)

@js.native
@JSGlobal("InputSlotTextMorph")
class InputSlotTextMorph(text: String = js.native, fontSize: Double = js.native, fontStyle: String = js.native, bold: Boolean = js.native, italic: Boolean = js.native, alignment: String = js.native, width: Double = js.native, fontName: String = js.native, shadowOffset: SnapPoint = js.native, shadowColor: SnapColor = js.native, color: SnapColor = js.native) extends TextMorph(text)

@js.native
@JSGlobal("TemplateSlotMorph")
class TemplateSlotMorph(name: String = js.native) extends InputSlotMorph:
  override def contents(): String = js.native

@js.native
@JSGlobal("BooleanSlotMorph")
class BooleanSlotMorph(initialValue: Boolean = js.native) extends ArgMorph:
  def getValue(): Boolean = js.native
  def setValue(value: Boolean): Unit = js.native
  def toggleValue(): Unit = js.native

@js.native
@JSGlobal("ArrowMorph")
class ArrowMorph(direction0: String = js.native, size0: Double = js.native, padding0: Double = js.native, color0: SnapColor = js.native, isBlockLabel0: Boolean = js.native) extends Morph:
  var direction: String = js.native

@js.native
@JSGlobal("TextSlotMorph")
class TextSlotMorph(text: String = js.native, isNumeric: Boolean = js.native, choiceDict: SnapChoiceDictionary = js.native, isReadOnly: Boolean = js.native) extends InputSlotMorph:
  override def contents(): String = js.native

@js.native
@JSGlobal("ColorSlotMorph")
class ColorSlotMorph(color: SnapColor = js.native) extends ArgMorph:
  def getValue(): SnapColor = js.native
  def setColor(color: SnapColor): Unit = js.native
  def setContents(color: SnapColor): Unit = js.native

@js.native
@JSGlobal("ADT_SlotMorph")
class ADTSlotMorph(typeString: String = js.native) extends ArgMorph:
  def contents(): String = js.native
  def setContents(typeString: String): Unit = js.native

@js.native
@JSGlobal("BlockHighlightMorph")
class BlockHighlightMorph() extends Morph

@js.native
@JSGlobal("MultiArgMorph")
class MultiArgMorph(spec: String = js.native, labelTxt: String = js.native, min: Double = js.native, max: Double = js.native) extends ArgMorph:
  var minInputs: Double = js.native
  var maxInputs: Double = js.native
  override def inputs(): js.Array[InputMorph] = js.native
  def setChoices(choices: SnapChoiceDictionary, readonly: Boolean = js.native): Unit = js.native
  def setContents(values: js.Array[SnapInputValue]): Unit = js.native

@js.native
@JSGlobal("ArgLabelMorph")
class ArgLabelMorph(argMorph0: ArgMorph, labelTxt: String = js.native) extends ArgMorph:
  var argMorph: ArgMorph = js.native
  var labelText: String = js.native

@js.native
@JSGlobal("FunctionSlotMorph")
class FunctionSlotMorph(isPredicate0: Boolean = js.native) extends ArgMorph:
  var isPredicate: Boolean = js.native

@js.native
@JSGlobal("ReporterSlotMorph")
class ReporterSlotMorph(isPredicate: Boolean = js.native) extends FunctionSlotMorph:
  def contents(): ReporterBlockMorph | Null = js.native
  def nestedBlock(): ReporterBlockMorph | Null = js.native

@js.native
@JSGlobal("RingReporterSlotMorph")
class RingReporterSlotMorph(isPredicate: Boolean = js.native) extends ReporterSlotMorph:
  def nestedBlock(block: ReporterBlockMorph = js.native): ReporterBlockMorph | Null = js.native
  def attachTargets(): js.Array[SnapAttachTarget] = js.native

@js.native
@JSGlobal("CommentMorph")
class CommentMorph(contents: String = js.native) extends BoxMorph:
  var text: TextMorph = js.native
  def align(topBlock: BlockMorph, ignoreLayer: Boolean = js.native): Unit = js.native
  def startFollowing(topBlock: BlockMorph, world: WorldMorph): Unit = js.native

@js.native
@JSGlobal("ScriptFocusMorph")
class ScriptFocusMorph(editor: js.Any, initialElement: SyntaxElementMorph = js.native, position: SnapPoint = js.native) extends BoxMorph

type SnapInputValue = String | Double | Boolean | SnapColor | js.Array[js.Any] | Null
type InputMorph = ArgMorph | ReporterBlockMorph
type SnapBlock = BlockMorph | ArgMorph
type SnapChoiceDictionary = js.Dictionary[String | Double | Boolean | js.Array[String]]
type SnapDefinitionMap = js.Dictionary[js.Any]
type SnapAttachTarget = js.Tuple3[SnapPoint, Morph, js.UndefOr[String]]
type SnapCanvas = dom.HTMLCanvasElement
type SnapMorphList[A <: Morph] = js.Array[A]
