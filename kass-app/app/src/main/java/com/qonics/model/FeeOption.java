package com.qonics.model;

/** Fee line option for payment spinner (mirrors web get_fees rows). */
public class FeeOption {
    public final int feesId;
    public final int feesType; // 0 school, 1 extra
    public final String title;
    public final int expected;
    public final int paid;
    public final int balance;

    public FeeOption(int feesId, int feesType, String title, int expected, int paid, int balance) {
        this.feesId = feesId;
        this.feesType = feesType;
        this.title = title;
        this.expected = expected;
        this.paid = paid;
        this.balance = balance;
    }

    @Override
    public String toString() {
        return title + " — remain " + String.format("%,d", balance);
    }
}
