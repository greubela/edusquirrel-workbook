package it.evadid.evacuation.eva2.configuration

import it.evadid.evacuation.config.property.ObservableConfigProperty.PropertyListener
import it.evadid.evacuation.config.property.discrete.{BasicDiscreteConfigProperty, BasicDiscreteObservableConfigProperty, ObservableDiscreteConfigProperty}
import it.evadid.evacuation.config.value.ConfigValue
import it.evadid.evacuation.core.datastructures.matrix.Neighbourhood
import it.evadid.evacuation.eva2.algorithm.escaping.strategies.{ClosestGoalStrategy, MultipleGoalStrategy, ThresholdAcceptanceStrategy}
import it.evadid.evacuation.eva2.algorithm.escaping.{EvacuationStrategy, PersonOrderSelector}
import it.evadid.evacuation.eva2.configuration.ui.ShowMovementOption
import it.evadid.evacuation.eva2.control.Eva2Control

import scala.concurrent.ExecutionContextExecutor

case class Eva2Config() {

  private implicit val context: ExecutionContextExecutor = scala.concurrent.ExecutionContext.global

  val previewInsertion: ObservableDiscreteConfigProperty[Boolean] = BasicDiscreteObservableConfigProperty(BasicDiscreteConfigProperty.createBooleanProperty("preview-insertion", "Preview Insertion"))

  val neighbourhood: ObservableDiscreteConfigProperty[Neighbourhood] = BasicDiscreteObservableConfigProperty(Eva2Config.createNeighbourhoodProperty())
  val strategy: ObservableDiscreteConfigProperty[EvacuationStrategy[?, ?]] = BasicDiscreteObservableConfigProperty(Eva2Config.createStrategyProperty())



  val showMovementOption: ObservableDiscreteConfigProperty[Integer] = {
    val listener: PropertyListener[Integer] = (prop, oldVal, newVal) => Eva2Control.requestRedrawTiles()
    val showMovementProperty = BasicDiscreteObservableConfigProperty(Eva2Config.createShowMovementOptionsProperty())
    showMovementProperty.listener += listener

    showMovementProperty
  }

  val showAnimations: ObservableDiscreteConfigProperty[Boolean] = {
    val res = BasicDiscreteObservableConfigProperty(BasicDiscreteConfigProperty.createBooleanProperty("showAnimation", "Show Animations"))
    res.setValue(false)
    res
  }
}

object Eva2Config {

  private def createShowMovementOptionsProperty(): BasicDiscreteConfigProperty[Integer] = {
    BasicDiscreteConfigProperty("movement",
      List[ConfigValue[Integer]](
        ConfigValue(ShowMovementOption.SHOW_NO_MOVEMENTS, "no", Some("Show no movements")),
        ConfigValue(ShowMovementOption.SHOW_MICRO_MOVEMENT, "micro", Some("Show micro movement")),
        ConfigValue(ShowMovementOption.SHOW_ALL_MOVEMENTS, "all", Some("Show all movements"))
      ), ShowMovementOption.SHOW_ALL_MOVEMENTS, "Movement Arrows")
  }

  private def createNeighbourhoodProperty(): BasicDiscreteConfigProperty[Neighbourhood] = BasicDiscreteConfigProperty("neighbourhood", List(
    ConfigValue(Neighbourhood.neumann, Neighbourhood.neumann.name, Some("Neumann (4 directions)")),
    ConfigValue(Neighbourhood.moore, Neighbourhood.moore.name, Some("Moore (8 directions)"))
  ), Neighbourhood.neumann, "Neighbourhood Function")

  private def createStrategyProperty(): BasicDiscreteConfigProperty[EvacuationStrategy[?, ?]] = {

    val strat1: EvacuationStrategy[?, ?] = ClosestGoalStrategy(PersonOrderSelector.getRandomSelector)
    val strat2: EvacuationStrategy[?, ?] = ThresholdAcceptanceStrategy(PersonOrderSelector.getRandomSelector, 1.3)
    val strat3: EvacuationStrategy[?, ?] = ThresholdAcceptanceStrategy(PersonOrderSelector.getRandomSelector, 1.5)
    val strat4: EvacuationStrategy[?, ?] = MultipleGoalStrategy(PersonOrderSelector.getRandomSelector, 1.3)
    val strat5: EvacuationStrategy[?, ?] = MultipleGoalStrategy(PersonOrderSelector.getRandomSelector, 1.5)
    BasicDiscreteConfigProperty[EvacuationStrategy[?, ?]]("strategy", List[ConfigValue[EvacuationStrategy[?, ?]]](
      ConfigValue[EvacuationStrategy[?, ?]](strat1, "cgro", Some("Closest Goal, Random Order")),
      ConfigValue[EvacuationStrategy[?, ?]](strat2, "taro30", Some("Threshold Acceptance (30%), Random Order")),
      ConfigValue[EvacuationStrategy[?, ?]](strat3, "raro50", Some("Threshold Acceptance (50%), Random Order")),
      ConfigValue[EvacuationStrategy[?, ?]](strat4, "mgro30", Some("Multiple Goals (30%), Random Order")),
      ConfigValue[EvacuationStrategy[?, ?]](strat5, "mgro50", Some("Multiple Goals (50%), Random Order"))
    ), strat4, "Fleeing Algorithm")
  }



}