package it.evadid.core.util.io

import it.evadid.core.util.io.TypeConverter.ConverterResult
import it.evadid.distribution.command.SerializedException
import upickle.*
import upickle.default.{read, readwriter, write}

import scala.util.Try
import scala.util.control.NonFatal

trait Serializer[T] extends TypeConverter[T, String] {
  override def convertToO(in: T): String = serialize(in)

  override def convertToI(in: String): T = deserialize(in)

  lazy val safeSeqReadWriter: ReadWriter[Seq[T]] = readwriter[ujson.Value].bimap(
    (seq: Seq[T]) => ujson.Arr(seq.map(item => writeJs(item)(using uPickleReadWrite))*),
    (json: ujson.Value) => json.arr.flatMap { elementBlob =>
      try Some(read[T](elementBlob)(using uPickleReadWrite))
      catch case NonFatal(_) => None
    }.toSeq
  )

  def trySerializeAll(in: IterableOnce[T]): ConverterResult[T, String] = super.tryConvertAllToO(in)

  def tryDeserializeAll(in: IterableOnce[String]): ConverterResult[T, String] = super.tryConvertAllToI(in)

  def trySerialize(obj: T): Either[SerializedException, String] = try {
    Right(serialize(obj))
  } catch case NonFatal(err) => {
    Left(SerializedException(s"Error during Serialization of '${obj.toString.take(60)}': ${err.getMessage}", err))
  }

  def tryDeserialize(str: String): Either[SerializedException, T] = try {
    Right(deserialize(str))
  } catch case NonFatal(err) => {
    Left(SerializedException(s"Error during Deserialisation of '${str.take(60)}': ${err.getMessage}", err))
  }

  def serialize(obj: T): String

  def deserialize(str: String): T

  lazy val uPickleReadWrite: ReadWriter[T] = readwriter[String].bimap[T](nonString => serialize(nonString), string => deserialize(string))

  def map[O](funcForward: T => O, funcBackward: O => T): Serializer[O] = new Serializer[O] {
    override def serialize(obj: O): String = try {
      Serializer.this.serialize(funcBackward(obj))
    } catch case NonFatal(err) => {
      throw SerializedException(s"Cannot serialize obj of type ${obj.getClass.getSimpleName} (${obj.toString.take(60)}): ${err.getMessage} ", err)
    }

    override def deserialize(str: String): O = {
      val baseValue = Try(Serializer.this.deserialize(str)).fold(
        error => throw SerializedException(s"Could not parse str ${str.take(60)} with base parser: >>${error.getMessage}<<", error),
        identity)
      try funcForward(baseValue)
      catch case NonFatal(error) =>
        val valueType = Option(baseValue).map(_.getClass.getSimpleName).getOrElse("null")
        val valueSummary = Option(baseValue).fold("null")(_.toString.take(60))
        throw SerializedException(s"Could not convert obj $valueType ('$valueSummary') with funcForward: ${error.getMessage}", error)

    }
  }

}


object Serializer {


  /** Opaque source text for unknown language literals; never a codec for arbitrary values. */
  val rawStringIO: Serializer[String] = new Serializer[String] {
    override def serialize(value: String): String = value
    override def deserialize(value: String): String = value
  }

  def constructorLikeSerializer[T](constructorName: String)(forward: T => String)(backward: String => T): Serializer[T] = {
    constructorLikeSerializer(constructorName, new Serializer[T]() {
      override def serialize(obj: T) = forward(obj)

      override def deserialize(str: String): T = backward(str)
    })
  }


  def constructorLikeSerializer[T](constructorName: String, base: Serializer[T]): Serializer[T] = new Serializer[T] {
    override def serialize(obj: T): String = {
      constructorName + "(" + base.serialize(obj) + ")"
    }

    override def deserialize(str: String): T =
      try {
        val trimmed = str.trim
        if (trimmed.startsWith(constructorName + "(") && trimmed.endsWith(")")) {
          val withoutEnd = trimmed.substring(0, trimmed.length - 1)
          val cleaned = withoutEnd.substring(constructorName.length + 1, withoutEnd.length)
          base.deserialize(cleaned)
        } else {
          throw new IllegalArgumentException(s"ConstructorLikeSerializer for '${constructorName} cannot deserialize ${str}")
        }
      } catch case NonFatal(err) => {
        throw SerializedException(s"ConstructorLikeSerializer cannot deserialize ${str}", err)
      }
  }

  def noneParser(noneLiteral: Option[String] = Some("None")): Serializer[Option[Unit]] = Serializer.singletonSerializer[Option[Unit]](None, noneLiteral)

  def singletonSerializer[T](singletonObject: T, singletonString: Option[String] = None): Serializer[T] = new Serializer[T] {
    val outputString: String = singletonString.getOrElse(singletonObject.toString)

    private val validRepresentations: Set[String] = Set(singletonObject.toString) ++ singletonString

    override def serialize(obj: T): String =
      if (validRepresentations.contains(obj.toString)) outputString
      else throw SerializedException(s"Cannot serialize ${obj.toString} (${obj.getClass.getSimpleName}) with SingletonSerializer(${outputString})")

    override def deserialize(serialized: String): T =
      if (validRepresentations.contains(serialized)) {
        singletonObject
      } else throw SerializedException(s"Cannot deserialize $serialized with SingletonSerializer(${outputString})")
  }


  /** `T` as JSON text through its uPickle codec. */
  def fromUpickleJson[T](using upickle: ReadWriter[T]): Serializer[T] = new Serializer[T] {
    def serialize(in: T): String =
      try write(in)(using upickle)
      catch case NonFatal(error) =>
        val name = Option(in).fold("null")(_.getClass.getSimpleName)
        throw SerializedException(s"Error serializing $name because: ${ParsingContext.reason(error)}", error)

    def deserialize(in: String): T = ParsingContext("JSON value", in) { read(in)(using upickle) }
  }


  val intDecimalIO: Serializer[BigInt] = integerBaseIO(10, "")
  val intBinaryIO: Serializer[BigInt] = integerBaseIO(2, "0b")
  val intHexIO: Serializer[BigInt] = integerBaseIO(16, "0x")
  val intOctalIO: Serializer[BigInt] = integerBaseIO(8, "0o")

  /** Source-code string literals accept both quote styles; this is not a JSON codec. */
  def stringLiteralIO(parseEverything: Boolean = false): Serializer[String] = new Serializer[String] {
    private def enclosedBy(value: String, quote: String): Boolean = value.startsWith(quote) && value.endsWith(quote)

    private def isQuoted(value: String): Boolean = enclosedBy(value, "\"") || enclosedBy(value, "'")

    override def serialize(value: String): String =
      if (isQuoted(value) || parseEverything) value
      else s"\"$value\""

    override def deserialize(value: String): String = {
      val trimmed = value.trim
      if (enclosedBy(trimmed, "\"\"\"") && trimmed.length >= 6)
        trimmed.substring(3, trimmed.length - 3)
      else if (isQuoted(trimmed) && trimmed.length >= 2)
        trimmed.substring(1, trimmed.length - 1)
      else if (parseEverything) trimmed
      else throw new IllegalArgumentException("Expected a quoted string literal")
    }
  }

  def integerBaseIO(base: Int, prefix: String = ""): Serializer[BigInt] = new Serializer[BigInt] {
    override def serialize(obj: BigInt): String =
      (if (obj < 0) "-" else "") + prefix + obj.abs.toString(base)

    override def deserialize(str: String): BigInt = {
      val trimmed = str.trim
      val negative = trimmed.startsWith("-")
      val magnitude = if (negative || trimmed.startsWith("+")) trimmed.drop(1) else trimmed
      val hasPrefix = prefix.nonEmpty && magnitude.toLowerCase.startsWith(prefix.toLowerCase)
      val number =
        if (hasPrefix) {
          val digits = magnitude.drop(prefix.length)
          BigInt(if (digits.isEmpty && prefix == "0") "0" else digits, base)
        } else BigInt(magnitude, base)
      if (negative) -number else number
    }
  }

  /* PYTHON SPECIFIC SERIALIZER */

  val pythonBooleanIO: Serializer[Boolean] = new Serializer[Boolean] {
    override def serialize(obj: Boolean): String = if (obj) "True" else "False"

    override def deserialize(serialized: String): Boolean = serialized.toLowerCase().trim == "true"
  }


  def eitherPlainValueIO[A, B](serializerA: Serializer[A], serializerB: Serializer[B]): Serializer[Either[A, B]] = new Serializer[Either[A, B]] {
    override def serialize(obj: Either[A, B]): String = obj.match {
      case Left(sa: A) => serializerA.serialize(sa)
      case Right(sb: B) => serializerB.serialize(sb)
    }

    override def deserialize(str: String): Either[A, B] = try {
      Left[A, B](serializerA.deserialize(str))
    } catch case NonFatal(_) => {
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
      if (str.startsWith("Some(") && str.endsWith(")")) Some[T](serializer.deserialize(str.drop(5).dropRight(1)))
      else if (str.trim == noneLiteralStr) None
      else throw new IllegalArgumentException(s"Invalid optional projection: $str")
  }

  def eitherProjectionIO[A, B](serializerA: Serializer[A], serializerB: Serializer[B]): Serializer[Either[A, B]] = new Serializer[Either[A, B]] {

    override def serialize(obj: Either[A, B]): String = obj.match {
      case Left(sa: A) => s"Left(${serializerA.serialize(sa)})"
      case Right(sb: B) => s"Right(${serializerB.serialize(sb)})"
    }

    override def deserialize(str: String): Either[A, B] =
      if (str.startsWith("Left(") && str.endsWith(")")) Left[A, B](serializerA.deserialize(str.drop(5).dropRight(1)))
      else if (str.startsWith("Right(") && str.endsWith(")")) Right[A, B](serializerB.deserialize(str.drop(6).dropRight(1)))
      else throw new IllegalArgumentException(s"Invalid either projection: $str")
  }


}
