package com.schnitzel.Blockchain.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

@Entity
@Table(name = "medical_records")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MedicalRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", nullable = false)
    @NotNull(message = "Patient is required")
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "doctor_id", nullable = false)
    @NotNull(message = "Doctor is required")
    private User doctor;

    @NotBlank(message = "Diagnosis is required")
    @Size(min = 5, max = 1000)
    @Column(nullable = false, length = 1000)
    private String diagnosis;

    @NotBlank(message = "Treatment is required")
    @Size(min = 5, max = 2000)
    @Column(nullable = false, length = 2000)
    private String treatment;

    @Size(max = 1000)
    @Column(length = 1000)
    private String prescription;

    @Size(max = 2000)
    @Column(length = 2000)
    private String notes;

    @NotNull
    @Column(nullable = false)
    private LocalDateTime recordDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RecordType recordType = RecordType.GENERAL;

    @Column(name = "blockchain_hash", length = 64)
    private String blockchainHash;

    @Column(name = "transaction_id", length = 128)
    private String transactionId;

    @Column(name = "on_blockchain")
    private boolean onBlockchain = false;

    @Column(name = "blockchain_timestamp")
    private LocalDateTime blockchainTimestamp;

    @Column(name = "previous_record_hash", length = 64)
    private String previousRecordHash;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "modified_by")
    private String modifiedBy;

    @Version
    private Long version;

    @Column(name = "is_deleted")
    private boolean isDeleted = false;

    public String calculateHash() {
        String data = String.format("%d|%s|%s|%s|%s|%s",
            patient.getId(),
            recordDate.toString(),
            diagnosis,
            treatment,
            prescription != null ? prescription : "",
            previousRecordHash != null ? previousRecordHash : "0"
        );

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();

            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }

            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("Error calculating hash", e);
        }
    }

    public boolean verifyHash() {
        return blockchainHash != null && blockchainHash.equals(calculateHash());
    }

    public void markAsOnBlockchain(String transactionId) {
        this.onBlockchain = true;
        this.transactionId = transactionId;
        this.blockchainTimestamp = LocalDateTime.now();
        this.blockchainHash = calculateHash();
    }

    public String getPatientFullName() {
        return patient != null ? patient.getFullName() : "Unknown";
    }

    public String getDoctorName() {
        return doctor != null ? doctor.getFullName() : "Unknown";
        // MISSING CLASS IN USER: to do create a user class in entity wuith backend
    }

    public enum RecordType {
        GENERAL,
        DIAGNOSIS,
        PRESCRIPTION,
        LAB_RESULT,
        SURGERY,
        CONSULTATION,
        VACCINATION,
        EMERGENCY
    }
}
