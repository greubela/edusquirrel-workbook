package contentmanagement.webElements.svg.builder

import contentmanagement.webElements.svg.builder.SvgPathBuilderCommand.*
import datastructures.core.geometry.{Dimension, Point}

// SvgPathParser.scala
object SvgPathParser {

  import scala.collection.mutable

  def parseString(pathDString: String): Option[SvgPathBuilder[Double]] = {
    // === Types ===
    case class Tok(cmd: Char, args: List[Double])

    // === Tokenization ===
    val cmdLetters = "AaCcHhLlMmQqVvZz"
    val s0 = pathDString.trim

    if (s0.isEmpty) return None

    // Normalize: separate command letters, replace commas with spaces
    val normalized = {
      val b = new StringBuilder(s0.length * 2)
      var i = 0
      while (i < s0.length) {
        val ch = s0.charAt(i)
        if (cmdLetters.indexOf(ch) >= 0) { b.append(' ').append(ch).append(' ') }
        else if (ch == ',' || ch.isWhitespace) b.append(' ')
        else b.append(ch)
        i += 1
      }
      b.toString.trim
    }

    val parts = if (normalized.isEmpty) Nil else normalized.split("\\s+").toList

    def arity(c: Char): Int = c.toLower match {
      case 'm' => 2
      case 'l' => 2
      case 'h' => 1
      case 'v' => 1
      case 'c' => 6
      case 'q' => 4
      case 'a' => 7
      case 'z' => 0
      case _   => -1
    }

    val tokensOpt = {
      val buf = mutable.ListBuffer[Tok]()
      var idx = 0
      var currentCmd: Char = 0
      var pendingArgs = false
      var valid = true

      def isCommandToken(s: String): Boolean = s.length == 1 && cmdLetters.indexOf(s.head) >= 0

      def readArgs(n: Int): Option[List[Double]] = {
        if (idx + n > parts.length) None
        else {
          val slice = parts.slice(idx, idx + n).map(_.toDoubleOption)
          if (slice.forall(_.isDefined)) Some(slice.flatten) else None
        }
      }

      while (valid && idx < parts.length) {
        val part = parts(idx)
        if (isCommandToken(part)) {
          currentCmd = part.head
          idx += 1
          val n = arity(currentCmd)
          if (n == 0) {
            buf += Tok(currentCmd, Nil)
            pendingArgs = false
            currentCmd = 0
          } else {
            pendingArgs = true
          }
        } else if (currentCmd != 0) {
          val n = arity(currentCmd)
          readArgs(n) match {
            case Some(nums) =>
              buf += Tok(currentCmd, nums)
              idx += n
              pendingArgs = false
              currentCmd =
                if (currentCmd == 'M') 'L'
                else if (currentCmd == 'm') 'l'
                else currentCmd
            case None =>
              valid = false
          }
        } else {
          valid = false
        }
      }

      if (!valid || pendingArgs || buf.isEmpty) None
      else if (buf.head.cmd != 'M' && buf.head.cmd != 'm') None
      else Some(buf.toList)
    }

    val tokens = tokensOpt match {
      case Some(value) => value
      case None        => return None
    }

    // === Build using only the SvgPathBuilder interface ===
    var builderOpt: Option[SvgPathBuilder[Double]] = None
    var current = Point(0.0, 0.0)
    var subStart = Point(0.0, 0.0)
    var haveStart = false

    def ensureStart(p: Point[Double]): Boolean = {
      if (!haveStart) {
        builderOpt = Some(SvgPathBuilder[Double](p))
        current = p
        subStart = p
        haveStart = true
      }
      true
    }

    def fail(): Option[SvgPathBuilder[Double]] = None

    tokens.foreach {
      // M: absolute moveto
      case Tok('M', List(x, y)) =>
        val p = Point(x, y)
        ensureStart(p)
        builderOpt = builderOpt.map(_.moveToAbs(p))
        current = p
        subStart = p

      // m: relative moveto
      case Tok('m', List(dx, dy)) =>
        val p = if (!haveStart) Point(dx, dy) else Point(current.x + dx, current.y + dy)
        ensureStart(p)
        builderOpt = builderOpt.map(_.moveToAbs(p))
        current = p
        subStart = p

      // L: absolute line
      case Tok('L', List(x, y)) =>
        val p = Point(x, y)
        builderOpt = builderOpt.map(_.lineToAbs(p))
        current = p

      // l: relative line
      case Tok('l', List(dx, dy)) =>
        builderOpt = builderOpt.map(_.lineToRel(Dimension(dx, dy)))
        current = Point(current.x + dx, current.y + dy)

      // H: absolute horizontal (translate to relative)
      case Tok('H', List(x)) =>
        val dx = x - current.x
        builderOpt = builderOpt.map(_.horizontalLineWithWidth(dx))
        current = Point(x, current.y)

      // h: relative horizontal
      case Tok('h', List(dx)) =>
        builderOpt = builderOpt.map(_.horizontalLineWithWidth(dx))
        current = Point(current.x + dx, current.y)

      // V: absolute vertical (translate to relative)
      case Tok('V', List(y)) =>
        val dy = y - current.y
        builderOpt = builderOpt.map(_.verticalLineWithHeight(dy))
        current = Point(current.x, y)

      // v: relative vertical
      case Tok('v', List(dy)) =>
        builderOpt = builderOpt.map(_.verticalLineWithHeight(dy))
        current = Point(current.x, current.y + dy)

      // C: absolute cubic bezier
      case Tok('C', List(x1, y1, x2, y2, x, y)) =>
        val cp1 = Point(x1, y1)
        val cp2 = Point(x2, y2)
        val e   = Point(x, y)
        builderOpt = builderOpt.map(_.cubicBezierToAbs(cp1, cp2, e))
        current = e

      // c: relative cubic bezier
      case Tok('c', List(dx1, dy1, dx2, dy2, dx, dy)) =>
        builderOpt = builderOpt.map(_.cubicBezierToRel(
          Dimension(dx1, dy1),
          Dimension(dx2, dy2),
          Dimension(dx, dy)
        ))
        current = Point(current.x + dx, current.y + dy)

      // Q: absolute quadratic
      case Tok('Q', List(cx, cy, x, y)) =>
        val cp = Point(cx, cy)
        val e  = Point(x, y)
        builderOpt = builderOpt.map(_.quadraticBezierToAbs(cp, e))
        current = e

      // q: relative quadratic
      case Tok('q', List(dcx, dcy, dx, dy)) =>
        builderOpt = builderOpt.map(_.quadraticBezierWithRel(
          Dimension(dcx, dcy),
          Dimension(dx, dy)
        ))
        current = Point(current.x + dx, current.y + dy)

      // a: relative arc
      case Tok('a', List(rx, ry, rot, largeFlag, sweepFlag, dx, dy)) =>
        val largeArc = largeFlag != 0.0
        val sweep = sweepFlag != 0.0
        builderOpt = builderOpt.map(_.arcToRel(rx, ry, rot, largeArc, sweep, Dimension(dx, dy)))
        current = Point(current.x + dx, current.y + dy)

      // A: absolute arc
      case Tok('A', List(rx, ry, rot, largeFlag, sweepFlag, x, y)) =>
        val largeArc = largeFlag != 0.0
        val sweep = sweepFlag != 0.0
        val end = Point(x, y)
        builderOpt = builderOpt.map(_.arcToAbs(rx, ry, rot, largeArc, sweep, end))
        current = end

      // Z / z
      case Tok('Z', Nil) | Tok('z', Nil) =>
        builderOpt = builderOpt.map(_.closePath())
        current = subStart

      // Anything else (S/s/T/t or malformed)
      case _ =>
        return None
    }

    builderOpt
  }
}
