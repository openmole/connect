package org.openmoleconnect.adminclient

import java.nio.ByteBuffer

import org.scalajs.dom
import scaladget.bootstrapnative.bsn._
import org.scalajs.dom.raw.{Event, HTMLFormElement}
import scalatags.JsDom.all._
import scalatags.JsDom.tags

import scala.scalajs.js.annotation.JSExportTopLevel
import boopickle.Default._
import shared.AdminApi
import autowire._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.scalajs.js.typedarray.{ArrayBuffer, TypedArrayBuffer}

object AdminPanel {

  @JSExportTopLevel("admin")
  def admin() = {
    println("admin ...")
   Post[AdminApi].users().call().foreach {u=>
     println("users " +u)
   }

    dom.document.body.appendChild(div("Admin").render)
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