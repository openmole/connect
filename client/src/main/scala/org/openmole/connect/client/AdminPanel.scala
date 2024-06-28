package org.openmole.connect.client

import scala.scalajs.js.annotation.JSExportTopLevel
import scala.concurrent.ExecutionContext.Implicits.global
import org.openmole.connect.shared.{Data, Storage}
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

    case class Pod(podInfo: Var[Option[PodInfo]], storage: Var[Option[Storage]])
    val pods = Var(Map[String, Pod]())
    val users: Var[Seq[User]] = Var(Seq())
    val registering: Var[Seq[RegisterUser]] = Var(Seq())
    val versions: Var[Seq[String]] = Var(Seq())
    val selectedUUID: Var[Option[String]] = Var(None)
    val settingsUUID: Var[Option[String]] = Var(None)

    case class UserInfo(show: BasicRow, expandedRow: ExpandedRow)

    def updateUserInfo() =
      AdminAPIClient.registeringUsers(()).future.foreach(rs => registering.set(rs))
      AdminAPIClient.allInstances(()).future.foreach: us =>
        users.set(us.map(_.user))
        val map =
          for
            u <- us
          yield u.user.email -> Pod(Var(u.podInfo), Var(None))
        pods.set(map.toMap)


    updateUserInfo()

    UserAPIClient.availableVersions((Some(10), false)).future.foreach(vs => versions.set(vs))

    def statusElement(registerinUser: RegisterUser) =
      registerinUser.status match
        case Data.EmailStatus.Checked => div(Css.badgeConnect, "Email Checked")
        case Data.EmailStatus.Unchecked => div(badge_danger, "Email Unchecked")

    def triggerButton(key: String) =
      span(cls := "bi-eye-fill",
        cursor.pointer,
        onClick --> { _ =>
          selectedUUID.update:
            case Some(uuid) if uuid == key =>
              settingsUUID.set(None)
              None
            case Some(_) => Some(key)
            case None => Some(key)
        })

    def registeringUserBlock(register: RegisterUser) =
      div(Css.centerRowFlex, padding := "10px",
        button(btn_secondary, "Accept", marginRight := "20px", onClick --> { _ =>
          AdminAPIClient.promoteRegisteringUser(register.uuid).future.foreach: _ =>
            updateUserInfo()
        }),
        button(btn_danger, "Reject", onClick --> { _ =>
          AdminAPIClient.deleteRegisteringUser(register.uuid).future.foreach: _ =>
            updateUserInfo()
        })
      )


    val settingsOnState = ToggleState("SAVE", "SAVE", "btn btnUser switchState", (_: String) => {})
    val settingsOffState = ToggleState("SETTINGS", "SETTINGS", btn_secondary_string + " switchState", (_: String) => {})

    def toBool(opt: Option[String], uuid: String) =
      opt match
        case Some(id) if id == uuid => true
        case _ => false

    def expandedUser(user: User, podInfo: Option[PodInfo], space: Var[Option[Storage]]) =
      lazy val settingsSwitch = toggle(settingsOnState, toBool(settingsUUID.now(), user.uuid), settingsOffState, () => {})
      val settings = UIUtils.settings(user.uuid)
      div(
        settingsSwitch.element.amend(margin := "30"),
        div(
          child <--
            settingsSwitch.toggled.signal.map:
              case true =>
                settingsUUID.set(Some(user.uuid))
                settings.element
              case false =>
                settings.save()
                settingsUUID.set(None)
                div(
                  Css.columnFlex, height := "350",
                  UIUtils.userInfoBlock(user, space),
                  div(
                    podInfo.flatMap(_.status) match
                      case Some(st) => UIUtils.openmoleBoard(Some(user.uuid), st)
                      case _ => UIUtils.openmoleBoard(Some(user.uuid), PodInfo.Status.Inactive)
                  )
                )
        )
      )

    def registeringInfo =
      registering.signal.map: rs =>
        rs.map: r =>
          UserInfo(
            BasicRow(
              Seq(
                div(r.name),
                div(r.firstName),
                div(r.email),
                div(r.institution),
                div(UIUtils.longTimeToString(r.created)),
                statusElement(r),
                triggerButton(r.uuid))),
            ExpandedRow(
              div(height := "150", display.flex, justifyContent.center, registeringUserBlock(r)),
              selectedUUID.signal.map(s => s.contains(r.uuid))
            )
          )

    def registeredInfos =
      (users.signal combineWith pods.signal).map: (us, pods) =>
        us.flatMap: user =>
          pods.get(user.email).map: pod =>
            UserInfo(
              BasicRow(
                Seq(
                  div(user.name),
                  div(user.firstName),
                  div(user.email),
                  div(user.institution),
                  div(UIUtils.longTimeToString(user.lastAccess)),
                  div(
                    child <--
                      pod.podInfo.signal.map: pi =>
                        pi.map(pi => UIUtils.statusLine(pi.status)).getOrElse(UIUtils.statusLine(Some(PodInfo.Status.Inactive)))
                  ),
                  triggerButton(user.uuid)
                )
              ),
              ExpandedRow(
                div(
                  child <--
                    (selectedUUID.signal combineWith pod.podInfo.signal).map: (s, pi) =>
                      if s.contains(user.uuid) then expandedUser(user, pi, pod.storage) else div(),
                  EventStream.periodic(5000).toObservable -->
                    Observer: _ =>
                      if selectedUUID.now().contains(user.uuid)
                      then
                        AdminAPIClient.instance(user.uuid).future.foreach(pod.podInfo.set)
                        val stopped = pod.podInfo.now().flatMap(_.status.map(PodInfo.Status.isStopped)).getOrElse(true)
                        if pod.storage.now().isEmpty && !stopped
                        then AdminAPIClient.usedSpace(user.uuid).future.foreach(pod.storage.set)
                ),
                selectedUUID.signal.map(s => s.contains(user.uuid))
              )
            )


    lazy val adminTable =
      new UserTable(
        Seq("Name", "First name", "Email", "Institution", "Activity","Status", ""),
        (registeringInfo combineWith registeredInfos).map: (ru, u) =>
          (ru ++ u).flatMap: ui =>
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
