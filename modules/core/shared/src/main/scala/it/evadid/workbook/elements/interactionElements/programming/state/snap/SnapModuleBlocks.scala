package it.evadid.workbook.elements.interactionElements.programming.state.snap

import it.evadid.vm.code.abstractions.BeExpression
import it.evadid.vm.code.defining.BeDefineFunction
import it.evadid.vm.conversion.snapexpression.SnapCustomBlockRules
import it.evadid.vm.conversion.snapxml.SnapXmlText
import it.evadid.vm.modules.{BeModule, BeModules}
import it.evadid.vm.simulation.data.{DataLibraries, DataWorld}
import it.evadid.workbook.elements.interactionElements.programming.state.snap.SnapVocabulary.{BlockCategory, BlockType}

/**
 * Predefined Snap custom blocks for a module's functions.
 *
 * Snap has no static methods, so a module function is a global custom block whose definition works on a
 * project variable holding the module's data (so the blocks also run inside Snap). The block's spec
 * identifies it: reading Snap XML binds its calls to the module function, exactly like `NumberList.get`
 * in Python and Java, and ignores its definition and data variable; writing Snap XML adds both whenever
 * the program calls the module. Positions count from 0, as in Python and Java.
 */
object SnapModuleBlocks {
  final case class ModuleBlock(module: BeModule[?], method: String, spec: String, blockType: BlockType,
      slotTypes: List[String], body: String) {
    /** Palette id, e.g. `NumberList.get`: listed among a palette's selectors to offer the block. */
    def id: String = s"${module.name}.$method"

    def definition: BeDefineFunction = module.library.methods.find(DataLibraries.methodName(_) == method)
      .getOrElse(throw IllegalStateException(s"${module.name} has no function $method"))

    /** The `s` of a call, which must equal the definition's `blockSpec()`. */
    def blockSpec: String = SnapCustomBlockRules.blockSpec(spec, name => slotTypes.lift(slotNames.indexOf(name)).getOrElse("%s"))

    def slotNames: List[String] = SnapCustomBlockRules.slotNames(spec)

    /** The name Snap readers give the block's function (all label words). */
    def pythonName: String = SnapTurtleCatalog.pythonNameFromCustomSpec(spec)

    def definitionXml: String = {
      val inputs = slotTypes.map(slotType => s"""<input type="$slotType"></input>""").mkString
      s"""<block-definition s="${SnapXmlText.escape(spec)}" type="${blockType.xml}" category="${BlockCategory.Lists.xml}">""" +
        s"""<comment w="200" collapsed="false">${SnapXmlText.escape(Comment)}</comment>""" +
        s"""<header></header><code></code><translations></translations><inputs>$inputs</inputs><script>$body</script></block-definition>"""
    }
  }

  /** The project variable the number-list blocks read and change. */
  val NumberListVariable = "number list"

  private val Comment = "Positions count from 0, as in Python and Java."

  private def variable(name: String) = s"""<block var="${SnapXmlText.escape(name)}"/>"""
  private def position(slot: String) = s"""<block s="reportVariadicSum"><list>${variable(slot)}<l>1</l></list></block>"""
  private val list = variable(NumberListVariable)
  private def item(slot: String) = s"""<block s="reportListItem">${position(slot)}$list</block>"""
  private def replace(slot: String, value: String) = s"""<block s="doReplaceInList">${position(slot)}$list$value</block>"""

  val numberList: List[ModuleBlock] = List(
    ModuleBlock(BeModules.NumberList, "get", "item %'index' of number list", BlockType.Reporter, List("%n"),
      s"""<block s="doReport">${item("index")}</block>"""),
    ModuleBlock(BeModules.NumberList, "set", "set item %'index' of number list to %'value'", BlockType.Command, List("%n", "%n"),
      replace("index", variable("value"))),
    ModuleBlock(BeModules.NumberList, "swap", "swap items %'first' and %'second' of number list", BlockType.Command, List("%n", "%n"),
      s"""<block s="doDeclareVariables"><list><l>kept</l></list></block><block s="doSetVar"><l>kept</l>${item("first")}</block>""" +
        replace("first", item("second")) + replace("second", variable("kept"))),
    ModuleBlock(BeModules.NumberList, "length", "length of number list", BlockType.Reporter, Nil,
      s"""<block s="doReport"><block s="reportListAttribute"><l><option>length</option></l>$list</block></block>"""))

  val all: List[ModuleBlock] = numberList

  def byId(id: String): Option[ModuleBlock] = all.find(_.id == id)

  /** The block of a module function, e.g. for a parsed `NumberList.swap` call. */
  def of(definition: BeDefineFunction): Option[ModuleBlock] =
    BeModules.of(definition).flatMap(module => all.find(block => block.module == module && block.method == DataLibraries.methodName(definition)))

  /** The block a Snap custom-block spec or definition spec names, by its label words and slot count. */
  def bySpec(spec: String): Option[ModuleBlock] = byName(SnapTurtleCatalog.pythonNameFromCustomSpec(spec), SnapCustomBlockRules.slotNames(spec).size)

  def byName(pythonName: String, arity: Int): Option[ModuleBlock] =
    all.find(block => block.pythonName == pythonName && block.slotNames.size == arity)

  def isDataVariable(name: String): Boolean = name.trim == NumberListVariable

  /** Blocks a written project needs: every block of each module the program calls. */
  def requiredBy(expression: BeExpression): List[ModuleBlock] =
    all.filter(block => expression.moduleCalls(block.module).nonEmpty)

  def variableXml(blocks: Seq[ModuleBlock], world: DataWorld = DataWorld()): String =
    if blocks.exists(_.module == BeModules.NumberList) then
      s"""<variable name="${SnapXmlText.escape(NumberListVariable)}"><list struct="atomic">${world.list.mkString(",")}</list></variable>"""
    else ""

  /** A Snap blocks library (as Snap's "Import…" reads it) with `blocks` and their data variable. */
  def libraryXml(blocks: Seq[ModuleBlock], world: DataWorld = DataWorld()): String =
    s"""<blocks app="TurtleStitch 2.11, http://www.turtlestitch.org" version="2">${blocks.map(_.definitionXml).mkString}""" +
      s"""<variables>${variableXml(blocks, world)}</variables></blocks>"""
}
