package org.openmole.connect.client

import scala.scalajs.js.annotation.JSExportTopLevel
import scala.concurrent.ExecutionContext.Implicits.global
import org.openmole.connect.shared.Data
import com.raquo.laminar.api.L.*
import org.openmole.connect.shared.Data.*
import org.scalajs.dom
import scaladget.bootstrapnative.Table.{BasicRow, ExpandedRow}
import scaladget.bootstrapnative.bsn.{toggle, *}
import scaladget.tools.*
import ConnectUtils.*
import org.openmole.connect.client.UIUtils.DetailedInfo
import org.openmole.connect.shared.Data.PodInfo.Status.Terminated
import org.openmoleconnect.client.Css

object AdminPanel:

  @JSExportTopLevel("admin")
  def admin() =

    val users: Var[Seq[UserAndPodInfo]] = Var(Seq())
    val registering: Var[Seq[RegisterUser]] = Var(Seq())
    val versions: Var[Seq[String]] = Var(Seq())

    val selected: Var[Option[String]] = Var(None)
    val settingsUUID: Var[Option[String]] = Var(None)

    case class UserInfo(show: BasicRow, expandedRow: ExpandedRow)

    def updateUserInfo =
      AdminAPIClient.registeringUsers(()).future.foreach(rs => registering.set(rs))
      AdminAPIClient.allInstances(()).future.foreach(us => users.set(us))

    updateUserInfo

    UserAPIClient.availableVersions((Some(10), false)).future.foreach(vs => versions.set(vs))

    def statusElement(registerinUser: RegisterUser) =
      registerinUser.status match
        case Data.EmailStatus.Checked => div(Css.badgeConnect, "Email Checked")
        case Data.EmailStatus.Unchecked => div(badge_danger, "Email Unchecked")

    def triggerButton(key: String) =
      span(cls := "bi-eye-fill",
        cursor.pointer,
        onClick --> { _ =>
          selected.update:
            case Some(email: String) if (email == key) =>
              settingsUUID.set(None)
              None
            case Some(email: String) => Some(key)
            case None => Some(key)
        })

    def registeringUserBlock(register: RegisterUser) =
      div(Css.centerRowFlex, padding := "10px",
        button(btn_secondary, "Accept", marginRight := "20px", onClick --> { _ =>
          AdminAPIClient.promoteRegisteringUser(register.uuid).future.foreach: _ =>
            updateUserInfo
        }),
        button(btn_danger, "Reject", onClick --> { _ =>
          AdminAPIClient.deleteRegisteringUser(register.uuid).future.foreach: _ =>
            updateUserInfo
        })
      )


    val settingsOnState = ToggleState("SAVE", "SAVE", "btn btnUser switchState", (_: String) => {})
    val settingsOffState = ToggleState("SETTINGS", "SETTINGS", btn_secondary_string + " switchState", (_: String) => {})

    def toBool(opt: Option[String], uuid: String) =
      opt match
        case Some(id) if id == uuid => true
        case _ => false

    lazy val adminTable =
      new UserTable(
        Seq("Name", "First name", "Email", "Institution", "Activity", ""),
        registering.signal.combineWith(users.signal).map: (rs, us) =>
          def registeringInfo =
            rs.map(r => UserInfo(
              BasicRow(
                Seq(
                  div(r.name),
                  div(r.firstName),
                  div(r.email),
                  div(r.institution),
                  statusElement(r),
                  triggerButton(r.email))),
              ExpandedRow(
                div(
                  height := "150", display.flex, justifyContent.center, registeringUserBlock(r)),
                selected.signal.map(s => s.contains(r.email))
              )
            ))

          def registeredInfos =
            us.map(u => UserInfo(
              BasicRow(
                Seq(
                  div(u.user.name),
                  div(u.user.firstName),
                  div(u.user.email),
                  div(u.user.institution),
                  u.podInfo.map(pi => UIUtils.statusLine(pi.status)).getOrElse(UIUtils.statusLine(Some(PodInfo.Status.Inactive))),
                  triggerButton(u.user.email)
                )
              ),
              ExpandedRow(
                div(
                  child <-- selected.signal.map: s =>
                    if (s.contains(u.user.email))
                    then
                      lazy val settingsSwitch = toggle(settingsOnState, toBool(settingsUUID.now(), u.user.uuid), settingsOffState, () => {})
                      val settings = UIUtils.settings(u.user.uuid)
                      div(
                        settingsSwitch.element.amend(margin := "30"),
                        div(
                          child <--
                            settingsSwitch.toggled.signal.map:
                              case true =>
                                settingsUUID.set(Some(u.user.uuid))
                                settings.element
                              case false =>
                                settings.save()
                                settingsUUID.set(None)
                                div(
                                  Css.columnFlex, height := "350",
                                  UIUtils.userInfoBlock(u.user, admin = true),
                                  div(
                                    u.podInfo.flatMap(_.status) match
                                      case Some(st) => UIUtils.openmoleBoard(Some(u.user.uuid), st)
                                      case _ => UIUtils.openmoleBoard(Some(u.user.uuid), PodInfo.Status.Inactive)
                                  )
                                )
                        ),
                      )
                    else div()
                ),
                selected.signal.map(s => s.contains(u.user.email))
              )
            )
            )

          def userInfos = registeringInfo ++ registeredInfos
          userInfos.flatMap: ui =>
            Seq(
              ui.show,
              ui.expandedRow
            )
      )


    lazy val appContainer = dom.document.querySelector("#appContainer")
    render(
      appContainer,
      div(
        UIUtils.mainPanel(adminTable.render.amend(cls := "border", width := "800"))
      )
    )
