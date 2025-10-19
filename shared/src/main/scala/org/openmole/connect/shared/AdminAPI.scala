package org.openmole.connect.shared

import io.circe.*
import io.circe.generic.auto.*


object TapirAdminAPI:

  import sttp.tapir.*
  import sttp.model.*
  import sttp.tapir.generic.auto.*
  import sttp.tapir.json.circe.*

  import Data.*

  lazy val users = endpoint.post.in("users").out(jsonBody[Seq[User]])
  lazy val registeringUsers = endpoint.post.in("registering-users").out(jsonBody[Seq[RegisterUser]])
  lazy val promoteRegisteringUser = endpoint.post.in("promote-registering-users").in(jsonBody[String])
  lazy val deleteRegisteringUser = endpoint.post.in("delete-registering-users").in(jsonBody[String])
  lazy val deleteUser = endpoint.post.in("delete-user").in(jsonBody[String]).out(jsonBody[Unit])
  lazy val usedSpace = endpoint.post.in("used-space").in(jsonBody[String]).out(jsonBody[Option[Storage]])
  lazy val pvcSize = endpoint.post.in("pvc-size").in(jsonBody[String]).out(jsonBody[Option[Int]])
  lazy val instance = endpoint.in("instance").in(jsonBody[String]).out(jsonBody[Option[Data.PodInfo]])
  lazy val allInstances = endpoint.post.in("all-instances").out(jsonBody[Seq[Data.UserAndPodInfo]])
  lazy val changePassword = endpoint.post.in("change-password").in(jsonBody[(String, String)]).out(jsonBody[Boolean])
  lazy val setRole = endpoint.post.in("set-role").in(jsonBody[(String, Data.Role)])
  lazy val setMemory = endpoint.post.in("set-memory").in(jsonBody[(String, Int)])
  lazy val setCPU = endpoint.post.in("set-cpu").in(jsonBody[(String, Double)])
  lazy val setStorage = endpoint.post.in("set-storage").in(jsonBody[(String, Int)])
  lazy val setInstitution = endpoint.post.in("set-institution").in(jsonBody[(String, String)])
  lazy val setEmail = endpoint.post.in("set-email").in(jsonBody[(String, String)])
  lazy val setName = endpoint.post.in("set-name").in(jsonBody[(String, String)])
  lazy val setFirstName = endpoint.post.in("set-first-name").in(jsonBody[(String, String)])
  lazy val setEmailStatus = endpoint.post.in("set-email-status").in(jsonBody[(String, EmailStatus)])
  lazy val setVersion = endpoint.post.in("set-version").in(jsonBody[(String, String)])
  lazy val launch = endpoint.post.in("launch").in(jsonBody[String])
  lazy val stop = endpoint.post.in("stop").in(jsonBody[String])
