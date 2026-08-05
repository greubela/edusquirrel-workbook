package it.evadid.core.util

import scala.annotation.tailrec

case class CodeStringBuilderMutable(initStr: String = "") {

  private var curIntLevel = 0
  private val curString = new StringBuilder(initStr)

  def appendInLine(str: String): CodeStringBuilderMutable = {
    curString.append(str)
    this
  }

  def appendParameters(pars: List[String]): CodeStringBuilderMutable = {
    curString.append(pars.mkString("(", ",", ")"))
    this
  }

  @tailrec
  final def appendAllAsLines(multipleLineStrings: Seq[String]): CodeStringBuilderMutable = {
    if (multipleLineStrings.isEmpty) this
    else if (multipleLineStrings.size == 1) appendAsLines(multipleLineStrings.head)
    else this.appendAsLines(multipleLineStrings.head).appendAllAsLines(multipleLineStrings.tail)
  }

  def appendAsLines(multipleLineString: String) = {
    val lines = multipleLineString.split("\n")
    changeForEach(lines.toIndexedSeq, (sb, str) => sb.appendNextLine(str))
  }

  def appendNextLine(str: String): CodeStringBuilderMutable = {
    if (curString.nonEmpty) {
      curString.append("\n")
    }
    curString.append("    " * curIntLevel)
    curString.append(str)
    this
  }

  def changeIntLevel(addLevel: Int): CodeStringBuilderMutable = {
    curIntLevel = curIntLevel + addLevel
    this
  }

  override def toString: String = curString.toString

  def setIntLevel(newLevel: Int): CodeStringBuilderMutable = {
    curIntLevel = newLevel
    this
  }

  def changeForEach[O](seq: Seq[O], func: (CodeStringBuilderMutable, O) => CodeStringBuilderMutable): CodeStringBuilderMutable = {
    var res = this
    seq.foreach(elem => res = func(res, elem))
    res
  }

}
