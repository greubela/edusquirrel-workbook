package interactionPlugins.blockEnvironment.programming.connection

/*
sealed trait BeConnectionCardinality {
  def hasMinimum: Boolean
  def hasMaximum: Boolean

  def minimum: Option[Int]
  def maximum: Option[Int]
}
*/

case class BeConnectionCardinality(hasMinimum: Boolean, hasMaximum: Boolean, minimumIncl: Option[Int], maximumIncl: Option[Int])


object BeConnectionCardinality {

  def fromTo(minIncl: Int, maxIncl: Int): BeConnectionCardinality = BeConnectionCardinality(true, true, Some(minIncl), Some(maxIncl))

  def atLeastN(minIncl: Int): BeConnectionCardinality = BeConnectionCardinality(true, false, Some(minIncl), None)

  def atMostN(maxIncl: Int): BeConnectionCardinality = BeConnectionCardinality(false, true, None, Some(maxIncl))

  def exactlyN(exact: Int): BeConnectionCardinality = BeConnectionCardinality(true, true, Some(exact), Some(exact))

  def anyAmount(): BeConnectionCardinality = BeConnectionCardinality(false, false, None, None)
  
  //  def none: BeConnectionCardinality = BeConnectionCardinality(false, false, None, None)


}