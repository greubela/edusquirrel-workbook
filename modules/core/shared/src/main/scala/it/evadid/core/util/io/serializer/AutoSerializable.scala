package it.evadid.core.util.io.serializer

import it.evadid.core.util.io.{Serializer, TypeConverter}
import it.evadid.distribution.command.SerializedException
import upickle.default.*


object AutoSerializable {
  
  trait AutoSerializableSingleton[T <: AutoSerializableSingleton[T]] {
    given ReadWriter[T] = {
      val ser: Serializer[T] = Serializer.singletonSerializer(this.asInstanceOf[T], Some(s"Singleton(${this.getClass.getSimpleName})"))
      ser.uPickleReadWrite
    }
  }

  def getSerializer[
    MainType <: AutoSerializableMainType[MainType, SubType],
    SubType <: AutoSerializableSubType[MainType, SubType]
  ](implicit rw: ReadWriter[SubType]): Serializer[MainType] = {
    Serializer.fromUpickleJson(rw).map(_.toTypedMainType, _.toSerializableSubType)
  }
  
  def getReadWriter[
    MainType <: AutoSerializableMainType[MainType, SubType],
    SubType <: AutoSerializableSubType[MainType, SubType]
  ](implicit rw: ReadWriter[SubType]): ReadWriter[MainType] = {
    Serializer.fromUpickleJson(rw).map(_.toTypedMainType, _.toSerializableSubType).uPickleReadWrite
  }

  def getTypeConverter[
    MainType <: AutoSerializableMainType[MainType, SubType],
    SubType <: AutoSerializableSubType[MainType, SubType]
  ]: TypeConverter[MainType, SubType] =
    new TypeConverter[MainType, SubType]() {
      def convertToO(in: MainType): SubType = in.toSerializableSubType

      def convertToI(in: SubType): MainType = in.toTypedMainType
    }


  trait AutoSerializableMainType[MainType <: AutoSerializableMainType[MainType, SubType], SubType <: AutoSerializableSubType[MainType, SubType]] {

    val typeConverter: TypeConverter[MainType, SubType] = getTypeConverter[MainType, SubType]

    lazy val readWriter: ReadWriter[MainType] = getReadWriter(using rwSub)
    lazy val serializerMain: Serializer[MainType] = Serializer.fromUpickleJson(readWriter)
    lazy val serializerSub: Serializer[SubType] = Serializer.fromUpickleJson(rwSub)

    lazy val rwSub: ReadWriter[SubType]

    lazy val toSerializableSubType: SubType

    lazy val toJson: String = try {
      write(this.asInstanceOf[MainType])(using readWriter)
    } catch case (err: Throwable) => {
      throw SerializedException(s"${this.getClass.getSimpleName} implements AutoSerializableMainType but is not an instance of the given main type (${err.getMessage})!")
    }
  }

  trait AutoSerializableSubType[MainType <: AutoSerializableMainType[MainType, SubType], SubType <: AutoSerializableSubType[MainType, SubType]] {
    lazy val toTypedMainType: MainType
  }


}

