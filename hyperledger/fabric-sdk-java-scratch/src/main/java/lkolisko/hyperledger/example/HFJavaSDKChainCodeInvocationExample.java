package lkolisko.hyperledger.example;

import com.google.protobuf.ByteString;
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

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonReader;
import java.io.ByteArrayInputStream;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static lkolisko.hyperledger.example.HFJavaSDKBasicExample.*;

public class HFJavaSDKChainCodeInvocationExample {

    private static final Logger log = Logger.getLogger(HFJavaSDKChainCodeInvocationExample.class);

    public static void main(String... args) throws Exception {
        HFCAClient caClient =
                getHfCaClient("http://localhost:7054", null);
        AppUser user = getAdmin(caClient);
        HFClient client = getHfClient();
        client.setUserContext(user);
        Channel channel = getChannel(client);
        Map<String, CarRecord> cars = queryAllCars(client);
        log.info(cars);
        addCar(client, channel);
        CarRecord carRecord = queryCar(client, "CAR11");
        log.info(carRecord);

    }

    static CarRecord queryCar(HFClient client, String key)
            throws InvalidArgumentException, ProposalException {
        Channel channel = client.getChannel("mychannel");
        QueryByChaincodeRequest qpr = client.newQueryProposalRequest();
        ChaincodeID chainCodeId = ChaincodeID.newBuilder().setName("fabcar").build();
        qpr.setChaincodeID(chainCodeId);
        qpr.setFcn("queryCar");
        qpr.setArgs(new String[]{key});

        Collection<ProposalResponse> queryProposals = channel.queryByChaincode(qpr);
        for (ProposalResponse response : queryProposals) {
            if (response.isVerified() && response.getStatus() == ChaincodeResponse.Status.SUCCESS) {
                ByteString payload = response.getProposalResponse().getResponse().getPayload();
                log.info("'" + payload.toStringUtf8() + "'");
                try (JsonReader jsonReader = Json.createReader(new ByteArrayInputStream(payload.toByteArray()))) {
                    CarRecord carRecord = CarRecord.fromJsonObject(jsonReader.readObject());
                    carRecord.setKey(key);
                    return carRecord;
                }
            } else {
                throw new RuntimeException("response failed. status: " + response.getStatus());
            }
        }
        return null;
    }


    static Map<String, CarRecord> queryAllCars(HFClient client)
            throws ProposalException, InvalidArgumentException {
        // get channel instance from client
        Channel channel = client.getChannel("mychannel");
        // create chaincode request
        QueryByChaincodeRequest qpr = client.newQueryProposalRequest();
        // build cc id providing the chaincode name. Version is omitted here.
        ChaincodeID fabcarCCId = ChaincodeID.newBuilder().setName("fabcar").build();
        qpr.setChaincodeID(fabcarCCId);
        // CC function to be called
        qpr.setFcn("queryAllCars");
        Collection<ProposalResponse> responses = channel.queryByChaincode(qpr);
        for (ProposalResponse response : responses) {
            if (response.isVerified() && response.getStatus() == ChaincodeResponse.Status.SUCCESS) {
                ByteString payload = response.getProposalResponse().getResponse().getPayload();
                try (JsonReader jsonReader = Json.createReader(new ByteArrayInputStream(payload.toByteArray()))) {
                    // parse response
                    JsonArray arr = jsonReader.readArray();
                    Map<String, CarRecord> cars = new HashMap<>();
                    for (int i = 0; i < arr.size(); i++) {
                        JsonObject rec = arr.getJsonObject(i);
                        CarRecord carRecord = getCar(rec);
                        cars.put(carRecord.getKey(), carRecord);
                    }
                    return cars;
                }
            } else {
                log.error("response failed. status: " + response.getStatus().getStatus());
            }
        }
        return Collections.emptyMap();
    }

    static CarRecord getCar(JsonObject rec) {
        String key = rec.getString("Key");
        JsonObject carRec = rec.getJsonObject("Record");
        CarRecord carRecord = CarRecord.fromJsonObject(carRec);
        carRecord.setKey(key);
        return carRecord;
    }


    static void addCar(HFClient client, Channel channel)
            throws ProposalException, InvalidArgumentException, InterruptedException, ExecutionException, TimeoutException {
        BlockEvent.TransactionEvent event = sendTransaction(client, channel).get(60, TimeUnit.SECONDS);
        if (event.isValid()) {
            log.info("Transacion tx: " + event.getTransactionID() + " is completed.");
        }

    }

    static CompletableFuture<BlockEvent.TransactionEvent> sendTransaction(HFClient client, Channel channel)
            throws InvalidArgumentException, ProposalException {
        TransactionProposalRequest tpr = client.newTransactionProposalRequest();
        ChaincodeID cid = ChaincodeID.newBuilder().setName("fabcar").build();
        tpr.setChaincodeID(cid);
        tpr.setFcn("createCar");
        tpr.setArgs(new String[]{"CAR11", "Skoda", "MB1000", "Yellow", "Lukas"});
        Collection<ProposalResponse> resp = channel.sendTransactionProposal(tpr);
        Set<ProposalResponse> invalid = new HashSet<>();
        Collection<Set<ProposalResponse>> sets = SDKUtils.getProposalConsistencySets(resp);
        if (sets.size() > 1) {
            throw new RuntimeException("invalid response: " + invalid.iterator().next());
        }
        return channel.sendTransaction(resp);
    }
}
