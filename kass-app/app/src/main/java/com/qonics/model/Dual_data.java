package com.qonics.model;

public class Dual_data {
    int id;
    String text1,text2;
    int studentId, feeId,feeType,paymentMode,term, createdBy;
    String paidAmount, createdAt,uuid;

    public Dual_data(int id, String text) {
        this.id = id;
        this.text1 = text;
    }
    public Dual_data(String text1, String text2) {
        this.text1 = text1;
        this.text2 = text2;
    }
    public Dual_data(int id, String text1, String text2) {
        this.id = id;
        this.text1 = text1;
        this.text2 = text2;
    }

    public Dual_data(String uuid, int studentId, int feeId, int feeType, int paymentMode, int term, int createdBy, String paidAmount, String createdAt) {
        this.uuid = uuid;
        this.studentId = studentId;
        this.feeId = feeId;
        this.feeType = feeType;
        this.paymentMode = paymentMode;
        this.term = term;
        this.createdBy = createdBy;
        this.paidAmount = paidAmount;
        this.createdAt = createdAt;
    }

    public Integer getId() {
        return id;
    }

    public String getText1() {
        return text1;
    }
    public String getText2() {
        return text2;
    }

    @Override
    public String toString() {
        return text1 != null ? text1 : "";
    }

    public String getUuid() {
        return uuid;
    }

    public int getStudentId() {
        return studentId;
    }

    public int getFeeId() {
        return feeId;
    }

    public int getFeeType() {
        return feeType;
    }

    public int getPaymentMode() {
        return paymentMode;
    }

    public int getTerm() {
        return term;
    }

    public int getCreatedBy() {
        return createdBy;
    }

    public String getPaidAmount() {
        return paidAmount;
    }

    public String getCreatedAt() {
        return createdAt;
    }


}
