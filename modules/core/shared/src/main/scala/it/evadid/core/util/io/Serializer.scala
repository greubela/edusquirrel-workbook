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

  val pythonBooleanIO: Serializer[Boolean] = new Serializer[Boolean] {
    override def serialize(obj: Boolean): String = if(obj) "True" else "False"

    override def deserialize(serialized: String): Boolean = if(serialized.toLowerCase().trim == "true") true else false
  }

  val booleanIO: Serializer[Boolean] = new Serializer[Boolean] {
    override def serialize(obj: Boolean): String = obj.toString

    override def deserialize(serialized: String): Boolean = serialized.toBooleanOption.getOrElse(false)
  }

  val floatIO: Serializer[Double] = new Serializer[Double] {
    override def serialize(obj: Double): String = obj.toString

    override def deserialize(str: String): Double = str.toDouble
  }

  val intDecimalIO: Serializer[BigInt] = integerBaseIO(10, "")
  val intBinaryIO: Serializer[BigInt] = integerBaseIO(2, "Ob")
  val intHexIO: Serializer[BigInt] = integerBaseIO(8, "Ox")
  val intOctalIO: Serializer[BigInt] = integerBaseIO(8, "O")

  def integerBaseIO(base: Int, prefix: String = ""): Serializer[BigInt] = new Serializer[BigInt] {
    override def serialize(obj: BigInt): String = {
      val res = if (obj >= 0) obj.toString(base) else "-" + (-obj).toString(base)
      (prefix + res).trim
    }

    override def deserialize(str: String): BigInt = {
      val removed = if (prefix.nonEmpty && str.toLowerCase.startsWith(prefix.toLowerCase)) str.substring(prefix.length, str.length).trim else str.trim
      BigInt(removed, base)
    }
  }


}