package org.openmoleconnect.client

import org.scalajs.dom
import scaladget.bootstrapnative.bsn._
import com.raquo.laminar.api.L.*

import scala.scalajs.js.annotation.JSExportTopLevel

import shared.Data._
import com.raquo.laminar.api.features.unitArrows

/*
 * Copyright (C) 11/07/19 // mathieu.leclaire@openmole.org
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

object Connection:

  @JSExportTopLevel("connection")
  def connect() =
    lazy val connectButton = button("Connect", btn_primary, `type` := "submit", float.right, right := "0")

    //lazy val cookieButton = tags.button("Cookuie", btn_default, onclick := { () => println("COOKIES: " + dom.document.cookie) }).render

    lazy val emailInput = inputTag("")
      .amend(
        nameAttr := "Email",
        placeholder := "Email",
        width := "130px",
        marginBottom := "15"
      )

    lazy val passwordInput = inputTag("")
      .amend(
        nameAttr := "Password",
        placeholder := "Password",
        `type` := "password",
        width := "130px",
        marginBottom := "15"
      )

    val connectionForm = form(
      method := "POST",
      action := connectionRoute,
      emailInput,
      passwordInput,
      connectButton
    )

    val renderContent =
      div(
        div(Css.connectionTabOverlay,
          div(
            img(src := "img/logo.png", Css.openmoleLogo),
            div(Css.connectionFormStyle, connectionForm)
          )
        )
      )

    lazy val appContainer = dom.document.querySelector("#appContainer")
    render(appContainer, renderContent)
