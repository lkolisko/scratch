
# Hyperledger fabric-sdk-java Basics Tutorial

This quick tutorial is for all Java developers, who started to look into Hyperledger Fabric platform (https://hyperledger.org/) and would like to use fabric-sdk-java for their projects.

When learning a new technology I always try to search for some minimal working example I can get up and running in my environment. This is a starting point for me to further play around with APIs, debug and get better handle of the framework.

There is a lot of good documentation and examples available on Hyperledger sites ( [http://hyperledger-fabric.readthedocs.io](http://hyperledger-fabric.readthedocs.io) and [https://github.com/hyperledger/fabric-samples.git](https://github.com/hyperledger/fabric-samples.git)). What I have been missing is some minimal working project for Java developers using fabric-sdk-java. This tutorial tries to serve this purpose.

This tutorial assumes you are familiar with basics of Hyperledger fabric. If not, then I suggest you to look around and especially go through [http://hyperledger-fabric.readthedocs.io/en/release/build_network.html](http://hyperledger-fabric.readthedocs.io/en/release/build_network.html) and [http://hyperledger-fabric.readthedocs.io/en/release/write_first_app.html](http://hyperledger-fabric.readthedocs.io/en/release/write_first_app.html) .

If you were successfully able to get through these two tutorials, then we are ready to have a look how to use fabric-sdk-java to do the same. We will use the fabcar example from [https://github.com/hyperledger/fabric-samples.git](https://github.com/hyperledger/fabric-samples.git) to setup the network.

### Tips:

1. fabric-samples/scripts/fabric-preload.sh script will download the docker images and tag them for you.

1. fabric-samples/fabcar/startFabric.sh can be used to start the fabcar network

1. In case you are experiencing issues you might want to do some docker cleanup

```
    # !!! THIS WILL REMOVE ALL YOUR DOCKER CONTAINERS AND IMAGES !!!
    # remove all containers
    $ docker rm $(docker ps -qa)
    # remove all mages
    $ docker rmi --force $(docker images -qa)
    # prune networks
    $ docker network prune
```

Enough talking and let’s get something up and running….

Clone the example code from here into demo directory

    git clone [https://github.com/lkolisko/scratch.git](https://github.com/lkolisko/scratch.git) tutorial

and navigate to tutorial/hyperledger/fabric-sdk-java-scratch.

The project itself has just 4 three important files.

1. **pom.xml** —maven build file including dependency to fabric-sdk-java artifact. org.hyperledger.fabric-sdk-java: fabric-sdk-java:1.0.1 . There might be a new version available at the time you are reading this. You must ensure you fabric-sdk-java version is the same release series as the images used in fabric-samples. Otherwise you might run into incompatibility issues at protobuf leves or APIs.

1. **src/main/java/lkolisko/hyperledger/example/AppUser.java **— this is minimal implementation of the User interface. The sdk itself does not provide implementation, therefore we must do here ourselves.

1. **src/main/java/lkolisko/hyperledger/example/HFJavaSDKBasicExample.java** — this is the main class and will further talk about details bellow.

1. **src/main/resources/log4j.xml** — log4j configuration. I highly suggest to set the root logger to debug. You will be able to see all the information about communication between client the other components of the fabric network.

## Enrolling admin

We need to create fabric-ca client to be able to register and enroll users. To be precise it might not be necessary, as you already enrolled admin and user using fabric-ca-client cli or following he fabcar example (the crypto material is available in fabric-samples/fabcar/hfc-key-store and can be loaded) . But lets start from scratch and learn how to do that in Java.

    CryptoSuite cryptoSuite = CryptoSuite.Factory.getCryptoSuite();
    HFCAClient caClient = HFCAClient.createNewInstance(“grpc://localhost:7054”, null);
    caClient.setCryptoSuite(cryptoSuite);

The grpc://localhost:7054 is the endpoint where fabric-ca server listens. You can check that running docker ps . The properties can be left empty or null for now.

## Enroll Admin

To communicate with fabric components we have to have key pair and a certificate signed by the fabric-ca. We can either use the crypto material generated in fabric-samples/fabcar/hfc-key-store. For the purpose of learning the API we will let the fabric-ca client generate the crypto material for us.

    Enrollment adminEnrollment = caClient.enroll("admin", "adminpw");
    AppUser admin = new AppUser("admin","org1", "Org1MSP", adminEnrollment);

The ca client will generate the key pair and CSR (certificate signature request) using the CryptoSuite and send it to the fabric-ca.

fabri-ca will

1. authenticate admin using basic authentication

1. perform verification e.g. check if max enrollment count for the account has not been reached

1. sign the certificate

1. store the certificate (along with its serial number, aki and other information)

1. send it back to client

The Enrollment object contains private key and the certificate. The sample stores the object using Java Serialization. This is definitely a bad practice and is used just for the purpose of keeping the example simple.

## Register and Enroll user

In the step two we are going to register a new user and enroll the user.

    RegistrationRequest rr = new RegistrationRequest("hfuser", "org1");
    String userSecret = caClient.register(rr, registrar);
    Enrollment userEnrollment = caClient.enroll("hfuser", userSecret);
    AppUser appUser = new AppUser("hfuser", "org1","Org1MSP", userEnrollment);

To do that RegistrationRequest object with userId and affiliation has to be created. To understand what affiliation means, please refer here [http://hyperledger-fabric-ca.readthedocs.io/en/latest/users-guide.html](http://hyperledger-fabric-ca.readthedocs.io/en/latest/users-guide.html) .

With the RegistrationRequest we will call fabri-ca using registrar (admin) . The fabric-ca will answer with secret (password) we will use for enrollment of the user. This is the same as we did for the admin in the previous step.

At this moment we have admin — private key and signed certificate and user with private key and signed certificate. This opens as the door to talk to fabric itself.

## Initialize HF Client

We will create client instance using HFClient factory and set the default crypto suite. Next we set use context. This will be the account under which we are going to talk to Hyperledger Fabric and its private key will be used to sign the request.

    CryptoSuite cryptoSuite = CryptoSuite.Factory.getCryptoSuite();
    HFClient client = HFClient.createNewInstance();
    client.setCryptoSuite(cryptoSuite);
    client.setUserContext(appUser);

## Initialize Channel object

The interesting stuff happens in channel. Therefore we have to get the Channel object. For this we need peer and orderer. The orderer is listening on 7050 and peer on 7051 ports of the localhost. We are not using TLS now so set the grpc protol. The orderer and peer names do not have to match the fqdn you see in the docker ps. The channel name for the sample is mychannel. After channel.initialize() we are ready to go.

    Peer peer = client.newPeer("peer0", "grpc://localhost:7051");
    Orderer orderer = client.newOrderer("orderer", "grpc://localhost:7050");
    Channel channel = client.newChannel("mychannel");
    channel.addPeer(peer);
    channel.addOrderer(orderer);
    channel.initialize();

## Invoking chain code

We will invoke simple query on the fabric chain code. To do so QueryByChaincodeRequest has to be set with chain code id fabcar and function we would like to invoke queryAllCars. Potentially you would like to pass arguments using setArgs and version using setVersion.

    QueryByChaincodeRequest qpr = client.newQueryProposalRequest();

    ChaincodeID fabcarCCId = ChaincodeID.newBuilder().setName("fabcar").build();
    qpr.setChaincodeID(fabcarCCId);
    qpr.setFcn("queryAllCars");

    Collection<ProposalResponse> res = channel.queryByChaincode(qpr);
    for (ProposalResponse pres : res) {
        String stringResponse = new    String(pres.getChaincodeActionResponsePayload());
        log.info(stringResponse);
    }

Once we are set, let submit the query request and enjoy the response. The response is protobuf backed object, therefore we just rely on simple toString here for simplicity.

Please do not consider the code above as a best practice to follow. We are ignoring handling invalid responses, exceptions, storing credentials using serialization and dozen of other bad practices. The purpose of the sample is to get something working with minimal effort and get you on track.

To study further I highly recommend checking

* [https://github.com/hyperledger/fabric-sdk-java/blob/master/src/test/java/org/hyperledger/fabric/sdkintegration/End2endIT.java](https://github.com/hyperledger/fabric-sdk-java/blob/master/src/test/java/org/hyperledger/fabric/sdkintegration/End2endIT.java)

* [https://github.com/hyperledger/fabric-sdk-java/blob/master/src/test/java/org/hyperledger/fabric/sdkintegration/NetworkConfigIT.java](https://github.com/hyperledger/fabric-sdk-java/blob/master/src/test/java/org/hyperledger/fabric/sdkintegration/NetworkConfigIT.java) .

Happy coding !

*— Lukas*
