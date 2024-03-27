package org.openmole.connect.client

import java.nio.ByteBuffer
import org.scalajs.dom

import scala.scalajs.js.annotation.JSExportTopLevel
import scaladget.bootstrapnative.*
import scaladget.bootstrapnative.bsn.*
import ConnectUtils.*
import org.scalajs.dom.raw.HTMLInputElement
import scaladget.bootstrapnative.Selector.Options
import com.raquo.laminar.api.L.*
import org.openmole.connect.shared.*
import org.openmole.connect.shared.Data.*

object AdminPanel {

  @JSExportTopLevel("admin")
  def admin() = {

    implicit def userDataSeqToRows(userData: Seq[UserData]): Seq[EmailRow] = userData.map { u =>
      EmailRow("stub")
      //      buildExpandable(u.name, u.email, u.password, u.role, u.omVersion, u.storage, u.lastAccess, podInfos.now().filter {
      //        _.userEmail == Some(u.email)
      //      }.headOption, open.now().map {
      //        _ == u.email
      //      }.getOrElse(false))
    }


    lazy val rowFlex = Seq(display.flex, flexDirection.row, justifyContent.spaceAround, alignItems.center)
    lazy val columnFlex = Seq(display.flex, flexDirection.column, justifyContent.center)

    //case class EmailRow(email: String, expandableRow: ExpandableRow)
    case class EmailRow(email: String)

    lazy val rows: Var[Seq[EmailRow]] = Var(Seq())
    lazy val open: Var[Option[String]] = Var(None)
    lazy val podInfos: Var[Seq[PodInfo]] = Var(Seq())


    def save(userData: UserData): Unit = {
      //  if (userData.name.isEmpty)
      //   rows.update(rows.now().filterNot(_.expandableRow == expandableRow))
      //  else {
      println(s"save with ${userData}")
      //upsert(userData)
      //   }
    }

    //    def upsert(userData: UserData) =
    //      Post[AdminApi].upserted(userData).call().foreach {
    //        rows() = _
    //      }
    //
    //    def deleteUser(userData: UserData) =
    //      Post[AdminApi].delete(userData).call().foreach {
    //        rows() = _
    //      }
    //
    //    def stopOpenMOLE(userData: UserData) =
    //      Post[AdminApi].stopOpenMOLE(userData).call().foreach {
    //        rows() = _
    //      }
    //
    //    def startOpenMOLE(userData: UserData) =
    //      Post[AdminApi].startOpenMOLE(userData).call().foreach {
    //        rows() = _
    //      }
    //
    //    def updateOpenMOLE(userData: UserData) = {
    //      Post[AdminApi].updateOpenMOLE(userData).call().foreach {
    //        rows() = _
    //      }
    //      upsert(userData)
    //    }
    //
    //    def updateOpenMOLEPersistentVolumeStorage(userData: UserData) = {
    //      Post[AdminApi].updateOpenMOLEPersistentVolumeStorage(userData).call().foreach {
    //        rows() = _
    //      }
    //      upsert(userData)
    //    }
    //
    //    def updateRows = {
    //      Post[AdminApi].users().call().foreach { us =>
    //        rows() = us
    //      }
    //    }
    //
    //    def updatePodInfos =
    //      Post[AdminApi].podInfos().call().foreach { pi =>
    //        println("PI\n" + pi.mkString("\n"))
    //        podInfos() = pi
    //        updateRows
    //      }
    //
    //    def isEditing(email: String): Boolean = rows.now().filter { er =>
    //      er.expandableRow.editableRow.cells.exists {
    //        _.editMode.now()
    //      }
    //    }.map {
    //      _.email
    //    }.headOption == Some(email)

    //    def updatePodInfoTimer: Unit = {
    //      setTimeout(10000) {
    //        updatePodInfos
    //        updatePodInfoTimer
    //      }
    //    }


    // def closeAll() = rows.now()
    //      .filterNot {
    //      _.expandableRow == except
    //    }
    //      .foreach {
    //        _.expandableRow.subRow.trigger() = false
    //      }


    def buildExpandable(userName: String = "",
                        userEmail: String = "",
                        userPassword: String = "",
                        userRole: Role = "",
                        userOMVersion: String = "",
                        userStorage: String = "10Gi", //
                        userLastAccess: Long = 0L,
                        podInfo: Option[PodInfo] = None,
                        expanded: Boolean = false,
                        edited: Option[Boolean] = None): EmailRow = {
      val aVar = Var(expanded)
      val editing = false //edited.getOrElse(isEditing(userEmail))
      val selectedOMVersion = Var(userOMVersion)
      val index = Data.openMOLEVersions.indexOf(userOMVersion)
      println(s"userOMVersion = ${userOMVersion} at index ${Data.openMOLEVersions.indexOf(userOMVersion)}")
      //        lazy val optionDropDown: Options[String] = Selector.options[String](
      //          contents = Data.openMOLEVersions,
      //          defaultIndex = if (index < 0) 0 else index,
      //          naming = (s: String) => s,
      //          //decorations = Map[String, ModifierSeq](),
      //          onclose = () => {
      //            val res: String = optionDropDown.content.now().get
      //            selectedOMVersion.update(res)
      //            println(s"selected is ${selectedOMVersion.now()}")
      //          }
      //        )
      val inputStorage = Var(userStorage)
      println(s"userOMStorage = ${userStorage}")
      lazy val storage: Input = inputTag(userStorage)
      //(onInput --> { _ =>
      //inputStorage.update((_=> )storage.ref.value)
      //})


      //  lazy val aSubRow: StaticSubRow = StaticSubRow({
      lazy val aSubRow =
        div(height := "350", rowFlex,
          // groupCell.build(margin := "25"),
          div(userLastAccess.toStringDate, fontSize := "12px", minWidth := "150"),
          label(badge_primary, userOMVersion),
          //  optionDropDown.selector,
          storage,
          span(columnFlex, alignItems.flexEnd, justifyContent.flexEnd,
            button(btn_danger, "Delete user (and data)", onClick --> { _ =>
              val userData = UserData(userName, userEmail, userPassword, userRole, userOMVersion, userStorage, userLastAccess)
              //  deleteUser(userData)
            }, margin := "10"),
            button(btn_danger, "Stop OpenMOLE", onClick --> { _ =>
              val userData = UserData(userName, userEmail, userPassword, userRole, userOMVersion, userStorage, userLastAccess)
              // stopOpenMOLE(userData)
            }, margin := "10"),
            button(btn_success, "Start OpenMOLE", onClick --> { _ =>
              val userData = UserData(userName, userEmail, userPassword, userRole, userOMVersion, userStorage, userLastAccess)
              //startOpenMOLE(userData)
            }, margin := "10"),
            button(btn_success, "Update OpenMOLE", onClick --> { _ =>
              val userData = UserData(userName, userEmail, userPassword, userRole, selectedOMVersion.now(), userStorage, userLastAccess)
              //updateOpenMOLE(userData)
            }, margin := "10"),
            button(btn_success, "Update OpenMOLE Storage", onClick --> { _ =>
              println(s"Update OpenMOLE Storage with ${inputStorage.now()}")
              val userData = UserData(userName, userEmail, userPassword, userRole, userOMVersion, inputStorage.now(), userLastAccess)
              //updateOpenMOLEPersistentVolumeStorage(userData)
            }, margin := "10"),
          )
        )

      def statusStyle(s: String) =
        if (s == "Running") badge_success
        else if (s == "Waiting") badge_warning
        else badge_danger

      //      lazy val expandableRow: ExpandableRow = ExpandableRow(EditableRow(Seq(
      //        TriggerCell(a(userName, onclick := { () =>
      //          closeAll(expandableRow)
      //          aVar() = !aVar.now()
      //          open() = {
      //            if (aVar.now()) Some(userEmail)
      //            else None
      //          }
      //        })),
      //        LabelCell(podInfo.map {
      //          _.status
      //        }.getOrElse("Unknown"), Seq(), optionStyle = _ => podInfo.map { pi => statusStyle(pi.status) }.getOrElse(label_danger)),
      //      )), aSubRow)
      //
      //
      //      lazy val groupCell: GroupCell = UserPanel.editableData(userName, userEmail, userPassword, userRole, podInfo, userOMVersion, userStorage, userLastAccess, editableEmail = true, editableRole = true, expanded, editing, (uData: UserData) => save(expandableRow, uData.copy(omVersion = selectedOMVersion.now(), storage = inputStorage.now())))
      //
      EmailRow(userEmail)
    }


    //    updateRows
    //    updatePodInfos
    //updatePodInfoTimer

    val addUserButton = button(btn_primary, "Add"
      /*, onClick --> { () =>
      val row = buildExpandable(userRole = user, userOMVersion = Data.openMOLEVersions.head, expanded = true, edited = Some(true))
      rows.update(rows.now() :+ row)
    }*/)

    val refreshButton = button(btn_secondary, "Refresh", onClick --> { _ =>
      //updatePodInfos
    })

    //    val editablePanel = div(maxWidth := "1000", margin := "40px auto")(
    //      img(src := "img/logo.png", Css.adminLogoStyle),
    //      ConnectUtils.logoutItem(display.flex, flexDirection.row, justifyContent.flexEnd),
    //      div(display.flex, flexDirection.row, justifyContent.flexStart, marginLeft := "50", marginBottom := "20", marginTop := "80")(
    //        addUserButton(display.flex, flexDirection.row, justifyContent.flexEnd),
    //        refreshButton(display.flex, flexDirection.row, justifyContent.flexEnd)
    //      ),
    //      Rx {
    //        div(display.flex, flexDirection.row, justifyContent.center)(
    //          EdiTable(Seq("Name", "Status"), rows().map {
    //            _.expandableRow
    //          }).render(width := "90%")
    //        )
    //      }
    //    )

    dom.document.body.appendChild(div().ref)
  }

}


//object Post extends autowire.Client[ByteBuffer, Pickler, Pickler] {
//
//  override def doCall(req: Request): Future[ByteBuffer] = {
//    dom.ext.Ajax.post(
//      url = req.path.mkString("/"),
//      data = Pickle.intoBytes(req.args),
//      responseType = "arraybuffer",
//      headers = Map("Content-Type" -> "application/octet-stream")
//    ).map(r => TypedArrayBuffer.wrap(r.response.asInstanceOf[ArrayBuffer]))
//  }
//
//  override def read[Result: Pickler](p: ByteBuffer) = Unpickle[Result].fromBytes(p)
//
//  override def write[Result: Pickler](r: Result) = Pickle.intoBytes(r)

//}

//}