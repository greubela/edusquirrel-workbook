package it.evadid.core.datastructures.state.observable

sealed trait ObserverDerivationLogic {

}

object ObserverDerivationLogic {

  case object DeriveAllValues extends ObserverDerivationLogic
  case object DeriveOnlyLastValues extends ObserverDerivationLogic

}

