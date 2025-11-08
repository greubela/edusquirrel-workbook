package contentmanagement.webElements.svg.builder

import contentmanagement.model.geometry.{Dimension, Point}
import contentmanagement.webElements.svg.builder.SvgPathBuilderCommand.*
// SvgPathParser.scala
object SvgPathParser {

  import scala.annotation.tailrec

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

    @tailrec
    def parseTokens(rest: List[String],
                    currentCmd: Char,
                    acc: List[Tok]): Either[String, List[Tok]] = {
      rest match {
        // New explicit command
        case h :: t if h.length == 1 && cmdLetters.indexOf(h.head) >= 0 =>
          val c = h.head
          val n = arity(c)
          if (n == 0) parseTokens(t, c, Tok(c, Nil) :: acc)
          else takeGroups(t, c, n, firstOfMove = true, acc)

        // Implicit repeating of last command
        case _ if currentCmd != 0 =>
          val n = arity(currentCmd)
          if (n == 0) Left("Unexpected arguments after a zero-arity command")
          else takeGroups(rest, currentCmd, n, firstOfMove = false, acc)

        case Nil =>
          Right(acc.reverse)

        case h :: _ =>
          Left(s"Path must start with a moveto; found: $h")
      }
    }

    @tailrec
    def takeGroups(rest: List[String],
                   cmd: Char,
                   nPer: Int,
                   firstOfMove: Boolean,
                   acc: List[Tok]): Either[String, List[Tok]] = {
      def takeN(xs: List[String], n: Int): Option[(List[Double], List[String])] =
        if (xs.lengthCompare(n) >= 0) {
          val head = xs.take(n).map(_.toDoubleOption)
          if (head.forall(_.isDefined)) Some(head.flatten -> xs.drop(n)) else None
        } else None

      rest match {
        case Nil =>
          if (firstOfMove && (cmd == 'M' || cmd == 'm')) Left("moveto requires coordinates")
          else Right(acc.reverse)

        case _ =>
          takeN(rest, nPer) match {
            case Some((nums, tail)) =>
              val (tok, nextCmd) =
                if (cmd == 'M' && !firstOfMove) Tok('L', nums) -> 'L'
                else if (cmd == 'm' && !firstOfMove) Tok('l', nums) -> 'l'
                else Tok(cmd, nums) -> cmd
              // After first group of M/m, subsequent numbers are implicit L/l
              val stillFirst = firstOfMove && (cmd == 'M' || cmd == 'm') && acc.nonEmpty && acc.head.cmd != cmd
              takeGroups(tail, nextCmd, nPer, firstOfMove = false, tok :: acc)

            case None =>
              if (firstOfMove && (cmd == 'M' || cmd == 'm')) Left("moveto requires coordinates")
              else Right(acc.reverse)
          }
      }
    }

    val tokens = parseTokens(parts, 0.toChar, Nil) match {
      case Left(_)      => return None
      case Right(value) => value
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

      // a: relative arc — support only the two convenience macros
      case Tok('a', List(rx, ry, rot, largeFlag, sweepFlag, dx, dy)) =>
        val isMacroTopRight   = (rot == 0.0) && (largeFlag == 1.0) && (sweepFlag == 1.0) && (ry == rx) && (dx == 2.0 * rx) && (dy == 0.0)
        val isMacroRightDown  = (rot == 0.0) && (largeFlag == 1.0) && (sweepFlag == 1.0) && (ry == rx) && (dx == 0.0)        && (dy == 2.0 * rx)

        if (isMacroTopRight) {
          builderOpt = builderOpt.map(_.addArcToTheTopMoveRight(rx))
          current = Point(current.x + dx, current.y + dy)
        } else if (isMacroRightDown) {
          builderOpt = builderOpt.map(_.addArcToTheRightMoveBottom(rx))
          current = Point(current.x + dx, current.y + dy)
        } else {
          return None // unsupported arc form for this interface
        }

      // A: absolute arc — try to map to the same macros
      case Tok('A', List(rx, ry, rot, largeFlag, sweepFlag, x, y)) =>
        val dx = x - current.x
        val dy = y - current.y
        val isMacroTopRight   = (rot == 0.0) && (largeFlag == 1.0) && (sweepFlag == 1.0) && (ry == rx) && (dx == 2.0 * rx) && (dy == 0.0)
        val isMacroRightDown  = (rot == 0.0) && (largeFlag == 1.0) && (sweepFlag == 1.0) && (ry == rx) && (dx == 0.0)        && (dy == 2.0 * rx)

        if (isMacroTopRight) {
          builderOpt = builderOpt.map(_.addArcToTheTopMoveRight(rx))
          current = Point(x, y)
        } else if (isMacroRightDown) {
          builderOpt = builderOpt.map(_.addArcToTheRightMoveBottom(rx))
          current = Point(x, y)
        } else {
          return None
        }

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
