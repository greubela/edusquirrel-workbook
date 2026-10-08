package it.evadid.core.datastructures.graph

import upickle.default.*



case class Edge[N, A](start: N, dest: N, content: A) derives ReadWriter
