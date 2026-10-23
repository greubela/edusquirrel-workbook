package it.evadid.vm.parsing.java.clean

import it.evadid.vm.parsing.java.clean.model.JavaAST
import it.evadid.vm.parsing.java.clean.model.JavaAST.JavaProgram

import scala.annotation.tailrec

object JavaSourceMap {
  /** Half-open UTF-16 offsets into the original source. */
  case class SourceRange(start: Int, end: Int) {
    require(start >= 0 && end >= start, "Invalid source range.")
  }

  case class Position(line: Int, column: Int) {
    require(line >= 1 && column >= 1, "Invalid source position.")
  }

  case class Node[+A <: JavaAST](value: A, range: SourceRange,
      children: Vector[Node[JavaAST]] = Vector.empty)
}

final class JavaSourceMap private[clean](val source: String, val root: JavaSourceMap.Node[JavaProgram]) {
  import JavaSourceMap.*

  private val lineStarts: Vector[Int] = {
    @tailrec def collect(offset: Int, starts: Vector[Int]): Vector[Int] =
      if offset >= source.length then starts
      else source.charAt(offset) match {
        case '\r' =>
          val next = offset + (if offset + 1 < source.length && source.charAt(offset + 1) == '\n' then 2 else 1)
          collect(next, starts :+ next)
        case '\n' => collect(offset + 1, starts :+ (offset + 1))
        case _ => collect(offset + 1, starts)
      }
    collect(0, Vector(0))
  }

  def program: JavaProgram = root.value

  def at(path: Vector[Int]): Option[Node[JavaAST]] =
    path.foldLeft(Option[Node[JavaAST]](root)) { (node, index) =>
      node.flatMap(_.children.lift(index))
    }

  def position(offset: Int): Option[Position] = {
    @tailrec def find(low: Int, high: Int): Int =
      if low > high then high
      else {
        val middle = low + (high - low) / 2
        if lineStarts(middle) <= offset then find(middle + 1, high)
        else find(low, middle - 1)
      }

    Option.when(offset >= 0 && offset <= source.length) {
      val line = find(0, lineStarts.size - 1)
      Position(line + 1, offset - lineStarts(line) + 1)
    }
  }

  def text(path: Vector[Int]): Option[String] =
    at(path).filter(_.range.end <= source.length).map(node => source.substring(node.range.start, node.range.end))
}
