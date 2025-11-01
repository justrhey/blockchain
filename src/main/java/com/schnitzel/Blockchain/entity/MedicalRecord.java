package com.schnitzel.Blockchain.entity;

import org.apache.milagro.amcl.RSA2048.private_key;

import jakarta.annotation.Generated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.*;

import jakarta.persistence.Table;
import jakarta.persistence.Id;
import jakarta.persistence.Entity;
import java.time.LocalDateTime;


@Entity
@Table (name = "medicalrecord")
public class MedicalRecord {
    
    @Id
    @GeneratedValue(strategy =  GenerationType.IDENTITY)
    private Long id;

    private String firstName;
    private String middleName;
    private String lastName;
    private Long age;
    private String address;
    private String gender;

    private String medicalHistory;
    private String previousBlockHash;

    private String publicKey;
    private String privateKey;


    public MedicalRecord(){
    }

    public MedicalRecord(String firstName, String middleName, String lastName,String lastName, Long age, String address,String gender ){

    }

    
}
