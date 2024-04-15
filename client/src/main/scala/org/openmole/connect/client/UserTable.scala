package org.openmole.connect.client

import scaladget.bootstrapnative.Table._
import com.raquo.laminar.api.L.*

class UserTable(headers: Seq[String],
                userRows: Signal[Seq[BasicRow]]):

  val selected: Var[Option[RowID]] = Var(None)
  val expanded: Var[Seq[RowID]] = Var(Seq())


  def updateExpanded(rowID: RowID) = expanded.update { e =>
    if (e.contains(rowID)) e.filterNot(_ == rowID)
    else e.appended(rowID)
  }

  def rowRender(rowID: RowID, initialRow: Row, rowStream: Signal[Row]): HtmlElement =
    initialRow match {
      case br: BasicRow =>
        tr(
          backgroundColor <-- selected.signal.map {
            s => if (Some(initialRow.rowID) == s) "#f2f2f2" else ""
          },
          onClick --> (_ => selected.set(Some(initialRow.rowID))),
          children <-- rowStream.map(r => r.tds)
        )
      case er: ExpandedRow =>
        tr(
          child <-- rowStream.map(rs => rs.tds.head)
        )
    }


  val render =
    table(cls := "table table-striped",
      headerRender(Some(Header(headers))),
      tbody(
        children <-- userRows.split(_.rowID)(rowRender)
      )
    )
