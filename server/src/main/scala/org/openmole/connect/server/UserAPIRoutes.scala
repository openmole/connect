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
    then DB.updateOMVersion(uuid, version)

  def setOMemory(memory: Int) = DB.updateOMMemory(uuid, math.min(memory, user.memory))

  def usedSpace = K8sService.usedSpace(uuid)

class UserAPIRoutes(impl: UserAPIImpl) extends server.Endpoints[IO]
  with UserAPI
  with server.JsonEntitiesFromCodecs:

  val routes: HttpRoutes[IO] = HttpRoutes.of:
    routesFromEndpoints(
      user.implementedBy { _ => DB.userToData(impl.user) },
      instance.implementedBy { _ => impl.instanceStatus },
      launch.implementedBy { _ => impl.launch },
      stop.implementedBy { _ => impl.stop },
      availableVersions.implementedBy { _ => impl.availableVersions },
      changePassword.implementedBy(impl.changePassword),
      setOpenMOLEVersion.implementedBy(impl.setVersion),
      usedSpace.implementedBy(_ => impl.usedSpace),
      setOpenMOLEMemory.implementedBy(impl.setOMemory)
    )
