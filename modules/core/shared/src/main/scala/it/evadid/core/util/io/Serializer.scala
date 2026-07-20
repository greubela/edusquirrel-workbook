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

  def map[O](funcForward: T => O, funcBackward: O => T): Serializer[O] = new Serializer[O] {
    override def serialize(obj: O): String = Serializer.this.serialize(funcBackward(obj))

    override def deserialize(str: String): O = funcForward(Serializer.this.deserialize(str))
  }

}


object Serializer {



  def noneParser(noneLiteral: Option[String] = Some("None")): Serializer[Option[Unit]] = Serializer.singletonSerializer[Option[Unit]](None, noneLiteral)

  def singletonSerializer[T](singletonObject: T, singletonString: Option[String] = None): Serializer[T] = new Serializer[T] {
    val outputString: String = singletonString.getOrElse(singletonObject.toString)

    override def serialize(obj: T): String = if (obj == singletonObject) outputString else ???

    override def deserialize(serialized: String): T = if (serialized == outputString) singletonObject else ???
  }


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


  def stringLiteralIO(parseEverything: Boolean = false): Serializer[String] = new Serializer[String] {
    override def serialize(obj: String): String = {
      if ((obj.startsWith("\"") && obj.endsWith("\"")) || (obj.startsWith("'") && obj.endsWith("'"))) obj
      else if (parseEverything) obj
      else s"\"${obj}\""
    }

    override def deserialize(serialized: String): String = {
      val trimmed = serialized.trim
      if (trimmed.startsWith("\"\"\"") && trimmed.endsWith("\"\"\"") && trimmed.length >= 6) trimmed.substring(3, trimmed.length - 3)
      else if (((trimmed.startsWith("\"") && trimmed.endsWith("\"")) || (trimmed.startsWith("'") && trimmed.endsWith("'"))) && trimmed.length >= 2) trimmed.substring(1, trimmed.length - 1)
      else if (parseEverything) trimmed
      else ???
    }
  }

  val stringIO: Serializer[String] = new Serializer[String] {
    override def serialize(obj: String): String = obj

    override def deserialize(serialized: String): String = serialized
  }

  val parseAnyAsUnderlyingString: Serializer[Any] = stringIO.map(_.asInstanceOf[Any], _.toString)

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

  /* PYTHON SPECIFIC SERIALIZER */

  val pythonBooleanIO: Serializer[Boolean] = new Serializer[Boolean] {
    override def serialize(obj: Boolean): String = if (obj) "True" else "False"

    override def deserialize(serialized: String): Boolean = if (serialized.toLowerCase().trim == "true") true else false
  }


  def eitherPlainValueIO[A, B](serializerA: Serializer[A], serializerB: Serializer[B]): Serializer[Either[A, B]] = new Serializer[Either[A, B]] {
    override def serialize(obj: Either[A, B]): String = obj.match {
      case Left(sa: A) => serializerA.serialize(sa)
      case Right(sb: B) => serializerB.serialize(sb)
    }

    override def deserialize(str: String): Either[A, B] = try {
      Left[A, B](serializerA.deserialize(str))
    } catch case (e: Throwable) => {
      Right[A, B](serializerB.deserialize(str))
    }
  }

  def optionPlainValueIO[T](serializer: Serializer[T], noneLiteralStr: String = "None"): Serializer[Option[T]] = new Serializer[Option[T]] {
    override def serialize(obj: Option[T]): String = obj.match {
      case Some(value) => serializer.serialize(value)
      case None => noneLiteralStr
    }

    override def deserialize(str: String): Option[T] = {
      if (str.trim == noneLiteralStr) None
      else Some(serializer.deserialize(str))
    }
  }

  def optionProjectionIO[T](serializer: Serializer[T], noneLiteralStr: String = "None"): Serializer[Option[T]] = new Serializer[Option[T]] {
    override def serialize(obj: Option[T]): String = obj.match {
      case Some(value) => s"Some(${serializer.serialize(value)})"
      case None => noneLiteralStr
    }

    override def deserialize(str: String): Option[T] =
      if (str.startsWith("Some(") && str.endsWith(")")) Some[T](serializer.deserialize(str.substring(5, str.length - 6)))
      else if (str.trim == noneLiteralStr) None
      else ???
  }

  def eitherProjectionIO[A, B](serializerA: Serializer[A], serializerB: Serializer[B]): Serializer[Either[A, B]] = new Serializer[Either[A, B]] {

    override def serialize(obj: Either[A, B]): String = obj.match {
      case Left(sa: A) => serializerA.serialize(sa)
      case Right(sb: B) => serializerB.serialize(sb)
    }

    override def deserialize(str: String): Either[A, B] =
      if (str.startsWith("Left(") && str.endsWith(")")) Left[A, B](serializerA.deserialize(str.substring(5, str.length - 6)))
      else if (str.startsWith("Right(") && str.endsWith(")")) Right[A, B](serializerB.deserialize(str.substring(6, str.length - 7)))
      else ???
  }


}