package it.evadid.core.util.io

object AutoSerializable {


}


trait AutoSerializable[MyBaseType <: AutoSerializable[MyBaseType, SerializableType], SerializableType] {

  def createConverter(backFunc: SerializableType => MyBaseType): TypeConverter[MyBaseType, SerializableType] = {
    new TypeConverter[MyBaseType, SerializableType]() {

      override def convertToO(in: MyBaseType): SerializableType = in.toSerializableType

      override def convertToI(in: SerializableType): MyBaseType = backFunc(in)
    }
  }

  def serializer: Serializer[SerializableType]

  def toSerializableType: SerializableType

  lazy val toJson: String = serializer.serialize(this.toSerializableType)

}
