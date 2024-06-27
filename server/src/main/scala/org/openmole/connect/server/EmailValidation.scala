package org.openmole.connect.server

import java.net.URLEncoder

/*
 * Copyright (C) 2024 Romain Reuillon
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

object EmailValidation:
  def send(server: ConnectServer.Config.Validation, url: String, user: DB.RegisterUser) =
    import cats.effect._
    import cats.data.NonEmptyList
    import emil._, emil.builder._
    import emil.javamail._
    import cats.effect.unsafe.implicits.global

    val link = s"$url${org.openmole.connect.shared.Data.validateRoute}?uuid=${user.uuid}&secret=${user.validationSecret}"

    val mail: Mail[IO] = MailBuilder.build(
      From(server.from),
      To(user.email),
      Subject("[OpenMOLE] Email Validation"),
      CustomHeader(Header("User-Agent", "User")),
      //TextBody("Hello!\n\nThis is a mail."),
      HtmlBody(s"""Dear ${user.firstName} ${user.name},<br/><br/>
        |Please validate your email for the OpenMOLE service by clinking on this link:<br/>
        |<a href="$link">$link</a><br/><br/>
        |Best Regards
      """.stripMargin)
    )

    val myemil = JavaMailEmil[IO]()
    val smtpConf = MailConfig(s"smtp://${server.server}:${server.port}", server.user, server.password, SSLType.StartTLS)

    val sendIO: IO[NonEmptyList[String]] = myemil(smtpConf).send(mail)
    sendIO.unsafeRunSync()

