package it.evadid.homepage.webElements.editor.code.SnapEditor

import it.evadid.core.datastructures.language.AppLanguage.{English, Python}
import it.evadid.vm.code.abstractions.BeExpression
import it.evadid.vm.naming.CodeRepresentationConfig
import it.evadid.workbook.elements.interactionElements.programming.{SnapPaletteCatalog, SnapTurtleCatalog}

/** A primitive in the Snap palette.
 *
 * `id` is Snap's stable selector (for example `gotoXY`). It is also the value
 * persisted in the `s` attribute of the resulting `<block>` element. The
 * description may replace the visible native label. Underscores are expanded
 * to the corresponding native input type, so `go to x: _ y: _` keeps the two
 * numeric input morphs belonging to `gotoXY`.
 */
case class LibraryBlock(id: String, snap_description_line: String, associatedExpression: BeExpression)

/** Named Snap palette color role; resolves via `SpriteMorph.prototype.blockColor`. */
enum SnapCategoryColor(val snapKey: String):
  case Motion    extends SnapCategoryColor("motion")
  case Looks     extends SnapCategoryColor("looks")
  case Sound     extends SnapCategoryColor("sound")
  case Pen       extends SnapCategoryColor("pen")
  case Control   extends SnapCategoryColor("control")
  case Sensing   extends SnapCategoryColor("sensing")
  case Operators extends SnapCategoryColor("operators")
  case Variables extends SnapCategoryColor("variables")
  case Other     extends SnapCategoryColor("other")
  case Embroidery extends SnapCategoryColor("embroidery")

/** One named, ordered and allow-listed palette tab. An empty tab is valid.
 *
 * `color` is a Snap built-in category role (resolved at runtime from blockColor).
 * When `useNativeCategory` is true, the tab shows Snap's native blocks for that
 * color role instead of `selectableElements`.
 */
case class LibraryTab(
    id: String,
    name: String,
    selectableElements: List[LibraryBlock],
    color: SnapCategoryColor = SnapCategoryColor.Other,
    includeVariableControls: Boolean = false,
    includeMakeBlockButton: Boolean = false,
    useNativeCategory: Boolean = false
)

/** Visibility of the independently configurable parts of the embedded IDE.
 *
 * Snap calls the palette the library. `spriteControls` includes its scripts /
 * costumes / sounds headline, the sprite corral and the stage resize handle.
 * Thus the small, programming-only editor requested by most exercises is
 * `SnapEditorParts(headline = false, stage = false, spriteControls = false)`:
 * it contains the library and scripts workspace, but no costumes tab, project
 * menu, green flag, stage, or sprite controls.
 */
case class SnapEditorParts(
                            headline: Boolean = true, // file saving etc
                            palette: Boolean = true, // library
                            libraryCategories: Boolean = true, //
                            stage: Boolean = true, // executable area right
                            spriteControls: Boolean = true // scripts / costumes / sound headline
                          )

case class SnapEditorVisuals(
                              ColorWorkspace: String = "#f6f8fa",
                              ColorEmpty: String = "#8c959f",
                              CanvasWidth: Int = 900,
                              CanvasHeight: Int = 520,
                              Padding: Double = 24.0,
                              BlockGap: Double = 8.0,
                              Indent: Double = 28.0
                            )

case class SnapCodeEditorConfig(
                                 parts: SnapEditorParts = SnapEditorParts(),
                                 libraryTabs: List[LibraryTab] = Nil,
                                 codeRepresentation: CodeRepresentationConfig = CodeRepresentationConfig(Python, English, skipUnparsable = false),
                                 visuals: SnapEditorVisuals = SnapEditorVisuals()
                               )

object SnapCodeEditorConfig:
  /** Snap's default palette buttons (lists/other live inside Variables). */
  val StandardSnapCategories: List[LibraryTab] = List(
    nativeTab("motion", "Motion", SnapCategoryColor.Motion),
    nativeTab("looks", "Looks", SnapCategoryColor.Looks),
    nativeTab("sound", "Sound", SnapCategoryColor.Sound),
    nativeTab("pen", "Pen", SnapCategoryColor.Pen),
    nativeTab("control", "Control", SnapCategoryColor.Control),
    nativeTab("sensing", "Sensing", SnapCategoryColor.Sensing),
    nativeTab("operators", "Operators", SnapCategoryColor.Operators),
    nativeTab("variables", "Variables", SnapCategoryColor.Variables)
  )

  /** Minimal configuration used by integration tests and manual smoke tests. */
  val Testing: SnapCodeEditorConfig = SnapCodeEditorConfig(
    parts = SnapEditorParts(
      headline = false,
      palette = true,
      libraryCategories = true,
      stage = false,
      spriteControls = false
    ),
    libraryTabs = SnapCodeEditorConfig.StandardSnapCategories
  )

  /** Explicit palette limited to blocks with full Snap ↔ BeExpression ↔ Python support. */
  val PythonCompatibleSnapCategories: List[LibraryTab] =
    SnapPaletteCatalog.TabOrder.map { tab =>
      val selectors = SnapPaletteCatalog.selectorsForTab(tab)
      LibraryTab(
        id = tab.toString.toLowerCase,
        name = tab.toString,
        selectableElements = selectors.map(block(_)),
        color = paletteColor(tab),
        includeVariableControls = tab == SnapTurtleCatalog.PaletteTab.Variables,
        includeMakeBlockButton = tab == SnapTurtleCatalog.PaletteTab.Variables
      )
    }

  /** Test-workbook editor config with a Python-safe Snap palette only. */
  val PythonCompatibleTesting: SnapCodeEditorConfig = SnapCodeEditorConfig(
    parts = Testing.parts,
    libraryTabs = PythonCompatibleSnapCategories
  )

  /** Same Python-safe palette plus embroidery blocks (already included above). */
  val EmbroideryTesting: SnapCodeEditorConfig = PythonCompatibleTesting

  /** One tab with blocks from any Snap categories (native block colors unchanged). */
  def mixedTab(
      id: String,
      name: String,
      blocks: List[LibraryBlock],
      color: SnapCategoryColor = SnapCategoryColor.Other,
      includeVariableControls: Boolean = false,
      includeMakeBlockButton: Boolean = false
  ): LibraryTab =
    LibraryTab(
      id = id,
      name = name,
      selectableElements = blocks,
      color = color,
      includeVariableControls = includeVariableControls,
      includeMakeBlockButton = includeMakeBlockButton,
      useNativeCategory = false
    )

  /** Flatten several tabs into one mixed tab (block order preserved). */
  def flattenToMixedTab(
      id: String,
      name: String,
      tabs: List[LibraryTab],
      color: SnapCategoryColor = SnapCategoryColor.Other
  ): LibraryTab =
    mixedTab(
      id = id,
      name = name,
      blocks = tabs.flatMap(_.selectableElements),
      color = color,
      includeVariableControls = tabs.exists(_.includeVariableControls),
      includeMakeBlockButton = tabs.exists(_.includeMakeBlockButton)
    )

  /** Pedagogical order for the beginner turtle circle exercise. */
  private val BeginnerTurtleBlockOrder: List[String] = List(
    "receiveGo",
    "doRepeat",
    "forward",
    "turn",
    "gotoXY",
    "setHeading",
    "clear",
    "up",
    "down"
  )

  private val BeginnerTurtleSelectors: Set[String] = BeginnerTurtleBlockOrder.toSet

  /** Single mixed tab with the beginner turtle allow-list. */
  val BeginnerTurtleCategories: List[LibraryTab] = {
    val byId = PythonCompatibleSnapCategories
      .flatMap(_.selectableElements)
      .filter(b => BeginnerTurtleSelectors.contains(b.id))
      .map(b => b.id -> b)
      .toMap
    val blocks = BeginnerTurtleBlockOrder.flatMap(byId.get)
    List(
      mixedTab(
        id = "blocks",
        name = "Blocks",
        blocks = blocks,
        color = SnapCategoryColor.Other,
        includeVariableControls = false
      )
    )
  }

  /** Editor config for beginner turtle exercises (circle, etc.). */
  val BeginnerTurtleTesting: SnapCodeEditorConfig = SnapCodeEditorConfig(
    parts = Testing.parts.copy(libraryCategories = false),
    libraryTabs = BeginnerTurtleCategories
  )

  /** All Snap selectors exposed by [[PythonCompatibleSnapCategories]]. */
  def pythonCompatibleBlockSelectors: Set[String] =
    PythonCompatibleSnapCategories.flatMap(_.selectableElements.map(_.id)).toSet

  /** All Snap selectors exposed by [[BeginnerTurtleCategories]]. */
  def beginnerTurtleBlockSelectors: Set[String] =
    BeginnerTurtleCategories.flatMap(_.selectableElements.map(_.id)).toSet

  private def block(id: String, snapDescriptionLine: String = ""): LibraryBlock =
    LibraryBlock(id, snapDescriptionLine, BeExpression.pass)

  private def paletteColor(tab: SnapTurtleCatalog.PaletteTab): SnapCategoryColor =
    tab match
      case SnapTurtleCatalog.PaletteTab.Motion => SnapCategoryColor.Motion
      case SnapTurtleCatalog.PaletteTab.Pen => SnapCategoryColor.Pen
      case SnapTurtleCatalog.PaletteTab.Embroidery => SnapCategoryColor.Embroidery
      case SnapTurtleCatalog.PaletteTab.Control => SnapCategoryColor.Control
      case SnapTurtleCatalog.PaletteTab.Operators => SnapCategoryColor.Operators
      case SnapTurtleCatalog.PaletteTab.Variables => SnapCategoryColor.Variables
      case SnapTurtleCatalog.PaletteTab.Other => SnapCategoryColor.Other

  private def nativeTab(id: String, name: String, color: SnapCategoryColor): LibraryTab =
    LibraryTab(
      id = id,
      name = name,
      selectableElements = Nil,
      color = color,
      useNativeCategory = true
    )
