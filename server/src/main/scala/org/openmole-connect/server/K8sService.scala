package org.openmoleconnect.server

import org.openmoleconnect.server.DB.UUID
import shared.Data
import skuber._
import skuber.json.ext.format._
import akka.actor.ActorSystem
import skuber.json.format._
import akka.stream.ActorMaterializer
import shared.Data._
import skuber.api.client.KubernetesClient
import skuber.ext.{Deployment, Ingress, IngressList}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

object K8sService {


  def listPods = {
    withK8s { k8s =>

      implicit val system = ActorSystem()
      implicit val materializer = ActorMaterializer()
      implicit val dispatcher = system.dispatcher

      val allPodsMapFut: Future[Map[String, PodList]] = k8s.listByNamespace[PodList]()
      val allPodsFuture: Future[List[Pod]] = allPodsMapFut map { allPodsMap =>
        allPodsMap.values.flatMap(_.items).toList
      }


      def listPods0(pods: List[Pod]) = {
        pods.flatMap { pod: Pod =>
          val name = pod.name
          val ns = pod.namespace

          for {
            stat <- pod.status.toList
            containerStat <- stat.containerStatuses
            status <- containerStat.state
            restarts <- stat.containerStatuses.headOption
            createTime <- pod.metadata.creationTimestamp
            podIP <- stat.podIP
          } yield {
            val st: Status = status match {
              case Container.Waiting(reason) => Data.Waiting(reason.getOrElse(""))
              case _: Container.Running => Data.Running()
              case Container.Terminated(_, _, _, message, _, finishedAt, _) => Data.Terminated(message.getOrElse(""), finishedAt.map {
                _.toEpochSecond
              }.getOrElse(0L))
            }

            PodInfo(pod.name, st.value, restarts.restartCount, createTime.toEpochSecond, podIP, DB.email(UUID(pod.metadata.name)).map {
              _.value
            })
          }
        }
      }

      allPodsFuture map { pods => listPods0(pods) }

    }
  }

  def withK8s[T](kubeAction: KubernetesClient => Future[T]) = {

    implicit val system = ActorSystem()
    implicit val materializer = ActorMaterializer()
    implicit val dispatcher = system.dispatcher
    val k8s = k8sInit

    Await.result(kubeAction(k8s), Duration.Inf)
  }

  def getIngress = withK8s { k8s =>

    implicit val system = ActorSystem()
    implicit val materializer = ActorMaterializer()
    implicit val dispatcher = system.dispatcher

    val allIngressMapFut: Future[ListResource[Ingress]] = k8s listInNamespace[IngressList] ("ingress-nginx")
    val allIngressFuture: Future[List[Ingress]] = allIngressMapFut map { allIngressMap =>
      allIngressMap.items
    }


    def listIngress(ingresses: List[Ingress]) = ingresses.headOption

    allIngressFuture map { ingresses => listIngress(ingresses) }
  }

  def ingressIP: Option[String] =
    getIngress.flatMap { ing =>
      ing.status.flatMap {
        _.loadBalancer.flatMap {
          _.ingress.headOption.flatMap {
            _.ip
          }
        }
      }
    }

      //    def withK8sToResult(k8Action: String)(kubeAction: KubernetesClient => Future[_ <: ObjectResource]): K8ActionResult = {
      //
      //      implicit val system = ActorSystem()
      //      implicit val materializer = ActorMaterializer()
      //      implicit val dispatcher = system.dispatcher
      //      val k8s = k8sInit(K8SConfiguration.useLocalProxyDefault)
      //
      //      Try {
      //        Await.result({
      //          kubeAction(k8s)
      //        }, Duration.Inf)
      //      } match {
      //        case Success(o: ObjectResource) => K8Success(s"$k8Action successfully completed " + o.name + " // " + o.metadata.generateName)
      //        case Failure(t: Throwable) => K8Failure(t.getMessage, t.toStackTrace)
      //      }
      //    }

      //  implicit class OResourceToK8ActionResult[OR <: ObjectResource](o: OR)  {
      //    def toK8ActionResult = o match {
      //          case Success(o: ObjectResource) => K8Success(s"$k8Action successfully completed " + o.name + " // " + o.metadata.generateName)
      //          case Failure(t: Throwable) => K8Failure(t.getMessage, t.toStackTrace)
      //        }
      //}

      def deployOpenMOLE(uuid: UUID) = {
        withK8s { k8s =>
          val openmoleLabel = "app" -> "openmole"
          val openmoleContainer = Container(name = "openmole", image = "openmole/openmole", command = List("bin/bash", "-c", "openmole --port 80 --password password --http --remote")).exposePort(80)

          val openmoleTemplate = Pod.Template.Spec(ObjectMeta(name = uuid.value, namespace = "default"))
            .addContainer(openmoleContainer)
            .addLabel(openmoleLabel)
          //.named("openmole")

          val desiredCount = 1
          val openmoleDeployment = Deployment(uuid.value)
            .withReplicas(desiredCount)
            .withTemplate(openmoleTemplate)

          println("\nCreating openmole deployment")
          k8s create openmoleDeployment
        }

      }

      def deployIfNotDeployedYet(uuid: UUID) = {
        if (!isDeploymentExists(uuid))
          deployOpenMOLE(uuid)

      }

      private def podInfo(uuid: UUID)
      =
      {

        //  import monix.execution.Scheduler.Implicits.global
        val lp = listPods
        println("pods " + lp)
        lp.find {
          _.name.contains(uuid.value)
        }
      }


      def isServiceUp(uuid: UUID): Boolean = {
        podInfo(uuid).map {
          _.status
        } == Some(Running)
      }

      def isDeploymentExists(uuid: UUID) = podInfo(uuid).isDefined

      def podInfos: Seq[PodInfo] = {

        for {
          uuid <- DB.uuids
          podInfo <- podInfo(uuid)
        } yield (podInfo)
      }

      def hostIP(uuid: UUID) = {
        podInfo(uuid).map {
          _.podIP
        }
      }
    }