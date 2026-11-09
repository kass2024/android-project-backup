package com.qonics.model;

import android.content.Context;
import android.graphics.Color;

import com.qonics.somanet.R;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;

public class BookRecord {
    int id,book_id,student_id,academic_year,term,borrow_date,return_due_date,return_date,status;
    String title,author;
    DateFormat df = new SimpleDateFormat("dd-MM-yyyy");
    Calendar c;

    public BookRecord(int id,int book_id, int student_id, int academic_year, int term, int borrow_date, int return_due_date, int return_date, int status, String title, String author) {
        this.id = id;
        this.book_id = book_id;
        this.student_id = student_id;
        this.academic_year = academic_year;
        this.term = term;
        this.borrow_date = borrow_date;
        this.return_due_date = return_due_date;
        this.return_date = return_date;
        this.status = status;
        this.title = title;
        this.author=author;
        c=Calendar.getInstance();
    }

    public int getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getAuthor() {
        return author;
    }

    public int getBook_id() {
        return book_id;
    }

    public int getStudent_id() {
        return student_id;
    }

    public int getAcademic_year() {
        return academic_year;
    }

    public int getTerm() {
        return term;
    }

    public String getTermStr(Context context) {
        switch (term){
            case 1: return context.getString(R.string.first_term);
            case 2: return context.getString(R.string.second_term);
            case 3: return context.getString(R.string.third_term);
        }
        return term+"";
    }

    public String getBorrow_date() {
        c.setTimeInMillis(borrow_date*1000L);
        return df.format(c.getTime());
    }

    public String getReturn_due_date() {
        c.setTimeInMillis(return_due_date*1000L);
        return df.format(c.getTime());
    }

    public String getReturn_date() {
        c.setTimeInMillis(return_date*1000L);
        return df.format(c.getTime());
    }

    public int getStatus() {
        return status;
    }

    public String getStatusStr(Context context) {
        switch (status){
            case 0: return context.getString(R.string.borrowed);
            case 1: return context.getString(R.string.returned);
        }
        return status+"";
    }
    public int getStatusColor() {
        switch (status){
            case 0: return Color.parseColor( "#FF5722");
            case 1: return Color.parseColor("#0C9911");
        }
        return Color.parseColor("#000000");
    }

}
