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
import org.openmole.connect.client.UIUtils.DetailedInfo
import org.openmoleconnect.client.Css

object AdminPanel:

  @JSExportTopLevel("admin")
  def admin() =

    val users: Var[Seq[User]] = Var(Seq())
    val registering: Var[Seq[RegisterUser]] = Var(Seq())
    val versions: Var[Seq[String]] = Var(Seq())
    val selected: Var[Option[String]] = Var(None)

    case class UserInfo(show: BasicRow, expandedRow: ExpandedRow)

    def updateUserInfo() =
      AdminAPIClient.registeringUsers(()).future.foreach(rs => registering.set(rs))
      AdminAPIClient.users(()).future.foreach(us => users.set(us))

    updateUserInfo()
    UserAPIClient.availableVersions((Some(10), false)).future.foreach(vs => versions.set(vs))

    def statusElement(registerinUser: RegisterUser) =
      registerinUser.status match
        case Data.checked => div(Css.badgeConnect, Data.checked)
        case Data.unchecked => div(badge_danger, Data.unchecked)

    def triggerButton(key: String) =
      span(cls := "bi-eye-fill",
        cursor.pointer,
        onClick --> { _ =>
        selected.update:
          case Some(email: String) if (email == key) => None
          case Some(email: String) => Some(key)
          case None => Some(key)
      })

    def registeringUserBlock(register: RegisterUser) =
      div(Css.centerRowFlex, padding := "10px",
        button(btn_secondary, "Validate", marginRight := "20px", onClick --> { _ =>
          AdminAPIClient.promoteRegisteringUser(register.uuid).future.foreach: _ =>
            updateUserInfo()
        }),
        button(btn_danger, "Reject", onClick --> { _ =>
          AdminAPIClient.deleteRegisteringUser(register.uuid).future.foreach: _ =>
            updateUserInfo()
        })
      )

    lazy val adminTable =
      new UserTable(
        Seq("Name", "First name", "Email", "Institution", "Activity", ""),
        registering.signal.combineWith(users.signal).map: (rs, us) =>
          val userInfos = rs.map(r => UserInfo(
            BasicRow(
              Seq(
                div(r.name),
                div(r.firstName),
                div(r.email),
                div(r.institution),
                statusElement(r),
                triggerButton(r.email))),
            ExpandedRow(
              div(height := "150", display.flex, justifyContent.center, registeringUserBlock(r)),
              selected.signal.map(s => s.contains(r.email))
            )
          )) ++
            us.map(u => UserInfo(
              BasicRow(
                Seq(
                  div(u.name),
                  div(u.firstName),
                  div(u.email),
                  div(u.institution),
                  div(u.lastAccess.toStringDate, Css.badgeConnect),
                  triggerButton(u.email))),

              ExpandedRow(
                div(
                  height := "350",
                  children <--
                    Signal.fromFuture(AdminAPIClient.usedSpace(u.uuid).future).map: v =>
                      Seq(
                        UIUtils.userInfoBlock(DetailedInfo(u.role, u.omVersion, v.flatten.map(_.toInt), u.storage, u.memory, u.cpu, u.openMOLEMemory)),
                        UIUtils.openmoleBoard(u.uuid)
                      )
                ),
                selected.signal.map(s => s.contains(u.email))
              )
            )
            )

          userInfos.flatMap: ui =>
            Seq(
              ui.show,
              ui.expandedRow
            )


      )

    lazy val appContainer = dom.document.querySelector("#appContainer")
    render(appContainer, UIUtils.mainPanel(adminTable.render.amend(cls := "border")))
