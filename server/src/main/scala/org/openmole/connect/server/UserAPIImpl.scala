package org.openmole.connect.server

import cats.effect._
import endpoints4s.http4s.server
import org.http4s.HttpRoutes
import org.openmole.connect.shared.*

class UserAPIImpl(userData: Data.UserData) extends server.Endpoints[IO]
  with UserAPI
  with server.JsonEntitiesFromCodecs:

  val userRoute = user.implementedBy { _ => userData }

//  val userWithDataRoute = userWithData.implementedBy { connectedUserData => connectedUserData }
//
//  val upsertedRoute = upserted.implementedBy { userData => None }
//
//  val upsertedWithDataRoute = upsertedWithData.implementedBy { case (userData, connectedUserData) =>
//    if(Some(userData.email) == connectedUserData.map{_.email}) Services.upserted(userData, kubeOff)
//    Some(userData)
//  }

  val routes: HttpRoutes[IO] = HttpRoutes.of(
    routesFromEndpoints(userRoute) //, userWithDataRoute, upsertedRoute, upsertedWithDataRoute)
  )
