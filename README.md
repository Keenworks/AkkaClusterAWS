
This is an example of how an Akka Cluster can be set up using 
the AWS SDK. This code sample is not designed to run "out of 
the box", although the tests should pass.

Some of this was cobbled together from other examples online.

For an Akka Cluster to start, an appropriate config needs to be
passed to the ActorSystem:

    val cloudConfig: Config = Await.result(CloudConfig().getCloudConfig, 10.minutes)
    override val actorSystem = ActorSystem("MySystem", cloudConfig)

When using AWS, the address of your seed nodes are only discoverable
at runtime.

This sample assumes you are starting with a small cluster where
every instance in the initial ASG is also a seed node, but the 
technique can be adapted to other use cases.

Seed nodes are discovered by:

- querying CloudFormation continually until the stack is completed building
- getting the instance and group of the running ec2 instance
- looking up the other instances in the group, and their ips
- sorting the ips
- generating the right "seed node" config syntax for each ip
- generating the full config block 
- generating a config object

Be sure to review the section on 
[joining seed nodes](http://doc.akka.io/docs/akka/2.4/java/cluster-usage.html#Joining_to_Seed_Nodes) - 
it is important to sort the ips so that you don't get split-brain.

Curt Siffert, curt@keenworks.com
