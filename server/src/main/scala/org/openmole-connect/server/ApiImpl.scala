package org.openmoleconnect.server

import shared._

object ApiImpl extends Api {

  def uuid(): String = {
    java.util.UUID.randomUUID.toString
  }
}