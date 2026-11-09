package com.hf.hp605pv.palm;

import org.greenrobot.greendao.annotation.Entity;
import org.greenrobot.greendao.annotation.Id;
import org.greenrobot.greendao.annotation.Property;
import org.greenrobot.greendao.annotation.Generated;

@Entity
public class User {
    @Id
    @Property
    public Long id = 0l;
    @Property
    public String name = "";
    public String feature = "";

    public static User create(){
        User user = new User();
        user.id = System.currentTimeMillis();
        return user;
    }

    public Long getId() {
        return this.id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getFeature() {
        return this.feature;
    }

    public void setFeature(String feature) {
        this.feature = feature;
    }

    public User(){

    }

    public User(String n, String f) {
        name = n;
        feature = f;
    }

    @Generated(hash = 318895193)
    public User(Long id, String name, String feature) {
        this.id = id;
        this.name = name;
        this.feature = feature;
    }
}
