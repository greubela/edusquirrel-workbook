package it.evadid.vm.parsing.java.clean

import it.evadid.core.util.io.Serializer

private[clean] object JavaCollectionSerializers {
  private def splitTopLevel(serialized: String): List[String] = {
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
            case '[' | '(' | '{' | '<' =>
              nesting += 1
              current.append(char)
            case ']' | ')' | '}' | '>' =>
              nesting -= 1
              current.append(char)
            case ',' if nesting == 0 =>
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

  def collectionSerializer[Element](elementSerializer: Serializer[Element], open: String, close: String): Serializer[List[Element]] = new Serializer[List[Element]] {
    override def serialize(obj: List[Element]): String = obj.map(elementSerializer.serialize).mkString(open, ", ", close)

    override def deserialize(serialized: String): List[Element] = {
      val trimmed = serialized.trim
      val inner = if (trimmed.startsWith(open) && trimmed.endsWith(close)) trimmed.drop(open.length).dropRight(close.length).trim else trimmed
      if (inner.isEmpty) List() else splitTopLevel(inner).map(elementSerializer.deserialize)
    }
  }
}
