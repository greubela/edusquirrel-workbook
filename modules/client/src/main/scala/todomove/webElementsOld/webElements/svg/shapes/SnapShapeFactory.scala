package todomove.webElementsOld.webElements.svg.shapes

import it.evadid.core.datastructures.geometry.{Bounds, Point}
import todomove.webElementsOld.webElements.svg.builder.SvgPathBuilder

/** SVG translations of Snap!'s canonical block-outline algorithms.
  *
  * Every builder below cites the matching method in the vendored Snap! source
  * and keeps the original Canvas operations next to the translation. This is
  * intentionally separate from [[ShapeFactory]], whose shapes predate the
  * Snap-compatible renderer and use different geometry.
  */
private[shapes] object SnapShapeFactory {

  /** Geometry constants used by Snap's SyntaxElementMorph at a given scale.
    * Keep these independent from the legacy segment-based shapes: they map
    * directly to blocks.js and are therefore suitable for an exact SVG port.
    *
    * Original: `SyntaxElementMorph.prototype.setScale`, `blocks.js:264-276`:
    * {{
    * this.corner = 3 * scale;      this.rounding = 9 * scale;
    * this.edge = scale;            this.inset = 6 * scale;
    * this.hatHeight = 12 * scale;  this.hatWidth = 70 * scale;
    * this.dent = 8 * scale;
    * }}
    * Vendored at `resources/programs/20260212TurtleStitch/turtlestitchsrc/blocks.js`.
    */
  private[shapes] final case class SnapBlockGeometry(
    corner: Double,
    rounding: Double,
    edge: Double,
    inset: Double,
    dent: Double,
    hatHeight: Double,
    hatWidth: Double
  )

  private[shapes] object SnapBlockGeometry {
    def atScale(scale: Double = 1.0): SnapBlockGeometry = SnapBlockGeometry(
      corner = 3 * scale,
      rounding = 9 * scale,
      edge = scale,
      inset = 6 * scale,
      dent = 8 * scale,
      hatHeight = 12 * scale,
      hatWidth = 70 * scale
    )
  }

  /** Bounds of a CSlotMorph relative to its enclosing block. */
  private[shapes] final case class SnapCSlot(bounds: Bounds[Double])

  /** Small CanvasRenderingContext2D-compatible path adapter. Snap describes
    * circular arcs by center and angles, whereas SVG describes them by their
    * endpoint. This conversion retains the exact center/radius geometry.
    */
  private final case class SnapPath(
    builder: SvgPathBuilder[Double],
    current: Point[Double]
  ) {
    private val Epsilon = 1e-9

    def moveTo(x: Double, y: Double): SnapPath = {
      val point = Point(x, y)
      copy(builder = builder.moveToAbs(point), current = point)
    }

    def lineTo(x: Double, y: Double): SnapPath = {
      val point = Point(x, y)
      copy(builder = builder.lineToAbs(point), current = point)
    }

    def bezierCurveTo(cp1x: Double, cp1y: Double, cp2x: Double, cp2y: Double, x: Double, y: Double): SnapPath = {
      val end = Point(x, y)
      copy(builder = builder.cubicBezierToAbs(Point(cp1x, cp1y), Point(cp2x, cp2y), end), current = end)
    }

    def arc(cx: Double, cy: Double, radius: Double, start: Double, end: Double, anticlockwise: Boolean): SnapPath = {
      if (radius <= 0) this
      else {
        val startPoint = Point(cx + radius * math.cos(start), cy + radius * math.sin(start))
        val endPoint = Point(cx + radius * math.cos(end), cy + radius * math.sin(end))
        val connected =
          if (math.abs(current.x - startPoint.x) <= Epsilon && math.abs(current.y - startPoint.y) <= Epsilon) this
          else lineTo(startPoint.x, startPoint.y)
        val tau = math.Pi * 2
        val rawSpan = if (anticlockwise) start - end else end - start
        val span = ((rawSpan % tau) + tau) % tau
        connected.copy(
          builder = connected.builder.arcToAbs(radius, radius, 0, span > math.Pi, sweep = !anticlockwise, endPoint),
          current = endPoint
        )
      }
    }

    def close(): SnapPath = copy(builder = builder.closePath())
  }

  private def radians(degrees: Double): Double = degrees * math.Pi / 180.0

  private def startSnapPath(bounds: Bounds[Double]): SnapPath =
    SnapPath(SvgPathBuilder(bounds.startPoint), bounds.startPoint)

  /** Literal translation of Snap's command-block outline.
    *
    * Original: `CommandBlockMorph.prototype.outlinePath`, `blocks.js:6886-6952`:
    * {{
    * ctx.arc(this.corner, this.corner, radius, radians(-180), radians(-90), false);
    * ctx.lineTo(this.corner + this.inset, inset);
    * ctx.lineTo(indent, this.corner + inset);
    * ctx.lineTo(indent + this.dent, this.corner + inset);
    * ctx.lineTo(this.corner * 3 + this.inset + this.dent, inset);
    * ctx.lineTo(this.width() - this.corner, inset);
    * ctx.arc(this.width() - this.corner, this.corner, radius, radians(-90), radians(0), false);
    * this.cSlots().forEach(slot => slot.outlinePath(ctx, inset, slot.position().subtract(pos)));
    * }}
    * The remainder of that method supplies the identical bottom corner and
    * bottom jigsaw sequence translated below. Vendored at
    * `resources/programs/20260212TurtleStitch/turtlestitchsrc/blocks.js`.
    */
  def buildSnapCommandShape(
    bounds: Bounds[Double],
    geometry: SnapBlockGeometry = SnapBlockGeometry.atScale(),
    outlineInset: Double = 0,
    isStop: Boolean = false,
    cSlots: List[SnapCSlot] = Nil
  ): SvgPathBuilder[Double] = {
    val x = bounds.startPoint.x
    val y = bounds.startPoint.y
    val width = bounds.width
    val height = bounds.height
    val corner = geometry.corner
    val indent = corner * 2 + geometry.inset
    val bottom = height - corner
    val bottomCorner = height - corner * 2
    val radius = math.max(corner - outlineInset, 0)

    var path = startSnapPath(bounds)
      .arc(x + corner, y + corner, radius, radians(-180), radians(-90), anticlockwise = false)
      .lineTo(x + corner + geometry.inset, y + outlineInset)
      .lineTo(x + indent, y + corner + outlineInset)
      .lineTo(x + indent + geometry.dent, y + corner + outlineInset)
      .lineTo(x + corner * 3 + geometry.inset + geometry.dent, y + outlineInset)
      .lineTo(x + width - corner, y + outlineInset)
      .arc(x + width - corner, y + corner, radius, radians(-90), radians(0), anticlockwise = false)

    cSlots.foreach(slot => path = appendCSlotOutline(path, bounds.startPoint, slot, geometry, outlineInset))

    path = path.arc(x + width - corner, y + bottomCorner, radius, radians(0), radians(90), anticlockwise = false)
    if (!isStop) path = path
      .lineTo(x + width - corner, y + bottom - outlineInset)
      .lineTo(x + corner * 3 + geometry.inset + geometry.dent, y + bottom - outlineInset)
      .lineTo(x + indent + geometry.dent, y + bottom + corner - outlineInset)
      .lineTo(x + indent, y + bottom + corner - outlineInset)
      .lineTo(x + corner + geometry.inset, y + bottom - outlineInset)

    path
      .arc(x + corner, y + bottomCorner, radius, radians(90), radians(180), anticlockwise = false)
      .close().builder
  }

  /** Command outline with one or more embedded C slots. One slot represents
    * while/if; two slots represent if/else. Slot bounds are the values Snap's
    * own layout pass would expose relative to the enclosing block.
    */
  def buildSnapCShape(
    bounds: Bounds[Double],
    slotBounds: List[Bounds[Double]],
    geometry: SnapBlockGeometry = SnapBlockGeometry.atScale(),
    outlineInset: Double = 0,
    isStop: Boolean = false
  ): SvgPathBuilder[Double] =
    buildSnapCommandShape(
      bounds,
      geometry,
      outlineInset,
      isStop,
      slotBounds.map(SnapCSlot.apply)
    )

  /** Literal translation of Snap's hat-block outline.
    *
    * Original: `HatBlockMorph.prototype.outlinePath`, `blocks.js:7297-7372`:
    * {{
    * r = ((4 * h * h) + (s * s)) / (8 * h);
    * a = degrees(4 * Math.atan(2 * h / s));
    * sa = a / 2;
    * sp = Math.min(s * 1.7, this.width() - this.corner);
    * ctx.moveTo(inset, h + this.corner);
    * ctx.arc(s / 2, r, r, radians(-sa - 90), radians(-90), false);
    * ctx.bezierCurveTo(s, 0, s, h, sp, h);
    * }}
    * Vendored at `resources/programs/20260212TurtleStitch/turtlestitchsrc/blocks.js`.
    */
  def buildSnapHatShape(
    bounds: Bounds[Double],
    geometry: SnapBlockGeometry = SnapBlockGeometry.atScale(),
    outlineInset: Double = 0,
    isStop: Boolean = false,
    cSlots: List[SnapCSlot] = Nil
  ): SvgPathBuilder[Double] = {
    val x = bounds.startPoint.x
    val y = bounds.startPoint.y
    val width = bounds.width
    val height = bounds.height
    val corner = geometry.corner
    val indent = corner * 2 + geometry.inset
    val bottom = height - corner
    val bottomCorner = height - corner * 2
    val radius = math.max(corner - outlineInset, 0)
    val s = geometry.hatWidth
    val h = geometry.hatHeight
    val r = ((4 * h * h) + (s * s)) / (8 * h)
    val angle = 4 * math.atan(2 * h / s)
    val halfAngle = angle / 2
    val sp = math.min(s * 1.7, width - corner)

    var path = startSnapPath(bounds)
      .moveTo(x + outlineInset, y + h + corner)
      .arc(x + s / 2, y + r, r, -halfAngle - math.Pi / 2, -math.Pi / 2, anticlockwise = false)
      .bezierCurveTo(x + s, y, x + s, y + h, x + sp, y + h)
      .arc(x + width - corner, y + h + corner, radius, radians(-90), radians(0), anticlockwise = false)

    cSlots.foreach(slot => path = appendCSlotOutline(path, bounds.startPoint, slot, geometry, outlineInset))

    path = path.arc(x + width - corner, y + bottomCorner, radius, radians(0), radians(90), anticlockwise = false)
    if (!isStop) path = path
      .lineTo(x + width - corner, y + bottom - outlineInset)
      .lineTo(x + corner * 3 + geometry.inset + geometry.dent, y + bottom - outlineInset)
      .lineTo(x + indent + geometry.dent, y + bottom + corner - outlineInset)
      .lineTo(x + indent, y + bottom + corner - outlineInset)
      .lineTo(x + corner + geometry.inset, y + bottom - outlineInset)

    path
      .arc(x + corner, y + bottomCorner, radius, radians(90), radians(180), anticlockwise = false)
      .close().builder
  }

  /** Literal translation of Snap's oval and predicate reporter outlines.
    *
    * Originals: `ReporterBlockMorph.prototype.outlinePathOval`,
    * `blocks.js:7771-7825`, and `outlinePathDiamond`, `blocks.js:7827-7851`:
    * {{
    * ctx.arc(r, r, radius, radians(-180), radians(-90), false);
    * ctx.arc(w - r, r, radius, radians(-90), radians(0), false);
    * ctx.arc(w - r, h - r, radius, radians(0), radians(90), false);
    * ctx.arc(r, h - r, radius, radians(90), radians(180), false);
    *
    * ctx.moveTo(inset, h2); ctx.lineTo(r, inset);
    * ctx.lineTo(right - inset, inset); ctx.lineTo(w - inset, h2);
    * ctx.lineTo(right - inset, h - inset); ctx.lineTo(r, h - inset);
    * }}
    * Vendored at `resources/programs/20260212TurtleStitch/turtlestitchsrc/blocks.js`.
    */
  def buildSnapReporterShape(
    bounds: Bounds[Double],
    geometry: SnapBlockGeometry = SnapBlockGeometry.atScale(),
    outlineInset: Double = 0,
    isPredicate: Boolean = false,
    cSlots: List[SnapCSlot] = Nil
  ): SvgPathBuilder[Double] = {
    val x = bounds.startPoint.x
    val y = bounds.startPoint.y
    val width = bounds.width
    val height = bounds.height
    if (isPredicate) {
      val halfHeight = math.floor(height / 2)
      val right = width - geometry.rounding
      var path = startSnapPath(bounds)
        .moveTo(x + outlineInset, y + halfHeight)
        .lineTo(x + geometry.rounding, y + outlineInset)
        .lineTo(x + right - outlineInset, y + outlineInset)
      if (cSlots.nonEmpty) cSlots.foreach(slot => path = appendCSlotOutline(path, bounds.startPoint, slot, geometry, outlineInset))
      else path = path.lineTo(x + width - outlineInset, y + halfHeight)
      path
        .lineTo(x + right - outlineInset, y + height - outlineInset)
        .lineTo(x + geometry.rounding, y + height - outlineInset)
        .close().builder
    } else {
      val r = math.min(geometry.rounding, height / 2)
      val radius = math.max(r - outlineInset, 0)
      var path = startSnapPath(bounds)
        .arc(x + r, y + r, radius, radians(-180), radians(-90), anticlockwise = false)
        .arc(x + width - r, y + r, radius, radians(-90), radians(0), anticlockwise = false)
      cSlots.foreach(slot => path = appendCSlotOutline(path, bounds.startPoint, slot, geometry, outlineInset))
      path
        .arc(x + width - r, y + height - r, radius, radians(0), radians(90), anticlockwise = false)
        .arc(x + r, y + height - r, radius, radians(90), radians(180), anticlockwise = false)
        .lineTo(x + r - radius, y + r)
        .close().builder
    }
  }

  /** Literal translation of Snap's C-slot indentation.
    *
    * Original: `CSlotMorph.prototype.outlinePath`, `blocks.js:10673-10750`:
    * {{
    * ctx.lineTo(this.width() + ox - inset, oy);
    * ctx.arc(this.width() - this.corner + ox, oy, radius,
    *         radians(90), radians(0), true);
    * // Snap's five lineTo calls form the inner jigsaw dent.
    * ctx.arc(this.inset + this.corner + ox, this.corner * 2 + oy,
    *         this.corner + inset, radians(270), radians(180), true);
    * ctx.arc(this.inset + this.corner + ox,
    *         this.height() - this.corner * 2 + oy,
    *         this.corner + inset, radians(180), radians(90), true);
    * }}
    * Vendored at `resources/programs/20260212TurtleStitch/turtlestitchsrc/blocks.js`.
    */
  private def appendCSlotOutline(
    initial: SnapPath,
    blockOrigin: Point[Double],
    slot: SnapCSlot,
    geometry: SnapBlockGeometry,
    outlineInset: Double
  ): SnapPath = {
    val ox = blockOrigin.x + slot.bounds.startPoint.x
    val oy = blockOrigin.y + slot.bounds.startPoint.y
    val width = slot.bounds.width
    val height = slot.bounds.height
    val corner = geometry.corner
    val radius = math.max(corner - outlineInset, 0)
    initial
      .lineTo(width + ox - outlineInset, oy)
      .arc(width - corner + ox, oy, radius, radians(90), radians(0), anticlockwise = true)
      .lineTo(width - corner + ox, corner + oy - outlineInset)
      .lineTo(geometry.inset * 2 + corner * 3 + geometry.dent + ox, corner + oy - outlineInset)
      .lineTo(geometry.inset * 2 + corner * 2 + geometry.dent + ox, corner * 2 + oy - outlineInset)
      .lineTo(geometry.inset * 2 + corner * 2 + ox, corner * 2 + oy - outlineInset)
      .lineTo(geometry.inset * 2 + corner + ox, corner + oy - outlineInset)
      .lineTo(geometry.inset + corner + ox, corner + oy - outlineInset)
      .arc(geometry.inset + corner + ox, corner * 2 + oy, corner + outlineInset, radians(270), radians(180), anticlockwise = true)
      .lineTo(geometry.inset + ox - outlineInset, height - corner * 2 + oy)
      .arc(geometry.inset + corner + ox, height - corner * 2 + oy, corner + outlineInset, radians(180), radians(90), anticlockwise = true)
      .lineTo(width - corner + ox, height - corner + oy + outlineInset)
      .arc(width - corner + ox, height + oy, radius, radians(-90), radians(0), anticlockwise = false)
  }


}
