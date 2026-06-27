package it.evadid.core.util.io

import it.evadid.core.datastructures.chat.MessengerModel
import it.evadid.core.util.io.TypeConverter.ConverterResult
import upickle.*
import upickle.default.{read, readwriter, write}

trait Serializer[T] extends TypeConverter[T, String] {
  override def convertToO(in: T): String = serialize(in)

  override def convertToI(in: String): T = deserialize(in)

  def trySerializeAll(in: IterableOnce[T]): ConverterResult[T, String] = super.tryConvertAllToO(in)

  def tryDeserializeAll(in: IterableOnce[String]): ConverterResult[T, String] = super.tryConvertAllToI(in)

  def serialize(obj: T): String

  def deserialize(str: String): T

  lazy val uPickleReadWrite: ReadWriter[T] = readwriter[String].bimap[T](nonString => serialize(nonString), string => deserialize(string))

}


object Serializer {


  def fromUpickleJson[T](upickle: ReadWriter[T]): Serializer[T] = new Serializer[T] {
    def serialize(in: T): String = write(in)(using upickle)

    def deserialize(in: String): T = read(in)(using upickle)
  }

  lazy val messengerIo: Serializer[MessengerModel] = new Serializer[MessengerModel] {
    override def serialize(obj: MessengerModel): String = obj.toJson

    override def deserialize(str: String): MessengerModel = MessengerModel.fromJson(str)
  }


  lazy val stringOptionIO: Serializer[Option[String]] = new Serializer[Option[String]] {
    override def serialize(obj: Option[String]): String = obj.map(str => "Some(" + str + ")").getOrElse("None")

    override def deserialize(serialized: String): Option[String] =
      if (serialized.startsWith("Some(") && serialized.endsWith(")")) Some(serialized.drop(5).dropRight(1).trim)
      else None
  }

  val stringIO: Serializer[String] = new Serializer[String] {
    override def serialize(obj: String): String = obj

    override def deserialize(serialized: String): String = serialized
  }

  val booleanIO: Serializer[Boolean] = new Serializer[Boolean] {
    override def serialize(obj: Boolean): String = obj.toString

    override def deserialize(serialized: String): Boolean = serialized.toBooleanOption.getOrElse(false)
  }


}