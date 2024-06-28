package org.openmole.connect.server

import cats.effect.*
import endpoints4s.http4s.server
import org.http4s.HttpRoutes
import org.openmole.connect.server.Authentication.AuthenticationCache
import org.openmole.connect.server.db.v1.DB.Salt
import org.openmole.connect.server.K8sService.KubeCache
import org.openmole.connect.server.OpenMOLE.DockerHubCache
import org.openmole.connect.server.db.v1.DB
import org.openmole.connect.shared.*


class UserAPIImpl(user: DB.User, k8sService: K8sService, history: Int)(using Salt, KubeCache, AuthenticationCache, DockerHubCache):
  def userData = DB.User.toData(user)
  def instanceStatus = K8sService.podInfo(user.uuid)
  def launch =
    if K8sService.deploymentExists(user.uuid)
    then K8sService.startOpenMOLEPod(user.uuid)
    else K8sService.deployOpenMOLE(k8sService, user.uuid, user.omVersion, user.openMOLEMemory, user.memory, user.cpu)

  def changePassword(oldPassword: String, newPassword: String) =
    DB.updatePassword(user.uuid, newPassword, Some(oldPassword))

  def stop = K8sService.stopOpenMOLEPod(user.uuid)

  def availableVersions =
    OpenMOLE.availableVersions(withSnapshot = true, history = Some(history), lastMajors = true)

  def setVersion(version: String) =
    val versions = availableVersions
    if versions.contains(version)
    then DB.updadeOMVersion(user.uuid, version)

  def usedSpace = K8sService.usedSpace(user.uuid)

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
  val usedSpaceRoute = usedSpace.implementedBy(_ => impl.usedSpace)

  val routes: HttpRoutes[IO] = HttpRoutes.of(
    routesFromEndpoints(userRoute, instanceRoute, launchRoute, stopRoute, availableVersionsRoute, changePasswordRoute, setOpenMOLEVersionRoute, usedSpaceRoute)
  )
