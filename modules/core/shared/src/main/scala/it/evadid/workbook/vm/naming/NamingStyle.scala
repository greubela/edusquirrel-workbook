package it.evadid.workbook.vm.naming


sealed trait NamingStyle {
  def applyStyle(parts: List[String]): String

  def stringToParts(string: String): List[String]
}


object NamingStyle {

  private def splitOnUnderscore(str: String): List[String] = str.split("[_]+").toList

  private def splitOnSpaces(str: String): List[String] = str.split("[\\s]+").toList

  private def splitOnUppercase(str: String): List[String] = {
    // split on uppercase but add splitting characters back as lowercase:
    val upper: Array[Char] = str.toCharArray.filter(!_.isLower)
    val split: List[String] = str.split("[A-Z]+").toList
    split.zipWithIndex.map { case (s: String, i: Int) =>
      if (i == 0) "" else upper(i - 1).toString + s.toLowerCase
    }
  }

  def fromAnyNotationToParts(stringWithUnknownStyle: String): List[String] = {
    splitOnSpaces(stringWithUnknownStyle).flatMap(splitOnUnderscore).flatMap(splitOnUppercase)
  }

  case object CamelCase extends NamingStyle {
    def applyStyle(parts: List[String]): String = {
      parts.head.toLowerCase + parts.tail.map(_.toLowerCase.capitalize).mkString("", "", "")
    }

    def stringToParts(string: String): List[String] = {
      splitOnUppercase(string)
    }
  }

  case object SnakeCase extends NamingStyle {
    def applyStyle(parts: List[String]): String = {
      parts.map(_.toLowerCase).mkString("_")
    }

    def stringToParts(string: String): List[String] = {
      splitOnUnderscore(string.toLowerCase)
    }
  }

  case object AllcapsSchool extends NamingStyle {
    def applyStyle(parts: List[String]): String = {
      parts.map(_.toUpperCase).mkString("_")
    }

    def stringToParts(string: String): List[String] = {
      splitOnUnderscore(string.toLowerCase)
    }
  }

}
