package org.openmole.connect.shared

import io.circe.*
import io.circe.generic.auto.*

object TapirUserAPI:

  import sttp.tapir.*
  import sttp.model.*
  import sttp.tapir.generic.auto.*
  import sttp.tapir.json.circe.*

  lazy val user =
    endpoint.post.in("user").out(jsonBody[Data.User])

  lazy val instance =
    endpoint.post.in("instance").out(jsonBody[Option[Data.PodInfo]])

  lazy val launch =
    endpoint.post.in("launch")

  lazy val stop =
    endpoint.post.in("stop")

  lazy val usedSpace =
    endpoint.post.in("used-space").out(jsonBody[Option[Storage]])

  lazy val availableVersions =
    endpoint.post.in("openmole-versions").out(jsonBody[Seq[String]])

  lazy val changePassword =
    endpoint.post.in("password").in(jsonBody[(String, String)]).out(jsonBody[Boolean])

  lazy val openMOLEVersionUpdate =
    endpoint.post.in("openmole-version-update").in(jsonBody[String]).out(jsonBody[Option[String]])

  val setOpenMOLEVersion =
    endpoint.post.in("set-openmole-version").in(jsonBody[String])

  val setOpenMOLEMemory =
    endpoint.post.in("set-openmole-memory").in(jsonBody[Int])

  val setInstitution =
    endpoint.post.in("set-institution").in(jsonBody[String])


