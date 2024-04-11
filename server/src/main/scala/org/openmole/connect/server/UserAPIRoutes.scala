package org.openmole.connect.server

import cats.effect.*
import endpoints4s.http4s.server
import org.http4s.HttpRoutes
import org.openmole.connect.server.DB.Salt
import org.openmole.connect.shared.*


class UserAPIImpl(user: DB.User, k8sService: K8sService, history: Int)(using salt: Salt):
  def userData = DB.User.toUserData(user)
  def instanceStatus = K8sService.podInfo(user.uuid)
  def launch =
    if K8sService.deploymentExists(user.uuid)
    then K8sService.startOpenMOLEPod(user.uuid)
    else K8sService.deployOpenMOLE(k8sService, user.uuid, user.omVersion, user.openMOLEMemory, user.memory, user.cpu, user.storage)

    instanceStatus

  def changePassword(oldPassword: String, newPassword: String) =
    DB.updatePassword(user.uuid, oldPassword, newPassword)

  def stop = K8sService.stopOpenMOLEPod(user.uuid)

  def availableVersions =
    OpenMOLE.availableVersions(withSnapshot = true, history = Some(history), lastMajors = true)

  def setVersion(version: String) =
    val versions = availableVersions
    if versions.contains(version)
    then DB.updadeOMVersion(user.uuid, version)

class UserAPIRoutes(impl: UserAPIImpl) extends server.Endpoints[IO]
  with UserAPI
  with server.JsonEntitiesFromCodecs:

  val userRoute = user.implementedBy { _ => impl.userData }
  val instanceRoute = instance.implementedBy { _ => impl.instanceStatus }
  val launchRoute = launch.implementedBy { _ => impl.launch }
  val stopRoute = stop.implementedBy { _ => impl.stop }
  val availableVersionsRoute = availableVersions.implementedBy { _ => impl.availableVersions }
  val changePasswordRoute = changePassword.implementedBy { (o, n) => impl.changePassword(o, n) }
  val setOpenMOLEVersionRoute = setOpenMOLEVersion.implementedBy { v => impl.setVersion(v) }

  val routes: HttpRoutes[IO] = HttpRoutes.of(
    routesFromEndpoints(userRoute, instanceRoute, launchRoute, stopRoute, availableVersionsRoute, changePasswordRoute, setOpenMOLEVersionRoute)
  )
