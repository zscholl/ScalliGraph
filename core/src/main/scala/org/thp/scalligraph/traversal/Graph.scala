package org.thp.scalligraph.traversal

import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource
import org.apache.tinkerpop.gremlin.structure.{Edge, Transaction, Vertex, Graph => TinkerGraph}
import org.thp.scalligraph.EntityId
import org.thp.scalligraph.models.{Database, Model}

import java.util.function.Consumer

trait Graph {
  def underlying: TinkerGraph
  def addVertex(label: String): Vertex
  def addTransactionListener(listener: Consumer[Transaction.Status]): Unit
  def commit(): Unit
  def rollback(): Unit
  def isOpen: Boolean
  def txId: String
  def traversal(): GraphTraversalSource = db.traversal()(this)
  def variables: TinkerGraph.Variables
  def db: Database
  def V[D <: Product](ids: EntityId*)(implicit model: Model.Vertex[D]): Traversal.V[D]        = db.V[D](ids: _*)(model, this)
  def V(label: String, ids: EntityId*): Traversal[Vertex, Vertex, Converter.Identity[Vertex]] = db.V(label, ids: _*)(this)
  def VV(ids: EntityId*): Traversal[Vertex, Vertex, Converter.Identity[Vertex]] =
    new Traversal[Vertex, Vertex, Converter.Identity[Vertex]](this, traversal().V(ids.map(_.value): _*), Converter.identity[Vertex])
  def E[D <: Product](ids: EntityId*)(implicit model: Model.Edge[D]): Traversal.E[D]    = db.E[D](ids: _*)(model, this)
  def E(label: String, ids: EntityId*): Traversal[Edge, Edge, Converter.Identity[Edge]] = db.E(label, ids: _*)(this)
  def EE(ids: EntityId*): Traversal[Edge, Edge, Converter.Identity[Edge]] =
    new Traversal[Edge, Edge, Converter.Identity[Edge]](this, traversal().E(ids.map(_.value): _*), Converter.identity[Edge])
  def empty[D, G] = new Traversal[D, G, Converter[D, G]](this, traversal().inject[G](), (_: G).asInstanceOf[D])
  def union[D, G, C <: Converter[D, G]](
      travFun: (Traversal[Vertex, Vertex, IdentityConverter[Vertex]] => Traversal[D, G, C])*
  ): Traversal[D, G, C] = {
    val traversals: Seq[Traversal[D, G, C]] = travFun.map { t =>
      val from = new Traversal[Vertex, Vertex, IdentityConverter[Vertex]](this, traversal().V(), Converter.identity[Vertex])
      t(from)
    }
    new Traversal[D, G, C](this, traversal().inject(1).union(traversals.map(_.raw): _*), traversals.head.converter)
  }
  var printByteCode: Boolean   = false
  var printStrategies: Boolean = false
  var printExplain: Boolean    = false
  var printProfile: Boolean    = false
}

class GraphWrapper(override val db: Database, val underlying: TinkerGraph) extends Graph {
  printByteCode = db.printByteCode
  printStrategies = db.printStrategies
  printExplain = db.printExplain
  printProfile = db.printProfile
  override def addVertex(label: String): Vertex                                     = underlying.addVertex(label)
  override val variables: TinkerGraph.Variables                                     = underlying.variables()
  override def addTransactionListener(listener: Consumer[Transaction.Status]): Unit = underlying.tx().addTransactionListener(listener)
  override def commit(): Unit                                                       = underlying.tx().commit()
  override def rollback(): Unit                                                     = underlying.tx().rollback()
  override def isOpen: Boolean                                                      = underlying.tx().isOpen
  override val txId: String                                                         = f"${System.identityHashCode(this)}%08x"
}

object AnonymousGraph extends Graph {
  override def underlying: TinkerGraph                                              = ???
  override def addVertex(label: String): Vertex                                     = ???
  override def variables: TinkerGraph.Variables                                     = ???
  override def db: Database                                                         = ???
  override def addTransactionListener(listener: Consumer[Transaction.Status]): Unit = ???
  override def commit(): Unit                                                       = ???
  override def rollback(): Unit                                                     = ???
  override def isOpen: Boolean                                                      = ???
  override val txId: String                                                         = "anonymous"
}
