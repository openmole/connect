package org.openmoleconnect.server

import org.openmoleconnect.server.DB.UUID
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import skuber._
import skuber.Timestamp
import skuber.json.format._

import scala.concurrent.duration._

import scala.concurrent.{Await, Future}

object K8sService {

  case class PodInfo(
                      name: String,
                      status: String,
                      restarts: Int,
                      createTime: Timestamp
                    )


  private def listPods = {
    implicit val system = ActorSystem()
    implicit val materializer = ActorMaterializer()
    implicit val dispatcher = system.dispatcher


    val k8s = k8sInit

    val podList: Future[PodList] = k8s list[PodList]()
    podList map {
      _.items.flatMap {
        pod: Pod =>
          println("POD " + pod.name)
          val name = pod.name
          val ns = pod.namespace

          (for {
            stat <- pod.status.toList
            containerStat <- stat.containerStatuses
            status <- containerStat.state
            restarts <- stat.containerStatuses.headOption
            createTime <- pod.metadata.creationTimestamp
          } yield {
            PodInfo(name, status.toString.slice(0, status.toString.indexOf("(")), restarts.restartCount, createTime)
          })
      }
    }
  }

  private def pod(uuid: UUID) = {

    import monix.execution.Scheduler.Implicits.global
    Await.result(
      listPods.map { list =>
        list.find {
          _.name.contains(uuid)
        }
      }, 1 minute)
  }


  def isServiceUp(uuid: UUID): Boolean = {
    pod(uuid).isDefined
    //    existsInMap(uuid) match {
    //      case true=>
    //        if(isUp(uuid)) forwarRequest
    //        else createOMService(uuid)
    //      case false => addService(uuid)
    //    }
  }
}