package it.evadid.workbook.elements.interactionElements.programming.state.snap

import upickle.default.ReadWriter

/** Complete, immutable Snap serialization tree. Unknown extension nodes are first-class data.
  * Lexical XML details are retained independently of the typed Snap views, so loading and saving
  * a project does not normalize entities, reorder attributes, discard assets or regenerate IDs.
  */
object SnapScalaModel {
  sealed trait Node derives ReadWriter {
    def toXml: String
  }

  final case class Text(raw: String) extends Node derives ReadWriter {
    def value: String = decode(raw)
    def toXml: String = raw
  }
  final case class CData(value: String) extends Node derives ReadWriter {
    def toXml: String = "<![CDATA[" + value + "]]>"
  }
  final case class XmlComment(value: String) extends Node derives ReadWriter {
    def toXml: String = "<!--" + value + "-->"
  }
  final case class ProcessingInstruction(value: String) extends Node derives ReadWriter {
    def toXml: String = "<?" + value + "?>"
  }
  /** DTDs are retained, never loaded or evaluated (including external entities). */
  final case class Declaration(raw: String) extends Node derives ReadWriter {
    def toXml: String = raw
  }
  final case class Attribute(name: String, rawValue: String, leading: String = " ",
      beforeEquals: String = "", afterEquals: String = "", quote: String = "\"") derives ReadWriter {
    def value: String = decode(rawValue)
    def toXml: String = leading + name + beforeEquals + "=" + afterEquals + quote + rawValue + quote
    def withValue(value: String): Attribute = copy(rawValue = escape(value))
  }
  /** Ordered attributes and mixed content are authoritative, rather than a lossy Map. */
  final case class Element(tag: String, attributes: Vector[Attribute] = Vector.empty,
      children: Vector[Node] = Vector.empty, trailing: String = "", selfClosing: Boolean = false,
      closing: String = "") derives ReadWriter {
    def attribute(name: String): Option[String] = attributes.find(_.name == name).map(_.value)
    def number(name: String): Option[Double] = attribute(name).flatMap(_.toDoubleOption)
    def integer(name: String): Option[Int] = attribute(name).flatMap(_.toIntOption)
    def boolean(name: String): Option[Boolean] = attribute(name).flatMap {
      case "true" => Some(true)
      case "false" => Some(false)
      case _ => None
    }
    def elements: Vector[SnapElement] = children.collect { case node: SnapElement => node }
    def named(name: String): Vector[SnapElement] = elements.filter(_.element.tag == name)
    def contained(container: String, tag: String): Vector[SnapElement] = named(container).flatMap(_.element.named(tag))
    def text: String = children.map {
      case t: Text => t.value
      case c: CData => c.value
      case e: SnapElement => e.element.text
      case _ => ""
    }.mkString
    def withAttribute(name: String, value: String): Element = {
      val updated = if attributes.exists(_.name == name) then
        attributes.map(a => if a.name == name then a.withValue(value) else a)
      else attributes :+ Attribute(name, escape(value))
      copy(attributes = updated)
    }
    def toXml: String = {
      val start = "<" + tag + attributes.map(_.toXml).mkString + trailing
      if selfClosing && children.isEmpty then start + "/>"
      else start + ">" + children.map(_.toXml).mkString + (if closing.nonEmpty then closing else "</" + tag + ">")
    }
  }

  sealed trait SnapElement extends Node derives ReadWriter {
    def element: Element
    final def toXml: String = element.toXml
    final def id: Option[String] = element.attribute("id")
    final def name: Option[String] = element.attribute("name")
    final def text: String = element.text
    final def variables: Vector[Variable] = element.contained("variables", "variable").collect { case v: Variable => v }
    final def scripts: Vector[Script] = element.contained("scripts", "script").collect { case s: Script => s }
    final def costumes: Vector[Costume] = element.named("costumes").flatMap(_.descendants).collect { case c: Costume => c }
    final def sounds: Vector[Sound] = element.named("sounds").flatMap(_.descendants).collect { case s: Sound => s }
    final def descendants: Vector[SnapElement] = element.elements.flatMap(e => e +: e.descendants)
    final def definitions: Vector[BlockDefinition] = element.contained("blocks", "block-definition").collect { case b: BlockDefinition => b }
    final def withAttribute(name: String, value: String): SnapElement = classify(element.withAttribute(name, value))
  }
  final case class Project(element: Element) extends SnapElement derives ReadWriter {
    def creator: Option[String] = element.named("creator").headOption.map(_.text)
    def originalCreator: Option[String] = element.named("origCreator").headOption.map(_.text)
    def originalName: Option[String] = element.named("origName").headOption.map(_.text)
    def scenes: Vector[Scene] = element.contained("scenes", "scene").collect { case s: Scene => s }
    def legacyStage: Option[Stage] = element.named("stage").collectFirst { case s: Stage => s }
    def selectedScene: Option[Int] = element.named("scenes").headOption.flatMap(_.element.integer("select"))
    def notes: Option[String] = element.named("notes").headOption.map(_.text)
    def thumbnail: Option[String] = element.named("thumbnail").headOption.map(_.text)
    def app: Option[String] = element.attribute("app")
    def version: Option[String] = element.attribute("version")
  }
  final case class Scene(element: Element) extends SnapElement derives ReadWriter {
    def role: Option[String] = element.attribute("role")
    def paletteMode: Option[String] = element.attribute("palette")
    def showCategories: Option[Boolean] = element.boolean("categories")
    def showButtons: Option[Boolean] = element.boolean("buttons")
    def clickRun: Option[Boolean] = element.boolean("clickrun")
    def dragData: Option[Boolean] = element.boolean("dragdata")
    def colorModel: Option[String] = element.attribute("colormodel")
    def strict: Option[Boolean] = element.boolean("strict")
    def stage: Option[Stage] = element.named("stage").collectFirst { case s: Stage => s }
    def primitiveDefinitions: Vector[BlockDefinition] = element.contained("primitives", "block-definition").collect { case b: BlockDefinition => b }
  }
  final case class Stage(element: Element) extends SnapElement derives ReadWriter {
    def costumeIndex: Option[Int] = element.integer("costume")
    def penLog: Option[Boolean] = element.boolean("penlog")
    def instrument: Option[Int] = element.integer("instrument")
    def volume: Option[Double] = element.number("volume")
    def pan: Option[Double] = element.number("pan")
    def lines: Option[String] = element.attribute("lines")
    def ternary: Option[Boolean] = element.boolean("ternary")
    def hyperops: Option[Boolean] = element.boolean("hyperops")
    def codify: Option[Boolean] = element.boolean("codify")
    def inheritanceEnabled: Option[Boolean] = element.boolean("inheritance")
    def sublistIDs: Option[Boolean] = element.boolean("sublistIDs")
    def color: Option[Rgba] = element.attribute("color").flatMap(Rgba.parse)
    def width: Option[Double] = element.number("width")
    def height: Option[Double] = element.number("height")
    def tempo: Option[Double] = element.number("tempo")
    def threadSafe: Option[Boolean] = element.boolean("threadsafe")
    def sprites: Vector[Sprite] = element.contained("sprites", "sprite").collect { case s: Sprite => s }
    def watchers: Vector[Watcher] = element.contained("sprites", "watcher").collect { case w: Watcher => w }
    def selectedSprite: Option[Int] = element.named("sprites").headOption.flatMap(_.element.integer("select"))
  }
  final case class Sprite(element: Element) extends SnapElement derives ReadWriter {
    def color: Option[Rgba] = element.attribute("color").flatMap(Rgba.parse)
    def index: Option[Int] = element.integer("idx")
    def volume: Option[Double] = element.number("volume")
    def pan: Option[Double] = element.number("pan")
    def rotation: Option[Int] = element.integer("rotation")
    def instrument: Option[Int] = element.integer("instrument")
    def costumeIndex: Option[Int] = element.integer("costume")
    def pen: Option[String] = element.attribute("pen")
    def x: Option[Double] = element.number("x")
    def y: Option[Double] = element.number("y")
    def heading: Option[Double] = element.number("heading")
    def scale: Option[Double] = element.number("scale")
    def hidden: Option[Boolean] = element.boolean("hidden")
    def draggable: Option[Boolean] = element.boolean("draggable")
  }
  final case class Script(element: Element) extends SnapElement derives ReadWriter {
    def x: Option[Double] = element.number("x")
    def y: Option[Double] = element.number("y")
    def blocks: Vector[BlockLike] = element.elements.collect { case b: BlockLike => b }
  }
  sealed trait BlockLike extends SnapElement derives ReadWriter {
    def inputs: Vector[SnapElement] = element.elements.filterNot {
      case _: Comment | _: Variables => true
      case _ => false
    }
    def selector: Option[String] = element.attribute("s")
  }
  final case class Block(element: Element) extends BlockLike derives ReadWriter {
    def variable: Option[String] = element.attribute("var")
  }
  final case class CustomBlock(element: Element) extends BlockLike derives ReadWriter {
    def scope: Option[String] = element.attribute("scope")
  }
  final case class BlockDefinition(element: Element) extends SnapElement derives ReadWriter {
    def primitiveSelector: Option[String] = element.attribute("selector")
    def primitive: Option[String] = element.attribute("primitive")
    def helper: Option[Boolean] = element.boolean("helper")
    def spaceAbove: Option[Boolean] = element.boolean("space")
    def reports: Option[String] = element.attribute("reports")
    def strict: Option[Boolean] = element.boolean("strict")
    def semantics: Option[String] = element.attribute("semantics")
    def spec: Option[String] = element.attribute("s")
    def category: Option[String] = element.attribute("category")
    def blockType: Option[String] = element.attribute("type")
    def body: Vector[Script] = element.named("script").collect { case s: Script => s }
    def inputs: Vector[InputDefinition] = element.contained("inputs", "input").collect { case i: InputDefinition => i }
  }
  final case class Variable(element: Element) extends SnapElement derives ReadWriter {
    def hidden: Option[Boolean] = element.boolean("hidden")
    def value: Option[SnapElement] = element.elements.headOption
    def transient: Option[Boolean] = element.boolean("transient")
  }
  final case class Literal(element: Element) extends SnapElement derives ReadWriter {
    def value: String = text
    def boolean: Option[BooleanValue] = element.named("bool").collectFirst { case b: BooleanValue => b }
    def option: Option[OptionValue] = element.named("option").collectFirst { case o: OptionValue => o }
    def wish: Option[Wish] = element.named("wish").collectFirst { case w: Wish => w }
    def number: Option[Double] = text.toDoubleOption
  }
  final case class BooleanValue(element: Element) extends SnapElement derives ReadWriter {
    def value: Option[Boolean] = text match {
      case "true" => Some(true)
      case "false" => Some(false)
      case _ => None
    }
  }
  final case class ListValue(element: Element) extends SnapElement derives ReadWriter {
    def atomicCsv: Option[String] = Option.when(structure.contains("atomic"))(text)
    def linked: Boolean = element.attribute("linked").contains("linked")
    def tail: Option[ListValue] = element.named("list").collectFirst { case l: ListValue => l }
    def items: Vector[Item] = element.named("item").collect { case i: Item => i }
    def structure: Option[String] = element.attribute("struct")
  }
  final case class Costume(element: Element) extends SnapElement derives ReadWriter {
    def embed: Option[String] = element.attribute("embed")
    def image: Option[String] = element.attribute("image")
    def centerX: Option[Double] = element.number("center-x")
    def centerY: Option[Double] = element.number("center-y")
  }
  final case class Sound(element: Element) extends SnapElement derives ReadWriter {
    def data: Option[String] = element.attribute("sound")
  }
  final case class Reference(element: Element) extends SnapElement derives ReadWriter {
    def target: Option[String] = element.attribute("id")
  }
  final case class Watcher(element: Element) extends SnapElement derives ReadWriter {
    def scope: Option[String] = element.attribute("scope")
    def selector: Option[String] = element.attribute("s")
    def variable: Option[String] = element.attribute("var")
    def style: Option[String] = element.attribute("style")
    def x: Option[Double] = element.number("x")
    def y: Option[Double] = element.number("y")
    def minimum: Option[Double] = element.number("min")
    def maximum: Option[Double] = element.number("max")
    def hidden: Option[Boolean] = element.boolean("hidden")
  }
  final case class Context(element: Element) extends SnapElement derives ReadWriter {
    def inputs: Vector[InputDefinition] = element.contained("inputs", "input").collect { case i: InputDefinition => i }
    def receiver: Option[Receiver] = element.named("receiver").collectFirst { case r: Receiver => r }
    def outerContext: Option[Context] = element.named("context").collectFirst { case c: Context => c }
    def expression: Option[SnapElement] = element.elements.find {
      case _: Script | _: BlockLike | _: Autolambda => true
      case _ => false
    }
  }
  final case class Item(element: Element) extends SnapElement derives ReadWriter {
    def value: Option[SnapElement] = element.elements.headOption
  }
  final case class InputDefinition(element: Element) extends SnapElement derives ReadWriter {
    def inputType: Option[String] = element.attribute("type")
    def readonly: Option[Boolean] = element.boolean("readonly")
    def irreplaceable: Option[Boolean] = element.boolean("irreplaceable")
    def separator: Option[String] = element.attribute("separator")
    def collapse: Option[String] = element.attribute("collapse")
    def expand: Option[String] = element.attribute("expand")
    def initial: Option[Int] = element.integer("initial")
    def minimum: Option[Double] = element.number("min")
    def maximum: Option[Double] = element.number("max")
  }
  final case class Comment(element: Element) extends SnapElement derives ReadWriter {
    def x: Option[Double] = element.number("x")
    def y: Option[Double] = element.number("y")
    def width: Option[Double] = element.number("w")
    def collapsed: Option[Boolean] = element.boolean("collapsed")
  }
  final case class Rgba(red: Double, green: Double, blue: Double, alpha: Double) derives ReadWriter
  object Rgba {
    def parse(value: String): Option[Rgba] = value.split(",", -1).toVector.map(_.toDoubleOption) match {
      case Vector(Some(r), Some(g), Some(b), Some(a)) => Some(Rgba(r, g, b, a))
      case _ => None
    }
  }
  final case class Color(element: Element) extends SnapElement derives ReadWriter {
    def value: Option[Rgba] = Rgba.parse(text)
  }
  final case class OptionValue(element: Element) extends SnapElement derives ReadWriter
  final case class Autolambda(element: Element) extends SnapElement derives ReadWriter
  final case class Receiver(element: Element) extends SnapElement derives ReadWriter
  final case class Inheritance(element: Element) extends SnapElement derives ReadWriter {
    def exemplar: Option[String] = element.attribute("exemplar")
  }
  final case class Nesting(element: Element) extends SnapElement derives ReadWriter {
    def anchor: Option[String] = element.attribute("anchor")
    def synchronous: Option[Boolean] = element.boolean("synch")
    def scale: Option[Double] = element.number("scale")
  }
  final case class Solution(element: Element) extends SnapElement derives ReadWriter
  final case class Dispatch(element: Element) extends SnapElement derives ReadWriter
  final case class Wish(element: Element) extends SnapElement derives ReadWriter {
    def spec: Option[String] = element.attribute("s")
    def blockType: Option[String] = element.attribute("type")
    def category: Option[String] = element.attribute("category")
  }
  final case class Scenes(element: Element) extends SnapElement derives ReadWriter
  final case class Sprites(element: Element) extends SnapElement derives ReadWriter
  final case class Scripts(element: Element) extends SnapElement derives ReadWriter
  final case class Variables(element: Element) extends SnapElement derives ReadWriter
  final case class Blocks(element: Element) extends SnapElement derives ReadWriter
  final case class Primitives(element: Element) extends SnapElement derives ReadWriter
  final case class Inputs(element: Element) extends SnapElement derives ReadWriter
  final case class Costumes(element: Element) extends SnapElement derives ReadWriter
  final case class Sounds(element: Element) extends SnapElement derives ReadWriter
  final case class Wear(element: Element) extends SnapElement derives ReadWriter
  final case class Palette(element: Element) extends SnapElement derives ReadWriter
  final case class Category(element: Element) extends SnapElement derives ReadWriter
  final case class Notes(element: Element) extends SnapElement derives ReadWriter
  final case class Thumbnail(element: Element) extends SnapElement derives ReadWriter
  final case class PenTrails(element: Element) extends SnapElement derives ReadWriter
  final case class Headers(element: Element) extends SnapElement derives ReadWriter
  final case class Code(element: Element) extends SnapElement derives ReadWriter
  final case class Header(element: Element) extends SnapElement derives ReadWriter
  final case class Translations(element: Element) extends SnapElement derives ReadWriter
  final case class Options(element: Element) extends SnapElement derives ReadWriter
  final case class Hidden(element: Element) extends SnapElement derives ReadWriter
  final case class Dispatches(element: Element) extends SnapElement derives ReadWriter
  final case class Origin(element: Element) extends SnapElement derives ReadWriter
  final case class Remark(element: Element) extends SnapElement derives ReadWriter
  final case class Template(element: Element) extends SnapElement derives ReadWriter
  final case class Media(element: Element) extends SnapElement derives ReadWriter
  final case class SnapData(element: Element) extends SnapElement derives ReadWriter
  final case class Creator(element: Element) extends SnapElement derives ReadWriter
  final case class OriginalCreator(element: Element) extends SnapElement derives ReadWriter
  final case class OriginalName(element: Element) extends SnapElement derives ReadWriter
  final case class Custom(element: Element) extends SnapElement derives ReadWriter
  /** Containers and future/version-specific tags retain their entire ordered subtree. */
  final case class Extension(element: Element) extends SnapElement derives ReadWriter

  final case class Document(nodes: Vector[Node]) derives ReadWriter {
    require(nodes.count(_.isInstanceOf[SnapElement]) == 1, "A Snap document needs exactly one root element.")
    def root: SnapElement = nodes.collectFirst { case e: SnapElement => e }.get
    def project: Option[Project] = root match {
      case p: Project => Some(p)
      case data: SnapData => data.element.named("project").collectFirst { case p: Project => p }
      case _ => None
    }
    def toXml: String = nodes.map(_.toXml).mkString
    /** Bottom-up immutable editing keeps unchanged lexical details and extension content. */
    def transform(f: SnapElement => SnapElement): Document = {
      def visit(node: Node): Node = node match {
        case e: SnapElement => f(classify(e.element.copy(children = e.element.children.map(visit))))
        case other => other
      }
      copy(nodes = nodes.map(visit))
    }
  }

  def parse(xml: String): Either[String, Document] = SnapScalaXmlCodec.parse(xml)

  private[snap] def classify(e: Element): SnapElement = e.tag match {
    case "project" => Project(e)
    case "scene" => Scene(e)
    case "stage" => Stage(e)
    case "sprite" => Sprite(e)
    case "script" => Script(e)
    case "block" => Block(e)
    case "custom-block" => CustomBlock(e)
    case "block-definition" => BlockDefinition(e)
    case "variable" => Variable(e)
    case "l" => Literal(e)
    case "bool" => BooleanValue(e)
    case "list" => ListValue(e)
    case "costume" => Costume(e)
    case "sound" => Sound(e)
    case "ref" => Reference(e)
    case "watcher" => Watcher(e)
    case "context" => Context(e)
    case "item" => Item(e)
    case "input" => InputDefinition(e)
    case "comment" => Comment(e)
    case "color" => Color(e)
    case "option" => OptionValue(e)
    case "autolambda" => Autolambda(e)
    case "receiver" => Receiver(e)
    case "inherit" => Inheritance(e)
    case "nest" => Nesting(e)
    case "solution" => Solution(e)
    case "dispatch" => Dispatch(e)
    case "wish" => Wish(e)
    case "scenes" => Scenes(e)
    case "sprites" => Sprites(e)
    case "scripts" => Scripts(e)
    case "variables" => Variables(e)
    case "blocks" => Blocks(e)
    case "primitives" => Primitives(e)
    case "inputs" => Inputs(e)
    case "costumes" => Costumes(e)
    case "sounds" => Sounds(e)
    case "wear" => Wear(e)
    case "palette" => Palette(e)
    case "category" => Category(e)
    case "notes" => Notes(e)
    case "thumbnail" => Thumbnail(e)
    case "pentrails" => PenTrails(e)
    case "headers" => Headers(e)
    case "code" => Code(e)
    case "header" => Header(e)
    case "translations" => Translations(e)
    case "options" => Options(e)
    case "hidden" => Hidden(e)
    case "dispatches" => Dispatches(e)
    case "origin" => Origin(e)
    case "remark" => Remark(e)
    case "template" => Template(e)
    case "media" => Media(e)
    case "snapdata" => SnapData(e)
    case "creator" => Creator(e)
    case "origCreator" => OriginalCreator(e)
    case "origName" => OriginalName(e)
    case "custom" => Custom(e)
    case _ => Extension(e)
  }

  private[snap] def escape(value: String): String = value.replace("&", "&amp;")
    .replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")

  /** Unknown DTD entities stay opaque. No network access or entity expansion is performed. */
  private[snap] def decode(raw: String): String = {
    val entity = "&(#x[0-9a-fA-F]+|#[0-9]+|amp|lt|gt|quot|apos);".r
    val out = new StringBuilder
    var previous = 0
    entity.findAllMatchIn(raw).foreach { m =>
      out.append(raw.substring(previous, m.start))
      val decoded = m.group(1) match {
        case "amp" => "&"
        case "lt" => "<"
        case "gt" => ">"
        case "quot" => "\""
        case "apos" => "'"
        case number =>
          val code = if number.startsWith("#x") then Integer.parseInt(number.drop(2), 16) else Integer.parseInt(number.drop(1))
          require(code > 0 && code <= 0x10ffff && !(code >= 0xd800 && code <= 0xdfff), "Invalid XML character reference.")
          if code <= 0xffff then code.toChar.toString
          else {
            val n = code - 0x10000
            (0xd800 + (n >> 10)).toChar.toString + (0xdc00 + (n & 0x3ff)).toChar.toString
          }
      }
      out.append(decoded)
      previous = m.end
    }
    out.append(raw.substring(previous)).toString
  }
}
