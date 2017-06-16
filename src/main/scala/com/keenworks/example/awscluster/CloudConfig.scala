package com.keenworks.example.awscluster

import com.typesafe.config.{Config, ConfigFactory, ConfigValueFactory}

import scala.collection.JavaConversions._
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class CloudConfig(cloudFormationStack: CloudFormationStack) {

    def getCloudConfig: Future[Config] = {
        def getSeeds(siblings: List[String]): List[String] = {
            siblings map (ip => s"akka.tcp://ClusterContentActorSystem@$ip:2552")
        }
        def getSeedConfig(seeds: List[String]): Config = {
            ConfigFactory.empty().withValue("akka.cluster.seed-nodes", ConfigValueFactory.fromIterable(seeds))
        }

        // https://gist.github.com/gclaramunt/5892633 - assumes ipv4
        // Note: if new ASG-added ip is first in the list, it's ok - it will still try to join existing cluster first
        def sortIps(ips: List[String]): List[String] = {
            ips.map(_.split('.').map(_.toInt)).
                map( { case Array(x1,x2,x3,x4) => (x1,x2,x3,x4) } ).sorted.
                map { case (x1,x2,x3,x4) => "%s.%s.%s.%s".format(x1,x2,x3,x4)}
        }

        val defaultConfig = ConfigFactory.load()
        val staticClusterConfig: Config = defaultConfig.getConfig("cluster") withFallback defaultConfig

        val thisConfig: Future[Config] = for {
            siblings: List[String] <- cloudFormationStack.getSiblingIps
            seedlings: List[String] = getSeeds(sortIps(siblings))
            seedConfig: Config = getSeedConfig(seedlings)
            thisConfig: Config = seedConfig withFallback staticClusterConfig
        } yield thisConfig

        thisConfig onFailure {
            case t: Throwable =>
//                Logger.error("ERROR: Unable to read config; unable to start cluster: " + t.getMessage)
        }

        thisConfig
    }

}

object CloudConfig {
    val cloudConfig = new CloudConfig(CloudFormationStack())

    def apply(): CloudConfig = cloudConfig
}
