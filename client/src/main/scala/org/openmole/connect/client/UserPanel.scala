package org.openmole.connect.client

import com.raquo.airstream.core
import com.raquo.laminar.api.L.*
import com.raquo.laminar.api.features.unitArrows
import org.openmole.connect
import org.openmole.connect.client.ConnectUtils.*
import org.openmole.connect.client.ConnectUtils.OpenMOLEPodStatus
import org.openmole.connect.client.UIUtils.{DetailedInfo, institutionsList, versionInfo}
import org.openmole.connect.shared.{Data, Storage}
import org.scalajs.dom
import scaladget.bootstrapnative.*
import scaladget.bootstrapnative.bsn.*
import org.openmole.connect.shared.Data.*
import org.openmoleconnect.client.Css
import scaladget.bootstrapnative.Selector.Options

import scala.collection.mutable.ListBuffer
import scala.scalajs.js.annotation.JSExportTopLevel
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object UserPanel:

  lazy val roles = Data.Role.values
  lazy val roleFilter = (r: Role) => r == Data.Role.Admin

  @JSExportTopLevel("user")
  def user(): Unit =
    sealed trait Panel
    object FrontPage extends Panel
    object AdminPage extends Panel
    object InfoPage extends Panel

    val reload: Var[Unit] = Var(())
    val podInfo: Var[Option[PodInfo]] = Var(None)
    val space: Var[Option[Storage]] = Var(None)
    val settings: Var[Panel] = Var(FrontPage)

    case class Settings(user: User) extends Panel:
      val selectedVersion = Var[Option[String]](None)

      lazy val openMOLEMemoryInput: Input =
        UIUtils.buildInput("").amend(width := "160", `type` := "number", value := user.openMOLEMemory.toString)

      lazy val institutionInput: Input = UIUtils.buildInput(user.institution).amend(width := "400px", listId := "institutions")

      def content =
        val html =
          Signal.fromFuture(UserAPIClient.availableVersions(()).future).map: versions =>
            lazy val versionChanger =
              val index =
                versions.toSeq.flatten.indexOf(user.omVersion) match
                  case -1 => 0
                  case x => x

              Selector.options[String](
                versions.toSeq.flatten,
                index,
                Seq(cls := "btn btnUser"),
                naming = identity,
                decorations = Map()
              )

            div(margin := "30",
              Css.rowFlex,
              div(styleAttr := "width: 30%;", Css.columnFlex, alignItems.flexEnd,
                div(Css.centerRowFlex, cls := "settingElement", "OpenMOLE Version"),
                div(Css.centerRowFlex, cls := "settingElement", "OpenMOLE Memory (MB)"),
                div(Css.centerRowFlex, cls := "settingElement", "Institution"),
              ),
              div(styleAttr := "width: 70%;", Css.columnFlex, alignItems.flexStart,
                div(Css.centerRowFlex, cls := "settingElement", versionChanger.selector.amend(width := "160")),
                div(Css.centerRowFlex, cls := "settingElement", openMOLEMemoryInput),
                div(Css.centerRowFlex, cls := "settingElement", institutionInput),
                institutionsList
              ),
              versionChanger.content.signal.changes.toObservable --> selectedVersion.toObserver
            )

        div(child <-- html)


      def save(): Unit =
        val futures = ListBuffer[Future[_]]()

        selectedVersion.now().foreach: v =>
          if v != user.omVersion
          then futures += UserAPIClient.setOpenMOLEVersion(v).future

        util.Try(openMOLEMemoryInput.ref.value.toInt).foreach: m =>
          if m != user.openMOLEMemory
          then futures += UserAPIClient.setOpenMOLEMemory(m).future

        val institution = institutionInput.ref.value
        if institution.nonEmpty
        then futures += UserAPIClient.setInstitution(institution).future

        Future.sequence(futures).andThen(_ => reload.set(()))


    def userPanel(user: User) =
      var refreshing = false
      val versionUpdate: Var[Option[String]] = Var(None)

      def userInfoBlock =
        def maxMemory =
          user.memory match
            case m if m <= 0 => "∞"
            case x => s"${UIUtils.toGB(x, true)} GB"

        def maxCPU =
          user.cpu match
            case x if x <= 0 => "∞"
            case x => x.toString

        def version =
          div(Css.centerColumnFlex,
            child <-- versionUpdate.signal.map:
              case Some(v) =>
                a(
                  div(cls := "statusBlock",
                    div("OpenMOLE version", cls := "info"),
                    div(user.omVersion, div(cls := "bi bi-arrow-bar-up", marginLeft := "5px"), cls := "infoContent", color := "#ffde75")
                  ),
                  onClick --> UserAPIClient.setOpenMOLEVersion(v).future.foreach(_ => reload.set(())),
                  cursor.pointer
                )
              case None =>
                div(cls := "statusBlock",
                  div("OpenMOLE version", cls := "info"),
                  div(user.omVersion, cls := "infoContent"),
                  onMountCallback: _ =>
                    UserAPIClient.openMOLEVersionUpdate(user.omVersion).future.foreach:
                      case Some(v) => versionUpdate.set(Some(v))
                      case None =>
                )
          )


        div(
          child <-- space.signal.distinct.map: storage =>
            div(Css.centerRowFlex, justifyContent.center, padding := "30px",
              UIUtils.badgeBlock("Role", user.role.toString),
              version,
              UIUtils.textBlock("OpenMOLE memory", s"${UIUtils.toGB(user.openMOLEMemory, true)} GB"),
              UIUtils.textBlock("Max Memory", maxMemory),
              UIUtils.textBlock("Max CPU", maxCPU),
              storage.toSeq.map: storage =>
                UIUtils.memoryBar("Storage", storage.used.toInt, (storage.used + storage.available).toInt)
            )
        )
      end userInfoBlock


      div(
        EventStream.periodic(5000).toObservable -->
          Observer: _ =>
            if !refreshing
            then
              refreshing = true
              try
                UserAPIClient.instance(()).future.foreach(podInfo.set)
                val stopped = podInfo.now().flatMap(_.status.map(PodInfo.Status.isStopped)).getOrElse(true)
                if space.now().isEmpty && !stopped
                then UserAPIClient.usedSpace(()).future.foreach(space.set)
              finally refreshing = false
        ,
        div(maxWidth := "1000",
          ConnectUtils.logoutItem.amend(Css.rowFlex, justifyContent.flexEnd),
          userInfoBlock,
          div(
            child <--
              podInfo.signal.map(_.flatMap(_.status)).map:
                case Some(st) => UIUtils.openmoleBoard(None, st)
                case None => UIUtils.openmoleBoard(None, PodInfo.Status.Inactive)
          )
        )
      )


    def infoPanel() =

      def webdavLocation =
        val location = dom.document.location
        s"${location.protocol}//${location.host}/openmole/webdav"

      div(margin := "30",
        "You can get help on the ", a("OpenMOLE chat", href := "https://chat.openmole.org/channel/help"), ".",
        br(), br(),
        s"When your OpenMOLE instance is running you can access your files externally via the webdav protocol using this URL:", a(webdavLocation, href := webdavLocation),
        br(), br(),
        child <-- versionInfo.map:
          case Some((v, n, bu)) => div(s"You are presently running OpenMOLE $v - $n, built on $bu")
          case None => emptyNode
      )

    def buttons(user: User) =
      def settingButton(user: User) =
        button(
          "Settings",
          `type` := "button",
          cls := "btn btnUser settings",
          onClick --> settings.set(Settings(user))
        )

      def saveButton(s: Settings) =
        button(
          `type` := "button",
          cls := "btn btnUser settings", "Apply",
          onClick --> {
            s.save()
            settings.set(FrontPage)
          }
        )

      val adminButton =
        button(
          "Admin",
          `type` := "button",
          cls := "btn btnUser settings",
          onClick --> settings.set(AdminPage)
        )

      val closeButton =
        button(
          "Close",
          `type` := "button",
          cls := "btn btnUser settings",
          onClick --> settings.set(FrontPage)
        )

      val infoButton =
        button(
          "Info",
          `type` := "button",
          cls := "btn btnUser settings",
          onClick --> settings.set(InfoPage)
        )

      div(display.flex, flexDirection.row, justifyContent.start, alignItems.center,
        children <-- settings.signal.map:
          case FrontPage if user.role == Role.Admin => Seq(settingButton(user), infoButton, adminButton)
          case AdminPage if user.role == Role.Admin => Seq(closeButton)
          case FrontPage => Seq(settingButton(user), infoButton)
          case InfoPage => Seq(closeButton)
          case settings: Settings =>  Seq(closeButton, saveButton(settings))
      )

    val mainPanel =
      reload.signal.flatMap: _ =>
        Signal.fromFuture(UserAPIClient.user(()).future).map:
          case None => UIUtils.waiter.amend(Css.rowFlex, justifyContent.flexEnd)
          case Some(u) =>
            UIUtils.mainPanel(
              div(
                child <-- settings.signal.map:
                  case FrontPage => userPanel(u)
                  case s: Settings => s.content
                  case AdminPage => AdminPanel.admin()
                  case InfoPage => infoPanel()
              ),
              div(s"${u.firstName} ${u.name}", marginRight := "20", fontFamily := "gi"),
              buttons(u)
            ).amend(width := "800")

    lazy val appContainer = dom.document.querySelector("#appContainer")
    render(
      appContainer,
      div(child <-- mainPanel)
    )

