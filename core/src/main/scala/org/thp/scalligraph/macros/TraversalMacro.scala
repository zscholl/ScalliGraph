package org.thp.scalligraph.macros

import java.util.{Collection => JCollection}

import org.thp.scalligraph.models.Mapping
import org.thp.scalligraph.traversal.{Converter, Traversal}

import scala.reflect.macros.blackbox

class TraversalMacro(val c: blackbox.Context) extends MacroUtil {
  import c.universe._

  def getSelectorName(tree: Tree): Option[Name] =
    tree match {
      case q"(${vd: ValDef}) => ${idt: Ident}.${fieldName: Name}" if vd.name == idt.name => Some(fieldName)
      case _                                                                             => None
    }

  def projectionBuilderByValue[DD, DU: WeakTypeTag, TR: WeakTypeTag](selector: Tree)(mapping: Tree, ev1: Tree, ev2: Tree, prepend: Tree): Tree = {
    val duType         = weakTypeTag[DU]
    val trType         = weakTypeTag[TR]
    val projectBuilder = c.prefix.tree
    val name: Name     = getSelectorName(q"$selector").getOrElse(fatal(s"$selector is an invalid selector"))
    val mtpe           = typeOf[Mapping[_, _, _]]
    val mappingType    = c.typecheck(mapping, pt = mtpe).tpe
    val ggType         = mappingType.baseType(mtpe.typeSymbol).typeArgs.last
    q"$projectBuilder._byValue[$duType, $ggType, $trType](${name.toString}, $mapping)"
  }

  def genericSelectorByValue[G: WeakTypeTag, DD, DU: WeakTypeTag, GG: WeakTypeTag](selector: Tree)(mapping: Tree, ev1: Tree, ev2: Tree): Tree = {
    val gType      = weakTypeTag[G]
    val duType     = weakTypeTag[DU]
    val ggType     = weakTypeTag[GG]
    val cType      = c.typecheck(q"$mapping", pt = typeOf[Mapping[_, _, _]]).tpe
    val name: Name = getSelectorName(q"$selector").getOrElse(fatal(s"$selector is an invalid selector"))
    q"org.thp.scalligraph.traversal.ByResult[$gType, $duType, $ggType, $cType]($mapping)(_.by(${name.toString}).asInstanceOf[org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal[_, $ggType]])"
  }

  def groupBySelectorByValue[G: WeakTypeTag, DD, DU: WeakTypeTag, GG: WeakTypeTag](selector: Tree)(mapping: Tree, ev1: Tree, ev2: Tree): Tree = {
    val gType      = weakTypeTag[G]
    val duType     = weakTypeTag[DU]
    val foldDuType = appliedType(typeOf[Seq[_]].typeConstructor, duType.tpe)
    val ggType     = weakTypeTag[GG]
    val foldGgType = appliedType(typeOf[JCollection[_]].typeConstructor, ggType.tpe)
    val cType      = c.typecheck(q"$mapping", pt = typeOf[Mapping[_, _, _]]).tpe
    val name: Name = getSelectorName(q"$selector").getOrElse(fatal(s"$selector is an invalid selector"))
    val converter  = q"org.thp.scalligraph.traversal.Converter.ccollection[$duType, $ggType, $cType]($mapping)"
    val foldCType  = c.typecheck(converter, pt = typeOf[Converter.CCollection[_, _, _]])
    q"org.thp.scalligraph.traversal.ByResult[$gType, $foldDuType, $foldGgType, $foldCType]($converter)(_.by(${name.toString}).asInstanceOf[org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal[_, $foldGgType]])"
  }

  def getValue[DD](selector: Tree)(mapping: Tree, ev1: Tree, ev2: Tree): Tree = {
    val traversalOps: Tree = c.prefix.tree
    val traversal          = q"$traversalOps.traversal"
    val name: Name         = getSelectorName(q"$selector").getOrElse(fatal(s"$selector is an invalid selector"))
    q"$traversal.property(${name.toString}, $mapping)"
  }

  def update[V: WeakTypeTag](selector: Tree, value: Tree)(ev1: Tree, ev2: Tree): Tree = {
    val traversalOps: Tree = c.prefix.tree
    val traversal          = q"$traversalOps.traversal"
    val traversalType      = c.typecheck(traversal).tpe
    val entityType: Type   = traversalType.baseType(typeOf[Traversal[_, _, _]].typeSymbol).typeArgs.head
    val valueType          = c.weakTypeOf[V]

    entityType match {
      case RefinedType((baseEntityType @ CaseClassType(members @ _*)) :: _, _) =>
        val entityModule: Symbol = baseEntityType.typeSymbol.companion
        val model: Tree          = q"$entityModule.model"
        val name: Name           = getSelectorName(q"$selector").getOrElse(fatal(s"$selector is an invalid selector"))
        val mapping: Tree        = q"$model.fields(${name.toString}).asInstanceOf[org.thp.scalligraph.models.Mapping[$valueType, _, _]]"
        ret("Update traversal", q"$mapping.setProperty($traversal, ${name.toString}, $value)")
      case _ => fatal(s"$entityType is not a valid entity type")
    }
  }
}
