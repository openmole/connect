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
import com.raquo.laminar.nodes.ReactiveElement
import org.openmole.connect.client.UIUtils.{DetailedInfo, statusLine}
import org.openmole.connect.shared.Data.PodInfo.Status.Terminated
import org.openmoleconnect.client.Css
import org.scalajs.dom.HTMLDivElement

import scala.collection.mutable.ListBuffer
import scala.concurrent.Future

object AdminPanel:

  def admin(interpreter: STTPInterpreter) =
    enum SortColumn:
      case Name, Activity, Email, Institution, FirstName

    val sortColumn = Var(SortColumn.Name)
    val sortReverse = Var(false)

    case class Pod(podInfo: Var[Option[PodInfo]], storage: Var[Option[Storage]])

    val pods = Var(Map[String, Pod]())
    val users: Var[Seq[User]] = Var(Seq())
    val registering: Var[Seq[RegisterUser]] = Var(Seq())
    val versions: Var[Seq[String]] = Var(Seq())
    val selectedUUID: Var[Option[String]] = Var(None)

    case class UserInfo(show: BasicRow, expandedRow: ExpandedRow, name: String, firstName: String, activity: Long, email: String, institution: String)

    def updateUserInfo() =
      interpreter.adminAPIRequest(_.registeringUsers)(()).foreach: rs =>
        registering.set(rs)
      interpreter.adminAPIRequest(_.allInstances)(()).foreach: us =>
        users.set(us.map(_.user))
        val map =
          for
            u <- us
          yield u.user.uuid -> Pod(Var(u.podInfo), Var(None))
        pods.set(map.toMap)

    updateUserInfo()

    interpreter.userAPIRequest(_.availableVersions)(()).foreach(vs => versions.set(vs))

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
          interpreter.adminAPIRequest(_.promoteRegisteringUser)(register.uuid).foreach: _ =>
            updateUserInfo()
        }),
        button(btn_danger, "Reject", onClick --> { _ =>
          interpreter.adminAPIRequest(_.deleteRegisteringUser)(register.uuid).foreach: _ =>
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
          val selectedEmailStatus = Var[Option[EmailStatus]](None)

          lazy val firstNameInput: Input = UIUtils.buildInput(user.firstName).amend(width := "400px")
          lazy val nameInput: Input = UIUtils.buildInput(user.name).amend(width := "400px")
          lazy val institutionInput: Input = UIUtils.buildInput(user.institution).amend(width := "400px", listId := "institutions")
          lazy val emailInput: Input = UIUtils.buildInput(user.email).amend(width := "400px")
          
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

          lazy val emailStatusChanger =
            Selector.options[EmailStatus](
              EmailStatus.values.toSeq,
              user.emailStatus.ordinal,
              Seq(cls := "btn btnUser", width := "160"),
              naming = _.toString,
              decorations = Map()
            )

          lazy val memoryInput: Input =
            UIUtils.buildInput("").amend(width := "160", `type` := "number", value := user.memory.toString)

          lazy val openMOLEMemoryInput: Input =
            UIUtils.buildInput("").amend(width := "160", `type` := "number", value := user.openMOLEMemory.toString)

          lazy val cpuInput: Input =
            UIUtils.buildInput("").amend(width := "160", `type` := "number", stepAttr := "0.01", value := user.cpu.toString)

          lazy val storageInput: Input =
            val pvcSizeSignal = Signal.fromFuture(interpreter.adminAPIRequest(_.pvcSize)(uuid)).map:
              case Some(Some(v)) => v.toString
              case _ => ""
            UIUtils.buildInput("").amend(width := "160", `type` := "number", onChange --> storageChanged.set(true), placeholder <-- pvcSizeSignal)


          lazy val versionInput: Input = UIUtils.buildInput(user.omVersion).amend(width := "400px", listId := "versions")

          def save(): Unit =
            val futures = ListBuffer[Future[_]]()

            val pwd = passwordInput.ref.value
            if pwd.nonEmpty && passwordClicked.now()
            then interpreter.adminAPIRequest(_.changePassword)(uuid, passwordInput.ref.value)

            val firstName = firstNameInput.ref.value
            if firstName.nonEmpty
            then futures += interpreter.adminAPIRequest(_.setFirstName)((uuid, firstName))

            val name = nameInput.ref.value
            if name.nonEmpty
            then futures += interpreter.adminAPIRequest(_.setName)((uuid, name))

            val institution = institutionInput.ref.value
            if institution.nonEmpty
            then futures += interpreter.adminAPIRequest(_.setInstitution)((uuid, institution))
            
            val email = emailInput.ref.value
            if email.nonEmpty
            then futures += interpreter.adminAPIRequest(_.setEmail)((uuid, email))

            selectedRole.now().foreach: role =>
              futures += interpreter.adminAPIRequest(_.setRole)((uuid, role))

            selectedEmailStatus.now().foreach: es =>
              futures += interpreter.adminAPIRequest(_.setEmailStatus)((uuid, es))

            util.Try(memoryInput.ref.value.toInt).foreach: m =>
              if m != user.memory
              then futures += interpreter.adminAPIRequest(_.setMemory)((uuid, m))

            util.Try(openMOLEMemoryInput.ref.value.toInt).foreach: m =>
              if m != user.openMOLEMemory
              then futures += interpreter.adminAPIRequest(_.setOpenMOLEMemory)(uuid, m)

            util.Try(cpuInput.ref.value.toDouble).foreach: cpu =>
              if cpu != user.cpu
              then futures += interpreter.adminAPIRequest(_.setCPU)((uuid, cpu))

            val s = storageInput.ref.value
            if storageChanged.now() && !s.isEmpty
            then
              util.Try(s.toInt).foreach: s =>
                futures += interpreter.adminAPIRequest(_.setStorage)((uuid, s))

            val version = versionInput.ref.value
            if version.nonEmpty
            then futures += interpreter.adminAPIRequest(_.setVersion)((uuid, version))

            val delete = deleteInput.ref.value
            if delete == "DELETE USER"
            then
              futures += interpreter.adminAPIRequest(_.deleteUser)(uuid)
              selectedUUID.set(None)

            Future.sequence(futures).andThen(_ => updateUserInfo())

          Settings(
            div(margin := "30",
              Css.rowFlex,
              div(styleAttr := "width: 20%;", Css.columnFlex, alignItems.flexEnd,
                div(Css.centerRowFlex, cls := "settingElement", "Memory (MB)"),
                div(Css.centerRowFlex, cls := "settingElement", "OM Memory (MB)"),
                div(Css.centerRowFlex, cls := "settingElement", "CPU"),
                div(Css.centerRowFlex, cls := "settingElement", "Storage (GB)"),
                div(Css.centerRowFlex, cls := "settingElement", "Version"),

                div(Css.centerRowFlex, cls := "settingElement", "First Name"),
                div(Css.centerRowFlex, cls := "settingElement", "Last Name"),
                div(Css.centerRowFlex, cls := "settingElement", "Institution"),
                div(Css.centerRowFlex, cls := "settingElement", "Email"),


                div(Css.centerRowFlex, cls := "settingElement", "Role"),
                div(Css.centerRowFlex, cls := "settingElement", "Email Status"),
                div(Css.centerRowFlex, cls := "settingElement", "Password"),
                div(Css.centerRowFlex, cls := "settingElement", "Delete"),
              ),
              div(styleAttr := "width: 80%;", Css.columnFlex, alignItems.flexStart,
                div(Css.centerRowFlex, cls := "settingElement", memoryInput),
                div(Css.centerRowFlex, cls := "settingElement", openMOLEMemoryInput),
                div(Css.centerRowFlex, cls := "settingElement", cpuInput),
                div(Css.centerRowFlex, cls := "settingElement", storageInput),
                div(Css.centerRowFlex, cls := "settingElement", versionInput),
                dataList(idAttr := "versions", children <-- versions.signal.map(_.map(v => option(value := v)))),

                div(Css.centerRowFlex, cls := "settingElement", firstNameInput),
                div(Css.centerRowFlex, cls := "settingElement", nameInput),
                div(Css.centerRowFlex, cls := "settingElement", institutionInput),
                UIUtils.institutionsList(interpreter),
                div(Css.centerRowFlex, cls := "settingElement", emailInput),

                div(Css.centerRowFlex, cls := "settingElement", roleChanger.selector),
                div(Css.centerRowFlex, cls := "settingElement", emailStatusChanger.selector),

                div(Css.centerRowFlex, cls := "settingElement", passwordInput),
                div(Css.centerRowFlex, cls := "settingElement", deleteInput),
              ),
              roleChanger.content.signal.changes.toObservable --> selectedRole.toObserver,
              emailStatusChanger.content.signal.changes.toObservable --> selectedEmailStatus.toObserver
            ),
            save
          )

      lazy val settings = Settings(user.uuid)

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
                      case Some(st) => UIUtils.openmoleBoard(interpreter, Some(user.uuid), st)
                      case _ => UIUtils.openmoleBoard(interpreter, Some(user.uuid), PodInfo.Status.Inactive)
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
            ),
            r.name, r.firstName, r.created, r.email, r.institution
          )

    def registeredInfos =
      (users.signal combineWith pods.signal).map: (us, pods) =>
        us.flatMap: user =>
          pods.get(user.uuid).map: pod =>
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
                        interpreter.adminAPIRequest(_.instance)(user.uuid).foreach(pod.podInfo.set)
                        val stopped = pod.podInfo.now().flatMap(_.status.map(PodInfo.Status.isStopped)).getOrElse(true)
                        if pod.storage.now().isEmpty && !stopped
                        then interpreter.adminAPIRequest(_.usedSpace)(user.uuid).foreach(pod.storage.set)
                ),
                selectedUUID.signal.map(s => s.contains(user.uuid))
              ),
              user.name, user.firstName, user.lastAccess, user.email, user.institution
            )


    def sort(users: Seq[UserInfo], s: SortColumn, reverse: Boolean) =
      val sorted =
        s match
          case SortColumn.Name => users.sortBy(_.name)
          case SortColumn.Email => users.sortBy(_.email)
          case SortColumn.Activity => users.sortBy(_.activity)
          case SortColumn.Institution => users.sortBy(_.institution)
          case SortColumn.FirstName => users.sortBy(_.firstName)

      if reverse then sorted.reverse else sorted

    def sortClicked(s: SortColumn) =
      if s == sortColumn.now()
      then sortReverse.update(!_)
      else sortReverse.set(false)
      sortColumn.set(s)

    lazy val adminTable =
      new UserTable(
        Seq(span("Name", onClick --> sortClicked(SortColumn.Name), cls := "noColorLinkLike"), span("First name", onClick --> sortClicked(SortColumn.FirstName), cls := "noColorLinkLike"), span("Email", onClick --> sortClicked(SortColumn.Email), cls := "noColorLinkLike"), span("Institution", onClick --> sortClicked(SortColumn.Institution), cls := "noColorLinkLike"), span("Activity", onClick --> sortClicked(SortColumn.Activity), cls := "noColorLinkLike"), span("Status"), span("")),
        (registeringInfo combineWith registeredInfos combineWith sortColumn combineWith sortReverse).map: (ru, u, s, reverse) =>
          (sort(ru, s, reverse) ++ sort(u, s, reverse)).flatMap: ui =>
            Seq(
              ui.show,
              ui.expandedRow
            )
      )


    div(
      adminTable.render.amend(cls := "border"),
      a("Dump", href := Data.downloadUserRoute)
    )
