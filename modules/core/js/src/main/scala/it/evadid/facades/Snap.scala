package it.evadid.facades

import com.raquo.airstream.state.Var
import org.scalajs.dom

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import scala.scalajs.js.annotation.*

object Snap {
  /** Native types used by the mounted Snap editor and its previews. */
  @js.native
  @JSGlobal("Color")
  class SnapColor(r0: Double = js.native, g0: Double = js.native, b0: Double = js.native, a0: Double = js.native) extends js.Object

  @js.native
  @JSGlobal("Point")
  class SnapPoint(x0: Double = js.native, y0: Double = js.native) extends js.Object:
    var x: Double = js.native
    var y: Double = js.native



  @js.native
  @JSGlobal("Morph")
  class Morph() extends js.Object:
    var color: SnapColor = js.native
    var isDraggable: Boolean = js.native


    def width(): Double = js.native

    def height(): Double = js.native

    def extent(): SnapPoint = js.native

    def setExtent(point: SnapPoint): Unit = js.native

    def fullImage(): dom.HTMLCanvasElement = js.native

    def fixLayout(): Unit = js.native

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

    def penTrails(): dom.HTMLCanvasElement = js.native

  @js.native
  @JSGlobal("IDE_Morph")
  class IDEMorph(config: js.Object = js.native) extends Morph:
    var currentSprite: SpriteMorph = js.native
    var stage: StageMorph = js.native

    def openIn(world: WorldMorph): Unit = js.native

    def getProjectXML(): String = js.native

    def rawOpenProjectString(projectXML: String): Unit = js.native

    def refreshPalette(shouldIgnorePosition: Boolean = js.native): Unit = js.native

    def flushPaletteCache(): Unit = js.native

    def createCategories(): Unit = js.native

    def runScripts(): Unit = js.native

    def stopAllScripts(): Unit = js.native

  @js.native
  @JSGlobal("SpriteMorph")
  class SpriteMorph() extends Morph:
    var scripts: ScriptsMorph = js.native
    var paletteCache: js.Dictionary[js.Any] = js.native

  @js.native
  @JSGlobal("SyntaxElementMorph")
  class SyntaxElementMorph() extends Morph:
    var isTemplate: Boolean = js.native

    def inputs(): js.Array[InputMorph] = js.native

  @js.native
  @JSGlobal("BlockMorph")
  class BlockMorph() extends SyntaxElementMorph:
    var category: String = js.native

    def setSpec(spec: String): Unit = js.native

    def parts(): js.Array[Morph] = js.native

  @js.native
  @JSGlobal("ReporterBlockMorph")
  class ReporterBlockMorph(isPredicate0: Boolean = js.native) extends BlockMorph

  @js.native
  @JSGlobal("ScriptsMorph")
  class ScriptsMorph() extends Morph:

    def scriptsPicture(): js.UndefOr[dom.HTMLCanvasElement] = js.native

  @js.native
  @JSGlobal("ArgMorph")
  class ArgMorph(`type`: String = js.native) extends SyntaxElementMorph

  type InputMorph = ArgMorph | ReporterBlockMorph

  @js.native trait KeyboardInput extends dom.HTMLTextAreaElement {
    var selectionDirection: String = js.native
    def setSelectionRange(start: Int, end: Int, direction: String): Unit = js.native
  }

  private val pending = Var(Option.empty[Future[Unit]])
  private val scripts = List(
    "adjusted/adjustedMorphic.js", "turtlestitchsrc/symbols.js", "turtlestitchsrc/widgets.js",
    "turtlestitchsrc/blocks.js", "turtlestitchsrc/threads.js", "adjusted/adjustedObjects.js",
    "turtlestitchsrc/scenes.js", "turtlestitchsrc/gui.js", "turtlestitchsrc/paint.js",
    "turtlestitchsrc/lists.js", "turtlestitchsrc/byob.js", "turtlestitchsrc/tables.js",
    "turtlestitchsrc/sketch.js", "turtlestitchsrc/video.js", "turtlestitchsrc/maps.js",
    "turtlestitchsrc/extensions.js", "turtlestitchsrc/xml.js", "turtlestitchsrc/store.js",
    "turtlestitchsrc/locale.js", "turtlestitchsrc/cloud.js", "turtlestitchsrc/api.js",
    "turtlestitchsrc/embroider.js")

  def load(): Future[Unit] = pending.now().getOrElse {
    prepareResources()
    val result = BrowserLoading.ordered(scripts.map(path => BrowserLoading.resource("programs/20260212TurtleStitch/" + path))).map(_ => installCompatibility())
    pending.set(Some(result))
    result.failed.foreach { _ =>
      if pending.now().contains(result) then pending.set(None)
    }
    result
  }

  def preload(): Future[Unit] = load()

  private def available: Boolean = {
    val serializer = js.Dynamic.global.globalThis.selectDynamic("SnapSerializer")
    js.typeOf(serializer) == "function" &&
      js.typeOf(serializer.selectDynamic("prototype").selectDynamic(originalField)) == "function"
  }

  private def dependency[T](create: => T): ExternalDependency[T] =
    new ExternalDependency(() => preload(), () => available, () => create)

  def world(canvas: dom.HTMLCanvasElement): ExternalDependency[WorldMorph] =
    dependency(new WorldMorph(canvas, false))

  def editor(config: js.Object): ExternalDependency[IDEMorph] =
    dependency(new IDEMorph(config))

  @js.native
  @JSGlobal("PushButtonMorph")
  class Button(target: js.Any, action: js.Function0[Unit], label: String) extends Morph

  @js.native
  @JSGlobal("DialogBoxMorph")
  class Dialog(target: js.Any, action: js.Any, owner: js.Any) extends Morph

  @js.native
  @JSGlobal("MenuMorph")
  class Menu(action: js.Any, environment: js.Any, owner: js.Any) extends Morph

  def button(action: js.Function0[Unit], label: String): ExternalDependency[Button] =
    dependency(new Button(null, action, label))

  def dialog(action: js.Any, owner: js.Any = null): ExternalDependency[Dialog] =
    dependency(new Dialog(null, action, owner))

  def menu(action: js.Any, owner: js.Any): ExternalDependency[Menu] =
    dependency(new Menu(action, null, owner))

  private val originalField = "eduSquirrelOriginalLoadProjectModel"

  private def prepareResources(): Unit =
    // The bundled IDE's language loader resolves its own assets using this global.
    js.Dynamic.global.globalThis.updateDynamic("base_prog_dir")(
      BrowserLoading.resource("programs/20260212TurtleStitch/"))

  private def installCompatibility(): Unit = {
    val serializer = js.Dynamic.global.globalThis.selectDynamic("SnapSerializer")
    require(js.typeOf(serializer) == "function", "SnapSerializer is unavailable after runtime loading")
    patchSerializer(serializer.selectDynamic("prototype"))
  }

  private[evadid] def patchSerializer(prototype: js.Dynamic): Unit = {
    val existing = prototype.selectDynamic(originalField)
    if js.isUndefined(existing) then {
      val original = prototype.selectDynamic("loadProjectModel")
      require(js.typeOf(original) == "function", "SnapSerializer.loadProjectModel is unavailable")
      val patched: js.ThisFunction3[js.Dynamic, js.Dynamic, js.Any, js.Any, js.Any] =
        (serializer: js.Dynamic, xml: js.Dynamic, ide: js.Any, remixId: js.Any) => {
          val attributes: js.Dynamic = if xml == null || js.isUndefined(xml) then null
          else xml.selectDynamic("attributes")
          val sourceApp = if attributes == null || js.isUndefined(attributes) then js.undefined
          else attributes.selectDynamic("app")
          val targetApp = serializer.selectDynamic("app")
          if js.typeOf(sourceApp) == "string" && js.typeOf(targetApp) == "string" then {
            val source = sourceApp.asInstanceOf[String]
            val target = targetApp.asInstanceOf[String]
            val sourceName = source.takeWhile(_ != ' ')
            val targetName = target.takeWhile(_ != ' ')
            if sourceName.nonEmpty && targetName.nonEmpty && sourceName != targetName then
              attributes.updateDynamic("app")(target)
          }
          original.applyDynamic("call")(serializer, xml, ide, remixId)
        }
      prototype.updateDynamic(originalField)(original)
      prototype.updateDynamic("loadProjectModel")(patched)
    }
  }
}
