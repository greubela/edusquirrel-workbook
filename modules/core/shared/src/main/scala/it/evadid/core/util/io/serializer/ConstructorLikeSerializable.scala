package it.evadid.core.util.io.serializer

import it.evadid.core.util.io.ConstructorLikeParserWithJsonElements.ConstructorLikeReadResult
import it.evadid.core.util.io.serializer.ConstructorLikeSerializer.*
import it.evadid.core.util.io.{ConstructorLikeParserWithJsonElements, Serializer, TypeConverter}
import upickle.default.*

trait ConstructorLikeSerializable[ActualType <: ConstructorLikeSerializable[ActualType]] {

  protected def deserializeFromConstructorLikeString(from: ConstructorLikeReadResult): Option[ActualType]

  protected def serializeToConstructorLikeString(): ConstructorLikeReadResult

  def serializerFromObjects(objs: Set[ActualType]): Serializer[ActualType] = ConstructorLikeSerializable.serializerFromObjects[ActualType](objs)

}

object ConstructorLikeSerializable {

  def typeConverterFromDeserializationMethods[T <: ConstructorLikeSerializable[T]](funcs: Set[ConstructorLikeReadResult => Option[T]]): TypeConverter[T, ConstructorLikeReadResult] = new TypeConverter[T, ConstructorLikeReadResult]() {
    override def convertToO(in: T): ConstructorLikeReadResult = {
      in.serializeToConstructorLikeString()
    }

    override def convertToI(in: ConstructorLikeReadResult): T = {
      funcs.flatMap(curFunc => curFunc.apply(in)).head
    }
  }

  def typeConverterFromObjects[T <: ConstructorLikeSerializable[T]](objs: Set[T]): TypeConverter[T, ConstructorLikeReadResult] = new TypeConverter[T, ConstructorLikeReadResult]() {
    override def convertToO(in: T): ConstructorLikeReadResult = {
      in.serializeToConstructorLikeString()
    }

    override def convertToI(in: ConstructorLikeReadResult): T = {
      objs.flatMap(_.deserializeFromConstructorLikeString(in)).head
    }
  }

  def serializerFromObjects[T <: ConstructorLikeSerializable[T]](objs: Set[T]): Serializer[T] = {
    val typeConv = typeConverterFromObjects(objs)
    new Serializer[T]() {
      override def serialize(obj: T): String = {
        write(typeConv.convertToO(obj))
      }

      override def deserialize(str: String): T = {
        val der = ConstructorLikeSerializer.deserialize(str)
        typeConv.convertToI(der)
      }
    }
  }


}



