package it.evadid.vm.parsing.python.clean.model

import it.evadid.core.util.io.Serializer
import it.evadid.vm.parsing.TopLevelSplitter.brackets

private[clean] object PythonCollectionSerializers {

  private def splitTopLevelKeyValue(entry: String): (String, String) =
    brackets.split(entry, ':') match {
      case key :: valueParts => key -> valueParts.mkString(":").trim
      case Nil => "" -> ""
    }

  def setSerializer[Element](elementSerializer: Serializer[Element]): Serializer[Set[Element]] = new Serializer[Set[Element]] {
    override def serialize(obj: Set[Element]): String = obj.map(elementSerializer.serialize).mkString("{", ", ", "}")

    override def deserialize(serialized: String): Set[Element] =
      brackets.elements(serialized, "{", "}").map(elementSerializer.deserialize).toSet
  }

  def dictSerializer[Key, Value](keySerializer: Serializer[Key], valueSerializer: Serializer[Value]): Serializer[Map[Key, Value]] = new Serializer[Map[Key, Value]] {
    override def serialize(obj: Map[Key, Value]): String = obj.map { case (key, value) => s"${keySerializer.serialize(key)}: ${valueSerializer.serialize(value)}" }.mkString("{", ", ", "}")

    override def deserialize(serialized: String): Map[Key, Value] =
      brackets.elements(serialized, "{", "}").map { entry =>
        val (key, value) = splitTopLevelKeyValue(entry)
        keySerializer.deserialize(key) -> valueSerializer.deserialize(value)
      }.toMap
  }
}
