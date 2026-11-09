package com.qonics.model;

public class FeeCart {
    String id,feeTitle;
    int studentId;
    int feeId,feeType,paymentMode,term,createdBy;
    String createdAt,paidAmount;
    public boolean selected;

    public FeeCart(String id, int studentId, int feeId, int feeType, String feeTitle, String paidAmount, int paymentMode, int term, int createdBy, String createdAt) {
        this.id = id;
        this.studentId = studentId;
        this.feeId = feeId;
        this.feeType = feeType;
        this.feeTitle = feeTitle;
        this.paidAmount = paidAmount;
        this.paymentMode = paymentMode;
        this.term = term;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public int getStudentId() {
        return studentId;
    }

    public void setStudentId(int studentId) {
        this.studentId = studentId;
    }

    public int getFeeId() {
        return feeId;
    }

    public void setFeeId(int feeId) {
        this.feeId = feeId;
    }

    public int getFeeType() {
        return feeType;
    }

    public void setFeeType(int feeType) {
        this.feeType = feeType;
    }

    public void setFeeTitle(String feeTitle) {
        this.feeTitle = feeTitle;
    }

    public String getFeeTitle() {
        return feeTitle;
    }

    public String getPaidAmount() {
        return paidAmount;
    }

    public void setPaidAmount(String paidAmount) {
        this.paidAmount = paidAmount;
    }

    public int getPaymentMode() {
        return paymentMode;
    }

    public void setPaymentMode(int paymentMode) {
        this.paymentMode = paymentMode;
    }

    public int getTerm() {
        return term;
    }

    public void setTerm(int term) {
        this.term = term;
    }

    public int getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(int createdBy) {
        this.createdBy = createdBy;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }
}
