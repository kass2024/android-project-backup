package com.qonics.model;

public class OfflineCourseModel {
    int id;
    int course_id;
    int mark_type_id;
    String name;
    String cat;
    double marks;

    public int getCourse_id() {
        return course_id;
    }

    public void setCourse_id(int course_id) {
        this.course_id = course_id;
    }

    public int getMark_type_id() {
        return mark_type_id;
    }

    public void setMark_type_id(int mark_type_id) {
        this.mark_type_id = mark_type_id;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCat() {
        return cat;
    }

    public void setCat(String cat) {
        this.cat = cat;
    }

    public double getMarks() {
        return marks;
    }

    public void setMarks(double marks) {
        this.marks = marks;
    }
}
