package org.thp.scalligraph.models

import scala.language.experimental.macros

import play.api.libs.json.Writes

import org.thp.scalligraph.macros.JsonMacro

object JsonWrites {
  def apply[T]: Writes[T] = macro JsonMacro.getJsonWrites[T]
}
