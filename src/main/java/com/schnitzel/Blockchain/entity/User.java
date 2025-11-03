package com.schnitzel.Blockchain.entity;

public class User {

    //TO DO: User accounts for doctors etc
    private String firstName;
    private String email;
    private String password;
    private String middleName;
    private String lastName;

    public User(){
    }

    public User(String firstName, String email, String password, String middleName, String lastName){   
        this.firstName = firstName;
        this.email = email;
        this.password = password;
        this.middleName = middleName;
        this.lastName = lastName;
    }
    
    public String getFirstName(){
        return firstName;
    }
    
}
