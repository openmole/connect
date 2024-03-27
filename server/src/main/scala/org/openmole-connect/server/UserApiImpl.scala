//package org.openmoleconnect.server
//
//import cats.effect._
//import endpoints4s.http4s.server
//import org.http4s.HttpRoutes
//import shared.Data.UserData
//
//class UserApiImpl(kubeOff: Boolean) extends server.Endpoints[IO] with shared.UserAPI with server.JsonEntitiesFromCodecs:
//
//  val userRoute = user.implementedBy { _ => None }
//
//  val userWithDataRoute = userWithData.implementedBy { connectedUserData => connectedUserData }
//
//  val upsertedRoute = upserted.implementedBy { userData => None }
//
//  val upsertedWithDataRoute = upsertedWithData.implementedBy { case (userData, connectedUserData) =>
//    if(Some(userData.email) == connectedUserData.map{_.email}) Services.upserted(userData, kubeOff)
//    Some(userData)
//  }
//
//  val routes: HttpRoutes[IO] = HttpRoutes.of(
//    routesFromEndpoints(userRoute, userWithDataRoute, upsertedRoute, upsertedWithDataRoute)
//  )
