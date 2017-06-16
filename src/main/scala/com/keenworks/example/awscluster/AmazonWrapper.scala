package com.keenworks.example.awscluster

import java.io.{BufferedReader, InputStreamReader}
import java.net.URL

import com.amazonaws.auth.InstanceProfileCredentialsProvider
import com.amazonaws.regions.Regions
import com.amazonaws.services.autoscaling.AmazonAutoScalingClient
import com.amazonaws.services.autoscaling.model.{DescribeAutoScalingGroupsRequest, DescribeAutoScalingInstancesRequest}
import com.amazonaws.services.cloudformation.AmazonCloudFormationClient
import com.amazonaws.services.ec2.AmazonEC2Client
import com.amazonaws.services.ec2.model.{DescribeInstancesRequest, Instance, InstanceStateName}

import scala.collection.JavaConversions._

class AmazonWrapper {
    private val credentials = new InstanceProfileCredentialsProvider
    private val region = Regions.getCurrentRegion
    private val cfClient = new AmazonCloudFormationClient(credentials) { setRegion(region) }
    private val scalingClient = new AmazonAutoScalingClient(credentials) { setRegion(region) }
    private val ec2Client = new AmazonEC2Client(credentials) { setRegion(region) }

    /**
      * Cloudformation stack status has many possible values:
      *     CREATE_IN_PROGRESS | CREATE_FAILED | CREATE_COMPLETE |
      *     ROLLBACK_IN_PROGRESS | ROLLBACK_FAILED | ROLLBACK_COMPLETE |
      *     DELETE_IN_PROGRESS | DELETE_FAILED | DELETE_COMPLETE |
      *     UPDATE_IN_PROGRESS | UPDATE_COMPLETE_CLEANUP_IN_PROGRESS | UPDATE_COMPLETE |
      *     UPDATE_ROLLBACK_IN_PROGRESS | UPDATE_ROLLBACK_FAILED |
      *     UPDATE_ROLLBACK_COMPLETE_CLEANUP_IN_PROGRESS | UPDATE_ROLLBACK_COMPLETE
      * @return the cloudformation stack's current status
      */
    def getStackStatus: String = {
        cfClient.describeStacks().getStacks.head.getStackStatus
    }

    /**
      * Every AWS instance knows its own instance id, from its local instance data
      * @return the alphanumeric instance ID for this instance, issued by AWS
      */
    def getCurrentInstanceId: String = {
        val conn = new URL("http://169.254.169.254/latest/meta-data/instance-id").openConnection
        val in = new BufferedReader(new InputStreamReader(conn.getInputStream))
        try in.readLine() finally in.close()
    }

    /**
      * This instance is by definition being launched as part of an autoscaling group, so this
      *     gets the name of that group
      * @param instanceId the instance ID for this instance
      * @return the name of the autoscaling group
      */
    def getGroupName(instanceId: String): String = {
        val scalingRequest = new DescribeAutoScalingInstancesRequest {
            setInstanceIds(instanceId :: Nil)
        }
        val scalingResult = scalingClient describeAutoScalingInstances scalingRequest
        scalingResult.getAutoScalingInstances.head.getAutoScalingGroupName
    }

    /**
      * Given an autoscaling group name, this returns the full list of instances in that
      *     autoscaling group
      * @param groupName the autoscaling group name
      * @return the list of instanceIds in this ASG
      */
    def getGroupInstances(groupName: String): List[String] = {
        val groupInstancesRequest = new DescribeAutoScalingGroupsRequest {
            setAutoScalingGroupNames(groupName :: Nil)
        }
        val groupInstanceResponse = scalingClient describeAutoScalingGroups groupInstancesRequest
        groupInstanceResponse.getAutoScalingGroups.head.getInstances.toList map (_.getInstanceId)
    }

    /**
      * Given a list of instances, get the ip address for each instance
      * @param groupInstances the list of instances in this ASG
      * @return the list of IPs of the instances in this ASG
      */
    def getIpsFromInstances(groupInstances: List[String]): List[String] = {
        groupInstances map instanceFromId collect {
            case instance if isRunning(instance) => instance.getPrivateIpAddress
        }
    }

    private val isRunning: Instance => Boolean = _.getState.getName == InstanceStateName.Running.toString

    private def instanceFromId(id: String): Instance = {
        val result = ec2Client describeInstances new DescribeInstancesRequest {
            setInstanceIds(id :: Nil)
        }
        result.getReservations.head.getInstances.head
    }
}
