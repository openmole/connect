package org.openmoleconnect.server

import java.text.SimpleDateFormat
import java.util.Locale

import org.apache.http.impl.EnglishReasonPhraseCatalog

object Utils {

  object openmoleversion {
    def stable = DB.Version("FIXME STABLE")

    def developpement = DB.Version("FIXME DEV")
  }
}
