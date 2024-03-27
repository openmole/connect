package org.openmoleconnect.server

import java.io.{PrintWriter, StringWriter}
import java.text.SimpleDateFormat
import java.util.Locale

import org.apache.http.impl.EnglishReasonPhraseCatalog

object Utils:

  object openmoleversion:
    def stable = "FIXME STABLE"
    def developpement = "FIXME DEV"

  implicit class ST(throwable: Throwable):
    def toStackTrace =
      val sw = new StringWriter()
      throwable.printStackTrace(new PrintWriter(sw))
      sw.toString


  def now = System.currentTimeMillis()