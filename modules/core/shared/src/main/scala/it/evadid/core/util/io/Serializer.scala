package it.evadid.core.util.io

import it.evadid.core.datastructures.chat.MessengerModel

import it.evadid.core.util.io.TypeConverter.ConverterResult
import it.evadid.distribution.command.SerializedException

import upickle.*
import upickle.default.{read, readwriter, write}

import scala.util.Try

trait Serializer[T] extends TypeConverter[T, String] {
  override def convertToO(in: T): String = serialize(in)

  override def convertToI(in: String): T = deserialize(in)

  lazy val safeSeqReadWriter: ReadWriter[Seq[T]] = {
    readwriter[ujson.Value].bimap(
      (seq: Seq[T]) => ujson.Arr(seq.map(item => writeJs(item)(using uPickleReadWrite)): _*),
      (json: ujson.Value) => json.arr.flatMap { elementBlob =>
        try {
          Some(read[T](elementBlob)(using uPickleReadWrite))
        } catch {
          case err: Exception =>
        //    println(s"[UGLY SERIALIZER SAFE-SEQ] cannot parse '$elementBlob': ${err.getMessage}")
            None
        }
      }.toSeq
    )
  }

  def trySerializeAll(in: IterableOnce[T]): ConverterResult[T, String] = super.tryConvertAllToO(in)

  def tryDeserializeAll(in: IterableOnce[String]): ConverterResult[T, String] = super.tryConvertAllToI(in)

  def serialize(obj: T): String

  def deserialize(str: String): T

  lazy val uPickleReadWrite: ReadWriter[T] = readwriter[String].bimap[T](nonString => serialize(nonString), string => deserialize(string))

  def map[O](funcForward: T => O, funcBackward: O => T): Serializer[O] = new Serializer[O] {
    override def serialize(obj: O): String = try {
      Serializer.this.serialize(funcBackward(obj))
    } catch case (err: Throwable) => {
      throw SerializedException(s"Cannot serialize obj of type ${obj.getClass.getSimpleName} (${obj.toString.take(60)}): ${err.getMessage} ")
    }

    override def deserialize(str: String): O = {
      val tryMain = Try {
        Serializer.this.deserialize(str)
      }
      if (tryMain.isFailure) throw SerializedException(s"Could not parse str ${str.take(60)} with base parser: >>${tryMain.failed.get.getMessage}<<")
      else try funcForward(tryMain.get)
      catch case (err: Throwable) => throw SerializedException(s"Could not convert obj ${tryMain.getClass.getSimpleName} ('${tryMain.get.toString.take(60)}') with funcForward: ${err.getMessage}")
    }
  }

}


object Serializer {


  def fromImplicitRW[T](implicit rw: ReadWriter[T]): Serializer[T] = Serializer.fromUpickleJson(rw)

  /*
    def constructorLikeSerializer[T](
                                      constructorName: String,
                                      construct: Seq[ujson.Value] => T,
                                      deconstruct: T => List[ujson.Value]
                                    ): Serializer[T] = new Serializer[T] {

      override def serialize(obj: T): String = {
        constructorName + deconstruct(obj).mkString("(", ")(", ")")
      }

      override def deserialize(str: String): T = {
        ConstructorLikeParserWithJsonElements.parseString(str).match {
          case Success(ConstructorLikeReadResult(parsedConstructor, jsons)) => if (constructorName != parsedConstructor) {
            throw SerializedException(s"ConstructorLikeSerializer(${constructorName}) cannot parse objects of type ${parsedConstructor}")
          } else try {
            construct(jsons.map(ujson.read(_)))
          } catch case (err: Throwable) => {
            throw SerializedException(s"ConstructorLikeSerializer(${constructorName}) had error while parsing jsons", err)
          }
          case Failure(err) => throw SerializedException(s"Could not parse ${str} with ConstructorLikeSerializer(${constructorName}", err)
        }
      }
    }*/


  /*def combineSerializerUseFirst[T](serializer: Seq[Serializer[T]]): Serializer[T] = new Serializer[T]{

    override def serialize(obj: T): String = ???

    override def deserialize(str: String): T = ???
  }*/

  def constructorLikeSerializer[T](constructorName: String, base: Serializer[T]): Serializer[T] = new Serializer[T] {
    override def serialize(obj: T): String = constructorName + "(" + write[String](base.serialize(obj)) + ")"

    override def deserialize(str: String): T =
      try {
        val trimmed = str.trim
        if (trimmed.startsWith(constructorName + "(") && trimmed.endsWith(")")) {
          val withoutEnd = trimmed.substring(0, str.length - 1)
          val cleaned = withoutEnd.substring(constructorName.length + 1, withoutEnd.length)
          val jsonRemove = read[String](cleaned)
          base.deserialize(jsonRemove)
        } else {
          throw new IllegalArgumentException(s"ConstructorLikeSerializer for '${constructorName} cannot deserialize ${str}")
        }
      } catch case (err: Throwable) => {
        throw SerializedException(s"ConstructorLikeSerializer cannot deserialize ${str}", err)
      }
  }

  def noneParser(noneLiteral: Option[String] = Some("None")): Serializer[Option[Unit]] = Serializer.singletonSerializer[Option[Unit]](None, noneLiteral)

  def singletonSerializer[T](singletonObject: T, singletonString: Option[String] = None): Serializer[T] = new Serializer[T] {
    val outputString: String = singletonString.getOrElse(singletonObject.toString)

    private val validRepresentations: Set[String] = Set(
      singletonObject.toString,
      singletonObject.getClass.getSimpleName,
      singletonObject.getClass.getName,
      singletonObject.getClass.getCanonicalName,
      s"Singleton(${singletonObject.getClass.getSimpleName})"
    ) ++ singletonString

    override def serialize(obj: T): String =
      if (validRepresentations.contains(obj.toString)) outputString
      else throw SerializedException(s"Cannot serialize ${obj.toString} (${obj.getClass.getSimpleName}) with SingletonSerializer(${outputString})")

    override def deserialize(serialized: String): T =
      if (validRepresentations.contains(serialized)) {
        singletonObject
      } else throw SerializedException(s"Cannot deserialize $serialized with SingletonSerializer(${outputString})")
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