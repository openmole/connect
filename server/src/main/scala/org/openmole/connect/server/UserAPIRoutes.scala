package org.openmole.connect.server

import cats.effect.*
import endpoints4s.http4s.server
import org.http4s.HttpRoutes
import org.openmole.connect.server.Authentication.AuthenticationCache
import org.openmole.connect.server.db.*
import org.openmole.connect.server.K8sService.KubeCache
import org.openmole.connect.server.OpenMOLE.DockerHubCache
import org.openmole.connect.shared.*


class UserAPIImpl(uuid: DB.UUID, openmole: ConnectServer.Config.OpenMOLE)(using DB.Salt, KubeCache, AuthenticationCache, DockerHubCache, K8sService):
  def user = DB.userFromUUID(uuid).getOrElse(throw RuntimeException(s"Not found user with uuid $uuid"))
  def instanceStatus = K8sService.podInfo(uuid)

  def launch =
    if K8sService.deploymentExists(uuid)
    then K8sService.startOpenMOLEPod(uuid)
    else
      val userValue = user
      K8sService.deployOpenMOLE(userValue.uuid, userValue.omVersion, userValue.openMOLEMemory, userValue.memory, userValue.cpu)

  def changePassword(oldPassword: String, newPassword: String) =
    DB.updatePassword(uuid, newPassword, Some(oldPassword))

  def stop = K8sService.stopOpenMOLEPod(uuid)

  def availableVersions =
    OpenMOLE.availableVersions(withSnapshot = true, history = openmole.versionHistory, minVersion = openmole.minimumVersion, lastMajors = true)

  def setVersion(version: String) =
    val versions = availableVersions
    if versions.contains(version)
    then DB.updadeOMVersion(uuid, version)

  def usedSpace = K8sService.usedSpace(uuid)

class UserAPIRoutes(impl: UserAPIImpl) extends server.Endpoints[IO]
  with UserAPI
  with server.JsonEntitiesFromCodecs:

  val userRoute = user.implementedBy { _ => DB.userToData(impl.user) }
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
