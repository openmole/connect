package org.openmole.connect.server

import org.json4s.*
import org.json4s.JsonAST.JValue
import org.json4s.JsonDSL.*
import org.json4s.jackson.JsonMethods.*

object Json:

  object key:
    val email = "email"
    val password = "password"

    val uuid = "uuid"
    val hostIP = "hostIP"

  def fromJson(json: String, jsonKey: String): String =
    fromJson(parse(json), jsonKey)

  def fromJson(json: JValue, jsonKey: String): String =
    (json \ jsonKey).values.toString
