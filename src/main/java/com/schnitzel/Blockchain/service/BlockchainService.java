package com.schnitzel.Blockchain.service;

import com.schnitzel.Blockchain.entity.MedicalRecord;
//import com.schnitzel.Blockchain.repository.MedicalRecordRepository;
import org.hyperledger.fabric.gateway.*;
import org.hyperledger.fabric.sdk.Enrollment;
import org.hyperledger.fabric.sdk.User;
import org.hyperledger.fabric.sdk.security.CryptoSuite;
import org.hyperledger.fabric.sdk.security.CryptoSuiteFactory;
import org.hyperledger.fabric_ca.sdk.HFCAClient;
import org.hyperledger.fabric_ca.sdk.RegistrationRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.PrivateKey;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.TimeoutException;

/**
 * Service for interacting with Hyperledger Fabric blockchain
 * Handles medical record storage, verification, and audit trails
 */
@Service
public class BlockchainService {

    private static final Logger logger = LoggerFactory.getLogger(BlockchainService.class);

    @Autowired
    private MedicalRecordRepository medicalRecordRepository;

    // Configuration from application.properties
    @Value("${hyperledger.network.config.path:network-config.json}")
    private String networkConfigPath;

    @Value("${hyperledger.wallet.path:wallet}")
    private String walletPath;

    @Value("${hyperledger.channel.name:healthcare-channel}")
    private String channelName;

    @Value("${hyperledger.chaincode.name:medical-records}")
    private String chaincodeName;

    @Value("${hyperledger.organization.msp:HospitalMSP}")
    private String organizationMSP;

    @Value("${hyperledger.user.name:admin}")
    private String userName;

    private Gateway gateway;
    private Network network;
    private Contract contract;
    private Wallet wallet;

    /**
     * Initialize Hyperledger Fabric connection on startup
     */
    @PostConstruct
    public void initialize() {
        try {
            logger.info("Initializing Hyperledger Fabric connection...");
            
            // Create wallet
            Path walletDirectory = Paths.get(walletPath);
            wallet = Wallets.newFileSystemWallet(walletDirectory);
            
            // Check if user exists in wallet
            if (!wallet.get(userName).exists()) {
                logger.warn("User {} not found in wallet. Enrollment required.", userName);
                // In production, this would trigger enrollment process
                return;
            }

            // Load network configuration
            Gateway.Builder builder = Gateway.createBuilder();
            Path networkConfigFile = Paths.get(networkConfigPath);
            
            builder.identity(wallet, userName)
                   .networkConfig(networkConfigFile)
                   .discovery(true);

            // Connect to gateway
            gateway = builder.connect();
            
            // Get network and contract
            network = gateway.getNetwork(channelName);
            contract = network.getContract(chaincodeName);
            
            logger.info("Successfully connected to Hyperledger Fabric network");
            
        } catch (Exception e) {
            logger.error("Failed to initialize Hyperledger Fabric connection", e);
            // In development mode, continue without blockchain
            // In production, this should halt the application
        }
    }

    /**
     * Submit a medical record to the blockchain
     * Stores the hash, timestamp, and metadata
     */
    public String submitMedicalRecord(MedicalRecord record) throws Exception {
        if (contract == null) {
            throw new IllegalStateException("Blockchain connection not initialized");
        }

        try {
            // Calculate hash of the medical record
            String recordHash = record.calculateHash();
            
            // Prepare transaction data
            String recordId = String.valueOf(record.getId());
            String patientId = String.valueOf(record.getPatient().getId());
            String doctorId = String.valueOf(record.getDoctor().getId());
            String timestamp = record.getRecordDate().toString();
            String previousHash = record.getPreviousRecordHash() != null ? 
                                 record.getPreviousRecordHash() : "0";

            // Submit transaction to blockchain
            byte[] result = contract.submitTransaction(
                "createMedicalRecord",
                recordId,
                patientId,
                doctorId,
                recordHash,
                timestamp,
                previousHash,
                record.getRecordType().name()
            );

            String transactionId = new String(result);
            logger.info("Medical record {} submitted to blockchain. Transaction ID: {}", 
                       recordId, transactionId);

            // Update database record
            record.markAsOnBlockchain(transactionId);
            medicalRecordRepository.save(record);

            return transactionId;

        } catch (TimeoutException | InterruptedException e) {
            logger.error("Timeout submitting record to blockchain", e);
            throw new Exception("Blockchain transaction timed out", e);
        } catch (ContractException e) {
            logger.error("Contract execution failed", e);
            throw new Exception("Failed to execute smart contract", e);
        }
    }

    /**
     * Verify a medical record against the blockchain
     * Returns true if the hash matches what's stored on-chain
     */
    public boolean verifyMedicalRecord(Long recordId) throws Exception {
        if (contract == null) {
            throw new IllegalStateException("Blockchain connection not initialized");
        }

        try {
            // Get record from database
            MedicalRecord record = medicalRecordRepository.findById(recordId)
                .orElseThrow(() -> new Exception("Medical record not found"));

            // Query blockchain for stored hash
            byte[] result = contract.evaluateTransaction(
                "queryMedicalRecord",
                String.valueOf(recordId)
            );

            String blockchainData = new String(result);
            
            // Parse blockchain response (format: hash|timestamp|transactionId)
            String[] parts = blockchainData.split("\\|");
            String storedHash = parts[0];

            // Calculate current hash
            String currentHash = record.calculateHash();

            // Verify hashes match
            boolean isValid = storedHash.equals(currentHash);
            
            if (!isValid) {
                logger.warn("Hash mismatch for record {}. Possible tampering detected!", recordId);
            }

            return isValid;

        } catch (ContractException e) {
            logger.error("Failed to verify record on blockchain", e);
            throw new Exception("Blockchain verification failed", e);
        }
    }

    /**
     * Get the complete audit trail for a medical record
     * Returns all blockchain transactions related to this record
     */
    public String getAuditTrail(Long recordId) throws Exception {
        if (contract == null) {
            throw new IllegalStateException("Blockchain connection not initialized");
        }

        try {
            byte[] result = contract.evaluateTransaction(
                "getRecordHistory",
                String.valueOf(recordId)
            );

            return new String(result);

        } catch (ContractException e) {
            logger.error("Failed to retrieve audit trail", e);
            throw new Exception("Failed to retrieve blockchain history", e);
        }
    }

    /**
     * Log access to a medical record on the blockchain
     * Creates an immutable audit trail of who accessed what and when
     */
    public String logAccess(Long recordId, String userId, String action) throws Exception {
        if (contract == null) {
            throw new IllegalStateException("Blockchain connection not initialized");
        }

        try {
            byte[] result = contract.submitTransaction(
                "logAccess",
                String.valueOf(recordId),
                userId,
                action,
                String.valueOf(System.currentTimeMillis())
            );

            String transactionId = new String(result);
            logger.info("Access logged: User {} performed {} on record {}", 
                       userId, action, recordId);

            return transactionId;

        } catch (TimeoutException | InterruptedException e) {
            logger.error("Failed to log access on blockchain", e);
            throw new Exception("Failed to log access", e);
        } catch (ContractException e) {
            logger.error("Contract execution failed for access logging", e);
            throw new Exception("Failed to execute access logging", e);
        }
    }

    /**
     * Get all medical records for a specific patient from blockchain
     */
    public String getPatientRecords(Long patientId) throws Exception {
        if (contract == null) {
            throw new IllegalStateException("Blockchain connection not initialized");
        }

        try {
            byte[] result = contract.evaluateTransaction(
                "getPatientRecords",
                String.valueOf(patientId)
            );

            return new String(result);

        } catch (ContractException e) {
            logger.error("Failed to retrieve patient records from blockchain", e);
            throw new Exception("Failed to query patient records", e);
        }
    }

    /**
     * Batch submit multiple records to blockchain
     * More efficient for bulk operations
     */
    public void batchSubmitRecords(Set<MedicalRecord> records) throws Exception {
        if (contract == null) {
            throw new IllegalStateException("Blockchain connection not initialized");
        }

        logger.info("Batch submitting {} records to blockchain", records.size());
        
        for (MedicalRecord record : records) {
            try {
                submitMedicalRecord(record);
            } catch (Exception e) {
                logger.error("Failed to submit record {} in batch", record.getId(), e);
                // Continue with other records instead of failing entire batch
            }
        }
    }

    /**
     * Check if blockchain connection is active
     */
    public boolean isConnected() {
        return gateway != null && contract != null;
    }

    /**
     * Get blockchain network statistics
     */
    public String getNetworkStats() throws Exception {
        if (contract == null) {
            throw new IllegalStateException("Blockchain connection not initialized");
        }

        try {
            byte[] result = contract.evaluateTransaction("getStats");
            return new String(result);
        } catch (ContractException e) {
            logger.error("Failed to get network stats", e);
            throw new Exception("Failed to retrieve network statistics", e);
        }
    }

    /**
     * Cleanup resources on shutdown
     */
    public void shutdown() {
        if (gateway != null) {
            gateway.close();
            logger.info("Hyperledger Fabric gateway closed");
        }
    }

    /**
     * Emergency function to resync a record with blockchain
     * Use when database and blockchain are out of sync
     */
    public void resyncRecord(Long recordId) throws Exception {
        MedicalRecord record = medicalRecordRepository.findById(recordId)
            .orElseThrow(() -> new Exception("Medical record not found"));

        // Query blockchain for current state
        byte[] result = contract.evaluateTransaction(
            "queryMedicalRecord",
            String.valueOf(recordId)
        );

        String blockchainData = new String(result);
        String[] parts = blockchainData.split("\\|");
        
        // Update local record with blockchain data
        record.setBlockchainHash(parts[0]);
        record.setTransactionId(parts[2]);
        record.setOnBlockchain(true);
        
        medicalRecordRepository.save(record);
        
        logger.info("Record {} resynced with blockchain", recordId);
    }
}