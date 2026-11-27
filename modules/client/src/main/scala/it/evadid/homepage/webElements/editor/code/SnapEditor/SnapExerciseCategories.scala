package it.evadid.homepage.webElements.editor.code.SnapEditor

import com.raquo.airstream.state.Var
import it.evadid.facades.Snap.*
import org.scalajs.dom
import scala.scalajs.js

import SnapProjectRuntime.*

/** The exercise's palette tabs as Snap categories: registered on Snap's prototype (and removed again), drawn
  * taller than Snap's defaults, and the home of custom blocks whose category no tab shows. `mountedCanvas`
  * provides the stylesheet's dimensions.
  */
private[SnapEditor] final class SnapExerciseCategories(mountedCanvas: () => Option[dom.html.Canvas]):
  private val installed = Var(Seq.empty[String])

  /**
   * A custom block only appears in the palette tab whose name equals its category, and
   * Snap rewrites categories it does not know to `other` while loading. Neither is a
   * tab this editor shows, so move those definitions onto the Make-a-Block tab instead
   * of letting the block disappear.
   */
  def retag(ide: IDEMorph, libraries: Seq[LibraryTab]): Unit =
    for target <- libraries.find(_.includeMakeBlockButton).orElse(libraries.headOption) do
      val tabNames = libraries.map(_.name).toSet
      val retagged = customBlockDefinitions(ide).count { definition =>
        val category = definition.selectDynamic("category")
        val current = if SnapJs.isAbsent(category) then "" else category.toString
        // Predefined module blocks are shown where the palette lists them.
        if tabNames.contains(current) || SnapCustomBlocks.moduleBlockOf(definition).nonEmpty then false
        else
          definition.updateDynamic("category")(target.name)
          true
      }
      if retagged > 0 then
        ide.flushPaletteCache()
        ide.refreshPalette(true)

  private def spriteMorphCustomCategories: js.Dynamic =
    spriteMorphPrototype.selectDynamic("customCategories")

  /** Register exercise tabs on Snap's customCategories Map (name → Color). */
  def register(libraries: Seq[LibraryTab]): Unit =
    val customCategories = spriteMorphCustomCategories
    val blockColor = spriteMorphPrototype.selectDynamic("blockColor")
    libraries.foreach { tab =>
      val resolved = blockColor.selectDynamic(tab.color.snapKey)
      val color =
        if SnapJs.isAbsent(resolved) then blockColor.selectDynamic("other")
        else resolved
      customCategories.applyDynamic("set")(tab.name, color)
      // eduLibraryTabs also look up colors via blockColor[name] for prim-category buttons.
      blockColor.updateDynamic(tab.name)(color)
    }
    installed.set(libraries.map(_.name))

  def clear(): Unit =
    val customCategories = spriteMorphCustomCategories
    val blockColor = spriteMorphPrototype.selectDynamic("blockColor")
    installed.now().foreach { name =>
      customCategories.applyDynamic("delete")(name)
      js.Dynamic.global.Reflect.applyDynamic("deleteProperty")(blockColor, name)
    }
    installed.set(Nil)

  // Morphic paints category tabs on a canvas, so it cannot consume CSS directly.
  // Read the shared dimensions at the DOM boundary instead of duplicating them.
  private def cssPixels(property: String): Option[Double] =
    mountedCanvas().flatMap { canvas =>
      dom.window.getComputedStyle(canvas).getPropertyValue(property).trim.stripSuffix("px")
        .toDoubleOption.filter(value => value.isFinite && value >= 0)
    }

  /** Make visible category tabs taller and restack them after Snap's default layout. */
  def enlargeTabs(ide: IDEMorph): Unit =
    val categories = ide.asInstanceOf[js.Dynamic].selectDynamic("categories")
    if SnapJs.isAbsent(categories) then return
    val buttons = categories.selectDynamic("buttons").asInstanceOf[js.UndefOr[js.Array[js.Dynamic]]]
      .toOption
      .getOrElse(js.Array())
    val visible = buttons.filter { button =>
      button.selectDynamic("isVisible").asInstanceOf[Boolean]
    }
    if visible.isEmpty then return

    val dimensions = List("--snap-category-row-gap", "--snap-category-border", "--snap-category-padding", "--snap-category-label-growth").map(cssPixels)
    // Without a stylesheet, retain Snap's own default category layout.
    if dimensions.exists(_.isEmpty) then return
    val yPadding = dimensions(0).get
    val border = dimensions(1).get
    val first = visible(0)
    val left = first.applyDynamic("left")().asInstanceOf[Double]
    val top = visible.foldLeft(first.applyDynamic("top")().asInstanceOf[Double]) { (currentTop, button) =>
      button.updateDynamic("padding")(dimensions(2).get)
      // Drop Snap's default label shadow — it fights colored category tabs.
      button.updateDynamic("labelShadowOffset")(new SnapPoint(0, 0))
      button.updateDynamic("labelShadowColor")(new SnapColor(0, 0, 0, 0))
      val label = button.selectDynamic("label")
      if !SnapJs.isAbsent(label) then
        val fontSize = label.selectDynamic("fontSize")
        if !SnapJs.isAbsent(fontSize) then
          label.updateDynamic("fontSize")(fontSize.asInstanceOf[Double] + dimensions(3).get)
        if label.selectDynamic("fixLayout").asInstanceOf[js.UndefOr[js.Function0[Unit]]].isDefined then
          label.applyDynamic("fixLayout")()
      button.applyDynamic("fixLayout")()
      button.applyDynamic("refresh")()
      button.applyDynamic("setPosition")(new SnapPoint(left, currentTop))
      val height = button.applyDynamic("height")().asInstanceOf[Double]
      currentTop + height + yPadding
    }

    categories.applyDynamic("setHeight")(top - categories.applyDynamic("top")().asInstanceOf[Double] + border)
