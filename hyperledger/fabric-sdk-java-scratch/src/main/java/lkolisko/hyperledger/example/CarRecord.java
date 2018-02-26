package lkolisko.hyperledger.example;

import javax.json.JsonObject;

/**
 * <h1>CarRecord</h1>
 * <p>
 * Value object holding the card record
 */
public class CarRecord {

    private String key;
    private String colour;
    private String make;
    private String model;
    private String owner;

    public CarRecord() {
        this(null, null, null, null, null);
    }

    public CarRecord(String colour, String make, String model, String owner) {
        this(null, colour, make, model, owner);
    }

    public CarRecord(String key, String colour, String make, String model, String owner) {
        this.key = key;
        this.colour = colour;
        this.make = make;
        this.model = model;
        this.owner = owner;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getColour() {
        return colour;
    }

    public void setColour(String colour) {
        this.colour = colour;
    }

    public String getMake() {
        return make;
    }

    public void setMake(String make) {
        this.make = make;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public static CarRecord fromJsonObject(JsonObject json) {
        return new CarRecord(
                json.getString("colour"), json.getString("make"),
                json.getString("model"), json.getString("owner")
        );
    }

    @Override
    public String toString() {
        return "CarRecord {" +
                "\n\tkey='" + key + '\'' +
                "\n\t, colour='" + colour + '\'' +
                "\n\t, make='" + make + '\'' +
                "\n\t, model='" + model + '\'' +
                "\n\t, owner='" + owner + '\'' +
                "\n}";
    }
}
