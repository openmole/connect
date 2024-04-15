package org.openmole.connect.client

import scala.scalajs.js.annotation.JSExportTopLevel
import scala.concurrent.ExecutionContext.Implicits.global
import org.openmole.connect.shared.Data
import com.raquo.laminar.api.L.*
import org.openmole.connect.shared.Data.*
import org.scalajs.dom
import scaladget.bootstrapnative.Table.{BasicRow, ExpandedRow}
import scaladget.bootstrapnative.bsn.*
import scaladget.tools.*
import ConnectUtils.*

object AdminPanel:

  @JSExportTopLevel("admin")
  def admin() =

    val users: Var[Seq[User]] = Var(Seq())
    val registering: Var[Seq[Register]] = Var(Seq())
    val versions: Var[Seq[String]] = Var(Seq())
    val selected: Var[Option[String]] = Var(None)

    case class UserInfo(key: String, show: BasicRow, hidden: Seq[String])

    AdminAPIClient.registeringUsers(()).future.foreach(rs => registering.set(rs))
    AdminAPIClient.users(()).future.foreach(us => users.set(us))
    UserAPIClient.availableVersions((Some(10), false)).future.foreach(vs => versions.set(vs))

    def statusElement(registerinUser: Register) =
      registerinUser.emailStatus match
        case Data.checked => div(badge_success, Data.checked)
        case Data.unchecked => div(badge_danger, Data.unchecked)

    def triggerButton(key: String) =
      button(cls := "btn bi-eye-fill", onClick --> { _ =>
        selected.update(_ match
          case Some(email: String) if (email == key) => None
          case Some(email: String) => Some(key)
          case None => Some(key)
        )
      })

    lazy val userTable =
      new UserTable(
        Seq("Name", "First name", "Email", "Institution", "Activity"),
        registering.signal.combineWith(users.signal).map { case (rs, us) =>
          val userInfos = rs.map(r => UserInfo(
            r.email,
            BasicRow(
              Seq(
                div(r.name),
                div(r.firstName),
                div(r.email),
                div(r.institution),
                statusElement(r),
                triggerButton(r.email))),
            Seq())
          ) ++
            us.map(u => UserInfo(
              u.email,
              BasicRow(
                Seq(
                  div(u.name),
                  div(u.firstName),
                  div(u.email),
                  div(u.institution),
                  div(u.lastAccess.toStringDate, cls := "badge", badge_info),
                  triggerButton(u.email))),
              Seq())
            )
          userInfos.flatMap { ui =>
            Seq(
              ui.show,
              ExpandedRow(div(height := "500", background := "pink", "expandedContent"), selected.signal.map(s=> s == Some(ui.key)))
            )
          }
        }
      )

    val adminPanel =
      div(cls := "columnCenter", width := "60%",
        a("disconnect", href := s"/${Data.disconnectRoute}"),
        userTable.render.amend(cls := "border")
      )

    lazy val appContainer = dom.document.querySelector("#appContainer")
    render(appContainer, adminPanel)
