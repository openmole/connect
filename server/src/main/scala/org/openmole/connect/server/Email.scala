package org.openmole.connect.server

import org.openmole.connect.server.db.DB
import org.openmole.connect.shared.Data.EmailStatus
import org.openmole.connect.server.ConnectServer.Config
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

object Email:

  import cats.effect._
  import cats.data.NonEmptyList
  import emil._, emil.builder._
  import emil.javamail._


  object Sender:
    import io.github.arainko.ducktape.*
    def apply(server: Option[ConnectServer.Config.SMTP]): Sender =
      server match
        case Some(config) => config.to[SMTP]
        case None => NoSender


    case class SMTP(server: String, port: Int, user: String, password: String, from: String) extends Sender
    object NoSender extends Sender


  sealed trait Sender

  def sendValidationLink(url: String, user: DB.RegisterUser, validationSecret: DB.Secret)(using Sender) =
    val link = s"$url${org.openmole.connect.shared.Data.validateRoute}?uuid=${user.uuid}&secret=${validationSecret}"

    sendMail: server =>
      MailBuilder.build(
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

  def sendResetPasswordLink(url: String, user: DB.User, validationSecret: DB.Secret)(using Sender) =
    val link = s"$url${org.openmole.connect.shared.Data.resetPasswordRoute}?uuid=${user.uuid}&secret=${validationSecret}"

    sendMail: server =>
      MailBuilder.build(
        From(server.from),
        To(user.email),
        Subject("[OpenMOLE] Password Reset"),
        CustomHeader(Header("User-Agent", "User")),
        HtmlBody(
          s"""Dear ${user.firstName} ${user.name},<br/><br/>
             |you can reset your password on the OpenMOLE service by clicking on this link (valid for ${ConnectServer.Config.resetPasswordExpire} hours):<br/>
             |<a href="$link">$link</a><br/><br/>
             |Best Regards
        """.stripMargin)
      )


  def sendValidated(user: DB.User)(using Sender, Config.Connect) =
    sendMail: server =>
      def begin =
        summon[Config.Connect].url match
          case Some(u) => s"your account on <a href=\"$u\">$u</a>"
          case None => "your account"

      MailBuilder.build(
        From(server.from),
        To(user.email),
        Subject("[OpenMOLE] Account Validated"),
        CustomHeader(Header("User-Agent", "User")),
        HtmlBody(
          s"""Dear ${user.firstName} ${user.name},<br/><br/>
             |your account as been approved on to the OpenMOLE service.<br/>
             |You should now be able to login.<br/><br/>
             |Best Regards
        """.stripMargin)
      )

  def sendInactive(user: DB.User, inactive: Int, remaining: Int)(using Sender, Config.Connect) =
    if EmailStatus.hasBeenChecked(user.emailStatus)
    then
      sendMail: server =>
        def begin =
          summon[Config.Connect].url match
            case Some(u) => s"Your account on <a href=\"$u\">$u</a>"
            case None => "Your account"
        MailBuilder.build(
          From(server.from),
          To(user.email),
          Subject("[OpenMOLE] Inactive account, shutdown scheduled"),
          CustomHeader(Header("User-Agent", "User")),
          //TextBody("Hello!\n\nThis is a mail."),
          HtmlBody(
            s"""Dear ${user.firstName} ${user.name},<br/><br/>
               |$begin as been inactive for the past $inactive days on to the OpenMOLE service.<br/>
               |If you don't login, your OpenMOLE instance will be automatically shutdown in $remaining days.<br/>
               |Note that yo
               |ur account will NOT be deleted and that your data will be preserved.<br/><br/>
               |Best Regards
          """.stripMargin)
        )



  def sendNotification(to: Seq[DB.Email], subject: String, content: String)(using Sender) =
    sendMail: server =>
      MailBuilder.build(
        From(server.from),
        Tos(to.map(t => MailAddress.unsafe(None, t))),
        Subject(s"[OpenMOLE] ${subject}"),
        CustomHeader(Header("User-Agent", "User")),
        //TextBody("Hello!\n\nThis is a mail."),
        HtmlBody(content)
      )

  def sendMail(mail: Sender.SMTP => Mail[IO])(using sender: Sender) =
    sender match
      case server: Sender.SMTP =>
        import cats.effect.unsafe.implicits.global
        val myemil = JavaMailEmil[IO]()
        val smtpConf = MailConfig(s"smtp://${server.server}:${server.port}", server.user, server.password, SSLType.StartTLS)

        val sendIO: IO[NonEmptyList[String]] = myemil(smtpConf).send(mail(server))
        sendIO.unsafeRunSync()
      case Sender.NoSender =>

