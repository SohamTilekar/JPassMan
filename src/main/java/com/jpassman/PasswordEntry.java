package com.jpassman;

public class PasswordEntry {
    private String service;
    private String username;
    private String password;
    private String notes;

    public PasswordEntry(String service, String username, String password, String notes) {
        this.service = service;
        this.username = username;
        this.password = password;
        this.notes = notes;
    }

    public String getService() {
        return service;
    }

    public void setService(String service) {
        this.service = service;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
