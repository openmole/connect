package org.openmole.connect.client

import scala.scalajs.js.annotation.JSExportTopLevel
import scala.concurrent.ExecutionContext.Implicits.global
import org.openmole.connect.shared.{Data, Storage}
import com.raquo.laminar.api.L.*
import com.raquo.laminar.api.features.unitArrows
import org.openmole.connect.shared.Data.*
import org.scalajs.dom
import scaladget.bootstrapnative.Table.{BasicRow, ExpandedRow}
import scaladget.bootstrapnative.Selector
import scaladget.bootstrapnative.bsn.{toggle, *}
import scaladget.tools.*
import ConnectUtils.*
import org.openmole.connect.client.UIUtils.DetailedInfo
import org.openmole.connect.shared.Data.PodInfo.Status.Terminated
import org.openmoleconnect.client.Css

object AdminPanel:

  def admin() =

    case class Pod(podInfo: Var[Option[PodInfo]], storage: Var[Option[Storage]])
    val pods = Var(Map[String, Pod]())
    val users: Var[Seq[User]] = Var(Seq())
    val registering: Var[Seq[RegisterUser]] = Var(Seq())
    val versions: Var[Seq[String]] = Var(Seq())
    val selectedUUID: Var[Option[String]] = Var(None)

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
      registerinUser.emailStatus match
        case Data.EmailStatus.Checked => div(Css.badgeConnect, "Email Checked")
        case Data.EmailStatus.Unchecked => div(badge_danger, "Email Unchecked")

    def triggerButton(key: String) =
      span(cls := "bi-eye-fill",
        cursor.pointer,
        onClick --> { _ =>
          selectedUUID.update:
            case Some(uuid) if uuid == key => None
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

    def expandedUser(user: User, podInfo: Option[PodInfo], space: Var[Option[Storage]], settingsOpen: Var[Boolean]) =
      case class Settings(element: HtmlElement, save: () => Unit)
      object Settings:
        def apply(uuid: String): Settings =
          val passwordClicked = Var(false)
          val storageChanged = Var(false)
          val selectedRole = Var[Option[Role]](None)

          lazy val passwordInput: Input = UIUtils.buildInput("New password").amend(
            `type` := "password",
            cls := "inPwd",
            width := "400px",
            onClick --> passwordClicked.set(true)
          )

          lazy val deleteInput: Input = UIUtils.buildInput("DELETE USER").amend(width := "400px")

          lazy val roleChanger =
            Selector.options[Role](
              Role.values.toSeq,
              user.role.ordinal,
              Seq(cls := "btn btnUser", width := "160"),
              naming = _.toString,
              decorations = Map()
            )

          lazy val memoryInput: Input =
            UIUtils.buildInput("").amend(width := "160", `type` := "number", value := user.memory.toString)

          lazy val cpuInput: Input =
            UIUtils.buildInput("").amend(width := "160", `type` := "number", stepAttr := "0.01", value := user.cpu.toString)

          lazy val storageInput: Input =
            UIUtils.buildInput("").amend(width := "160", `type` := "number", onChange --> storageChanged.set(true))

          def save(): Unit =
            val pwd = passwordInput.ref.value
            if pwd.nonEmpty && passwordClicked.now()
            then AdminAPIClient.changePassword(uuid, passwordInput.ref.value)

            selectedRole.now().foreach: role =>
              AdminAPIClient.setRole((uuid, role)).future.andThen: _ =>
                updateUserInfo()

            util.Try(memoryInput.ref.value.toInt).foreach: m =>
              if m != user.memory
              then AdminAPIClient.setMemory((uuid, m)).future.andThen(_ => updateUserInfo())

            util.Try(cpuInput.ref.value.toDouble).foreach: cpu =>
              if cpu != user.cpu
              then AdminAPIClient.setCPU((uuid, cpu)).future.andThen(_ => updateUserInfo())

            val s = storageInput.ref.value
            if storageChanged.now() && !s.isEmpty
            then
              util.Try(s.toInt).foreach: s =>
                AdminAPIClient.setStorage((uuid, s)).future.andThen(_ => updateUserInfo())

            val delete = deleteInput.ref.value
            if delete == "DELETE USER"
            then
              AdminAPIClient.deleteUser(uuid).future
              users.update(u => u.filter(u => u.uuid != uuid))
              pods.update(p => p.removed(uuid))
              selectedUUID.set(None)

          Settings(
            div(margin := "30",
              Css.rowFlex,
              div(styleAttr := "width: 20%;", Css.columnFlex, alignItems.flexEnd,
                div(Css.centerRowFlex, cls := "settingElement", "Memory (MB)"),
                div(Css.centerRowFlex, cls := "settingElement", "CPU"),
                div(Css.centerRowFlex, cls := "settingElement", "Storage (GB)"),
                div(Css.centerRowFlex, cls := "settingElement", "Role"),
                div(Css.centerRowFlex, cls := "settingElement", "Password"),
                div(Css.centerRowFlex, cls := "settingElement", "Delete"),
              ),
              div(styleAttr := "width: 80%;", Css.columnFlex, alignItems.flexStart,
                div(Css.centerRowFlex, cls := "settingElement", memoryInput),
                div(Css.centerRowFlex, cls := "settingElement", cpuInput),
                div(Css.centerRowFlex, cls := "settingElement", storageInput),
                div(Css.centerRowFlex, cls := "settingElement", roleChanger.selector),
                div(Css.centerRowFlex, cls := "settingElement", passwordInput),
                div(Css.centerRowFlex, cls := "settingElement", deleteInput),
              ),
              roleChanger.content.signal.changes.toObservable --> selectedRole.toObserver
            ),
            save
          )

      val settings = Settings(user.uuid)

      val settingButton =
        button(
          `type` := "button",
          cls := "btn btnUser settings",
          child <--
            settingsOpen.signal.map:
              case true  => "Cancel"
              case false => "Settings"
          ,
          onClick --> settingsOpen.update(v => !v)
        )

      val saveButton =
        button(
          `type` := "button",
          cls := "btn btnUser settings", "Apply",
          onClick --> {
            settings.save()
            settingsOpen.set(false)
          }
        )

      div(
        settingButton.amend(marginLeft := "30"),
        child <-- settingsOpen.signal.map(s => if s then saveButton else emptyNode),
        div(
          child <--
            settingsOpen.signal.map:
              case true => settings.element
              case false =>
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
            val settingsOpen: Var[Boolean] = Var(false)
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
                      if s.contains(user.uuid) then expandedUser(user, pi, pod.storage, settingsOpen) else div(),
                  EventStream.periodic(5000).toObservable -->
                    Observer: _ =>
                      if selectedUUID.now().contains(user.uuid) && !settingsOpen.now()
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

    adminTable.render.amend(cls := "border", width := "800")
