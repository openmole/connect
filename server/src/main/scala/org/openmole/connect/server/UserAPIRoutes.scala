package org.openmole.connect.server

import cats.effect._
import endpoints4s.http4s.server
import org.http4s.HttpRoutes
import org.openmole.connect.shared.*


class UserAPIImpl(user: DB.User, k8sService: K8sService):
  def userData = DB.User.toUserData(user)
  def instanceStatus = K8sService.podInfo(user.uuid)
  def launch =
    if K8sService.deploymentExists(user.uuid)
    then K8sService.startOpenMOLEPod(user.uuid)
    else K8sService.deployOpenMOLE(k8sService, user.uuid, user.omVersion, user.storage)

    instanceStatus
  
  def stop = K8sService.stopOpenMOLEPod(user.uuid)

  def availableVersions(history: Option[Int], lastMajors: Boolean) =
    Utils.availableOpenMOLEVersions(withSnapshot = true, history = history, lastMajors = lastMajors)

class UserAPIRoutes(impl: UserAPIImpl) extends server.Endpoints[IO]
  with UserAPI
  with server.JsonEntitiesFromCodecs:

  val userRoute = user.implementedBy { _ => impl.userData }
  val instanceRoute = instance.implementedBy { _ => impl.instanceStatus }
  val launchRoute = launch.implementedBy { _ => impl.launch }
  val stopRoute = stop.implementedBy { _ => impl.stop }
  val availableVersionsRoute = availableVersions.implementedBy { (h, m) => impl.availableVersions(h, m) }

  val routes: HttpRoutes[IO] = HttpRoutes.of(
    routesFromEndpoints(userRoute, instanceRoute, launchRoute, stopRoute, availableVersionsRoute) //, userWithDataRoute, upsertedRoute, upsertedWithDataRoute)
  )
