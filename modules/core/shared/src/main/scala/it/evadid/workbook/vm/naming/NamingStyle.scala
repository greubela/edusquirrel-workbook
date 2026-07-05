package it.evadid.workbook.vm.naming


sealed trait NamingStyle {
  def applyStyle(parts: List[String]): String

  def stringToParts(string: String): List[String]
}


object NamingStyle {

  private def splitOnUnderscore(str: String): List[String] = str.split("[_]+").toList

  private def splitOnSpaces(str: String): List[String] = str.split("[\\s]+").toList

  private def splitOnUppercase(str: String): List[String] = {
    if (str.isEmpty) Nil
    else {
      val builder = List.newBuilder[String]
      val current = new StringBuilder
      str.foreach { char =>
        if (char.isUpper && current.nonEmpty) {
          builder += current.toString().toLowerCase
          current.clear()
        }
        current.append(char)
      }
      if (current.nonEmpty) builder += current.toString().toLowerCase
      builder.result()
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
