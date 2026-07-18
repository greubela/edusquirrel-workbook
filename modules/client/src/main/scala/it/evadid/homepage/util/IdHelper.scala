package it.evadid.homepage.util

object IdHelper {

  private var curMaxId = 0;
  
  def getNextId(): String = {
    curMaxId += 1
    "IdHelper-" + curMaxId 
  }
  
  
}
