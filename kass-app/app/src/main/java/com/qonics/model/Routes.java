package com.qonics.model;

public class Routes {
    int id;
    String title,details;
    int price;

    public Routes(int id, String title, String details, int price) {
        this.id = id;
        this.title = title;
        this.details = details;
        this.price = price;
    }

    public int getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getDetails() {
        return details;
    }

    public int getPrice() {
        return price;
    }
}
