package it.evadid.workbook.elements.interactionElements.programming

import it.evadid.vm.BeProgram
import it.evadid.vm.code.defining.BeDefineFunction

/**
 * Python write-back that merges into existing custom-block XML instead of replacing it.
 *
 * Snap XML carries more about a custom block than Python can express: the label
 * words, the declared slot types, the palette category, defaults and comments. Apply
 * therefore keeps every existing `<block-definition>` and only regenerates its body,
 * so a block built in Snap survives being edited as Python.
 */
object SnapCustomBlockMerge {

  /** Palette category for blocks Python declares from scratch. */
  val DefaultNewBlockCategory: String = SnapTurtleCatalog.PaletteTab.Variables.toString

  /** Everything the write-back needs to emit one `<block-definition>` and its calls. */
  final case class CustomBlockPlan(
      pythonName: String,
      spec: String,
      slotNames: List[String],
      slotTypes: List[String],
      blockType: String,
      category: String,
      extraAttributes: String,
      commentXml: String,
      variablesXml: String,
      headerXml: String,
      codeXml: String,
      translationsXml: String,
      inputsXml: String,
      scriptsXml: String,
      /** Python parameter name → slot name of this definition. */
      parameterSlots: Map[String, String]
  ) {

    /** The `s` every `<custom-block>` calling this definition must carry. */
    def callSpec: String = SnapCustomBlockRules.blockSpec(spec, slotTypeOf)

    def slotTypeOf(slotName: String): String =
      slotNames.indexOf(slotName) match
        case -1 => SnapCustomBlockRules.DefaultSlotType
        case index => slotTypes.lift(index).getOrElse(SnapCustomBlockRules.DefaultSlotType)

    /** Child order follows Snap's own `CustomBlockDefinition.toXML`. */
    def toXml(bodyXml: String): String = {
      val script = if bodyXml.nonEmpty then s"<script>$bodyXml</script>" else ""
      val attributes =
        s"""s="${SnapInputCodec.escapeXml(spec)}" type="${SnapInputCodec.escapeXml(blockType)}" category="${SnapInputCodec.escapeXml(category)}"$extraAttributes"""
      s"<block-definition $attributes>" +
        commentXml + variablesXml + headerXml + codeXml + translationsXml + inputsXml + script + scriptsXml +
        "</block-definition>"
    }
  }

  /** The planned definitions of one write-back, in emission order. */
  final class CustomBlockPlans(val ordered: List[CustomBlockPlan]) {
    private val byPythonName: Map[String, CustomBlockPlan] =
      ordered.map(plan => plan.pythonName -> plan).toMap

    def get(pythonName: String): Option[CustomBlockPlan] = byPythonName.get(pythonName)

    def categories: List[String] = ordered.map(_.category).distinct
  }

  object CustomBlockPlans {
    val empty: CustomBlockPlans = new CustomBlockPlans(Nil)
  }

  /** Parse Python and write Snap XML, keeping what `previousXml` already knows. */
  def applyProgram(
      program: BeProgram,
      canvasLayout: SnapCanvasLayout = SnapCanvasLayout.empty,
      previousXml: String = ""
  ): String =
    SnapProjectXml.toXml(program.fullProgram, canvasLayout = canvasLayout, previousXml = previousXml)

  /**
   * Pair each Python `def` with the existing definition of the same Python name.
   *
   * Equal arity and equal slot names keep the spec verbatim, so label words and
   * quoted Snap slot names survive. Equal arity with renamed Python parameters
   * rewrites only the `%slot` parts. A changed parameter *count* keeps the label
   * words and per-position slot types but rebuilds the spec, which drops slots
   * interleaved between label words.
   */
  def planFor(
      defs: List[BeDefineFunction],
      previousXml: String,
      newBlockCategory: String = DefaultNewBlockCategory
  ): CustomBlockPlans = {
    val existingByName =
      SnapCustomBlockRules
        .globalDefinitions(previousXml)
        .filter(_.isPythonCompatible)
        .groupBy(_.pythonName)
    var claimed = Set.empty[Int]
    val plans = defs.map { defn =>
      val pythonName = SnapTurtlePythonBridge.pythonNameOf(defn)
      val parameters = defn.inputs.map(SnapControlFlow.variableName)
      val pythonSlots = SnapTurtleCatalog.uniqueSlotNames(parameters)
      val candidates = existingByName.getOrElse(pythonName, Nil).filterNot(c => claimed.contains(c.element.start))
      val matched = candidates.find(_.arity == parameters.size).orElse(candidates.headOption)
      matched.foreach(existing => claimed += existing.element.start)
      matched match
        case Some(existing) if existing.arity == parameters.size && existing.slotNames == pythonSlots =>
          preservedPlan(pythonName, parameters, existing)
        case Some(existing) if existing.arity == parameters.size =>
          renamedSlotsPlan(pythonName, parameters, pythonSlots, existing)
        case Some(existing) => reshapedPlan(pythonName, parameters, existing)
        case None => freshPlan(pythonName, parameters, newBlockCategory)
    }
    new CustomBlockPlans(plans)
  }

  /** Existing definition kept as is; only the body will be replaced. */
  private def preservedPlan(
      pythonName: String,
      parameters: List[String],
      existing: SnapCustomBlockRules.SnapCustomBlock
  ): CustomBlockPlan = {
    val slotNames = existing.slotNames
    CustomBlockPlan(
      pythonName = pythonName,
      spec = existing.spec,
      slotNames = slotNames,
      slotTypes = slotNames.map(existing.typeOf),
      blockType = existing.blockType,
      category = existing.rawCategory,
      extraAttributes = extraAttributes(existing.element),
      commentXml = childOuter(existing, "comment", ""),
      variablesXml = childOuter(existing, "variables", ""),
      headerXml = childOuter(existing, "header", "<header></header>"),
      codeXml = childOuter(existing, "code", "<code></code>"),
      translationsXml = childOuter(existing, "translations", "<translations></translations>"),
      inputsXml = inputsXmlOf(existing, slotNames.map(existing.typeOf)),
      scriptsXml = childOuter(existing, "scripts", ""),
      parameterSlots = parameters.zip(slotNames).toMap
    )
  }

  /** Same arity, different Python names: keep labels and types, rewrite `%slot` names. */
  private def renamedSlotsPlan(
      pythonName: String,
      parameters: List[String],
      slotNames: List[String],
      existing: SnapCustomBlockRules.SnapCustomBlock
  ): CustomBlockPlan = {
    val slotTypes = slotNames.indices.toList.map { index =>
      existing.slots.lift(index).map(_.slotType).getOrElse("%n")
    }
    CustomBlockPlan(
      pythonName = pythonName,
      spec = SnapCustomBlockRules.rewriteSpecSlots(existing.spec, slotNames),
      slotNames = slotNames,
      slotTypes = slotTypes,
      blockType = existing.blockType,
      category = existing.rawCategory,
      extraAttributes = extraAttributes(existing.element),
      commentXml = childOuter(existing, "comment", ""),
      variablesXml = childOuter(existing, "variables", ""),
      headerXml = childOuter(existing, "header", "<header></header>"),
      codeXml = childOuter(existing, "code", "<code></code>"),
      translationsXml = childOuter(existing, "translations", "<translations></translations>"),
      inputsXml = inputsXmlOf(existing, slotTypes),
      scriptsXml = "",
      parameterSlots = parameters.zip(slotNames).toMap
    )
  }

  /** Parameter list changed in Python: keep the label and known slot types, rebuild the spec. */
  private def reshapedPlan(
      pythonName: String,
      parameters: List[String],
      existing: SnapCustomBlockRules.SnapCustomBlock
  ): CustomBlockPlan = {
    val label = existing.labelWords match
      case Nil => pythonName
      case words => words.mkString(" ")
    val slotNames = SnapTurtleCatalog.uniqueSlotNames(parameters)
    val slotTypes = slotNames.indices.toList.map { index =>
      existing.slots.lift(index).map(_.slotType).getOrElse("%n")
    }
    CustomBlockPlan(
      pythonName = pythonName,
      spec = label + slotNames.map(name => s" %$name").mkString,
      slotNames = slotNames,
      slotTypes = slotTypes,
      blockType = existing.blockType,
      category = existing.rawCategory,
      extraAttributes = extraAttributes(existing.element, dropIdentity = true),
      commentXml = childOuter(existing, "comment", ""),
      variablesXml = childOuter(existing, "variables", ""),
      headerXml = childOuter(existing, "header", "<header></header>"),
      codeXml = childOuter(existing, "code", "<code></code>"),
      translationsXml = childOuter(existing, "translations", "<translations></translations>"),
      inputsXml = inputsXmlFor(slotTypes),
      // Old Block-Editor leftovers still call the previous spec and would load as Undefined!.
      scriptsXml = "",
      parameterSlots = parameters.zip(slotNames).toMap
    )
  }

  /** Block declared in Python only: single label word, numeric slots. */
  private def freshPlan(pythonName: String, parameters: List[String], category: String): CustomBlockPlan = {
    val slotNames = SnapTurtleCatalog.uniqueSlotNames(parameters)
    val slotTypes = List.fill(slotNames.size)("%n")
    CustomBlockPlan(
      pythonName = pythonName,
      spec = SnapTurtleCatalog.customBlockSemanticSpec(pythonName, parameters),
      slotNames = slotNames,
      slotTypes = slotTypes,
      blockType = "command",
      category = category,
      extraAttributes = "",
      commentXml = "",
      variablesXml = "",
      headerXml = "<header></header>",
      codeXml = "<code></code>",
      translationsXml = "<translations></translations>",
      inputsXml = inputsXmlFor(slotTypes),
      scriptsXml = "",
      parameterSlots = parameters.zip(slotNames).toMap
    )
  }

  private def inputsXmlFor(slotTypes: List[String]): String =
    slotTypes
      .map(slotType => s"""<input type="${SnapInputCodec.escapeXml(slotType)}"></input>""")
      .mkString("<inputs>", "", "</inputs>")

  /** Keep Snap's declared types when they exist; never persist an empty `<inputs>` list. */
  private def inputsXmlOf(existing: SnapCustomBlockRules.SnapCustomBlock, slotTypes: List[String]): String =
    val xml = childOuter(existing, "inputs", "")
    val declaredInputs = SnapXmlParser.child(existing.element.inner, "inputs")
      .toList.flatMap(inputs => SnapXmlParser.children(inputs.inner).filter(_.tag == "input"))
    if declaredInputs.size == slotTypes.size then xml else inputsXmlFor(slotTypes)

  private def childOuter(
      existing: SnapCustomBlockRules.SnapCustomBlock,
      tag: String,
      fallback: String
  ): String =
    SnapXmlParser.child(existing.element.inner, tag).map(_.outer).getOrElse(fallback)

  private val ManagedAttributePattern = """\s+(?:s|type|category)="[^"]*"""".r
  private val IdentityAttributePattern = """\s+(?:selector|primitive)="[^"]*"""".r

  /**
   * `selector`, `primitive`, `helper`, `space`, `semantics` — kept in their original order.
   * On reshape, `selector`/`primitive` are dropped: they name the old spec, and a leftover
   * primitive selector would bootstrap the definition out of `globalBlocks`.
   */
  private def extraAttributes(element: SnapXmlParser.Element, dropIdentity: Boolean = false): String = {
    val headerEnd = element.outer.indexOf('>')
    if headerEnd < 0 then ""
    else
      val header = element.outer.substring(0, headerEnd).stripSuffix("/")
      val withoutTag = header.dropWhile(ch => ch != ' ' && ch != '\t' && ch != '\n')
      val withoutManaged = ManagedAttributePattern.replaceAllIn(withoutTag, "")
      val remaining =
        if dropIdentity then IdentityAttributePattern.replaceAllIn(withoutManaged, "").trim
        else withoutManaged.trim
      if remaining.isEmpty then "" else " " + remaining
  }
}
