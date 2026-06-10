package it.evadid.homepage.workbook.legacy.plantworkshop.helpers

object PumpControlValidator {
  def validatePumpControl(code: String): String = {
    val hasOn = code.contains("digitalWrite(PUMP_PIN, HIGH)")
    val hasOff = code.contains("digitalWrite(PUMP_PIN, LOW)")
    val hasDelay = code.contains("delay(2000)")

    if (hasOn && hasOff && hasDelay)
      "✅ Perfekt! Die Pumpe wird richtig gesteuert."
    else
      "⚠️ Der Code scheint noch nicht vollständig. Achte auf HIGH, delay(2000) und LOW."
  }
}
