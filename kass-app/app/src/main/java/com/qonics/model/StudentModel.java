package com.qonics.model;

import android.content.Context;

import com.qonics.somanet.R;

public class StudentModel {
    int id;
    int classId;
    String name,regno,classe,sex,photo,card,phone;
    int term;
    Double marks;
    public StudentModel(int id, String name, String regno,int classId, String classe, String sex, String photo,int term,String card,String phone) {
        this.id = id;
        this.name = name;
        this.regno = regno;
        this.classe = classe;
        this.classId = classId;
        this.sex = sex;
        this.photo = photo;
        this.term = term;
        this.card = card;
        this.phone = phone;
    }

    public StudentModel(int id, String name, String regno, String card) {
        this.id = id;
        this.name = name;
        this.regno = regno;
        this.card = card;
    }

    public StudentModel(int id, int classId) {
        this.id = id;
        this.classId = classId;
    }

    public int getClassId() {
        return classId;
    }

    public void setClassId(int classId) {
        this.classId = classId;
    }

    public void setId(int id) {
        this.id = id;
    }

    public void setMarks(double marks) {
        this.marks = marks;
    }

    public Double getMarks() {
        return marks;
    }

    public StudentModel(int id, String name, String regno, String classe, String photo) {
        this.id = id;
        this.name = name;
        this.regno = regno;
        this.classe = classe;
        this.photo = photo;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getRegno() {
        return regno;
    }

    public String getClasse() {
        return classe;
    }

    public String getSex() {
        return sex;
    }

    public String getPhoto() {
        return photo;
    }
    public String getPhone() {
        return phone;
    }

    public String getTerm(Context context){
        switch (term){
            case 1: return context.getString(R.string.first_term);
            case 2: return context.getString(R.string.second_term);
            case 3: return context.getString(R.string.third_term);
        }
        return term+"";
    }
    public int getTermInt(){
        return term;
    }

    public String getCard() {
        return card;
    }
}
