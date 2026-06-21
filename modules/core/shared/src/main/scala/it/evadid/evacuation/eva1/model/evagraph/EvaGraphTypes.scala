package it.evadid.evacuation.eva1.model.evagraph

import scala.language.implicitConversions

import it.evadid.evacuation.core.datastructures.graphs._

object EvaGraphTypes {

  type EvaGraph = Graph[Router, ConnectionInfo, PositionableEdge[Router, ConnectionInfo]] & WeightedNeighbourStructure[Router, ConnectionInfo, PositionableEdge[Router, ConnectionInfo]]

  type EvaEdge = PositionableEdge[Router, ConnectionInfo]
  //type RouterOrEdge = Either[Router, EvaEdge]

  case class RouterOrEdge(either: Either[Router, EvaEdge]) extends Positionable {
    def getEither(): Either[Router, EvaEdge] = either

    override def pos: Position = either match {
      case Left(router) => router.pos
      case Right(edge) => edge.pos
    }
  }

  /*
  implicit class RouterOrEdge(either: Either[Router, EvaEdge]) extends GraphObject {
    def get(): Either[Router, EvaEdge] = either
  }

  implicit def toGraphObject(either: Either[Router, EvaEdge]): RouterOrEdge = new RouterOrEdge(either)


  implicit def routerToEither: Router => RouterOrEdge = r => toGraphObject(Left(r))

  implicit def edgeToEither: EvaEdge => RouterOrEdge = e => toGraphObject(Right(e))

  implicit def leftToEither: LeftProjection[Router, EvaEdge] => RouterOrEdge = r => toGraphObject(Left(r.get))
*/

  implicit def posOfEdge(evaEdge: EvaEdge): Position = evaEdge.start.pos.pointBetween(evaEdge.dest.pos, 0.5)

  implicit def routerToEither: Router => RouterOrEdge = r => RouterOrEdge(Left(r))

  implicit def edgeToEither: EvaEdge => RouterOrEdge = e => RouterOrEdge(Right(e))

  implicit def unpack(roe: RouterOrEdge): Either[Router, EvaEdge] = roe.getEither()

  implicit def packRoe(either: Either[Router, EvaEdge]): RouterOrEdge = RouterOrEdge(either)

  implicit def edgeOptionToEitherOption: Option[EvaEdge] => Option[RouterOrEdge] = _.map(edgeToEither)

  implicit def routerOptionToEitherOption: Option[Router] => Option[RouterOrEdge] = _.map(routerToEither)

}
