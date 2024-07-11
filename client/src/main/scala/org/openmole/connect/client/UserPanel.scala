package org.openmole.connect.client

import com.raquo.airstream.core
import com.raquo.laminar.api.L.*
import com.raquo.laminar.api.features.unitArrows
import org.openmole.connect
import org.openmole.connect.client.ConnectUtils.*
import org.openmole.connect.client.ConnectUtils.OpenMOLEPodStatus
import org.openmole.connect.client.UIUtils.DetailedInfo
import org.openmole.connect.shared.{Data, Storage}
import org.scalajs.dom
import scaladget.bootstrapnative.*
import scaladget.bootstrapnative.bsn.*
import org.openmole.connect.shared.Data.*
import org.openmoleconnect.client.Css
import scaladget.bootstrapnative.Selector.Options

import scala.scalajs.js.annotation.JSExportTopLevel
import scala.concurrent.ExecutionContext.Implicits.global

object UserPanel:

  lazy val roles = Data.Role.values
  lazy val roleFilter = (r: Role) => r == Data.Role.Admin

  @JSExportTopLevel("user")
  def user(): Unit =
    val reload: Var[Unit] = Var(())
    val podInfo: Var[Option[PodInfo]] = Var(None)
    val space: Var[Option[Storage]] = Var(None)
    val settings: Var[Option[Settings]] = Var(None)
    val admin: Var[Boolean] = Var(false)

    case class Settings(user: User):
      val selectedVersion = Var[Option[String]](None)

      lazy val openMOLEMemoryInput: Input =
        UIUtils.buildInput("").amend(width := "160", `type` := "number", value := user.openMOLEMemory.toString)

      def content =
        Signal.fromFuture(UserAPIClient.availableVersions(()).future).map: versions =>
          lazy val versionChanger =
            Selector.options[String](
              versions.toSeq.flatten,
              versions.toSeq.flatten.indexOf(user.omVersion),
              Seq(cls := "btn btnUser"),
              naming = identity,
              decorations = Map()
            )


          div(margin := "30",
            Css.rowFlex,
            div(styleAttr := "width: 50%;", Css.columnFlex, alignItems.flexEnd,
              div(Css.centerRowFlex, cls := "settingElement", "OpenMOLE Version"),
              div(Css.centerRowFlex, cls := "settingElement", "OpenMOLE Memory"),
            ),
            div(styleAttr := "width: 50%;", Css.columnFlex, alignItems.flexStart,
              div(Css.centerRowFlex, cls := "settingElement", versionChanger.selector.amend(width := "160")),
              div(Css.centerRowFlex, cls := "settingElement", openMOLEMemoryInput),

            ),
            versionChanger.content.signal.changes.toObservable --> selectedVersion.toObserver
          )


      def save(): Unit =
        selectedVersion.now().foreach: v =>
          if v != user.omVersion
          then UserAPIClient.setOpenMOLEVersion(v).future.andThen(_ => reload.set(()))

        util.Try(openMOLEMemoryInput.ref.value.toInt).foreach: m =>
          if m != user.openMOLEMemory
          then UserAPIClient.setOpenMOLEMemory(m).future.andThen(_ => reload.set(()))



    def settingButton(user: User) =
      button(
        `type` := "button",
        cls := "btn btnUser settings",
        child <--
          settings.signal.map:
            case Some(_) => "Cancel"
            case None => "Settings"
        ,
        onClick -->
          settings.update:
            case Some(_) => None
            case None => Some(Settings(user))
      )

    def saveButton =
      button(
       `type` := "button",
        cls := "btn btnUser settings", "Apply",
        onClick --> {
          settings.now().foreach(_.save())
          settings.set(None)
        }
      )

    def userPanel(user: User) =
      def content =
        settings.signal.flatMap:
          case Some(settings) => settings.content
          case None => Signal.fromValue:
            div(
              EventStream.periodic(5000).toObservable -->
                Observer: _ =>
                  UserAPIClient.instance(()).future.foreach: i =>
                    podInfo.set(i)
                  val stopped = podInfo.now().flatMap(_.status.map(PodInfo.Status.isStopped)).getOrElse(true)
                  if space.now().isEmpty && !stopped
                  then UserAPIClient.usedSpace(()).future.foreach(space.set),
              div(maxWidth := "1000", margin := "40px auto",
                ConnectUtils.logoutItem.amend(Css.rowFlex, justifyContent.flexEnd),
                UIUtils.userInfoBlock(user, space),
                div(
                  child <--
                    podInfo.signal.map(_.flatMap(_.status)).map:
                      case Some(st) => UIUtils.openmoleBoard(None, st)
                      case None => UIUtils.openmoleBoard(None, PodInfo.Status.Inactive)
                )
              )
            )


      div(child <-- content)

    def buttons(user: User) =
      val adminButton =
        button(
          child <-- admin.signal.map:
            case false =>"Admin"
            case true => "User"
          ,
          `type` := "button",
          cls := "btn btnUser settings"
          ,
          onClick --> admin.update(!_)
        )


      div(display.flex, flexDirection.row, justifyContent.start, alignItems.center,
        children <-- (settings.signal combineWith admin.signal).map:
          case (None, false) if user.role == Role.Admin => Seq(settingButton(user), adminButton)
          case (None, true) if user.role == Role.Admin => Seq(adminButton)
          case (None, _) => Seq(settingButton(user))
          case (Some(_), _) =>  Seq(settingButton(user), saveButton)
      )

    val mainPanel =
      reload.signal.flatMap: _ =>
        Signal.fromFuture(UserAPIClient.user(()).future).map:
          case None => UIUtils.waiter.amend(Css.rowFlex, justifyContent.flexEnd)
          case Some(u) =>
            UIUtils.mainPanel(
              div(
                child <-- admin.signal.map:
                  case false => userPanel(u)
                  case true => AdminPanel.admin()
              ),
              buttons(u)
            ).amend(width := "800")

    lazy val appContainer = dom.document.querySelector("#appContainer")
    render(
      appContainer,
      div(child <-- mainPanel)
    )

