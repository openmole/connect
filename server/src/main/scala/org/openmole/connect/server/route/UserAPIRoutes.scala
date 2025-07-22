package org.openmole.connect.server

import cats.effect.*
import endpoints4s.http4s.server
import org.http4s.HttpRoutes
import org.openmole.connect.server.Authentication.UserCache
import org.openmole.connect.server.db.*
import org.openmole.connect.server.K8sService.KubeCache
import org.openmole.connect.server.OpenMOLE.DockerHubCache
import org.openmole.connect.shared.*

import scala.concurrent.ExecutionContext


class UserAPIImpl(uuid: DB.UUID, openmole: ConnectServer.Config.OpenMOLE)(using DB.Salt, KubeCache, UserCache, DockerHubCache, K8sService, ExecutionContext):
  def user = DB.userFromUUID(uuid).getOrElse(throw RuntimeException(s"Not found user with uuid $uuid"))
  def instanceStatus = K8sService.podInfo(uuid)

  def launch = K8sService.launch(user)
  def stop = K8sService.stopOpenMOLEPod(uuid)

  def changePassword(oldPassword: String, newPassword: String) =
    DB.updatePassword(uuid, newPassword, Some(oldPassword))

  def availableVersions =
    OpenMOLE.availableVersions(withSnapshot = true, history = openmole.versionHistory, min = openmole.minimumVersion, lastMajors = true)

  def setVersion(version: String) =
    val versions = availableVersions
    if versions.contains(version)
    then DB.updateOMVersion(uuid, version)

  def setOMemory(memory: Int) = DB.updateOMMemory(uuid, math.min(memory, user.memory))

  def usedSpace = K8sService.usedSpace(uuid)

  def setInstitution(i: String) = DB.updateInstitution(uuid, i)

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
      setOpenMOLEMemory.implementedBy(impl.setOMemory),
      setInstitution.implementedBy(impl.setInstitution)
    )
