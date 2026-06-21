package it.evadid.evacuation.core.datastructures.graphs



case class Edge[N, A](start: N, dest: N, content: A)
