package it.evadid.vm.conversion.structured

import it.evadid.vm.naming.BeEntityName
import it.evadid.util.logging.Logger
import scala.collection.immutable.VectorMap

/** Lexical structure only: opaque and unfinished method bodies remain editable source. */
object SourceStructure {
  enum MemberScope { case Static, Instance }
  case class Block(name: String, start: Int, end: Int, header: String, body: String, source: String, indent: Int)
  case class Parts(definedClasses: Map[BeEntityName, String], remaining: String)
  private val logger = Logger.withNameAndPrefixes(Some("SourceStructure"))

  def mask(source: String, python: Boolean): String = {
    val result = source.toCharArray
    var i = 0
    def erase(until: Int): Unit = {
      while i < until do { if result(i) != '\n' && result(i) != '\r' then result(i) = ' '; i += 1 }
    }
    while i < source.length do {
      if (python && source(i) == '#') || (!python && source.startsWith("//", i)) then {
        val end = source.indexOf('\n', i)
        erase(if end < 0 then source.length else end)
      } else if !python && source.startsWith("/*", i) then {
        val end = source.indexOf("*/", i + 2)
        erase(if end < 0 then source.length else end + 2)
      } else if source(i) == '"' || source(i) == '\'' then {
        val delimiter = if source.startsWith(source(i).toString * 3, i) then source(i).toString * 3 else source(i).toString
        var end = i + delimiter.length
        var closed = false
        while end < source.length && !closed do {
          if source(end) == '\\' then end = (end + 2).min(source.length)
          else if source.startsWith(delimiter, end) then { end += delimiter.length; closed = true }
          else end += 1
        }
        erase(end)
      } else i += 1
    }
    new String(result)
  }

  def remove(source: String, ranges: Seq[(Int, Int)]): String = {
    val outer = ranges.sortBy(_._1).foldLeft(Vector.empty[(Int, Int)]) { (kept, next) =>
      if kept.lastOption.exists(_._2 >= next._2) then kept else kept :+ next
    }
    outer.reverse.foldLeft(source) { case (text, (start, end)) => text.take(start) + text.drop(end) }
  }

  def dedent(source: String): String = {
    val lines = source.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1).toVector
    val indent = lines.filter(_.trim.nonEmpty).map(_.takeWhile(c => c == ' ' || c == '\t').length).minOption.getOrElse(0)
    lines.map(_.drop(indent)).mkString("\n").trim
  }
  def indent(source: String, spaces: Int = 4): String = source.linesIterator.map(" " * spaces + _).mkString("\n")

  def pythonBlocks(source: String, kind: String): Vector[Block] = {
    val clean = mask(source, true)
    val header = ("^([ \\t]*)(?:async[ \\t]+)?" + kind + "[ \\t]+([A-Za-z_][A-Za-z0-9_]*)\\b[^\\n]*").r
    val starts = Vector(0) ++ source.indices.filter(source(_) == '\n').map(_ + 1)
    val lines = starts.zip(starts.drop(1) :+ source.length)
    lines.zipWithIndex.flatMap { case ((start, limit), index) =>
      header.findFirstMatchIn(clean.substring(start, limit)).map { found =>
        val level = found.group(1).length
        val lineEnd = if limit > start && source(limit - 1) == '\n' then limit - 1 else limit
        val end = lines.drop(index + 1).find { (next, until) =>
          val line = clean.substring(next, until)
          line.trim.nonEmpty && line.takeWhile(c => c == ' ' || c == '\t').length <= level
        }.map(_._1).getOrElse(source.length)
        val colon = clean.substring(start, lineEnd).lastIndexOf(':')
        val bodyStart = if colon < 0 then lineEnd else start + colon + 1
        Block(found.group(2), start, end, source.substring(start, bodyStart).trim,
          dedent(source.substring(bodyStart, end)), dedent(source.substring(start, end)), level)
      }
    }
  }

  def javaClasses(source: String): Vector[Block] = {
    val clean = mask(source, false)
    val declaration = "\\b(?:(?:public|private|protected|static|final|abstract|strictfp)\\s+)*class\\s+([A-Za-z_$][\\w$]*)\\b".r
    declaration.findAllMatchIn(clean).map { found =>
      val open = clean.indexOf('{', found.end)
      var end = if open < 0 then source.length else open + 1
      var depth = 1
      while open >= 0 && end < clean.length && depth > 0 do {
        if clean(end) == '{' then depth += 1
        if clean(end) == '}' then depth -= 1
        end += 1
      }
      Block(found.group(1), found.start, end, source.substring(found.start, if open < 0 then end else open),
        if open < 0 then "" else source.substring(open + 1, if depth == 0 then end - 1 else end),
        source.substring(found.start, end), 0)
    }.toVector
  }

  private def extract(source: String, blocks: Vector[Block], python: Boolean, warn: String => Unit): Parts = {
    val classes = blocks.foldLeft(VectorMap.empty[BeEntityName, String]) { (saved, block) =>
      val name = BeEntityName.fromCodeString(block.name)
      if saved.keys.exists(_.universalInterpretation() == name.universalInterpretation()) then { warn(s"Duplicate class '${block.name}' ignored; keeping the first definition."); saved }
      else {
        val children = blocks.filter(child => child.start > block.start && child.end <= block.end)
        val text = remove(source.substring(block.start, block.end), children.map(c => (c.start - block.start, c.end - block.start)))
        saved.updated(name, if python then repairEmptyBlocks(dedent(text)) else text)
      }
    }
    val remaining = remove(source, blocks.map(b => b.start -> b.end))
    Parts(classes, if python then repairEmptyBlocks(remaining).trim else remaining.trim)
  }

  private def repairEmptyBlocks(source: String): String = {
    val lines = source.linesIterator.toVector
    val clean = mask(source, true).linesIterator.toVector
    lines.indices.flatMap { index =>
      val line = lines(index)
      val level = line.takeWhile(c => c == ' ' || c == '\t').length
      val next = clean.drop(index + 1).find(_.trim.nonEmpty)
      val needsPass = clean.lift(index).exists(_.trim.endsWith(":")) &&
        next.forall(_.takeWhile(c => c == ' ' || c == '\t').length <= level)
      if needsPass then Vector(line, " " * (level + 4) + "pass") else Vector(line)
    }.mkString("\n")
  }

  def java(source: String, warn: String => Unit = logger.logWarn): Parts = {
    val parts = extract(source, javaClasses(source), false, warn)
    val imports = "\\b(?:package|import)\\s+[^;]+;".r.findAllMatchIn(mask(parts.remaining, false)).toVector
    val rest = remove(parts.remaining, imports.map(m => m.start -> m.end))
    val onlyTrivia = mask(rest, false).trim.isEmpty
    val preamble = (imports.map(m => parts.remaining.substring(m.start, m.end)) ++
      Option.when(onlyTrivia)(rest.trim)).filter(_.nonEmpty).mkString("\n")
    if preamble.isEmpty then parts
    else {
      val (name, body) = parts.definedClasses.headOption.getOrElse(
        BeEntityName.fromCodeString("Main") -> "class Main {}")
      Parts(parts.definedClasses.updated(name, preamble + "\n" + body), if onlyTrivia then "" else rest.trim)
    }
  }
  def python(source: String, warn: String => Unit = logger.logWarn): Parts = extract(source, pythonBlocks(source, "class"), true, warn)

  /** Direct class members, separated without parsing their bodies. */
  def javaMembers(source: String): Vector[String] = {
    val clean = mask(source, false)
    val open = clean.indexOf('{')
    val close = clean.lastIndexOf('}')
    if open < 0 then Vector.empty
    else {
      val end = if close < open then source.length else close
      val out = Vector.newBuilder[String]
      var start = open + 1
      var depth = 0
      var parentheses = 0
      var i = start
      while i < end do {
        clean(i) match {
          case '(' => parentheses += 1
          case ')' => parentheses -= 1
          case '{' => depth += 1
          case '}' => depth -= 1
          case _ => ()
        }
        if (clean(i) == ';' && depth == 0 && parentheses == 0) || (clean(i) == '}' && depth == 0) then {
          out += source.substring(start, i + 1).trim
          start = i + 1
        }
        i += 1
      }
      if source.substring(start, end).trim.nonEmpty then out += source.substring(start, end).trim
      out.result().filter(_.nonEmpty)
    }
  }
  def scope(member: String): MemberScope =
    if "\\bstatic\\b".r.findFirstIn(mask(member.takeWhile(_ != '{'), false)).nonEmpty then MemberScope.Static else MemberScope.Instance
}
