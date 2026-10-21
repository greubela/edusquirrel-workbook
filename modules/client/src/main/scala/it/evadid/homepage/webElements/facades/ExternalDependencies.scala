package it.evadid.homepage.webElements.facades

import com.raquo.airstream.state.Var
import it.evadid.homepage.control.startup.BrowserLoading
import org.scalajs.dom
import scala.concurrent.{Future, Promise}
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import scala.scalajs.js.annotation.*
import scala.scalajs.js.Thenable.Implicits.*
import scala.scalajs.js.timers.{clearTimeout, setTimeout}
import scala.util.Try

/** Loading shares code only; each creation returns a consumer-owned native handle. */
trait EvaExternalDependency[T] {
  def preload(): Future[Unit]
  def createInstance(): Future[T]
}

/** The synchronous entry point is for renderers that have already awaited readiness. */
final class ExternalDependency[T](ready: () => Future[Unit], available: () => Boolean, create: () => T)
    extends EvaExternalDependency[T] {
  override def preload(): Future[Unit] = Future.fromTry(Try(ready())).flatten
  def createLoadedInstance(): T = {
    require(available(), "External dependency has not been preloaded")
    create()
  }
  override def createInstance(): Future[T] = preload().map(_ => createLoadedInstance())
}

/** Native dependency boundaries. Startup schedules preloads; mounted consumers own instances. */
object ExternalDependencies {
  object CodeMirror {
    private[homepage] object Api {
      type Extension = js.Any

      @js.native trait Text extends js.Object {
        val lines: Int = js.native
        val length: Int = js.native
        def line(number: Int): Line = js.native
        def sliceString(from: Int, to: Int): String = js.native
        override def toString(): String = js.native
      }
      @js.native trait Line extends js.Object {
        val from: Int = js.native
        val to: Int = js.native
      }
      @js.native trait Changes extends js.Object {
        def mapPos(pos: Int, association: Int, mode: Int): Int | Null = js.native
        val invertedDesc: Changes = js.native
        def iterChangedRanges(callback: js.Function4[Int, Int, Int, Int, Unit]): Unit = js.native
      }
      @js.native trait Effect[A] extends js.Object {
        def is[B](kind: EffectType[B]): Boolean = js.native
        val value: A = js.native
      }
      @js.native trait EffectType[A] extends js.Object {
        def of(value: A): Effect[A] = js.native
      }
      @js.native trait Field[A] extends js.Object {
        def init(create: js.Function1[State, A]): Extension = js.native
      }
      @js.native trait Facet[A] extends js.Object {
        def of(value: A): Extension = js.native
      }
      @js.native trait AnnotationType[A] extends js.Object {
        def of(value: A): js.Object = js.native
      }
      @js.native trait SelectionRange extends js.Object {
        val from: Int = js.native
        val to: Int = js.native
        val empty: Boolean = js.native
      }
      @js.native trait Selection extends js.Object {
        val main: SelectionRange = js.native
      }
      @js.native trait State extends js.Object {
        val doc: Text = js.native
        val selection: Selection = js.native
        def field[A](field: Field[A]): A = js.native
        def update(specs: js.Object*): Transaction = js.native
      }
      @js.native trait Transaction extends js.Object {
        val startState: State = js.native
        val state: State = js.native
        val newDoc: Text = js.native
        val changes: Changes = js.native
        val effects: js.Array[Effect[js.Any]] = js.native
        val docChanged: Boolean = js.native
        val reconfigured: Boolean = js.native
      }
      @js.native trait ViewUpdate extends js.Object {
        val startState: State = js.native
        val state: State = js.native
        val view: View = js.native
        val docChanged: Boolean = js.native
        val viewportChanged: Boolean = js.native
        val transactions: js.Array[Transaction] = js.native
      }
      @js.native trait VisibleRange extends js.Object {
        val from: Int = js.native
        val to: Int = js.native
      }
      @js.native trait View extends js.Object {
        val state: State = js.native
        val dom: org.scalajs.dom.html.Div = js.native
        val visibleRanges: js.Array[VisibleRange] = js.native
        def dispatch(spec: js.Object): Unit = js.native
        def dispatch(transactions: js.Array[Transaction]): Unit = js.native
        def focus(): Unit = js.native
        def requestMeasure(): Unit = js.native
        def destroy(): Unit = js.native
      }
      @js.native trait DecorationSet extends js.Object {
        def map(changes: Changes): DecorationSet = js.native
      }
      @js.native trait Decoration extends js.Object {
        def range(from: Int): js.Object = js.native
        def range(from: Int, to: Int): js.Object = js.native
      }
      @js.native trait SyntaxNode extends js.Object {
        val name: String = js.native
        val from: Int = js.native
        val to: Int = js.native
        val parent: SyntaxNode | Null = js.native
      }
      @js.native trait SyntaxTree extends js.Object {
        def resolveInner(pos: Int, side: Int): SyntaxNode = js.native
        def iterate(config: js.Object): Unit = js.native
      }
      @js.native trait JavaModule extends js.Object {
        def java(): Extension = js.native
      }
      @js.native @JSGlobal("EduSquirrelCodeMirrorLibraries.Compartment")
      class Compartment extends js.Object {
        def of(extension: Extension): Extension = js.native
        def reconfigure(extension: Extension): js.Object = js.native
      }
      @js.native @JSGlobal("EduSquirrelCodeMirrorLibraries.EditorView")
      class EditorView(config: js.Object) extends View

      @js.native @JSGlobal("EduSquirrelCodeMirrorLibraries")
      object Libraries extends js.Object {
        val EditorState: StateFactory = js.native
        val EditorView: ViewFactory = js.native
        val StateEffect: EffectFactory = js.native
        val StateField: FieldFactory = js.native
        val Text: TextFactory = js.native
        val MapMode: MapModeFactory = js.native
        val Transaction: TransactionFactory = js.native
        val Decoration: DecorationFactory = js.native
        val ViewPlugin: PluginFactory = js.native
        val invertedEffects: Facet[js.Function1[Transaction, js.Array[js.Object]]] = js.native
        val keymap: Facet[js.Array[js.Object]] = js.native
        val indentUnit: Facet[String] = js.native
        val defaultKeymap: js.Array[js.Object] = js.native
        val historyKeymap: js.Array[js.Object] = js.native
        val foldKeymap: js.Array[js.Object] = js.native
        val searchKeymap: js.Array[js.Object] = js.native
        val defaultHighlightStyle: js.Object = js.native
        val oneDark: Extension = js.native
        val MySQL: js.Object = js.native
        def history(): Extension = js.native
        def indentLess(target: js.Object): Boolean = js.native
        def indentMore(target: js.Object): Boolean = js.native
        def lineNumbers(): Extension = js.native
        def highlightActiveLineGutter(): Extension = js.native
        def drawSelection(): Extension = js.native
        def foldGutter(): Extension = js.native
        def indentOnInput(): Extension = js.native
        def bracketMatching(): Extension = js.native
        def highlightActiveLine(): Extension = js.native
        def highlightSelectionMatches(): Extension = js.native
        def indentationMarkers(config: js.Object): Extension = js.native
        def syntaxHighlighting(style: js.Object, config: js.Object): Extension = js.native
        def syntaxTree(state: State): SyntaxTree = js.native
        def python(): Extension = js.native
        def cpp(): Extension = js.native
        def sql(config: js.Object): Extension = js.native
        def loadJavaModule(): js.Promise[JavaModule] = js.native
        def flowchartExtension(chartForDoc: js.Function1[String, js.Promise[js.Object]]): Extension = js.native
      }
      @js.native trait StateFactory extends js.Object {
        def create(config: js.Object): State = js.native
        val tabSize: Facet[Int] = js.native
      }
      @js.native trait ViewFactory extends js.Object {
        val decorations: DecorationsFacet = js.native
        val editorAttributes: Facet[js.Object] = js.native
        val updateListener: Facet[js.Function1[ViewUpdate, Unit]] = js.native
      }
      @js.native trait DecorationsFacet extends js.Object {
        def from(field: Field[DecorationSet]): Extension = js.native
      }
      @js.native trait EffectFactory extends js.Object {
        def define[A](config: js.Object = js.native): EffectType[A] = js.native
      }
      @js.native trait FieldFactory extends js.Object {
        def define[A](config: js.Object): Field[A] = js.native
      }
      @js.native trait TextFactory extends js.Object {
        def of(lines: js.Array[String]): Text = js.native
      }
      @js.native trait MapModeFactory extends js.Object { val TrackAfter: Int = js.native }
      @js.native trait TransactionFactory extends js.Object { val addToHistory: AnnotationType[Boolean] = js.native }
      @js.native trait DecorationFactory extends js.Object {
        val none: DecorationSet = js.native
        def line(config: js.Object): Decoration = js.native
        def mark(config: js.Object): Decoration = js.native
        def set(ranges: js.Array[js.Object], sort: Boolean): DecorationSet = js.native
      }
      @js.native trait PluginFactory extends js.Object {
        def define(create: js.Function1[View, js.Object], config: js.Object): Extension = js.native
      }

      def available: Boolean = {
        val value = js.Dynamic.global.globalThis.selectDynamic("EduSquirrelCodeMirrorLibraries")
        !js.isUndefined(value) && value != null
      }
    }

    @js.native
    trait CodeMirrorFacade extends js.Object {
      def createEditor(config: EditorConfig): CodeMirrorHandle = js.native
    }

    @js.native
    trait CodeMirrorHandle extends js.Object {
      def setDoc(value: String): Unit = js.native

      def getDoc(): String = js.native

      def setDiagnostics(diagnostics: js.Array[js.Object]): Unit = js.native

      def focus(): Unit = js.native

      def destroy(): Unit = js.native
    }

    trait EditorConfig extends js.Object {
      val parent: dom.Element
      val doc: js.UndefOr[String]
      val language: js.UndefOr[String]
      val onDocChange: js.Function1[String, Unit]
      val flowchart: js.UndefOr[js.Function1[String, js.Promise[js.Object]]]
    }

    object EditorConfig {
      def apply(
                 parent: dom.Element,
                 doc: String,
                 onDocChange: String => Unit,
                 language: String = "python",
                 flowchart: Option[String => js.Promise[js.Object]] = None
               ): EditorConfig = {
        js.Dynamic.literal(
          parent = parent,
          doc = doc,
          language = language,
          onDocChange = (value: String) => onDocChange(value),
          flowchart = flowchart.fold[js.UndefOr[js.Function1[String, js.Promise[js.Object]]]](js.undefined)(
            callback => ((value: String) => callback(value)): js.Function1[String, js.Promise[js.Object]]
          )
        ).asInstanceOf[EditorConfig]
      }
    }



    private val pending = Var(Option.empty[Future[CodeMirrorFacade]])

    private def facade: Option[CodeMirrorFacade] = {
      val value = dom.window.asInstanceOf[js.Dynamic].selectDynamic("EduSquirrelCodeMirror")
      if (js.isUndefined(value) || value == null || js.typeOf(value.selectDynamic("createEditor")) != "function") None
      else Some(value.asInstanceOf[CodeMirrorFacade])
    }

    private[homepage] def inBackground[A](work: => A): Future[A] = {
      val result = Promise[A]()
      setTimeout(0) { result.complete(Try(work)) }
      result.future
    }

    def preload(): Future[Unit] = {
      val ready = load(background = true)
      loadJava().failed.foreach(_ => ())
      ready.map(_ => ())
    }

    def editor(config: => EditorConfig): ExternalDependency[CodeMirrorHandle] =
      new ExternalDependency(() => preload(), () => facade.nonEmpty, () => facade.get.createEditor(config))

    private[homepage] def view(config: js.Object): ExternalDependency[Api.View] =
      new ExternalDependency(() => preload(), () => Api.available, () => new Api.EditorView(config))

    // Check the shared load after the calling reactive transaction has committed its Vars.
    private def load(background: Boolean = false): Future[CodeMirrorFacade] =
      Future.unit.flatMap(_ => loadOnce(background))

    private def loadOnce(background: Boolean): Future[CodeMirrorFacade] = facade match {
      case Some(value) => Future.successful(value)
      case None => pending.now().getOrElse {
        val result = Promise[CodeMirrorFacade]()
        pending.set(Some(result.future))
        inBackground {
          val script = dom.document.createElement("script").asInstanceOf[dom.html.Script]
          script.`type` = "module"
          script.async = true
          if background then script.setAttribute("fetchpriority", "low")
          script.src = new dom.URL("CodeMirrorLoader.js", BrowserLoading.bootstrapUrl).href
          val timeout = setTimeout(15000) {
            result.tryFailure(new RuntimeException("CodeMirror module load timed out"))
            script.remove()
          }
          script.onload = _ => {
            clearTimeout(timeout)
            facade match {
              case Some(value) => result.trySuccess(value)
              case None => result.tryFailure(new RuntimeException("CodeMirror module did not expose its facade"))
            }
            ()
          }
          script.addEventListener("error", (_: dom.Event) => {
            clearTimeout(timeout)
            result.tryFailure(new RuntimeException("CodeMirror module failed to load"))
            script.remove()
          })
          dom.document.head.appendChild(script)
        }.failed.foreach(result.tryFailure)
        result.future.failed.foreach { _ => if (pending.now().contains(result.future)) pending.set(None) }
        result.future
      }
    }

    private val pendingJava = Var(Option.empty[Future[Api.JavaModule]])

    def loadJava(): Future[Api.JavaModule] = Future.unit.flatMap { _ =>
      pendingJava.now().getOrElse {
        val result = load().flatMap { _ =>
          Api.Libraries.loadJavaModule().toFuture
        }
        pendingJava.set(Some(result))
        result.failed.foreach { _ => if pendingJava.now().contains(result) then pendingJava.set(None) }
        result
      }
    }
  }

  object Snap {
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
      def penTrails(): dom.HTMLCanvasElement = js.native

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
      def flushPaletteCache(): Unit = js.native
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
    def world(canvas: dom.HTMLCanvasElement): ExternalDependency[WorldMorph] =
      new ExternalDependency(() => preload(), () => available, () => new WorldMorph(canvas, false))
    def editor(config: js.Object): ExternalDependency[IDEMorph] =
      new ExternalDependency(() => preload(), () => available, () => new IDEMorph(config))

    @js.native @JSGlobal("PushButtonMorph")
    class Button(target: js.Any, action: js.Function0[Unit], label: String) extends Morph
    @js.native @JSGlobal("DialogBoxMorph")
    class Dialog(target: js.Any, action: js.Any, owner: js.Any) extends Morph
    @js.native @JSGlobal("MenuMorph")
    class Menu(action: js.Any, environment: js.Any, owner: js.Any) extends Morph

    def button(action: js.Function0[Unit], label: String): ExternalDependency[Button] =
      new ExternalDependency(() => preload(), () => available, () => new Button(null, action, label))
    def dialog(action: js.Any, owner: js.Any = null): ExternalDependency[Dialog] =
      new ExternalDependency(() => preload(), () => available, () => new Dialog(null, action, owner))
    def menu(action: js.Any, owner: js.Any): ExternalDependency[Menu] =
      new ExternalDependency(() => preload(), () => available, () => new Menu(action, null, owner))

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

    private[homepage] def patchSerializer(prototype: js.Dynamic): Unit = {
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

    @js.native
    trait PyodideRuntime extends js.Object {
      val globals: js.Dynamic = js.native

      def registerJsModule(name: String, module: js.Object): Unit = js.native

      def setStdout(options: js.Object): Unit = js.native

      def setStderr(options: js.Object): Unit = js.native

      def runPythonAsync(code: String): js.Promise[js.Any] = js.native
    }

  object Pyodide extends EvaExternalDependency[PyodideRuntime] {
    @js.native
    @JSGlobal("loadPyodide")
    private def loadPyodide(): js.Promise[PyodideRuntime] = js.native
    private val runtimeUrl = "https://cdn.jsdelivr.net/pyodide/v0.29.3/full/pyodide.js"
    private def available: Boolean = js.typeOf(js.Dynamic.global.globalThis.selectDynamic("loadPyodide")) == "function"
    override def preload(): Future[Unit] = if available then Future.unit else BrowserLoading.load(runtimeUrl)
    override def createInstance(): Future[PyodideRuntime] = preload().flatMap(_ => loadPyodide().toFuture)

    def configuredWorkerUrl(globalObject: js.Dynamic = js.Dynamic.global.globalThis): String = {
      val configured = globalObject.selectDynamic("PYODIDE_WORKER_URL")
      if !js.isUndefined(configured) && configured != null then configured.asInstanceOf[String]
      else "../js/pyodide-worker.js"
    }

    def worker(url: String = configuredWorkerUrl()): ExternalDependency[dom.Worker] =
      Workers.module(url)
  }

  object JSXGraph {
    @js.native trait Api extends js.Object { val JSXGraph: BoardFactory = js.native }
    @js.native trait BoardFactory extends js.Object {
      def initBoard(container: dom.Element, config: js.Object): Board = js.native
      def freeBoard(board: Board): Unit = js.native
    }
    @js.native trait Board extends js.Object {
      def create(kind: String, parents: js.Array[js.Any], config: js.Object): js.Dynamic = js.native
      def suspendUpdate(): Unit = js.native
      def unsuspendUpdate(): Unit = js.native
      def update(): Unit = js.native
    }
    def available: Boolean = {
      val library = js.Dynamic.global.globalThis.selectDynamic("JXG")
      !js.isUndefined(library) && library != null
    }
    def preload(): Future[Unit] = if available then Future.unit
      else BrowserLoading.load(BrowserLoading.resource("programs/20261004JSXGraph/jsxgraphcore.js"))
    private def api: Api = js.Dynamic.global.globalThis.selectDynamic("JXG").asInstanceOf[Api]
    def board(container: dom.Element, config: js.Object): ExternalDependency[Board] =
      new ExternalDependency(() => preload(), () => available, () => api.JSXGraph.initBoard(container, config))
    def free(board: Board): Unit = api.JSXGraph.freeBoard(board)
  }

  object Workers {
    private def dependency(url: String, options: dom.WorkerOptions): ExternalDependency[dom.Worker] =
      new ExternalDependency(() => { BrowserLoading.preload(List(url)); Future.unit }, () => true,
        () => new dom.Worker(url, options))
    def classic(url: String): ExternalDependency[dom.Worker] = dependency(url, new dom.WorkerOptions {})
    def module(url: String): ExternalDependency[dom.Worker] =
      dependency(url, js.Dynamic.literal(`type` = "module").asInstanceOf[dom.WorkerOptions])
  }
}
