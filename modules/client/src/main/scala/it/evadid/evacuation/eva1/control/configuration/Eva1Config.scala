package it.evadid.evacuation.eva1.control.configuration

import it.evadid.evacuation.config.property.discrete.{BasicDiscreteConfigProperty, BasicDiscreteObservableConfigProperty, ObservableDiscreteConfigProperty}
import it.evadid.evacuation.config.value.ConfigValue
import it.evadid.evacuation.eva1.algorithm.routing.FlowStrategy
import it.evadid.evacuation.eva1.algorithm.strategy.{ClosestGoalStrategy, MultipleGoalStrategy}

case class Eva1Config() {

  val evacuationStrategy: ObservableDiscreteConfigProperty[FlowStrategy] = BasicDiscreteObservableConfigProperty(Eva1Config.createStrategyProperty)

}

object Eva1Config {


  private val closestGoalStrategy: FlowStrategy = ClosestGoalStrategy
  private val mg30Strategy: FlowStrategy = new MultipleGoalStrategy(1.3)
  private val mg50Strategy: FlowStrategy = new MultipleGoalStrategy(1.5)

  private def createStrategyProperty: BasicDiscreteConfigProperty[FlowStrategy] = BasicDiscreteConfigProperty("flow_strategy",
    List[ConfigValue[FlowStrategy]](
      ConfigValue(closestGoalStrategy, "cgs", Some("Closest Goal Strategy")),
      ConfigValue(mg30Strategy, "mg30s", Some("Multiple Goal Strategy (30%)")),
      ConfigValue(mg50Strategy, "mg50s", Some("Multiple Goal Strategy (50%)"))
    ), closestGoalStrategy, "Evacuation Strategy")

}
