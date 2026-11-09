package com.qonics.model;

public class Buses {
    int id,places;
    String make,model,plate;

    public Buses(int id, String make, String model, String plate,int places) {
        this.id = id;
        this.make = make;
        this.model = model;
        this.plate = plate;
        this.places = places;
    }

    public int getId() {
        return id;
    }

    public int getPlaces() {
        return places;
    }

    public String getMake() {
        return make;
    }

    public String getModel() {
        return model;
    }

    public String getPlate() {
        return plate;
    }
}
