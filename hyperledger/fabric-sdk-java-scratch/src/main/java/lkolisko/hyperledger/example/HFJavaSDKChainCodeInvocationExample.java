package lkolisko.hyperledger.example;

import org.apache.log4j.Logger;
import org.hyperledger.fabric.sdk.BlockEvent;
import org.hyperledger.fabric.sdk.ChaincodeID;
import org.hyperledger.fabric.sdk.ChaincodeResponse;
import org.hyperledger.fabric.sdk.Channel;
import org.hyperledger.fabric.sdk.HFClient;
import org.hyperledger.fabric.sdk.ProposalResponse;
import org.hyperledger.fabric.sdk.QueryByChaincodeRequest;
import org.hyperledger.fabric.sdk.SDKUtils;
import org.hyperledger.fabric.sdk.TransactionProposalRequest;
import org.hyperledger.fabric.sdk.exception.InvalidArgumentException;
import org.hyperledger.fabric.sdk.exception.ProposalException;
import org.hyperledger.fabric_ca.sdk.HFCAClient;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static lkolisko.hyperledger.example.HFJavaSDKBasicExample.getAdmin;
import static lkolisko.hyperledger.example.HFJavaSDKBasicExample.getChannel;
import static lkolisko.hyperledger.example.HFJavaSDKBasicExample.getHfCaClient;
import static lkolisko.hyperledger.example.HFJavaSDKBasicExample.getHfClient;

public class HFJavaSDKChainCodeInvocationExample {

    private static final Logger log = Logger.getLogger(HFJavaSDKChainCodeInvocationExample.class);

    public static void main(String... args) throws Exception {
        HFCAClient caClient =
                getHfCaClient("http://localhost:7054", null);
        AppUser user = getAdmin(caClient);
        HFClient client = getHfClient();
        client.setUserContext(user);
        Channel channel = getChannel(client);
        queryChainCode(client, channel);
        invokeChainCode(client, channel);
    }

    static void queryChainCode(HFClient client, Channel channel) throws InvalidArgumentException, ProposalException {
        QueryByChaincodeRequest qpr = client.newQueryProposalRequest();
        ChaincodeID chainCodeId = ChaincodeID.newBuilder().setName("fabcar").build();
        qpr.setChaincodeID(chainCodeId);
        qpr.setFcn("queryCar");
        qpr.setArgs(new String[]{"CAR0"});

        Collection<ProposalResponse> queryProposals = channel.queryByChaincode(qpr);
        for (ProposalResponse response : queryProposals) {
            if (response.isVerified() && response.getStatus() == ChaincodeResponse.Status.SUCCESS) {
                String payload = response.getProposalResponse().getResponse().getPayload().toStringUtf8();
                log.info("'" + payload + "'");
            } else {
                throw new RuntimeException("response failed. status: " + response.getStatus());
            }
        }
    }

    static void invokeChainCode(HFClient client, Channel channel) throws ProposalException, InvalidArgumentException, InterruptedException, ExecutionException, TimeoutException {
        BlockEvent.TransactionEvent event = sendTransaction(client, channel).get(60, TimeUnit.SECONDS);
        if (event.isValid()) {
            log.info("Transacion tx: " + event.getTransactionID() + " is completed.");
        }

    }

    static CompletableFuture<BlockEvent.TransactionEvent> sendTransaction(HFClient client, Channel channel) throws InvalidArgumentException, ProposalException {
        TransactionProposalRequest tpr = client.newTransactionProposalRequest();
        ChaincodeID cid = ChaincodeID.newBuilder().setName("fabcar").build();
        tpr.setChaincodeID(cid);
        tpr.setFcn("createCar");
        tpr.setArgs(new String[]{"CAR12", "Skoda", "MB1000", "Yellow", "Lukas"});
        Collection<ProposalResponse> resp = channel.sendTransactionProposal(tpr);
        Set<ProposalResponse> invalid = new HashSet<>();
        Collection<Set<ProposalResponse>> sets = SDKUtils.getProposalConsistencySets(resp);
        if (sets.size() > 1) {
            throw new RuntimeException("invalid response: " + invalid.iterator().next());
        }

        return channel.sendTransaction(resp);
    }
}
