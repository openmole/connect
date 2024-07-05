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
    val settingsOpen: Var[Boolean] = Var(false)

    case class Settings(user: User):
      val selectedVersion = Var[Option[String]](None)

      def content =
        Signal.fromFuture(UserAPIClient.availableVersions(()).future).map: versions =>
          lazy val versionChanger =
            Selector.options[String](
              versions.toSeq.flatten,
              versions.toSeq.flatten.indexOf(user.omVersion),
              Seq(cls := "btn btnUser", width := "160"),
              naming = identity,
              decorations = Map()
            )

          div(margin := "30",
            Css.rowFlex,
            div(styleAttr := "width: 30%;", Css.columnFlex, alignItems.flexEnd,
              div(Css.centerRowFlex, cls := "settingElement", "OpenMOLE Version")
            ),
            div(styleAttr := "width: 70%;", Css.columnFlex, alignItems.flexStart,
              div(Css.centerRowFlex, cls := "settingElement", versionChanger.selector)
            ),
            versionChanger.content.signal.changes.toObservable --> selectedVersion.toObserver
          )


      def save(): Unit =
        selectedVersion.now().foreach: v =>
          if v != user.omVersion
          then
            UserAPIClient.setOpenMOLEVersion(v).future.andThen: _ =>
              reload.set(())


    def settingButton =
      button(
        `type` := "button",
        cls := "btn btnUser settings",
        child <--
          settingsOpen.signal.map:
            case true => "CANCEL"
            case false => "SETTINGS"
        ,
        onClick --> settingsOpen.update(v => !v)
      )

    def saveButton(settings: Settings) =
      button(
        `type` := "button",
        cls := "btn btnUser settings", "APPLY",
        onClick --> {
          settings.save()
          settingsOpen.set(false)
        }
      )

    lazy val userPanel =

      def content =
        reload.signal.flatMap: _ =>
          Signal.fromFuture(UserAPIClient.user(()).future).map:
            case None => UIUtils.waiter.amend(Css.rowFlex, justifyContent.flexEnd)
            case Some(u) =>
              val settings = Settings(u)
              div(
                settingButton.amend(marginLeft := "30px"),
                child <-- settingsOpen.signal.map(s => if s then saveButton(settings) else emptyNode),
                child <--
                  settingsOpen.signal.flatMap:
                    case true => settings.content
                    case false => Signal.fromValue:
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
                          UIUtils.userInfoBlock(u, space),
                          div(
                            child <--
                              podInfo.signal.map(_.flatMap(_.status)).map:
                                case Some(st) => UIUtils.openmoleBoard(None, st)
                                case None => UIUtils.openmoleBoard(None, PodInfo.Status.Inactive)
                          )
                        )
                      )
              )

      div(child <-- content)

    lazy val appContainer = dom.document.querySelector("#appContainer")
    render(appContainer, UIUtils.mainPanel(userPanel).amend(width := "800"))

