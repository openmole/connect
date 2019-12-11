package org.openmoleconnect.server

import java.text.SimpleDateFormat
import java.util.Locale

import org.apache.http.impl.EnglishReasonPhraseCatalog

object Utils {

  object openmoleversion {
    def stable = DB.Version("FIXME STABLE")

    def developpement = DB.Version("FIXME DEV")
  }

  val DATE_FORMAT  = "EEE, d MMM yyyy HH:mm:ss"

  def toStringDate(date: Long) =  date match {
    case 0L => "NEVER CONNECTED"
    case l => new SimpleDateFormat(DATE_FORMAT, new Locale("en")).format(l)
  }

  def toLongDate(date: String) = {
    new SimpleDateFormat(DATE_FORMAT).parse(date).getTime
  }
}
