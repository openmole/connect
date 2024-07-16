package org.openmole.connect.server

import cats.effect.*
import endpoints4s.http4s.server
import org.http4s.HttpRoutes
import org.openmole.connect.server.Authentication.UserCache
import org.openmole.connect.server.K8sService.KubeCache
import org.openmole.connect.server.OpenMOLE.DockerHubCache
import org.openmole.connect.server.db.*
import org.openmole.connect.shared.*


class APIImpl()(using DB.Salt, KubeCache, UserCache, DockerHubCache, K8sService):
  def institutions = DB.institutions

class APIRoutes(impl: APIImpl) extends server.Endpoints[IO]
  with API
  with server.JsonEntitiesFromCodecs:

  val routes: HttpRoutes[IO] = HttpRoutes.of:
    routesFromEndpoints(
      institutions.implementedBy(_ => impl.institutions)
    )
