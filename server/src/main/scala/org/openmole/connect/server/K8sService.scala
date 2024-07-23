package org.openmole.connect.server

import io.kubernetes.client.openapi.ApiClient
import io.kubernetes.client.openapi.models.V1JobStatus
import io.kubernetes.client.openapi.apis.{ApiextensionsV1Api, CoreV1Api}
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaim
import org.openmole.connect.shared.{Data, Storage}
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
import monocle.macros.*
import monocle.syntax.all.*
import org.apache.pekko.actor.ActorSystem
import org.openmole.connect.server.db.DB
import org.openmole.connect.server.db.DB.UUID
import tool.*

object K8sService:

  object KubeCache:
    type Cached = String

    import com.google.common.cache.*
    import java.util.concurrent.TimeUnit

    def apply(): KubeCache =
      val ipCache = tool.cache[DB.UUID, Cached]()

      KubeCache(ipCache)

  case class KubeCache(ipCache: com.google.common.cache.Cache[DB.UUID, KubeCache.Cached])


  object Namespace:
    val openmole = "openmole"

  val userLabel = "user"
  //val connect = "connect"

  //  object Ceph:
  //    val storageClassName = "rook-ceph-block"

  def toPodInfo(pod: Pod) =

    val containerStatus = pod.status.flatMap(_.containerStatuses.headOption)
    val deletionTimeStamp = pod.metadata.deletionTimestamp

    val st =
      deletionTimeStamp match
        case Some(timestamp) => Some(Data.PodInfo.Status.Terminating)
        case None =>
          containerStatus.flatMap(_.state).map:
            case w: Container.Waiting => Data.PodInfo.Status.Waiting(w.reason.getOrElse(""))
            case r: Container.Running => Data.PodInfo.Status.Running(r.startedAt.map(_.toEpochSecond).getOrElse(0L))
            case t: Container.Terminated => Data.PodInfo.Status.Terminated(t.message.getOrElse(""), t.finishedAt.map { _.toEpochSecond }.getOrElse(0L))
          .orElse:
            pod.metadata.creationTimestamp match
              case Some(timestamp) => Some(Data.PodInfo.Status.Creating)
              case None => None


    // Using the added metadata label to get the podName (if any) instead of the actual podName which is now generated by the deployment
    // Should we rename it deploymentName?
    PodInfo(
      pod.name,
      st,
      containerStatus.map(_.restartCount),
      pod.metadata.creationTimestamp.map(_.toEpochSecond),
      pod.status.flatMap(_.podIP),
      pod.metadata.labels.get("podName")
    )

  type KubeAction[+T] = KubernetesClient => T

  def withK8s[T](kubeAction: KubeAction[T])(using K8sService) =
    val k8s = k8sInit(summon[K8sService].system)
    try kubeAction(k8s)
    finally k8s.close

  def getIngress(using K8sService) = withK8s: k8s =>
    val allIngressMapFut = k8s.listInNamespace[IngressList]("ingress-nginx")
    val allIngressFuture = allIngressMapFut.map { allIngressMap => allIngressMap.items }

    def listIngress(ingresses: List[Ingress]) = ingresses.headOption

    allIngressFuture.map { ingresses => listIngress(ingresses) }.await

  def ingressIP(using K8sService): Option[String] =
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
      command = List("bin/bash", "-c", s"openmole-docker --port 80 --remote --mem ${openMOLEMemory}m --workspace /var/openmole/.openmole"),
      volumeMounts = List(Volume.Mount(name = "data", mountPath = "/var/openmole/")),
      securityContext = Some(SecurityContext(privileged = Some(true))),
      imagePullPolicy = Some(Container.PullPolicy.Always),
      resources = Some(Resource.Requirements(limits = limits))
    ).exposePort(80)

  def persistentVolumeClaim(pvcName: String, uuid: DB.UUID, storage: Int, storageClassName: Option[String]) =
    def metadata(name: String) = ObjectMeta(name, namespace = Namespace.openmole, labels = Map(userLabel -> uuid))

    PersistentVolumeClaim(
      metadata = metadata(pvcName),
      spec =
        Some(
          PersistentVolumeClaim.Spec(
            //volumeName = Some(pvcName),
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

  def deployOpenMOLE(uuid: DB.UUID, omVersion: String, openMOLEMemory: Int, memoryLimit: Int, cpuLimit: Double)(using KubeCache, K8sService) =
    val k8sService = summon[K8sService]
    summon[KubeCache].ipCache.invalidate(uuid)
    withK8s: k8s =>
      val podName = uuid.value
      val pvcName = s"pvc-$podName"
      //val pvName = s"pv-${podName}"

      val pvc = persistentVolumeClaim(pvcName, uuid, k8sService.storageSize, k8sService.storageClassName)

      val openMOLESelector: LabelSelector = LabelSelector.IsEqualRequirement("app", "openmole")
      val openMOLEContainer = createOpenMOLEContainer(omVersion, openMOLEMemory, memoryLimit, cpuLimit)

      val openMOLELabel = "app" -> "openmole"

      // Adding a metadata label to store the deloyment name (called podName for historical reasons)
      // The actual podName is now generated by the deployment
      // Should we rename it deploymentName?
      val openMOLETemplate = Pod.Template.Spec
        .named("openmole")
        .withPodSpec(Pod.Spec(terminationGracePeriodSeconds = Some(60)))
        .addContainer(openMOLEContainer)
        .addVolume(Volume(name = "data", source = Volume.PersistentVolumeClaimRef(claimName = pvcName)))
        .addLabel(openMOLELabel)
        .addLabel("podName" -> podName)
        .addLabel("user" -> uuid)


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


  def launch(user: DB.User)(using KubeCache, K8sService): Unit =
    if K8sService.podExists(user.uuid)
    then
      K8sService.updateOpenMOLEPod(user.uuid, user.omVersion, user.openMOLEMemory, user.memory, user.cpu)
      K8sService.startOpenMOLEPod(user.uuid)
    else
      DB.userFromUUID(user.uuid).foreach: user =>
        K8sService.deployOpenMOLE(user.uuid, user.omVersion, user.openMOLEMemory, user.memory, user.cpu)

  def stopOpenMOLEPod(uuid: DB.UUID)(using KubeCache, K8sService) =
    withK8s: k8s =>
      k8s.usingNamespace(Namespace.openmole).get[Deployment](uuid.value).map: d =>
        k8s.usingNamespace(Namespace.openmole) update d.withReplicas(0)
      .await
    summon[KubeCache].ipCache.invalidate(uuid)

  def startOpenMOLEPod(uuid: DB.UUID)(using K8sService) = withK8s: k8s =>
    k8s.usingNamespace(Namespace.openmole).get[Deployment](uuid.value).map: d =>
      k8s.usingNamespace(Namespace.openmole) update d.withReplicas(1)
    .await

  def updateOpenMOLEPod(uuid: DB.UUID, newVersion: String, openmoleMemory: Int, memoryLimit: Int, cpuLimit: Double)(using KubeCache, K8sService) =
    withK8s: k8s =>
      k8s.usingNamespace(Namespace.openmole).get[Deployment](uuid.value).map: d =>
        val container = createOpenMOLEContainer(newVersion, openmoleMemory, memoryLimit, cpuLimit)
        k8s.usingNamespace(Namespace.openmole) update d.updateContainer(container)
      .await
    summon[KubeCache].ipCache.invalidate(uuid)


  def getPVCName(uuid: DB.UUID)(using K8sService): Option[String] =
    withK8s: k8s =>
      val deployment = k8s.usingNamespace(Namespace.openmole).get[Deployment](uuid.value).await
      deployment.focus(_.spec.some.template.spec.some.volumes.index(0).source).getOption match
          case Some(v: Volume.PersistentVolumeClaimRef) => Some(v.claimName)
          case _ => None

  private def getPVC(uuid: DB.UUID)(using K8sService) = withK8s: k8s =>
    k8s.listSelected[PersistentVolumeClaimList](LabelSelector.IsEqualRequirement(userLabel, uuid.value), namespace = Some(Namespace.openmole)).await.headOption

  def updateOpenMOLEPersistentVolumeStorage(uuid: DB.UUID, size: Int)(using K8sService) =
    withK8s: k8s =>
      val newPVC =
        getPVC(uuid).focus(_.some.spec.some.resources.some).set:
          Resource.Requirements(
            requests = Map(Resource.storage -> s"${size}Gi")
          )
      newPVC.foreach(np => k8s.update(np, namespace = Some(Namespace.openmole)).await)

  def getPVCSize(uuid: DB.UUID)(using K8sService) =
    getPVC(uuid).flatMap(_.spec).flatMap(_.resources).flatMap(_.requests.get(Resource.storage)).map(_.number.toInt)

  def deleteOpenMOLE(uuid: DB.UUID)(using KubeCache, K8sService): Unit =
    //k8s.usingNamespace(Namespace.openmole).deleteAllSelected[PodList](LabelSelector.IsEqualRequirement("podName",uuid.value))

    summon[KubeCache].ipCache.invalidate(uuid)
    withK8s: k8s =>
      getPVCName(uuid).foreach: name =>
        k8s.usingNamespace(Namespace.openmole).delete[PersistentVolumeClaim](name)

      val deleteOptions = DeleteOptions(propagationPolicy = Some(DeletePropagation.Foreground))
      k8s.usingNamespace(Namespace.openmole).deleteWithOptions[Deployment](uuid.value, deleteOptions)

  private def podInfo(uuid: DB.UUID, podList: List[PodInfo]): Option[PodInfo] =
    podList.find { _.name.contains(uuid.value) }

  // This method was kept to test the LabelSelector method. Is works but requires more API requests => longer
  def podInfo(uuid: DB.UUID)(using K8sService): Option[PodInfo] =
    withK8s: k8s =>
      // FIXME switch to user label at some point
      val pods = k8s.usingNamespace(Namespace.openmole).listSelected[PodList](LabelSelector.IsEqualRequirement("podName", uuid.value))
      pods.map { list => list.items.map(toPodInfo).headOption }.await

  def listPods(using K8sService) = withK8s: k8s =>
    val allPodsMapFut = k8s.list[PodList](Some(Namespace.openmole)) //k8s.listSelected[PodList](LabelSelector.IsEqualRequirement("app","openmole"))
    val allPodsFuture = allPodsMapFut.map(_.items)
    allPodsFuture.map { _.map(toPodInfo) }.await


  def podIP(uuid: DB.UUID)(using KubeCache, K8sService) =
    def ip(uuid: DB.UUID) = podInfo(uuid).flatMap(_.podIP)
    summon[KubeCache].ipCache.getOptional(uuid, ip)


  def usedSpace(uuid: DB.UUID)(using K8sService): Option[Storage] =
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
            val fields = l.replaceAll("  *", " ").split(' ')
            val used = fields(2).toDouble
            val free = fields(3).toDouble
            Storage(
              used / 1024,
              free / 1024
            )

        if proc.waitFor() == 0 then result else None

  def podExists(uuid: DB.UUID)(using K8sService) = podInfo(uuid).isDefined

  def podInfos(using K8sService): Seq[PodInfo] =
    val pods = listPods
    for
      uuid <- DB.users.map(_.uuid)
      podInfo <- podInfo(uuid, pods)
    yield podInfo


case class K8sService(storageClassName: Option[String], storageSize: Int, system: ActorSystem = ActorSystem())
