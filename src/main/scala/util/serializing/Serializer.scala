package util.serializing

trait Serializer[T] {

  def serialize(obj: T): String
  def deserialize(str: String): T

}
