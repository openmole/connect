package org.openmoleconnect.server

import java.net.URLEncoder

import org.json4s.JsonAST.JValue
import org.json4s.jackson.JsonMethods._

object Json {

  object key {
    val login = "login"
  }

  def fromJson(json: String, jsonKey: String): String = {
    fromJson(parse(json), jsonKey)
  }

  def fromJson(json: JValue, jsonKey: String): String = {
    (json \ jsonKey).values.toString
  }
}