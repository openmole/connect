package org.openmoleconnect.client

import org.scalajs.dom
import scaladget.bootstrapnative.bsn._
import org.scalajs.dom.raw._
import scalatags.JsDom.all._
import scalatags.JsDom.tags

import scala.scalajs.js.annotation.JSExportTopLevel

import scala.collection.mutable
import shared.Data._


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

object Connection {

  @JSExportTopLevel("connection")
  def connect() = {

    lazy val connectButton = tags.button("Connect", btn_primary, `type` := "submit", float.right, right := 0).render

    //lazy val cookieButton = tags.button("Cookuie", btn_default, onclick := { () => println("COOKIES: " + dom.document.cookie) }).render

    lazy val emailInput = inputTag("")(
      name := "email",
      placeholder := "Email",
      width := "130px",
      marginBottom := 15,
      autofocus := true
    ).render

    lazy val passwordInput = inputTag("")(
      name := "password",
      placeholder := "Password",
      `type` := "password",
      width := "130px",
      marginBottom := 15
    ).render

    def cleanInputs = {
      passwordInput.value = ""
      emailInput.value = ""
    }

    val connectionForm: HTMLFormElement = form(
      method := "post",
      action := connectionRoute,
      emailInput,
      passwordInput,
      connectButton
    ).render

    val render = {
      div(
        div(css.connectionTabOverlay)(
          div(
            img(src := "img/logo.png", css.openmoleLogo),
            div(css.connectionFormStyle)(
              connectionForm
            )
          )
        )
      ).render

    }

    dom.document.body.appendChild(render)
  }
}