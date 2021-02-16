package org.thp.scalligraph.query

import org.apache.tinkerpop.gremlin.process.traversal.P
import org.apache.tinkerpop.gremlin.process.traversal.util.{AndP, OrP}

import java.util.function.BiPredicate
import java.util.stream.Collectors
import java.util.{List => JList}

object PredicateOps {

  implicit class PredicateOpsDefs[A](predicate: P[A]) {
    def map[B](f: A => B): P[B] =
      predicate match {
        case or: OrP[_]   => new OrP[B](or.getPredicates.stream().map[P[B]](p => p.asInstanceOf[P[A]].map(f)).collect(Collectors.toList()))
        case and: AndP[_] => new AndP[B](and.getPredicates.stream().map[P[B]](p => p.asInstanceOf[P[A]].map(f)).collect(Collectors.toList()))
        case _ =>
          val biPredicate: BiPredicate[B, B] = predicate.getBiPredicate.asInstanceOf[BiPredicate[B, B]]
          predicate.getValue match {
            case l: JList[_] =>
              val x: JList[B] = l.stream().map[B](v => f(v.asInstanceOf[A])).collect(Collectors.toList())
              new P(biPredicate, x.asInstanceOf[B])
            case v => new P(biPredicate, f(v))
          }
      }
  }
}