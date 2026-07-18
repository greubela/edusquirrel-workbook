package it.evadid.vm.parsing.python.clean

import it.evadid.core.util.io.Serializer

private[clean] object PythonCollectionSerializers {

  private def splitTopLevel(serialized: String, separator: Char): List[String] = {
    val parts = scala.collection.mutable.ListBuffer.empty[String]
    val current = new StringBuilder
    var nesting = 0
    var quote: Option[Char] = None
    var escaped = false

    serialized.foreach { char =>
      quote match {
        case Some(activeQuote) =>
          current.append(char)
          if (escaped) escaped = false
          else if (char == '\\') escaped = true
          else if (char == activeQuote) quote = None
        case None =>
          char match {
            case '\'' | '"' =>
              quote = Some(char)
              current.append(char)
            case '[' | '(' | '{' =>
              nesting += 1
              current.append(char)
            case ']' | ')' | '}' =>
              nesting -= 1
              current.append(char)
            case `separator` if nesting == 0 =>
              parts += current.result().trim
              current.clear()
            case _ => current.append(char)
          }
      }
    }

    val last = current.result().trim
    if (last.nonEmpty) parts += last
    parts.toList
  }

  private def splitTopLevelKeyValue(entry: String): (String, String) =
    splitTopLevel(entry, ':') match {
      case key :: valueParts => key -> valueParts.mkString(":").trim
      case Nil => "" -> ""
    }

  def collectionSerializer[Element](elementSerializer: Serializer[Element], open: String, close: String): Serializer[List[Element]] = new Serializer[List[Element]] {
    override def serialize(obj: List[Element]): String = obj.map(elementSerializer.serialize).mkString(open, ", ", close)

    override def deserialize(serialized: String): List[Element] = {
      val trimmed = serialized.trim
      val inner = if (trimmed.startsWith(open) && trimmed.endsWith(close)) trimmed.drop(open.length).dropRight(close.length).trim else trimmed
      if (inner.isEmpty) List() else splitTopLevel(inner, ',').map(elementSerializer.deserialize)
    }
  }

  def setSerializer[Element](elementSerializer: Serializer[Element]): Serializer[Set[Element]] = new Serializer[Set[Element]] {
    override def serialize(obj: Set[Element]): String = obj.map(elementSerializer.serialize).mkString("{", ", ", "}")

    override def deserialize(serialized: String): Set[Element] = {
      val trimmed = serialized.trim
      val inner = if (trimmed.startsWith("{") && trimmed.endsWith("}")) trimmed.drop(1).dropRight(1).trim else trimmed
      if (inner.isEmpty) Set() else splitTopLevel(inner, ',').map(elementSerializer.deserialize).toSet
    }
  }

  def dictSerializer[Key, Value](keySerializer: Serializer[Key], valueSerializer: Serializer[Value]): Serializer[Map[Key, Value]] = new Serializer[Map[Key, Value]] {
    override def serialize(obj: Map[Key, Value]): String = obj.map { case (key, value) => s"${keySerializer.serialize(key)}: ${valueSerializer.serialize(value)}" }.mkString("{", ", ", "}")

    override def deserialize(serialized: String): Map[Key, Value] = {
      val trimmed = serialized.trim
      val inner = if (trimmed.startsWith("{") && trimmed.endsWith("}")) trimmed.drop(1).dropRight(1).trim else trimmed
      if (inner.isEmpty) Map()
      else splitTopLevel(inner, ',').map { entry =>
        val (key, value) = splitTopLevelKeyValue(entry)
        keySerializer.deserialize(key) -> valueSerializer.deserialize(value)
      }.toMap
    }
  }
}
