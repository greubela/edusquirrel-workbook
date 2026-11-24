package it.evadid.core.datastructures.uml

import upickle.default.ReadWriter

enum UmlEntityKind derives ReadWriter { case Class, Attribute, Function }
