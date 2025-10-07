package com.jpassman;

import javax.crypto.AEADBadTagException;
import javax.crypto.SecretKey;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class Database {
    private final String dbPath;
    private List<PasswordEntry> entries;
    private byte[] salt;

    public Database(String dbPath) {
        this.dbPath = dbPath;
        this.entries = new ArrayList<>();
        this.salt = null;
    }

    public List<PasswordEntry> getEntries() {
        return entries;
    }

    public boolean exists() {
        return Files.exists(Paths.get(dbPath));
    }

    /**
     * Initializes a new database file with a newly generated salt and no entries.
     */
    public void createNewDatabase(char[] masterPassword) throws Exception {
        this.salt = CryptoUtils.generateRandomBytes(CryptoUtils.SALT_LENGTH_BYTES);
        this.entries = new ArrayList<>();
        save(masterPassword);
    }

    /**
     * Loads and decrypts the database from the file path.
     */
    public void load(char[] masterPassword) throws Exception {
        Path path = Paths.get(dbPath);
        if (!Files.exists(path)) {
            throw new FileNotFoundException("Database file not found: " + dbPath);
        }

        byte[] fileBytes = Files.readAllBytes(path);
        if (fileBytes.length < CryptoUtils.SALT_LENGTH_BYTES + CryptoUtils.IV_LENGTH_BYTES) {
            throw new IOException("Database file is corrupted or incomplete.");
        }

        // 1. Extract Salt
        this.salt = new byte[CryptoUtils.SALT_LENGTH_BYTES];
        System.arraycopy(fileBytes, 0, this.salt, 0, CryptoUtils.SALT_LENGTH_BYTES);

        // 2. Extract Encrypted Payload (IV + Ciphertext)
        int encryptedLength = fileBytes.length - CryptoUtils.SALT_LENGTH_BYTES;
        byte[] encryptedData = new byte[encryptedLength];
        System.arraycopy(fileBytes, CryptoUtils.SALT_LENGTH_BYTES, encryptedData, 0, encryptedLength);

        // 3. Derive Key
        SecretKey key = CryptoUtils.deriveKey(masterPassword, this.salt);

        // 4. Decrypt Payload
        byte[] decryptedBytes;
        try {
            decryptedBytes = CryptoUtils.decrypt(encryptedData, key);
        } catch (AEADBadTagException e) {
            throw new SecurityException("Incorrect master password or corrupted database.");
        }

        // 5. Deserialize Entries
        try (ByteArrayInputStream bais = new ByteArrayInputStream(decryptedBytes);
             DataInputStream dis = new DataInputStream(bais)) {
            
            int size = dis.readInt();
            this.entries = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                String service = dis.readUTF();
                String username = dis.readUTF();
                String password = dis.readUTF();
                String notes = dis.readUTF();
                entries.add(new PasswordEntry(service, username, password, notes));
            }
        }
    }

    /**
     * Encrypts and saves the current list of entries to the database file.
     */
    public void save(char[] masterPassword) throws Exception {
        if (this.salt == null) {
            this.salt = CryptoUtils.generateRandomBytes(CryptoUtils.SALT_LENGTH_BYTES);
        }

        // 1. Serialize Entries
        byte[] serializedBytes;
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             DataOutputStream dos = new DataOutputStream(baos)) {
            
            dos.writeInt(entries.size());
            for (PasswordEntry entry : entries) {
                dos.writeUTF(entry.getService());
                dos.writeUTF(entry.getUsername());
                dos.writeUTF(entry.getPassword());
                dos.writeUTF(entry.getNotes());
            }
            dos.flush();
            serializedBytes = baos.toByteArray();
        }

        // 2. Derive Key
        SecretKey key = CryptoUtils.deriveKey(masterPassword, this.salt);

        // 3. Encrypt Payload
        byte[] encryptedBytes = CryptoUtils.encrypt(serializedBytes, key);

        // 4. Combine Salt and Encrypted Payload
        byte[] fileBytes = new byte[salt.length + encryptedBytes.length];
        System.arraycopy(salt, 0, fileBytes, 0, salt.length);
        System.arraycopy(encryptedBytes, 0, fileBytes, salt.length, encryptedBytes.length);

        // 5. Write to File
        Files.write(Paths.get(dbPath), fileBytes);
    }
}
