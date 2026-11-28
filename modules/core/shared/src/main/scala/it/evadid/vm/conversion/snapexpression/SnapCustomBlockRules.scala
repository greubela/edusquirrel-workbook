package it.evadid.vm.conversion.snapexpression

import it.evadid.vm.parsing.source.SourceParsing.SourceLanguage
import it.evadid.workbook.interactions.programming.state.snap.SnapTurtleCatalog
import it.evadid.vm.parsing.source.SourceParsing
import it.evadid.workbook.interactions.programming.state.snap.SnapScalaModel

/**
 * Port of the rules Snap applies when it loads a custom block, so generated XML
 * can be checked against them before it ever reaches the IDE.
 *
 * A `<custom-block s="...">` only becomes a real block if a definition exists whose
 * `blockSpec()` is character-identical to that `s` (`store.js` `loadBlock`); otherwise
 * Snap substitutes an `errorObsolete` block labelled `Undefined!`. And `blockSpec()`
 * keeps every label word while replacing each `%name` with that slot's *declared*
 * type (`byob.js` `CustomBlockDefinition.blockSpec`), defaulting to `%s` — which is
 * also the type Snap gives new slots in the Block Editor.
 */
import it.evadid.workbook.interactions.programming.state.snap.SnapVocabulary.{BlockCategory, BlockType}

object SnapCustomBlockRules {

  /** The bundled TurtleStitch's built-in palette categories (`SpriteMorph.prototype.categories`), `embroidery` included. */
  val BuiltInCategories: List[String] = BlockCategory.builtIn.map(_.xml)

  /** Snap's fallback slot type when a declaration is missing (`CustomBlockDefinition.typeOf`). */
  val DefaultSlotType: String = "%s"

  /** Slot types that cannot be a single Python argument (scripts, upvars, variadic groups). */
  val NonScalarSlotTypes: Set[String] =
    Set("%upvar", "%cs", "%ca", "%loop", "%cmdRing", "%repRing", "%predRing")

  val NonScalarSlotTypePrefixes: List[String] = List("%mult", "%group")

  /** One declared input of a custom block definition. */
  final case class SnapSlot(name: String, slotType: String, element: SnapScalaModel.InputDefinition)

  /** One `<block-definition>` as Snap would load it. */
  final case class SnapCustomBlock(
      spec: String,
      blockType: BlockType,
      category: BlockCategory,
      /** Defined in the scene's `<blocks>` (or a bare library); otherwise in a sprite's, and so sprite-local. */
      isGlobal: Boolean,
      slots: List[SnapSlot],
      element: SnapScalaModel.BlockDefinition,
      /** Document order among all definitions; identifies the definition within its document. */
      position: Int
  ) {
    def slotNames: List[String] = SnapCustomBlockRules.slotNames(spec)

    def arity: Int = slotNames.size

    def pythonName: String = SnapTurtleCatalog.pythonNameFromCustomSpec(spec)

    /** Declared type of a slot, falling back to Snap's `%s` for undeclared names. */
    def typeOf(slotName: String): String =
      slots.find(_.name == slotName).map(_.slotType).getOrElse(DefaultSlotType)

    /** Snap `CustomBlockDefinition.blockSpec()` — the string a call's `s` must equal. */
    def blockSpec: String = SnapCustomBlockRules.blockSpec(spec, typeOf)

    /** Label words without the slots, e.g. `draw square %size` → `List("draw", "square")`. */
    def labelWords: List[String] =
      parseSpec(spec).filterNot(part => part.startsWith("%") && part.length > 1).filter(_.nonEmpty)

    def bodyScript: Option[SnapScalaModel.Script] = element.body.headOption

    /** Why Python cannot represent this block faithfully, if so. */
    def pythonIncompatibility: Option[String] =
      if blockType == BlockType.Hat then Some(s"'${spec}' is a hat block; Python functions are command, reporter or predicate blocks")
      else if !isGlobal then Some(s"'${spec}' is a sprite-local block; Python apply only writes global blocks")
      else
        slots
          .find(slot => isNonScalarSlotType(slot.slotType))
          .map(slot => s"'${spec}' has a ${slot.slotType} slot that Python cannot pass as an argument")

    def isPythonCompatible: Boolean = pythonIncompatibility.isEmpty
  }

  /** A call Snap would replace with `Undefined!`. */
  final case class ObsoleteCall(spec: String, reason: String)

  def isNonScalarSlotType(slotType: String): Boolean =
    NonScalarSlotTypes.contains(slotType) || NonScalarSlotTypePrefixes.exists(slotType.startsWith)

  /**
   * `CustomBlockDefinition.parseSpec`: split on unquoted spaces, `'` toggles quoting.
   * Empty parts are kept because `blockSpec()` joins parts with a single space.
   */
  def parseSpec(spec: String): List[String] = {
    val parts = List.newBuilder[String]
    val word = new StringBuilder
    var quoted = false
    spec.foreach { ch =>
      if ch == '\'' then quoted = !quoted
      else if ch == ' ' && !quoted then
        parts += word.toString
        word.clear()
      else word.append(ch)
    }
    parts += word.toString
    parts.result()
  }

  /** Input names in spec order, as `loadCustomBlocks` derives them. */
  def slotNames(spec: String): List[String] =
    parseSpec(spec).collect {
      case part if part.startsWith("%") && part.length > 1 => part.substring(1)
    }

  /**
   * Keep label words (including quoted phrases) and replace each `%slot` with the
   * corresponding name from `newSlotNames`. Used when Python renames parameters
   * without changing arity.
   */
  def rewriteSpecSlots(spec: String, newSlotNames: List[String]): String = {
    var index = 0
    parseSpec(spec)
      .map { part =>
        if part.startsWith("%") && part.length > 1 then
          val name = newSlotNames.lift(index).getOrElse(part.substring(1))
          index += 1
          quoteSpecSlot(name)
        else quoteSpecLabel(part)
      }
      .map(_ + " ")
      .mkString
      .trim
  }

  private def quoteSpecLabel(part: String): String =
    if part.contains(" ") || part.contains("'") then s"'$part'" else part

  private def quoteSpecSlot(name: String): String =
    if name.exists(ch => ch.isWhitespace || ch == '\'') then s"%'$name'" else s"%$name"

  /** Rebuild Snap's `blockSpec()` from a definition spec and a slot-name → type lookup. */
  def blockSpec(spec: String, typeOf: String => String): String =
    parseSpec(spec)
      .map { part =>
        if part.startsWith("%") && part.length > 1 then typeOf(part.substring(1))
        else if part == "$nl" then "%br"
        else part
      }
      .map(_ + " ")
      .mkString
      .trim

  /** Spec Snap writes on a call to a freshly created definition (all slots `%n`). */
  def blockSpecOfNewDefinition(spec: String): String =
    blockSpec(spec, _ => "%n")

  /** Snap's load defaults: a missing type is a command, a missing category `other`. */
  def definition(element: SnapScalaModel.BlockDefinition, position: Int, isGlobal: Boolean): SnapCustomBlock = {
    val spec = element.spec.getOrElse("")
    val slots = slotNames(spec).zip(element.inputs).map { (name, input) =>
      SnapSlot(name, input.inputType.getOrElse(DefaultSlotType), input)
    }
    SnapCustomBlock(
      spec = spec,
      blockType = element.blockType.getOrElse(BlockType.Command),
      category = element.category.getOrElse(BlockCategory.Other),
      isGlobal = isGlobal,
      slots = slots,
      element = element,
      position = position
    )
  }

  /** Malformed XML has no definitions; the Snap reader reports it where a program is read. */
  private def document(xml: String): Option[SnapScalaModel.Document] = SourceParsing.parse(xml, SourceLanguage.Snap).syntax.toOption

  private def elements(document: SnapScalaModel.Document): Vector[SnapScalaModel.SnapElement] =
    document.root +: document.root.descendants

  def allDefinitions(document: SnapScalaModel.Document): List[SnapCustomBlock] = {
    // Snap loads the scene's definitions as global blocks; a bare `<blocks>` library has no scene, so all are global.
    val global = elements(document).collectFirst { case scene: SnapScalaModel.Scene => scene }.map(_.definitions)
    elements(document).collect { case definition: SnapScalaModel.BlockDefinition => definition }.zipWithIndex
      .map((element, position) => definition(element, position, global.forall(_.exists(_ eq element)))).toList
  }

  def allDefinitions(xml: String): List[SnapCustomBlock] = document(xml).toList.flatMap(allDefinitions)

  /**
   * Definitions in the scene-level `<blocks>`, which Snap loads as global blocks.
   * A bare `<blocks>` library fragment has no scene, so every definition counts.
   */
  def globalDefinitions(xml: String): List[SnapCustomBlock] = document(xml).toList.flatMap(globalDefinitions)

  private def globalDefinitions(document: SnapScalaModel.Document): List[SnapCustomBlock] =
    allDefinitions(document).filter(_.isGlobal)

  def localDefinitions(xml: String): List[SnapCustomBlock] = document(xml).toList.flatMap(localDefinitions)

  private def localDefinitions(document: SnapScalaModel.Document): List[SnapCustomBlock] =
    allDefinitions(document).filterNot(_.isGlobal)

  /** The `<category>` entries of `<palette>`, which Snap adds to the built-ins. */
  def paletteEntries(xml: String): List[SnapScalaModel.Element] = document(xml).toList.flatMap(paletteEntries)

  private def paletteEntries(document: SnapScalaModel.Document): List[SnapScalaModel.Element] =
    elements(document).collect { case palette: SnapScalaModel.Palette => palette.element.named("category").map(_.element) }.flatten.toList

  /** Category names from `<palette>`, which Snap adds to the built-ins. */
  def paletteCategories(xml: String): List[String] = document(xml).toList.flatMap(paletteCategories)

  private def paletteCategories(document: SnapScalaModel.Document): List[String] =
    paletteEntries(document).flatMap(_.attribute("name")).distinct

  /**
   * `SpriteMorph.prototype.allCategories()`.
   * @param registered categories the host already registered (exercise palette tabs)
   */
  def allCategories(xml: String, registered: List[String] = Nil): List[String] =
    document(xml).fold(BuiltInCategories ++ registered)(allCategories(_, registered)).distinct

  private def allCategories(document: SnapScalaModel.Document, registered: List[String]): List[String] =
    (BuiltInCategories ++ paletteCategories(document) ++ registered).distinct

  /** Snap resets a definition's category to `other` when it is unknown at load time. */
  def effectiveCategory(rawCategory: String, categories: List[String]): String =
    if categories.contains(rawCategory) then rawCategory else "other"

  /**
   * Calls Snap would render as `Undefined!`.
   *
   * In practice only the `blockSpec()` comparison can fail: Snap already rewrites an
   * unknown category to `other`, which is itself built in.
   */
  def obsoleteCalls(xml: String, registered: List[String] = Nil): List[ObsoleteCall] =
    document(xml).toList.flatMap(obsoleteCalls(_, registered))

  def obsoleteCalls(document: SnapScalaModel.Document, registered: List[String]): List[ObsoleteCall] = {
    val categories = allCategories(document, registered)
    val globals = globalDefinitions(document)
    val locals = localDefinitions(document)
    elements(document).collect { case call: SnapScalaModel.CustomBlock => call }.toList.flatMap { call =>
      val spec = call.selector.getOrElse("")
      val pool = if call.isLocal then locals else globals
      pool.find(_.blockSpec == spec) match
        case None =>
          val known = pool.map(_.blockSpec).mkString(", ")
          Some(ObsoleteCall(spec, s"no definition has blockSpec() '$spec' (defined: [$known])"))
        case Some(defn) if !categories.contains(effectiveCategory(defn.category.xml, categories)) =>
          Some(ObsoleteCall(spec, s"category '${defn.category.xml}' is outside allCategories"))
        case Some(_) => None
    }
  }

  def resolvesInSnap(xml: String, registered: List[String] = Nil): Boolean =
    obsoleteCalls(xml, registered).isEmpty

}
