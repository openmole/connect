package org.openmoleconnect.client

import java.nio.ByteBuffer

import org.scalajs.dom

import scala.scalajs.js.annotation.JSExportTopLevel
import boopickle.Default._
import shared.AdminApi
import autowire._
import rx._
import scaladget.bootstrapnative.Table.StaticSubRow
import scaladget.bootstrapnative._
import scaladget.bootstrapnative.bsn.{glyph_edit2, glyph_save}
import scalatags.JsDom.styles
import shared.Data._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.scalajs.js.typedarray.{ArrayBuffer, TypedArrayBuffer}
import scaladget.bootstrapnative.bsn._
import scaladget.tools._
import scalatags.JsDom.all._

import scala.scalajs.js.Date

object AdminPanel {

  @JSExportTopLevel("admin")
  def admin() = {

    implicit def userDataSeqToRows(userData: Seq[UserData]): Seq[ExpandableRow] = userData.map { u =>
      buildExpandable(u.name, u.email, u.password, u.role, u.omVersion, u.lastAccess, running)
    }


    lazy val rowFlex = Seq(styles.display.flex, flexDirection.row, justifyContent.spaceAround, alignItems.center)
    lazy val columnFlex = Seq(styles.display.flex, flexDirection.column, styles.justifyContent.center)


    lazy val rows: Var[Seq[ExpandableRow]] = Var(Seq())


    def save(expandableRow: ExpandableRow, userData: UserData): Unit = {
      if (userData.name.isEmpty)
        rows.update(rows.now.filterNot(_ == expandableRow))
      else {
        upsert(userData)
      }
    }

    def upsert(userData: UserData) =
      Post[AdminApi].upserted(userData).call().foreach {
        rows() = _
      }

    def delete(userData: UserData) =
      Post[AdminApi].delete(userData).call().foreach {
        rows() = _
      }


    def closeAll(except: ExpandableRow) = rows.now.filterNot {
      _ == except
    }.foreach {
      _.subRow.trigger() = false
    }


    def buildExpandable(userName: String = "",
                        userEmail: String = "",
                        userPassword: String = "",
                        userRole: Role = "",
                        userOMVersion: String = "",
                        userLastAccess: Long = 0L,
                        userStatus: Status = user,
                        expanded: Boolean = false): ExpandableRow = {
      val aVar = Var(expanded)

      val lastAccess = new Date(userLastAccess.toDouble)

      lazy val aSubRow: StaticSubRow = StaticSubRow({
        div(height := 300, rowFlex)(
          groupCell.build(margin := 25),
          label(label_primary, userOMVersion),
          div(lastAccess.toUTCString), //.formatted("EEE, d MMM yyyy HH:mm:ss"),
          span(columnFlex, alignItems.flexEnd, justifyContent.flexEnd)(
          button(btn_danger, "Delete", onclick := { () =>
            val userData = UserData(userName, userEmail, userPassword, userRole, userOMVersion, userLastAccess)
            delete(userData)
          }, margin := 10)
          )
        )
      }, aVar)

      def statusStyle(s: Status) =
        if (s == running) label_success
        else if (s == off) label_default
        else label_danger

      lazy val expandableRow: ExpandableRow = ExpandableRow(EditableRow(Seq(
        TriggerCell(a(userName, onclick := { () =>
          closeAll(expandableRow)
          aVar() = !aVar.now
        })),
        LabelCell(userStatus, Seq(), optionStyle = statusStyle),
      )), aSubRow)


      lazy val groupCell: GroupCell = UserPanel.editableData(userName, userEmail, userPassword, userRole, userStatus, userOMVersion, userLastAccess, expanded, (uData: UserData) => save(expandableRow, uData))

      expandableRow
    }


    Post[AdminApi].users().call().foreach { us =>
      rows() = us
    }

    val addUserButton = button(btn_success, "Add", onclick := { () =>
      val row = buildExpandable(userRole = user, expanded = true)
      rows.update(rows.now :+ row)
    })

    val headerStyle: ModifierSeq = Seq(
      height := 40.85
    )

    val editablePanel = div(maxWidth := 1000, margin := "40px auto")(
      addUserButton(styles.display.flex, flexDirection.row, styles.justifyContent.flexEnd),
      Rx {
        div(styles.display.flex, flexDirection.row, styles.justifyContent.center)(
          EdiTable(Seq("Name", "Status"), rows()).render(width := "90%")
        )
      }
    )

    dom.document.body.appendChild(editablePanel.render)
  }

}


object Post extends autowire.Client[ByteBuffer, Pickler, Pickler] {

  override def doCall(req: Request): Future[ByteBuffer] = {
    dom.ext.Ajax.post(
      url = req.path.mkString("/"),
      data = Pickle.intoBytes(req.args),
      responseType = "arraybuffer",
      headers = Map("Content-Type" -> "application/octet-stream")
    ).map(r => TypedArrayBuffer.wrap(r.response.asInstanceOf[ArrayBuffer]))
  }

  override def read[Result: Pickler](p: ByteBuffer) = Unpickle[Result].fromBytes(p)

  override def write[Result: Pickler](r: Result) = Pickle.intoBytes(r)

}

//}