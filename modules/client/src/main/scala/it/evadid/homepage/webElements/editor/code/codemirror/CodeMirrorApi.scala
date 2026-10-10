package it.evadid.homepage.webElements.editor.code.codemirror

import scala.scalajs.js
import scala.scalajs.js.annotation.JSGlobal

private[code] object CodeMirrorApi {
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
    val parent: SyntaxNode | Null = js.native
  }
  @js.native trait SyntaxTree extends js.Object {
    def resolveInner(pos: Int, side: Int): SyntaxNode = js.native
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
  }
  @js.native trait StateFactory extends js.Object {
    def create(config: js.Object): State = js.native
    val tabSize: Facet[Int] = js.native
  }
  @js.native trait ViewFactory extends js.Object {
    val decorations: DecorationsFacet = js.native
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
