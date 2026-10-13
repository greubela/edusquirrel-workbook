package it.evadid.homepage.control.change

/** A user-facing authentication failure; the key is translated by the login view. */
final case class AuthenticationFailure(messageKey: String) extends RuntimeException(messageKey)
