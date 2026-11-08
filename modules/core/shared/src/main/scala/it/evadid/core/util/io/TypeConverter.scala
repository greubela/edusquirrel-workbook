package it.evadid.core.util.io

import it.evadid.core.util.io.TypeConverter.ConverterResult
import it.evadid.distribution.command.SerializedException
import upickle.ReadWriter
import upickle.default.*

import scala.collection.mutable
import scala.util.control.NonFatal

trait TypeConverter[I, O] {
  def convertToO(in: I): O

  def convertToI(in: O): I

  def tryConvertToO(in: I): Either[SerializedException, O] = try {
    Right(convertToO(in))
  } catch case (err: Throwable) => Left(SerializedException(err))

  def tryConvertToI(out: O): Either[SerializedException, I] = try {
    Right(convertToI(out))
  } catch case (err: Throwable) => Left(SerializedException(err))

  def tryConvertAllToO(obj: IterableOnce[I]): ConverterResult[I, O] = {
    val input = mutable.ListBuffer[I]()
    val output = mutable.ListBuffer[O]()
    obj.iterator.foreach((curObj: I) => try output += convertToO(curObj) catch case NonFatal(e) => input += curObj)
    ConverterResult(input.toSet, output.toSet)
  }

  def tryConvertAllToI(obj: IterableOnce[O]): ConverterResult[I, O] = {
    val input = mutable.ListBuffer[I]()
    val output = mutable.ListBuffer[O]()
    obj.iterator.foreach((curObj: O) => try input += convertToI(curObj) catch case NonFatal(e) => output += curObj)
    ConverterResult(input.toSet, output.toSet)
  }

  lazy val reversed: TypeConverter[O, I] = {
    val outer = this
    new TypeConverter[O, I] {
      override def convertToO(in: O): I = outer.convertToI(in)

      override def convertToI(in: I): O = outer.convertToO(in)
    }
  }
}

object TypeConverter {


  def tupleConverter[I1, O1, I2, O2](conv1: TypeConverter[I1, O1], conv2: TypeConverter[I2, O2]) = new TypeConverter[(I1, I2), (O1, O2)] {

    def handle[A, B](tup: (Either[SerializedException, A], Either[SerializedException, B])): (A, B) = {
      tup.match {
        case (Left(err1), Left(err2)) => throw SerializedException(s"Could convert neither tuple part\nerror first:${err1}\nerror second:${err2}", err1)
        case (Left(err), _) => throw SerializedException(s"Could not convert first tuple part: ${err}", err)
        case (_, Left(err)) => throw SerializedException(s"Could not convert second tuple part: ${err.getMessage}", err)
        case (Right(out1), Right(out2)) => (out1, out2)
      }
    }

    override def convertToO(in: (I1, I2)): (O1, O2) = {
      handle(conv1.tryConvertToO(in._1), conv2.tryConvertToO(in._2))
    }

    override def convertToI(in: (O1, O2)): (I1, I2) = {
      handle(conv1.tryConvertToI(in._1), conv2.tryConvertToI(in._2))
    }
  }


  case class ConverterResult[I, O](inputAfterOperation: Set[I], outputAfterOpteration: Set[O]) derives ReadWriter

  lazy val singleValueMap: TypeConverter[Map[String, String], String] = new TypeConverter[Map[String, String], String] {
    def convertToO(in: Map[String, String]): String = in.values.head

    def convertToI(in: String): Map[String, String] = Map("singleValue" -> in)
  }

}


