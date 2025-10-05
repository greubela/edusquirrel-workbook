package util

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


  def appendNextLine(str: String): CodeStringBuilder = {
    curString.append("\n" + ("    " * curIntLevel) + str)
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

}
