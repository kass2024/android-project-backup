package com.qonics.model;

import java.util.List;
import java.util.Vector;

public class FeeCartHelper {
    public static final String FEE_INDEX = "FEE_INDEX";

    private static List<FeeCart> cart;



    public static List<FeeCart> getCart() {
        if(cart == null) {
            cart = new Vector<FeeCart>();
        }

        return cart;
    }
}
