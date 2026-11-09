package com.qonics.model;

import android.content.Context;

import com.qonics.somanet.R;

public class MarksModel {
    int studentId;
    double marks;


    public int getStudentId() {
        return studentId;
    }

    public double getMarks() {
        return marks;
    }



    public MarksModel(int studentId, double marks) {
        this.studentId = studentId;
        this.marks = marks;
    }
}
