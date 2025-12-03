package org.openmole.connect.server

import com.google.common.base.{Supplier, Suppliers}
import com.google.common.cache.CacheBuilder
import org.apache.hc.client5.http.classic.methods.HttpGet
import org.openmole.connect.server.tool.cache

import java.util.concurrent.TimeUnit

/*
 * Copyright (C) 2024 Romain Reuillon
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
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

object OpenMOLE:
  val stablePattern = "[0-9]*\\.[0-9]*"
  val snapshotPattern = stablePattern + "-SNAPSHOT"
  val rcPattern = stablePattern + "-RC[0-9]*"
  val latest = "latest"

  def wellFormedVersion(v: String) =
    v.matches(stablePattern) || v.matches(snapshotPattern) || v.matches(rcPattern)

  def availableVersions(withSnapshot: Boolean = true, history: Option[Int] = None, min: Option[Int] = None, lastMajors: Boolean = false, latest: Boolean = true)(using DockerHubCache): Seq[String] =
    val tags = summon[DockerHubCache].get()

    val stableFormed = tags.filter(v => v.matches(stablePattern))
    val rcFormed = tags.filter(v => v.matches(rcPattern))
    val snapshotFormed = tags.filter(v => v.matches(snapshotPattern))

    def allMajors =
      tags.flatMap: t =>
        util.Try(t.split('.').headOption.map(_.toInt)).toOption.flatten
      .distinct

    val majors =
      val h =
        history match
          case Some(h) => allMajors.take(h)
          case None => allMajors

      min match
        case Some(m) => h.filter(_ >= m)
        case None => h

    val minorVersionMap =
      majors.map: m =>
        m -> stableFormed.filter(_.startsWith(s"$m."))
      .toMap

    val rcVersionMap =
      majors.map: m =>
        m -> rcFormed.filter(_.startsWith(s"$m."))
      .toMap

    val snapshotVersionMap =
      majors.map: m =>
        m -> snapshotFormed.filter(_.startsWith(s"$m."))
      .toMap

    def snapshots =
      majors.flatMap: maj =>
        if withSnapshot && minorVersionMap(maj).isEmpty && rcVersionMap(maj).isEmpty
        then snapshotVersionMap(maj)
        else Seq()

    majors.flatMap: maj =>
      if minorVersionMap(maj).nonEmpty
      then
        if lastMajors
        then minorVersionMap(maj).headOption.toSeq
        else minorVersionMap(maj)
      else
        if rcVersionMap(maj).nonEmpty
        then
          if lastMajors
          then rcVersionMap(maj).headOption.toSeq
          else rcVersionMap(maj)
        else Seq()
    ++ snapshots ++ (if latest then Seq(OpenMOLE.latest) else Seq())


  object DockerHubCache:
    def apply(): DockerHubCache =
      Suppliers.memoizeWithExpiration(() => dockerHubTags("openmole", "openmole"), 2, TimeUnit.MINUTES)

  opaque type DockerHubCache = Supplier[Seq[String]]

  def dockerHubTags(group: String, image: String, pageSize: Int = 100): Seq[String] =
    import org.json4s.*
    import org.json4s.jackson.JsonMethods.*

    val httpClient = tool.buildHttpClient()
    try
      val httpGet = new HttpGet(s"https://hub.docker.com/v2/namespaces/$group/repositories/$image/tags?page_size=$pageSize")
      val response = httpClient.execute(httpGet)
      try
        val json = parse(response.getEntity.getContent)
        (json \\ "name").children.map(_.values.toString)
      finally response.close()
    finally httpClient.close()

  import scala.math.Ordering
  import scala.util.matching.Regex

  enum OpenMOLEVersion:
    case Stable(major: Int, minor: Int)
    case RC(major: Int, minor: Int, rc: Int)
    case Snapshot(major: Int, minor: Int)
    case Latest

  object OpenMOLEVersion:

    private val StableRegex: Regex = raw"(\d+)\.(\d+)".r
    private val RCRegex: Regex = raw"(\d+)\.(\d+)-RC(\d+)".r
    private val SnapshotRegex: Regex = raw"(\d+)\.(\d+)-SNAPSHOT".r

    def parse(version: String): Option[OpenMOLEVersion] = version match
      case "latest" => Some(Latest)
      case RCRegex(maj, min, rc) => Some(RC(maj.toInt, min.toInt, rc.toInt))
      case SnapshotRegex(maj, min) => Some(Snapshot(maj.toInt, min.toInt))
      case StableRegex(maj, min) if !version.contains("-") =>
        Some(Stable(maj.toInt, min.toInt))
      case _ => None

    extension (v: OpenMOLEVersion)
      def isVolatile: Boolean =
        v match 
          case _: Snapshot | Latest => true
          case _ => false
      
      def isStable: Boolean =
        v match
          case Stable(_, _) => true
          case _ => false
      
      def compare(v2: OpenMOLEVersion) =
        summon[Ordering[OpenMOLEVersion]].compare(v, v2)

    given Ordering[OpenMOLEVersion] with
      def compare(a: OpenMOLEVersion, b: OpenMOLEVersion): Int =
        (a, b) match
          case (Latest, Latest) => 0
          case (Latest, _) => 1
          case (_, Latest) => -1

          case (Stable(maj1, min1), Stable(maj2, min2)) =>
            comparePair((maj1, min1), (maj2, min2))

          case (RC(maj1, min1, rc1), RC(maj2, min2, rc2)) =>
            val base = comparePair((maj1, min1), (maj2, min2))
            if base != 0 then base else rc1.compare(rc2)

          case (Snapshot(maj1, min1), Snapshot(maj2, min2)) =>
            comparePair((maj1, min1), (maj2, min2))

          case (RC(maj1, min1, _), Stable(maj2, min2)) =>
            val base = comparePair((maj1, min1), (maj2, min2))
            if base != 0 then base else -1

          case (Stable(maj1, min1), RC(maj2, min2, _)) =>
            val base = comparePair((maj1, min1), (maj2, min2))
            if base != 0 then base else 1

          case (Snapshot(maj1, min1), Stable(maj2, min2)) =>
            val base = comparePair((maj1, min1), (maj2, min2))
            if base != 0 then base else -1

          case (Stable(maj1, min1), Snapshot(maj2, min2)) =>
            val base = comparePair((maj1, min1), (maj2, min2))
            if base != 0 then base else 1

          case (Snapshot(maj1, min1), RC(maj2, min2, _)) =>
            val base = comparePair((maj1, min1), (maj2, min2))
            if base != 0 then base else -1

          case (RC(maj1, min1, _), Snapshot(maj2, min2)) =>
            val base = comparePair((maj1, min1), (maj2, min2))
            if base != 0 then base else 1

      private def comparePair(a: (Int, Int), b: (Int, Int)): Int =
        val (a1, a2) = a
        val (b1, b2) = b
        Seq(a1.compare(b1), a2.compare(b2)).find(_ != 0).getOrElse(0)

