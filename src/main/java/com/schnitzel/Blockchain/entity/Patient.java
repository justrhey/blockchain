package com.schnitzel.Blockchain.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "patients")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Patient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(min = 2, max = 50)
    private String firstName;

    @Size(max = 50)
    private String middleName;

    @NotBlank
    @Size(min = 2, max = 50)
    private String lastName;

    @Past(message = "Date of birth must be in the past")
    @NotNull
    private LocalDate dateOfBirth;

    @NotBlank
    @Pattern(regexp = "^(A|B|AB|O)[+-]$", message = "Invalid blood type")
    private String bloodType;

    @NotBlank
    @Pattern(regexp = "^(MALE|FEMALE|OTHER)$")
    private String gender;

    @Email
    private String email;

    @Pattern(regexp = "^\\+?[1-9]\\d{1,14}$")
    private String phoneNumber;

    @Size(max = 200)
    private String address;

    @OneToMany(mappedBy = "patient", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MedicalRecord> medicalRecords = new ArrayList<>();

    @CreationTimestamp
    private LocalDateTime createdAt;

    // Calculate age dynamically
    public int getAge() {
        return Period.between(dateOfBirth, LocalDate.now()).getYears();
    }

    // Full name
    public String getFullName() {
        return String.format("%s %s %s",
                firstName,
                middleName != null ? middleName : "",
                lastName
        ).replaceAll("\\s+", " ").trim();
    }
}
