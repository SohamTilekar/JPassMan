package com.jpassman;

import java.io.Console;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

public class PasswordManager {
    private static final String DB_FILENAME = "passwords.db";
    private static final Scanner SCANNER = new Scanner(System.in);
    
    // ANSI Colors for terminal layout
    private static final String RESET = "\u001B[0m";
    private static final String BOLD = "\u001B[1m";
    private static final String GREEN = "\u001B[32m";
    private static final String RED = "\u001B[31m";
    private static final String YELLOW = "\u001B[33m";
    private static final String BLUE = "\u001B[34m";
    private static final String CYAN = "\u001B[36m";
    private static final String PURPLE = "\u001B[35m";

    private Database database;
    private char[] masterPassword;

    public static void main(String[] args) {
        PasswordManager manager = new PasswordManager();
        manager.run();
    }

    public void run() {
        printBanner();
        database = new Database(DB_FILENAME);

        try {
            if (!database.exists()) {
                System.out.println(YELLOW + "No database file found. Let's set up a new one!" + RESET);
                masterPassword = promptNewMasterPassword();
                database.createNewDatabase(masterPassword);
                System.out.println(GREEN + "✔ Database initialized successfully!" + RESET);
            } else {
                System.out.println(CYAN + "Existing database found." + RESET);
                unlockDatabase();
            }
            
            mainMenuLoop();

        } catch (Exception e) {
            System.out.println(RED + "Fatal Error: " + e.getMessage() + RESET);
        } finally {
            clearMasterPassword();
            SCANNER.close();
            System.out.println(BLUE + "Goodbye! Stay secure." + RESET);
        }
    }

    private void printBanner() {
        System.out.println(BLUE + BOLD + "========================================" + RESET);
        System.out.println(CYAN + BOLD + "       🔑 JPassMan - Java Password CLI" + RESET);
        System.out.println(BLUE + BOLD + "========================================" + RESET);
    }

    private void unlockDatabase() {
        int attempts = 3;
        while (attempts > 0) {
            char[] pw = promptPassword("Enter your Master Password to unlock: ");
            try {
                database.load(pw);
                this.masterPassword = pw;
                System.out.println(GREEN + "✔ Database unlocked successfully!" + RESET);
                return;
            } catch (SecurityException e) {
                attempts--;
                System.out.println(RED + "Incorrect password. " + attempts + " attempts remaining." + RESET);
                Arrays.fill(pw, ' ');
            } catch (Exception e) {
                System.out.println(RED + "Error loading database: " + e.getMessage() + RESET);
                Arrays.fill(pw, ' ');
                System.exit(1);
            }
        }
        System.out.println(RED + "Too many failed attempts. Exiting." + RESET);
        System.exit(1);
    }

    private char[] promptNewMasterPassword() {
        while (true) {
            char[] pw1 = promptPassword("Set your Master Password: ");
            if (pw1.length < 8) {
                System.out.println(YELLOW + "Password must be at least 8 characters long." + RESET);
                Arrays.fill(pw1, ' ');
                continue;
            }
            char[] pw2 = promptPassword("Confirm Master Password: ");
            if (Arrays.equals(pw1, pw2)) {
                Arrays.fill(pw2, ' ');
                return pw1;
            } else {
                System.out.println(RED + "Passwords do not match. Try again." + RESET);
                Arrays.fill(pw1, ' ');
                Arrays.fill(pw2, ' ');
            }
        }
    }

    private char[] promptPassword(String promptText) {
        Console console = System.console();
        if (console != null) {
            return console.readPassword(promptText);
        } else {
            // Fallback if running inside IDE terminal where System.console() is null
            System.out.print(promptText);
            String input = SCANNER.nextLine();
            return input.toCharArray();
        }
    }

    private void mainMenuLoop() {
        while (true) {
            System.out.println("\n" + BOLD + "=== MAIN MENU ===" + RESET);
            System.out.println(CYAN + "1." + RESET + " List all accounts");
            System.out.println(CYAN + "2." + RESET + " Get/Copy password");
            System.out.println(CYAN + "3." + RESET + " Add new password");
            System.out.println(CYAN + "4." + RESET + " Update password");
            System.out.println(CYAN + "5." + RESET + " Delete password");
            System.out.println(CYAN + "6." + RESET + " Generate secure password");
            System.out.println(CYAN + "7." + RESET + " Change Master Password");
            System.out.println(RED + "8." + RESET + " Exit & Lock");
            System.out.print("\nChoose an option: ");

            String choice = SCANNER.nextLine().trim();
            try {
                switch (choice) {
                    case "1":
                        listAccounts();
                        break;
                    case "2":
                        getPassword();
                        break;
                    case "3":
                        addPassword();
                        break;
                    case "4":
                        updatePassword();
                        break;
                    case "5":
                        deletePassword();
                        break;
                    case "6":
                        generatePasswordOption();
                        break;
                    case "7":
                        changeMasterPassword();
                        break;
                    case "8":
                        return;
                    default:
                        System.out.println(YELLOW + "Invalid choice. Please select 1-8." + RESET);
                }
            } catch (Exception e) {
                System.out.println(RED + "An error occurred: " + e.getMessage() + RESET);
            }
        }
    }

    private void listAccounts() {
        List<PasswordEntry> entries = database.getEntries();
        if (entries.isEmpty()) {
            System.out.println(YELLOW + "No passwords saved yet." + RESET);
            return;
        }

        System.out.println("\n" + BOLD + "Stored Accounts:" + RESET);
        System.out.printf(BOLD + "%-4s | %-25s | %-25s | %-20s\n" + RESET, "ID", "Service", "Username", "Notes");
        System.out.println("--------------------------------------------------------------------------------");
        for (int i = 0; i < entries.size(); i++) {
            PasswordEntry entry = entries.get(i);
            System.out.printf("%-4d | %-25s | %-25s | %-20s\n", 
                    (i + 1), 
                    entry.getService(), 
                    entry.getUsername(), 
                    entry.getNotes().isEmpty() ? "-" : entry.getNotes());
        }
    }

    private void getPassword() {
        List<PasswordEntry> entries = database.getEntries();
        if (entries.isEmpty()) {
            System.out.println(YELLOW + "No passwords saved yet." + RESET);
            return;
        }

        listAccounts();
        System.out.print("\nEnter ID to view password (or Enter to cancel): ");
        String input = SCANNER.nextLine().trim();
        if (input.isEmpty()) return;

        try {
            int index = Integer.parseInt(input) - 1;
            if (index < 0 || index >= entries.size()) {
                System.out.println(RED + "Invalid ID." + RESET);
                return;
            }

            PasswordEntry entry = entries.get(index);
            System.out.println("\n" + BOLD + "Details for " + entry.getService() + ":" + RESET);
            System.out.println("Username: " + entry.getUsername());
            System.out.println("Password: " + GREEN + entry.getPassword() + RESET);
            System.out.println("Notes:    " + entry.getNotes());

            System.out.print("\nWould you like to copy the password to your clipboard? (y/n): ");
            String copyChoice = SCANNER.nextLine().trim().toLowerCase();
            if (copyChoice.equals("y") || copyChoice.equals("yes")) {
                copyToClipboard(entry.getPassword());
            }
        } catch (NumberFormatException e) {
            System.out.println(RED + "Please enter a valid number." + RESET);
        }
    }

    private void addPassword() throws Exception {
        System.out.print("Enter Service (e.g., github.com): ");
        String service = SCANNER.nextLine().trim();
        if (service.isEmpty()) {
            System.out.println(RED + "Service cannot be empty." + RESET);
            return;
        }

        System.out.print("Enter Username/Email: ");
        String username = SCANNER.nextLine().trim();

        System.out.print("Enter Password (or press Enter to auto-generate): ");
        String password = SCANNER.nextLine();
        if (password.isEmpty()) {
            password = PasswordGenerator.generate(16, true, true, true, true);
            System.out.println(GREEN + "Generated secure password: " + password + RESET);
        }

        System.out.print("Enter Notes (optional): ");
        String notes = SCANNER.nextLine().trim();

        database.getEntries().add(new PasswordEntry(service, username, password, notes));
        database.save(masterPassword);
        System.out.println(GREEN + "✔ Account saved successfully!" + RESET);
    }

    private void updatePassword() throws Exception {
        List<PasswordEntry> entries = database.getEntries();
        if (entries.isEmpty()) {
            System.out.println(YELLOW + "No passwords saved yet." + RESET);
            return;
        }

        listAccounts();
        System.out.print("\nEnter ID to update (or Enter to cancel): ");
        String input = SCANNER.nextLine().trim();
        if (input.isEmpty()) return;

        try {
            int index = Integer.parseInt(input) - 1;
            if (index < 0 || index >= entries.size()) {
                System.out.println(RED + "Invalid ID." + RESET);
                return;
            }

            PasswordEntry entry = entries.get(index);
            System.out.println("Updating details for: " + entry.getService() + " (" + entry.getUsername() + ")");
            System.out.println("Leave blank to keep existing value.");

            System.out.print("New Service [" + entry.getService() + "]: ");
            String service = SCANNER.nextLine().trim();
            if (!service.isEmpty()) entry.setService(service);

            System.out.print("New Username [" + entry.getUsername() + "]: ");
            String username = SCANNER.nextLine().trim();
            if (!username.isEmpty()) entry.setUsername(username);

            System.out.print("New Password [Click Enter to keep]: ");
            String password = SCANNER.nextLine();
            if (!password.isEmpty()) entry.setPassword(password);

            System.out.print("New Notes [" + entry.getNotes() + "]: ");
            String notes = SCANNER.nextLine().trim();
            if (!notes.isEmpty()) entry.setNotes(notes);

            database.save(masterPassword);
            System.out.println(GREEN + "✔ Account updated successfully!" + RESET);
        } catch (NumberFormatException e) {
            System.out.println(RED + "Please enter a valid number." + RESET);
        }
    }

    private void deletePassword() throws Exception {
        List<PasswordEntry> entries = database.getEntries();
        if (entries.isEmpty()) {
            System.out.println(YELLOW + "No passwords saved yet." + RESET);
            return;
        }

        listAccounts();
        System.out.print("\nEnter ID to delete (or Enter to cancel): ");
        String input = SCANNER.nextLine().trim();
        if (input.isEmpty()) return;

        try {
            int index = Integer.parseInt(input) - 1;
            if (index < 0 || index >= entries.size()) {
                System.out.println(RED + "Invalid ID." + RESET);
                return;
            }

            PasswordEntry entry = entries.get(index);
            System.out.print(RED + "Are you sure you want to delete the credentials for " 
                    + entry.getService() + "? (y/n): " + RESET);
            String confirm = SCANNER.nextLine().trim().toLowerCase();
            if (confirm.equals("y") || confirm.equals("yes")) {
                database.getEntries().remove(index);
                database.save(masterPassword);
                System.out.println(GREEN + "✔ Credentials deleted successfully." + RESET);
            }
        } catch (NumberFormatException e) {
            System.out.println(RED + "Please enter a valid number." + RESET);
        }
    }

    private void generatePasswordOption() {
        System.out.print("Enter password length [16]: ");
        String lengthInput = SCANNER.nextLine().trim();
        int length = lengthInput.isEmpty() ? 16 : Integer.parseInt(lengthInput);

        System.out.print("Include uppercase letters? (y/n) [y]: ");
        boolean useUpper = !SCANNER.nextLine().trim().toLowerCase().equals("n");

        System.out.print("Include numbers? (y/n) [y]: ");
        boolean useDigits = !SCANNER.nextLine().trim().toLowerCase().equals("n");

        System.out.print("Include special symbols? (y/n) [y]: ");
        boolean useSymbols = !SCANNER.nextLine().trim().toLowerCase().equals("n");

        try {
            String generated = PasswordGenerator.generate(length, true, useUpper, useDigits, useSymbols);
            System.out.println("\nGenerated Password: " + GREEN + BOLD + generated + RESET);
            System.out.print("\nCopy to clipboard? (y/n): ");
            String copyChoice = SCANNER.nextLine().trim().toLowerCase();
            if (copyChoice.equals("y") || copyChoice.equals("yes")) {
                copyToClipboard(generated);
            }
        } catch (IllegalArgumentException e) {
            System.out.println(RED + e.getMessage() + RESET);
        }
    }

    private void changeMasterPassword() throws Exception {
        System.out.println(YELLOW + "Changing Master Password. This will re-encrypt all stored passwords." + RESET);
        char[] current = promptPassword("Confirm current Master Password: ");
        if (!Arrays.equals(current, masterPassword)) {
            System.out.println(RED + "Incorrect current Master Password." + RESET);
            Arrays.fill(current, ' ');
            return;
        }
        Arrays.fill(current, ' ');

        char[] newPw = promptNewMasterPassword();
        masterPassword = newPw;
        database.save(masterPassword);
        System.out.println(GREEN + "✔ Master Password changed successfully!" + RESET);
    }

    private static void copyToClipboard(String str) {
        try {
            java.awt.datatransfer.StringSelection selection = new java.awt.datatransfer.StringSelection(str);
            java.awt.datatransfer.Clipboard clipboard = java.awt.Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.setContents(selection, selection);
            System.out.println(GREEN + "✔ Copied to clipboard!" + RESET);
        } catch (Throwable e) {
            System.out.println(YELLOW + "⚠ Clipboard not available in this terminal/environment." + RESET);
        }
    }

    private void clearMasterPassword() {
        if (masterPassword != null) {
            Arrays.fill(masterPassword, ' ');
        }
    }
}
