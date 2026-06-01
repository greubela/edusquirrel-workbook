package it.evadid.core.datastructures.user


case class User(name: String, mail: String) {

 def id: String = mail

}


