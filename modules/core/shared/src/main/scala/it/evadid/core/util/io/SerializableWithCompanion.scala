package it.evadid.core.util.io

import it.evadid.core.util.io.SerializableWithCompanion.SerializableWithGenericFactory
import it.evadid.distribution.command.SerializedException
import upickle.{ReadWriter, macroRW}


object SerializableWithCompanion {

  case class GenericSerializableFactory(factoryName: String, factoryParameter: Map[String, String])

  private val serializerGenericFactory: ReadWriter[GenericSerializableFactory] = macroRW

  trait SerializableWithGenericFactory[BaseType <: SerializableWithGenericFactory[BaseType]] {

    val factoryName: String = getClass.getSimpleName + "Factory"

    val serializerCompanion: Serializer[GenericSerializableFactory] = Serializer.fromUpickleJson(serializerGenericFactory)

    given ReadWriter[GenericSerializableFactory] = serializerCompanion.uPickleReadWrite

    def convertToCompanion: TypeConverter[BaseType, GenericSerializableFactory] = new TypeConverter[BaseType, GenericSerializableFactory] {
      override def convertToO(in: BaseType): GenericSerializableFactory = GenericSerializableFactory(factoryName, in.parameterForFactory())

      override def convertToI(in: GenericSerializableFactory): BaseType = {
        if (in.factoryName != factoryName) throw SerializedException(s"Cannot create Object of type ${in.factoryName} with factory for ${factoryName}!")
        else {
          val map = in.factoryParameter
          val missing = factoryRequiresMapKeys.filter(!in.factoryParameter.contains(_))
          if (missing.nonEmpty) throw SerializedException(s"Cannot create ${in.factoryName} object because of missing data ${missing.mkString("(missing: ", " & ", "): ")} ${map})")
          else createObjectWithVerifiedMap(map)
        }
      }
    }

    def createObjectWithVerifiedMap(verifiedMap: Map[String, String]): BaseType

    def factoryRequiresMapKeys: Set[String]

    def parameterForFactory(): Map[String, String]


    val serializer: Serializer[BaseType] = new Serializer[BaseType]() {
      override def serialize(obj: BaseType): String = serializerCompanion.serialize(convertToCompanion.convertToO(obj))

      override def deserialize(str: String): BaseType = convertToCompanion.convertToI(serializerCompanion.deserialize(str))
    }

    lazy val toJson: String = {
      serializer.serialize(this.asInstanceOf[BaseType])
    }

  }

}


trait SerializableWithCompanion[BaseType <: SerializableWithGenericFactory[BaseType], CompanionType] {

  def serializerCompanion: Serializer[CompanionType]

  given ReadWriter[CompanionType] = serializerCompanion.uPickleReadWrite

  def convertToCompanion: TypeConverter[BaseType, CompanionType]

  val serializer: Serializer[BaseType] = new Serializer[BaseType]() {

    override def serialize(obj: BaseType): String = serializerCompanion.serialize(convertToCompanion.convertToO(obj))

    override def deserialize(str: String): BaseType = convertToCompanion.convertToI(serializerCompanion.deserialize(str))
  }

  lazy val toJson: String = {
    serializer.serialize(this.asInstanceOf[BaseType])
  }

}
