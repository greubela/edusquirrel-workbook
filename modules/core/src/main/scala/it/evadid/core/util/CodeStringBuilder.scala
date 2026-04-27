package it.evadid.core.util

case class CodeStringBuilder(initStr: String = "") {

  private var curIntLevel = 0
  private val curString = new StringBuilder(initStr)

  def appendInLine(str: String): CodeStringBuilder = {
    curString.append(str)
    this
  }

  def appendParameters(pars: List[String]): CodeStringBuilder = {
    curString.append(pars.mkString("(", ",", ")"))
    this
  }

  def appendAsLines(multipleLineString: String) = {
    val lines = multipleLineString.split("\n")
    changeForEach(lines, (sb, str) => sb.appendNextLine(str))
  }

  def appendNextLine(str: String): CodeStringBuilder = {
    if (curString.nonEmpty) {
      curString.append("\n")
    }
    curString.append("    " * curIntLevel)
    curString.append(str)
    this
  }

  def changeIntLevel(addLevel: Int): CodeStringBuilder = {
    curIntLevel = curIntLevel + addLevel
    this
  }

  override def toString: String = curString.toString

  def setIntLevel(newLevel: Int): CodeStringBuilder = {
    curIntLevel = newLevel
    this
  }

  def changeForEach[O](seq: Seq[O], func: (CodeStringBuilder, O) => CodeStringBuilder): CodeStringBuilder = {
    var res = this
    seq.foreach(elem => res = func(res, elem))
    res
  }

}
