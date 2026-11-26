package it.evadid.workbook.elements.interactionElements.programming.state.snap

/** Closed attribute vocabularies of the bundled Snap/TurtleStitch serializer
  * (`resources/programs/20260212TurtleStitch/turtlestitchsrc/store.js`).
  *
  * Each case carries its exact XML spelling. Readers return `None` for absent or unrecognized
  * values; the raw attribute always remains in the lossless [[SnapScalaModel.Element]].
  */
object SnapVocabulary {
  /** Values written verbatim into one XML attribute. */
  sealed trait XmlValue { def xml: String }

  private def reader[E <: XmlValue](values: Array[E]): String => Option[E] = raw => values.find(_.xml == raw)

  /** `SpriteMorph.penPoint`: where the pen sits relative to the costume. */
  enum PenPoint(val xml: String) extends XmlValue {
    case Tip extends PenPoint("tip")
    case Center extends PenPoint("center")
  }
  object PenPoint { val fromXml: String => Option[PenPoint] = reader(values) }

  /** `SpriteMorph.rotationStyle`: 0 = off, 1 = full, 2 = left/right. */
  enum RotationStyle(val xml: String) extends XmlValue {
    case DontRotate extends RotationStyle("0")
    case Free extends RotationStyle("1")
    case LeftRight extends RotationStyle("2")
  }
  object RotationStyle { val fromXml: String => Option[RotationStyle] = reader(values) }

  /** `SpriteMorph.prototype.useFlatLineEnds`, saved on the stage. */
  enum LineEnds(val xml: String) extends XmlValue {
    case Round extends LineEnds("round")
    case Flat extends LineEnds("flat")
  }
  object LineEnds { val fromXml: String => Option[LineEnds] = reader(values) }

  /** `Scene.penColorModel`; only `hsl` is written, its absence means HSV. */
  enum ColorModel(val xml: String) extends XmlValue {
    case Hsv extends ColorModel("hsv")
    case Hsl extends ColorModel("hsl")
  }
  object ColorModel { val fromXml: String => Option[ColorModel] = reader(values) }

  /** `CustomBlockDefinition.type`, also used by `<wish>`. */
  enum BlockType(val xml: String) extends XmlValue {
    case Command extends BlockType("command")
    case Reporter extends BlockType("reporter")
    case Predicate extends BlockType("predicate")
    case Hat extends BlockType("hat")
  }
  object BlockType { val fromXml: String => Option[BlockType] = reader(values) }

  /** `WatcherMorph.style`. */
  enum WatcherStyle(val xml: String) extends XmlValue {
    case Normal extends WatcherStyle("normal")
    case Large extends WatcherStyle("large")
    case Slider extends WatcherStyle("slider")
  }
  object WatcherStyle { val fromXml: String => Option[WatcherStyle] = reader(values) }

  /** A palette category: Snap's built-ins, TurtleStitch's `embroidery`, or one a project declares in `<palette>`. */
  enum BlockCategory extends XmlValue {
    case Motion, Looks, Sound, Pen, Control, Sensing, Operators, Variables, Lists, Other, Embroidery
    case Custom(name: String)

    def xml: String = this match {
      case Custom(name) => name
      case builtIn => builtIn.toString.toLowerCase
    }
    def isBuiltIn: Boolean = !this.isInstanceOf[Custom]
  }
  object BlockCategory {
    /** `SpriteMorph.prototype.categories` of the bundled TurtleStitch, in palette order. */
    val builtIn: List[BlockCategory] = List(Motion, Looks, Sound, Pen, Control, Sensing, Operators, Variables, Lists, Other, Embroidery)
    /** Category names are case-sensitive in Snap: `Variables` is a project category, not `variables`. */
    def fromXml(raw: String): BlockCategory = builtIn.find(_.xml == raw).getOrElse(Custom(raw))
  }
}
