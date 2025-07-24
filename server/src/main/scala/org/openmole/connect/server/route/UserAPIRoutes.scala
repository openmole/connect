package org.openmole.connect.server

import cats.effect.*
import endpoints4s.http4s.server
import org.http4s.HttpRoutes
import org.openmole.connect.server.Authentication.UserCache
import org.openmole.connect.server.db.*
import org.openmole.connect.server.KubeService.KubeCache
import org.openmole.connect.server.OpenMOLE.{DockerHubCache, OpenMOLEVersion}
import org.openmole.connect.shared.*

import scala.concurrent.ExecutionContext


class UserAPIImpl(uuid: DB.UUID, openmole: ConnectServer.Config.OpenMOLE)(using DB.Salt, KubeCache, UserCache, DockerHubCache, KubeService, ExecutionContext):
  def user = DB.userFromUUID(uuid).getOrElse(throw RuntimeException(s"Not found user with uuid $uuid"))
  def instanceStatus = KubeService.podInfo(uuid)

  def launch = KubeService.launch(user)
  def stop = KubeService.stopOpenMOLEPod(uuid)

  def changePassword(oldPassword: String, newPassword: String) =
    DB.updatePassword(uuid, newPassword, Some(oldPassword))

  def availableVersions =
    OpenMOLE.availableVersions(withSnapshot = true, history = openmole.versionHistory, min = openmole.minimumVersion, lastMajors = true)

  def setVersion(version: String) =
    val versions = availableVersions
    if versions.contains(version)
    then DB.updateOMVersion(uuid, version)

  def setOMemory(memory: Int) = DB.updateOMMemory(uuid, math.min(memory, user.memory))

  def usedSpace = KubeService.usedSpace(uuid)

  def setInstitution(i: String) = DB.updateInstitution(uuid, i)

  def openMOLEVersionUpdate(v: String) =
    val lastVersion =
      availableVersions.flatMap: v =>
        OpenMOLEVersion.parse(v).map(p => (string = v, version = p))
      .filter(_.version.isStable).sortBy(_.version).lastOption

    (OpenMOLEVersion.parse(v), lastVersion) match
      case (None, _) => None
      case (_, None) => None
      case (Some(v), Some(l)) =>
        if summon[Ordering[OpenMOLEVersion]].compare(v, l.version) < 0
        then Some(l.string)
        else None

class UserAPIRoutes(impl: UserAPIImpl) extends server.Endpoints[IO]
  with UserAPI
  with server.JsonEntitiesFromCodecs:

  private type FixEffecet = super.Effect

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
      setInstitution.implementedBy(impl.setInstitution),
      openMOLEVersionUpdate.implementedBy(impl.openMOLEVersionUpdate)
    )
