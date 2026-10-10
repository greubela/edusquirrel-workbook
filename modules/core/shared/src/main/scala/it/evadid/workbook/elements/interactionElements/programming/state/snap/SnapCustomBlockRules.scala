package it.evadid.workbook.elements.interactionElements.programming.state.snap

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
object SnapCustomBlockRules {

  /** Snap's built-in palette categories (`SpriteMorph.prototype.categories`). */
  val BuiltInCategories: List[String] =
    List("motion", "looks", "sound", "pen", "control", "sensing", "operators", "variables", "lists", "other")

  /** Snap's fallback slot type when a declaration is missing (`CustomBlockDefinition.typeOf`). */
  val DefaultSlotType: String = "%s"

  /** Slot types that cannot be a single Python argument (scripts, upvars, variadic groups). */
  val NonScalarSlotTypes: Set[String] =
    Set("%upvar", "%cs", "%ca", "%loop", "%cmdRing", "%repRing", "%predRing")

  val NonScalarSlotTypePrefixes: List[String] = List("%mult", "%group")

  /** One declared input of a custom block definition. */
  final case class SnapSlot(name: String, slotType: String, element: SnapXmlParser.Element)

  /** One `<block-definition>` as Snap would load it. */
  final case class SnapCustomBlock(
      spec: String,
      blockType: String,
      rawCategory: String,
      scope: Option[String],
      slots: List[SnapSlot],
      element: SnapXmlParser.Element
  ) {
    def isGlobal: Boolean = scope.isEmpty

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

    def bodyScript: Option[SnapXmlParser.Element] = SnapXmlParser.child(element.inner, "script")

    /** Why Python cannot represent this block faithfully, if so. */
    def pythonIncompatibility: Option[String] =
      if blockType != "command" then Some(s"'${spec}' is a $blockType block; Python apply only writes command blocks")
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

  def definition(element: SnapXmlParser.Element): SnapCustomBlock = {
    val spec = element.attrOrEmpty("s")
    val declaredTypes = SnapXmlParser
      .child(element.inner, "inputs")
      .map(inputs => SnapXmlParser.children(inputs.inner).filter(_.tag == "input"))
      .getOrElse(Nil)
    val slots = slotNames(spec).zip(declaredTypes).map { (name, input) =>
      SnapSlot(name, input.attributes.getOrElse("type", DefaultSlotType), input)
    }
    SnapCustomBlock(
      spec = spec,
      blockType = element.attributes.getOrElse("type", "command"),
      rawCategory = element.attributes.getOrElse("category", "other"),
      scope = element.attr("scope").filter(_.nonEmpty),
      slots = slots,
      element = element
    )
  }

  def allDefinitions(xml: String): List[SnapCustomBlock] =
    SnapXmlParser.elements(xml, "block-definition").map(definition)

  /**
   * Definitions in the scene-level `<blocks>`, which Snap loads as global blocks.
   * A bare `<blocks>` library fragment has no scene, so every definition counts.
   */
  def globalDefinitions(xml: String): List[SnapCustomBlock] =
    sceneInner(xml).flatMap(inner => SnapXmlParser.child(inner, "blocks")) match
      case Some(blocks) => SnapXmlParser.children(blocks.inner).filter(_.tag == "block-definition").map(definition)
      case None => allDefinitions(xml)

  def localDefinitions(xml: String): List[SnapCustomBlock] = {
    val globalStarts = globalDefinitions(xml).map(_.element.start).toSet
    allDefinitions(xml).filterNot(defn => globalStarts.contains(defn.element.start))
  }

  /** Category names from `<palette>`, which Snap adds to the built-ins. */
  def paletteCategories(xml: String): List[String] =
    SnapXmlParser
      .elements(xml, "palette")
      .flatMap(palette => SnapXmlParser.children(palette.inner).filter(_.tag == "category"))
      .flatMap(_.attr("name"))
      .distinct

  /**
   * `SpriteMorph.prototype.allCategories()`.
   * @param registered categories the host already registered (exercise palette tabs)
   */
  def allCategories(xml: String, registered: List[String] = Nil): List[String] =
    (BuiltInCategories ++ paletteCategories(xml) ++ registered).distinct

  /** Snap resets a definition's category to `other` when it is unknown at load time. */
  def effectiveCategory(rawCategory: String, categories: List[String]): String =
    if categories.contains(rawCategory) then rawCategory else "other"

  /**
   * Calls Snap would render as `Undefined!`.
   *
   * In practice only the `blockSpec()` comparison can fail: Snap already rewrites an
   * unknown category to `other`, which is itself built in.
   */
  def obsoleteCalls(xml: String, registered: List[String] = Nil): List[ObsoleteCall] = {
    val categories = allCategories(xml, registered)
    val globals = globalDefinitions(xml)
    val locals = localDefinitions(xml)
    SnapXmlParser.elements(xml, "custom-block").flatMap { call =>
      val spec = call.attrOrEmpty("s")
      val pool = if call.attr("scope").exists(_.nonEmpty) then locals else globals
      pool.find(_.blockSpec == spec) match
        case None =>
          val known = pool.map(_.blockSpec).mkString(", ")
          Some(ObsoleteCall(spec, s"no definition has blockSpec() '$spec' (defined: [$known])"))
        case Some(defn) if !categories.contains(effectiveCategory(defn.rawCategory, categories)) =>
          Some(ObsoleteCall(spec, s"category '${defn.rawCategory}' is outside allCategories"))
        case Some(_) => None
    }
  }

  def resolvesInSnap(xml: String, registered: List[String] = Nil): Boolean =
    obsoleteCalls(xml, registered).isEmpty

  private def sceneInner(xml: String): Option[String] =
    SnapXmlParser.elements(xml, "scene").headOption.map(_.inner)
}
