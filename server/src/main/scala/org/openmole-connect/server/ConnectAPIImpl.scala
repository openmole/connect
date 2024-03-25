//package org.openmole
//
//import cats.effect.IO
//import endpoints4s.http4s.server
//import org.http4s.*
//
// /*
// * Copyright (C) 2024 Romain Reuillon
// *
// * This program is free software: you can redistribute it and/or modify
// * it under the terms of the GNU Affero General Public License as published by
// * the Free Software Foundation, either version 3 of the License, or
// * (at your option) any later version.
// *
// * This program is distributed in the hope that it will be useful,
// * but WITHOUT ANY WARRANTY; without even the implied warranty of
// * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// * GNU Affero General Public License for more details.
// *
// * You should have received a copy of the GNU Affero General Public License
// * along with this program.  If not, see <http://www.gnu.org/licenses/>.
// */
//
//class ConnectAPIImpl
//  extends server.Endpoints[IO]
//  with shared.ConnectAPI
//  with server.JsonEntitiesFromCodecs:
//
//  val connectRoute =
//    connect.implementedBy { _ =>
//      println("test")
//    }.andThen(_.map(_.addCookie(ResponseCookie("name", "plouf"))))
//
//  val routes: HttpRoutes[IO] = HttpRoutes.of(
//    routesFromEndpoints(connectRoute)
//  )
