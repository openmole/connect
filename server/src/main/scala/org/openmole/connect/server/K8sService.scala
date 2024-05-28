package org.openmole.connect.server

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import io.kubernetes.client.openapi.ApiClient
import io.kubernetes.client.openapi.models.V1JobStatus
import org.openmole.connect.server.DB.UUID
import org.openmole.connect.shared.Data
import org.openmole.connect.shared.Data.*
import skuber.LabelSelector.dsl.*
import skuber.PersistentVolume.AccessMode
import skuber.*
import skuber.Resource.Requirements
import skuber.api.client.KubernetesClient
import skuber.apps.v1.Deployment
import skuber.json.format.*
import skuber.json.networking.format.*
import skuber.networking.{Ingress, IngressList}

import java.util
import scala.collection.immutable.List
import scala.collection.mutable.ListBuffer
import scala.concurrent.ExecutionContext.Implicits.global

import monocle.*
import monocle.syntax.all.*

object K8sService:

  object Namespace:
    val openmole = "openmole"
  //val connect = "connect"

  //  object Ceph:
  //    val storageClassName = "rook-ceph-block"

  def toPodInfo(pod: Pod) =

    val containerStatus = pod.status.flatMap(_.containerStatuses.headOption)
    val deletionTimeStamp = pod.metadata.deletionTimestamp

    val st =
      deletionTimeStamp match
        case Some(timestamp) => Some(Data.PodInfo.Status.Terminating())
        case None =>
          containerStatus.flatMap(_.state).map:
            case w: Container.Waiting => Data.PodInfo.Status.Waiting(w.reason.getOrElse(""))
            case r: Container.Running => Data.PodInfo.Status.Running(r.startedAt.map(_.toEpochSecond).getOrElse(0L))
            case t: Container.Terminated => Data.PodInfo.Status.Terminated(t.message.getOrElse(""), t.finishedAt.map {
              _.toEpochSecond
            }.getOrElse(0L))

    // Using the added metadata label to get the podName (if any) instead of the actual podName which is now generated by the deployment
    // Should we rename it deploymentName?
    PodInfo(
      pod.name,
      st,
      containerStatus.map(_.restartCount),
      pod.metadata.creationTimestamp.map(_.toEpochSecond),
      pod.status.flatMap(_.podIP),
      DB.userFromUUID(pod.metadata.labels.getOrElse("podName", "")).map(_.email)
    )

  def listPods = withK8s: k8s =>
    val allPodsMapFut = k8s.listInNamespace[PodList]("openmole") //k8s.listSelected[PodList](LabelSelector.IsEqualRequirement("app","openmole"))
    val allPodsFuture = allPodsMapFut.map(_.items)
    allPodsFuture.map { _.map(toPodInfo) }.await


  type KubeAction[+T] = KubernetesClient => T

  def withK8s[T](kubeAction: KubeAction[T]) =
    implicit val system: ActorSystem = ActorSystem()
    val k8s = k8sInit
    try kubeAction(k8s)
    finally k8s.close()

  def getIngress = withK8s: k8s =>
    val allIngressMapFut = k8s.listInNamespace[IngressList]("ingress-nginx")
    val allIngressFuture = allIngressMapFut.map { allIngressMap => allIngressMap.items }

    def listIngress(ingresses: List[Ingress]) = ingresses.headOption

    allIngressFuture.map { ingresses => listIngress(ingresses) }.await

  def ingressIP: Option[String] =
    for
      i <- getIngress
      s <- i.status
      b <- s.loadBalancer
      ig <- b.ingress.headOption
      ip <- ig.ip
    yield ip

  def createOpenMOLEContainer(version: String, openMOLEMemory: Int, memoryLimit: Int, cpuLimit: Double) =
    // Create the openMOLE container with the volume and SecurityContext privileged (necessary for singularity).
    // see also https://kubernetes.io/docs/concepts/security/pod-security-standards/
    val limits: Resource.ResourceList =
      Map() ++
        Some(memoryLimit).filter(_ > 0).map(m => Resource.memory -> Resource.Quantity(s"${memoryLimit}Mi")) ++
        Some(cpuLimit).filter(_ > 0).map(m => Resource.cpu -> Resource.Quantity(s"${(cpuLimit * 1000).toInt}m"))


    Container(
      name = "openmole",
      image = s"openmole/openmole:${version}",
      command = List("bin/bash", "-c", s"openmole-docker --port 80 --password password --remote --mem ${openMOLEMemory}m --workspace /var/openmole/.openmole"),
      volumeMounts = List(Volume.Mount(name = "data", mountPath = "/var/openmole/")),
      securityContext = Some(SecurityContext(privileged = Some(true))),
      imagePullPolicy = Container.PullPolicy.Always,
      resources = Some(Resource.Requirements(limits = limits))
    ).exposePort(80)

  def persistentVolumeClaim(pvcName: String, storage: Int, storageClassName: Option[String]) =
    def metadata(name: String) = ObjectMeta(name, namespace = Namespace.openmole, labels = Map("app" -> Namespace.openmole))

    PersistentVolumeClaim(
      metadata = metadata(pvcName),
      spec =
        Some(
          PersistentVolumeClaim.Spec(
            //volumeName = Some(pvName),
            storageClassName = storageClassName,
            accessModes = List(AccessMode.ReadWriteOnce),
            resources =
              Some(
                Resource.Requirements(
                  requests = Map(Resource.storage -> s"${storage}Mi")
                )
              )
          )
        )
    )

  def deployOpenMOLE(k8sService: K8sService, uuid: UUID, omVersion: String, openMOLEMemory: Int, memoryLimit: Int, cpuLimit: Double, storageRequirement: Int) =
    withK8s: k8s =>
      val podName = uuid.value
      val pvcName = s"pvc-$podName-${util.UUID.randomUUID().toString}"
      //val pvName = s"pv-${podName}"

      val pvc = persistentVolumeClaim(pvcName, storageRequirement, k8sService.storageClassName)

      val openMOLESelector: LabelSelector = LabelSelector.IsEqualRequirement("app", "openmole")
      val openMOLEContainer = createOpenMOLEContainer(omVersion, openMOLEMemory, memoryLimit, cpuLimit)

      val openMOLELabel = "app" -> "openmole"

      // Adding a metadata label to store the deloyment name (called podName for historical reasons)
      // The actual podName is now generated by the deployment
      // Should we rename it deploymentName?
      val openMOLETemplate = Pod.Template.Spec
        .named("openmole")
        .addContainer(openMOLEContainer)
        .addVolume(Volume(name = "data", source = Volume.PersistentVolumeClaimRef(claimName = pvcName)))
        .addLabel(openMOLELabel)
        .addLabel("podName" -> podName)

      val desiredCount = 1

      // https://kubernetes.io/docs/tasks/run-application/run-single-instance-stateful-application/
      val openMOLEDeployment =
        Deployment(
          metadata = ObjectMeta(name = podName),
          spec = Some:
            Deployment.Spec(
              replicas = Some(desiredCount),
              selector = openMOLESelector,
              template = openMOLETemplate,
              strategy = Some(Deployment.Strategy.Recreate)
            )
        )
      //        .withReplicas(desiredCount)
      //        .withTemplate(openMOLETemplate)
      //        .withLabelSelector(openMOLESelector)

      // Creating the openmole deployment
      k8s.usingNamespace(Namespace.openmole) create pvc
      val createdDeploymentFut = k8s.usingNamespace(Namespace.openmole) create openMOLEDeployment

      createdDeploymentFut.recoverWith:
        case ex: K8SException if ex.status.code.contains(409) =>
          k8s.get[Deployment](openMOLEDeployment.name).flatMap: curr =>
            val updated = openMOLEDeployment.withResourceVersion(curr.metadata.resourceVersion)
            k8s update updated
      .await

  def stopOpenMOLEPod(uuid: UUID) = withK8s: k8s =>
    k8s.usingNamespace(Namespace.openmole).get[Deployment](uuid.value).map: d =>
      k8s.usingNamespace(Namespace.openmole) update d.withReplicas(0)
    .await

  def startOpenMOLEPod(uuid: UUID) = withK8s: k8s =>
    k8s.usingNamespace(Namespace.openmole).get[Deployment](uuid.value).map: d =>
      k8s.usingNamespace(Namespace.openmole) update d.withReplicas(1)
    .await

  def updateOpenMOLEPod(uuid: UUID, newVersion: String, openmoleMemory: Int, memoryLimit: Int, cpuLimit: Double) = withK8s: k8s =>
    k8s.usingNamespace(Namespace.openmole).get[Deployment](uuid.value).map: d =>
      val container = createOpenMOLEContainer(newVersion, openmoleMemory, memoryLimit, cpuLimit)
      k8s.usingNamespace(Namespace.openmole) update d.updateContainer(container)
    .await

  def getPVCName(uuid: UUID): Option[String] =
    withK8s: k8s =>
      val deployment = k8s.usingNamespace(Namespace.openmole).get[Deployment](uuid.value).await
      deployment.focus(_.spec.some.template.spec.some.volumes.index(0).source).getOption match
          case Some(v: Volume.PersistentVolumeClaimRef) => Some(v.claimName)
          case _ => None

  // FIXME test with no pv name
  def updateOpenMOLEPersistentVolumeStorage(uuid: UUID, size: Int, storageClassName: Option[String]) = withK8s: k8s =>
    //stopOpenMOLEPod(uuid)

    //val pvcName = s"pvc-$uuid-${util.UUID.randomUUID().toString}"
    //val newPVC = persistentVolumeClaim(pvcName, size, storageClassName)

    //k8s.usingNamespace(Namespace.openmole).create(newPVC).await
    getPVCName(uuid).foreach: pvcName =>
      k8s.usingNamespace(Namespace.openmole).update(persistentVolumeClaim(pvcName, size, storageClassName)).await

//    val deployment = k8s.usingNamespace(Namespace.openmole).get[Deployment](uuid.value).await
//
//    k8s.usingNamespace(Namespace.openmole).update[Deployment]:
//      deployment.focus(_.spec.some.template.spec.some.volumes).set:
//        List(Volume(name = "data", source = Volume.PersistentVolumeClaimRef(claimName = pvcName)))

//    deployment.withTemplate:
//      deployment.spec.get.template.spec.get.volumes
//      d.spec.map: s =>
//        s.template.spec.map: t =>
//          t.volumes.head.source match
//            case v: Volume.PersistentVolumeClaimRef => println(v.claimName)
//            case _ =>
//
//    //val pvc = k8s.usingNamespace(Namespace.openmole).get[PersistentVolumeClaim](s"pvc-${uuid.value}").await
//
//    k8s.usingNamespace(Namespace.openmole).delete(pvcName).await
//    k8s.usingNamespace(Namespace.openmole).update(newPVC).await //createPersistentVolumeClaim(s"pvc-${uuid.value}", newStorage, storageClassName))

  def deleteOpenMOLE(uuid: UUID): Unit =
    //k8s.usingNamespace(Namespace.openmole).deleteAllSelected[PodList](LabelSelector.IsEqualRequirement("podName",uuid.value))
    withK8s: k8s =>
      getPVCName(uuid).foreach: name =>
        k8s.usingNamespace(Namespace.openmole).delete[PersistentVolumeClaim](name).await

      val deleteOptions = DeleteOptions(propagationPolicy = Some(DeletePropagation.Foreground))
      k8s.usingNamespace(Namespace.openmole).deleteWithOptions[Deployment](uuid.value, deleteOptions).await


  //  def deployIfNotDeployedYet(k8sService: K8sService, uuid: UUID, omVersion: String, storage: String) =
  //    if !deploymentExists(uuid) then deployOpenMOLE(k8sService, uuid, omVersion, storage)

  private def podInfo(uuid: UUID, podList: List[PodInfo]): Option[PodInfo] =
    podList.find { _.name.contains(uuid.value) }

  // This method was kept to test the LabelSelector method. Is works but requires more API requests => longer
  def podInfo(uuid: UUID): Option[PodInfo] =
    withK8s: k8s =>
      val pods = k8s.usingNamespace(Namespace.openmole).listSelected[PodList](LabelSelector.IsEqualRequirement("podName", uuid.value))
      pods.map { list => list.items.map(toPodInfo).headOption }.await

  //  def isServiceUp(uuid: UUID): Boolean =
  //    podInfo(uuid).flatMap { _.status.contains() }.isDefined

  def usedSpace(uuid: UUID): Option[Double] =
    import io.kubernetes.client.openapi.*
    import io.kubernetes.client.*
    import io.kubernetes.client.util.*

    val podName = podInfo(uuid).map(_.name)

    podName.flatMap: name =>
      val client = ClientBuilder.cluster().build()
      val exec = new Exec(client)
      val builder = exec.newExecutionBuilder(Namespace.openmole, name, Array("df", "-a"))
      builder.setStdout(true)

      scala.util.Try(builder.execute()).toOption.flatMap: proc =>
        val result =
          scala.io.Source.fromInputStream(proc.getInputStream).getLines().find(_.endsWith("/var/openmole")).map: l =>
            val octets = l.replaceAll("  *", " ").split(' ')(2).toDouble
            octets / (1024 * 1024)

        if proc.waitFor() == 0 then result else None

  def deploymentExists(uuid: UUID) = podInfo(uuid).isDefined

  def podInfos: Seq[PodInfo] =
    val pods = listPods
    for
      uuid <- DB.users.map(_.uuid)
      podInfo <- podInfo(uuid, pods)
    yield podInfo

  /*def migratePV(source: String, destination: String) =
    withK8s: k8s =>
      import skuber.batch.*
      import skuber.json.batch.format.*

      val migrate = Job(
        metadata = ObjectMeta(name = s"migrate-pv-$source"),
        spec = Some:
          Job.Spec(
            template = Some:
              Pod.Template.Spec(
                spec = Some:
                  Pod.Spec(
                    containers = List:
                      Container(
                        name = "migrate",
                        image = "debian",
                        command = List("/bin/bash", "-c"),
                        args = List("ls -lah /src_vol /dst_vol && df -h && cp -a /src_vol/. /dst_vol/ && ls -lah /dst_vol/ && du -shxc /src_vol/ /dst_vol/"),
                        volumeMounts = List(
                          Volume.Mount(
                            mountPath = "/src_vol",
                            name = "src",
                            readOnly = true
                          ),
                          Volume.Mount(
                            mountPath = "/dst_vol",
                            name = "dst"
                          )
                        )
                      ),
                    restartPolicy = RestartPolicy.Never,
                    volumes = List(
                      Volume(name = "src", source = Volume.PersistentVolumeClaimRef(claimName = source)),
                      Volume(name = "dst", source = Volume.PersistentVolumeClaimRef(claimName = destination))
                    )
                  )
                ),
            backoffLimit = Some(1)
          )

      )

      k8s.usingNamespace(Namespace.openmole).create(migrate)
  */


case class K8sService(storageClassName: Option[String])
