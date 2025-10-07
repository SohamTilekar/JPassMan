package com.jpassman;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TuiPasswordManager {
    // Keys definitions
    private static final int KEY_UP = 1000;
    private static final int KEY_DOWN = 1001;
    private static final int KEY_LEFT = 1002;
    private static final int KEY_RIGHT = 1003;
    private static final int KEY_ESC = 1004;
    private static final int KEY_HOME = 1005;
    private static final int KEY_END = 1006;
    private static final int KEY_PGUP = 1007;
    private static final int KEY_PGDN = 1008;
    private static final int KEY_SHIFT_TAB = 1009;
    private static final int KEY_ENTER = 13;
    private static final int KEY_BACKSPACE = 127;
    private static final int KEY_TAB = 9;
    private static final int KEY_MOUSE_CLICK = 2000;

    // ANSI Escape codes
    private static final String CLEAR_SCREEN = "\u001B[2J\u001B[H";
    private static final String HIDE_CURSOR = "\u001B[?25l";
    private static final String SHOW_CURSOR = "\u001B[?25h";
    private static final String RESET = "\u001B[0m";
    private static final String BOLD = "\u001B[1m";
    private static final String BG_BLUE = "\u001B[44m";
    private static final String BG_MAGENTA = "\u001B[45m";
    private static final String BG_CYAN = "\u001B[46m";
    private static final String FG_WHITE = "\u001B[37m";
    private static final String FG_GREEN = "\u001B[32m";
    private static final String FG_RED = "\u001B[31m";
    private static final String FG_YELLOW = "\u001B[33m";
    private static final String FG_CYAN = "\u001B[36m";
    private static final String FG_MAGENTA = "\u001B[35m";
    private static final String FG_BLACK = "\u001B[30m";
    private static final String SELECT_COLOR = BG_CYAN + FG_BLACK; 
    private static final String DIM_SELECT_COLOR = "\u001B[40m" + FG_CYAN; // Dark bg, cyan text

    // Box drawing
    private static final String B_TL = "╭";
    private static final String B_TR = "╮";
    private static final String B_BL = "╰";
    private static final String B_BR = "╯";
    private static final String B_H = "─";
    private static final String B_V = "│";

    // Mouse Tracking ANSI
    private static final String ENABLE_MOUSE = "\u001B[?1000h\u001B[?1006h";
    private static final String DISABLE_MOUSE = "\u001B[?1000l\u001B[?1006l";

    private enum ScreenMode {
        PROFILE_SELECT,
        DASHBOARD
    }

    private Database database;
    private char[] masterPassword;
    private int selectedIndex = 0;
    private String statusMessage = "Welcome to JPassMan!";
    private boolean isErrorStatus = false;

    // Mouse event state
    private int mouseX = 0;
    private int mouseY = 0;
    private boolean mouseReleased = false;

    // Profiles state
    private String currentProfile = "default";
    private List<String> profilesList = new ArrayList<>();
    private ScreenMode currentScreen = ScreenMode.PROFILE_SELECT;

    // Navigation focus
    private int focusedElement = 0; // 0 = Table/List, 1+ = Footer buttons

    public static void main(String[] args) {
        TuiPasswordManager tui = new TuiPasswordManager();
        tui.start();
    }

    private void start() {
        setRawMode(true);
        System.out.print(HIDE_CURSOR + ENABLE_MOUSE);
        System.out.flush();
        
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            setRawMode(false);
            System.out.print(SHOW_CURSOR + DISABLE_MOUSE + RESET + CLEAR_SCREEN);
            System.out.flush();
        }));

        try {
            scanProfiles();
            profileSelectionLoop();
            
            if (currentProfile != null) {
                String dbPath = "profile_" + currentProfile + ".db";
                database = new Database(dbPath);
                if (!database.exists()) {
                    initializeNewDatabase();
                } else {
                    loginDatabase();
                }

                currentScreen = ScreenMode.DASHBOARD;
                focusedElement = 0;
                mainLoop();
            }

        } catch (Exception e) {
            setRawMode(false);
            System.out.print(SHOW_CURSOR + DISABLE_MOUSE + RESET + CLEAR_SCREEN);
            System.out.flush();
            System.out.println("Fatal TUI Error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            cleanup();
        }
    }

    private void cleanup() {
        if (masterPassword != null) {
            Arrays.fill(masterPassword, ' ');
        }
        setRawMode(false);
        System.out.print(SHOW_CURSOR + DISABLE_MOUSE + RESET + CLEAR_SCREEN);
        System.out.flush();
        System.out.println("Thank you for using JPassMan! Stay secure.");
    }

    private void setRawMode(boolean raw) {
        try {
            String[] cmd = { "/bin/sh", "-c", raw ? "stty raw -echo </dev/tty" : "stty cooked echo </dev/tty" };
            Runtime.getRuntime().exec(cmd).waitFor();
        } catch (Exception e) {
            // Ignore
        }
    }

    private void scanProfiles() {
        profilesList.clear();
        File dir = new File(".");
        File[] files = dir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.startsWith("profile_") && name.endsWith(".db");
            }
        });
        if (files != null) {
            for (File file : files) {
                String name = file.getName();
                String profileName = name.substring("profile_".length(), name.length() - ".db".length());
                profilesList.add(profileName);
            }
        }
        if (profilesList.isEmpty()) {
            profilesList.add("default");
        }
    }

    private int readKey() throws IOException {
        int ch = System.in.read();
        if (ch == 27) { // Escape sequence
            if (System.in.available() > 0) {
                int next1 = System.in.read();
                if (next1 == '[') {
                    if (System.in.available() > 0) {
                        int next2 = System.in.read();
                        if (next2 == '<') {
                            StringBuilder sb = new StringBuilder();
                            int b;
                            while ((b = System.in.read()) != -1) {
                                if (b == 'M' || b == 'm') {
                                    sb.append((char) b);
                                    break;
                                }
                                sb.append((char) b);
                            }
                            parseMouseEvent(sb.toString());
                            return KEY_MOUSE_CLICK;
                        } else if (next2 == 'Z') {
                            return KEY_SHIFT_TAB;
                        } else if (next2 >= '1' && next2 <= '6') {
                            if (System.in.available() > 0) {
                                int next3 = System.in.read();
                                if (next3 == '~') {
                                    switch (next2) {
                                        case '1': return KEY_HOME;
                                        case '4': return KEY_END;
                                        case '5': return KEY_PGUP;
                                        case '6': return KEY_PGDN;
                                    }
                                }
                            }
                        } else {
                            switch (next2) {
                                case 'A': return KEY_UP;
                                case 'B': return KEY_DOWN;
                                case 'C': return KEY_RIGHT;
                                case 'D': return KEY_LEFT;
                                case 'H': return KEY_HOME;
                                case 'F': return KEY_END;
                            }
                        }
                    }
                }
            }
            return KEY_ESC;
        }
        if (ch == 8 || ch == 127) return KEY_BACKSPACE;
        if (ch == 9) return KEY_TAB;
        return ch;
    }

    private void parseMouseEvent(String sgrData) {
        try {
            boolean released = sgrData.endsWith("m");
            String clean = sgrData.substring(0, sgrData.length() - 1);
            String[] parts = clean.split(";");
            if (parts.length == 3) {
                int btn = Integer.parseInt(parts[0]);
                int x = Integer.parseInt(parts[1]);
                int y = Integer.parseInt(parts[2]);
                
                if (btn == 0) {
                    this.mouseX = x;
                    this.mouseY = y;
                    this.mouseReleased = released;
                }
            }
        } catch (Exception e) {}
    }

    private void drawBox(int width, int height, String title, List<String> lines) {
        System.out.print(CLEAR_SCREEN);
        System.out.print("\r\n");
        String paddingLeft = "   ";
        
        String horizontal = B_H.repeat(width - 2);
        System.out.print(paddingLeft + BOLD + FG_MAGENTA + B_TL + horizontal + B_TR + "\r\n" + RESET);
        
        String titlePadding = " ".repeat(Math.max(0, (width - title.length() - 4) / 2));
        String titleLine = paddingLeft + BOLD + FG_MAGENTA + B_V + " " + titlePadding + BOLD + FG_CYAN + title + RESET + titlePadding;
        
        int visibleLength = titleLine.replaceAll("\u001B\\[[;\\d]*m", "").length() - paddingLeft.length();
        int remainingPad = (width - 1) - visibleLength;
        if (remainingPad > 0) titleLine += " ".repeat(remainingPad);
        titleLine += BOLD + FG_MAGENTA + B_V + "\r\n" + RESET;
        System.out.print(titleLine);

        System.out.print(paddingLeft + BOLD + FG_MAGENTA + "├" + horizontal + "┤\r\n" + RESET);

        for (int i = 0; i < height - 4; i++) {
            String content = "";
            if (i < lines.size()) content = lines.get(i);
            int contentLen = content.replaceAll("\u001B\\[[;\\d]*m", "").length();
            int pad = Math.max(0, width - 4 - contentLen);
            System.out.print(paddingLeft + BOLD + FG_MAGENTA + B_V + " " + RESET + content + " ".repeat(pad) + BOLD + FG_MAGENTA + " " + B_V + "\r\n" + RESET);
        }

        System.out.print(paddingLeft + BOLD + FG_MAGENTA + B_BL + horizontal + B_BR + "\r\n\r\n" + RESET);
        System.out.flush();
    }

    private String readInputLine(String prompt, boolean mask) throws IOException {
        StringBuilder sb = new StringBuilder();
        System.out.print(SHOW_CURSOR);
        System.out.flush();
        while (true) {
            System.out.print("\r\u001B[K   " + BOLD + FG_CYAN + "❯ " + RESET + prompt + (mask ? "*".repeat(sb.length()) : sb.toString()));
            System.out.flush();
            int k = readKey();
            if (k == KEY_ENTER) break;
            else if (k == KEY_BACKSPACE && sb.length() > 0) sb.setLength(sb.length() - 1);
            else if (k == KEY_ESC) {
                System.out.print(HIDE_CURSOR + "\r\n");
                System.out.flush();
                return null;
            } else if (k >= 32 && k < 127) {
                sb.append((char) k);
            }
        }
        System.out.print(HIDE_CURSOR + "\r\n");
        System.out.flush();
        return sb.toString();
    }

    private void profileSelectionLoop() throws IOException {
        currentScreen = ScreenMode.PROFILE_SELECT;
        selectedIndex = 0;
        focusedElement = 0; // 0=List, 1=Add, 2=Select, 3=Quit

        while (true) {
            List<String> lines = new ArrayList<>();
            lines.add("Select a Profile:");
            lines.add("");
            for (int i = 0; i < profilesList.size(); i++) {
                String profile = profilesList.get(i);
                if (i == selectedIndex) {
                    lines.add((focusedElement == 0 ? SELECT_COLOR : DIM_SELECT_COLOR) + "  ► " + profile + "  " + RESET);
                } else {
                    lines.add("    " + profile);
                }
            }
            lines.add("");

            // Render buttons
            StringBuilder btns = new StringBuilder();
            String[] acts = {"Add Profile", "Select", "Quit"};
            char[] hotkeys = {'a', 'E', 'q'};
            for (int i = 0; i < acts.length; i++) {
                if (focusedElement == i + 1) {
                    btns.append(BG_MAGENTA).append(FG_WHITE).append(BOLD)
                        .append(" [").append(hotkeys[i] == 'E' ? "Enter" : hotkeys[i]).append("] ").append(acts[i]).append(" ")
                        .append(RESET).append("  ");
                } else {
                    btns.append(FG_MAGENTA)
                        .append("[").append(hotkeys[i] == 'E' ? "Enter" : hotkeys[i]).append("] ").append(acts[i])
                        .append(RESET).append("  ");
                }
            }
            lines.add(btns.toString());

            drawBox(50, 10 + profilesList.size(), "PROFILE SELECTION", lines);

            int key = readKey();
            if (key == KEY_TAB) {
                focusedElement = (focusedElement + 1) % 4;
            } else if (key == KEY_SHIFT_TAB) {
                focusedElement = (focusedElement == 0) ? 3 : focusedElement - 1;
            } else if (key == KEY_RIGHT) {
                focusedElement = (focusedElement == 0) ? 1 : (focusedElement % 3) + 1;
            } else if (key == KEY_LEFT) {
                focusedElement = (focusedElement <= 1) ? 3 : focusedElement - 1;
            } else if (key == KEY_UP) {
                focusedElement = 0;
                if (selectedIndex > 0) selectedIndex--;
            } else if (key == KEY_DOWN) {
                focusedElement = 0;
                if (selectedIndex < profilesList.size() - 1) selectedIndex++;
            } else if (key == KEY_ENTER) {
                if (focusedElement == 0 || focusedElement == 2) {
                    currentProfile = profilesList.get(selectedIndex);
                    break;
                } else if (focusedElement == 1) {
                    addProfileFlow();
                } else if (focusedElement == 3) {
                    System.exit(0);
                }
            } else if (key == 'a' || key == 'A') {
                addProfileFlow();
            } else if (key == 'q' || key == 'Q' || key == KEY_ESC) {
                System.exit(0);
            } else if (key == KEY_MOUSE_CLICK && mouseReleased) {
                int clickIndex = mouseY - 5; 
                if (clickIndex >= 0 && clickIndex < profilesList.size()) {
                    selectedIndex = clickIndex;
                    focusedElement = 0;
                    currentProfile = profilesList.get(selectedIndex);
                    break;
                } else if (mouseY == 5 + profilesList.size() + 2) { // Button row
                    // Rough estimates for button clicks
                    if (mouseX < 25) addProfileFlow();
                    else if (mouseX < 40) {
                        currentProfile = profilesList.get(selectedIndex);
                        break;
                    } else System.exit(0);
                }
            }
        }
    }

    private void addProfileFlow() throws IOException {
        String newProfile = readInputLine("Enter New Profile Name: ", false);
        if (newProfile != null && !newProfile.trim().isEmpty()) {
            newProfile = newProfile.trim().replaceAll("[^a-zA-Z0-9_]", "");
            if (!profilesList.contains(newProfile)) {
                profilesList.add(newProfile);
                selectedIndex = profilesList.size() - 1;
            }
        }
        focusedElement = 0;
    }

    // Initialize/Login omitted for brevity from tab loop logic, as they are sequential forms.
    private void initializeNewDatabase() throws Exception {
        while (true) {
            List<String> promptLines = new ArrayList<>();
            promptLines.add("No database found for profile: " + FG_GREEN + currentProfile + RESET);
            promptLines.add("Please choose a Master Password.");
            
            drawBox(55, 6, "INITIALIZE DATABASE", promptLines);

            String p1 = readInputLine("Set Master Password (min 8 chars): ", true);
            if (p1 == null) continue;
            if (p1.length() < 8) {
                showModal("ERROR", "Password must be at least 8 characters!");
                continue;
            }

            String p2 = readInputLine("Confirm Password: ", true);
            if (p2 == null) continue;

            if (p1.equals(p2)) {
                masterPassword = p1.toCharArray();
                database.createNewDatabase(masterPassword);
                showModal("SUCCESS", "Database created successfully!");
                break;
            } else {
                showModal("ERROR", "Passwords do not match!");
            }
        }
    }

    private void loginDatabase() throws IOException {
        int attempts = 3;
        while (attempts > 0) {
            List<String> promptLines = new ArrayList<>();
            promptLines.add("Profile: " + FG_GREEN + currentProfile + RESET);
            promptLines.add("Attempts remaining: " + FG_YELLOW + attempts + RESET);
            
            drawBox(50, 6, "UNLOCK DATABASE", promptLines);

            String pw = readInputLine("Enter Master Password: ", true);
            if (pw == null) continue;

            try {
                char[] pwChars = pw.toCharArray();
                database.load(pwChars);
                this.masterPassword = pwChars;
                return;
            } catch (SecurityException e) {
                attempts--;
                showModal("ACCESS DENIED", "Incorrect Master Password!");
            } catch (Exception e) {
                showModal("DATABASE ERROR", "Failed to load database: " + e.getMessage());
                System.exit(1);
            }
        }
        showModal("ACCESS DENIED", "Too many failed attempts. Exiting.");
        System.exit(1);
    }

    private void showModal(String title, String message) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add("");
        lines.add("  " + message);
        lines.add("");
        lines.add("  " + FG_MAGENTA + "[Enter]" + RESET + " to continue...");
        drawBox(50, 8, title, lines);
        while (readKey() != KEY_ENTER);
    }

    private void drawDashboard() {
        System.out.print(CLEAR_SCREEN);
        
        String header = String.format("  🔐 JPassMan  %s│%s  Profile: %s  %s│%s  DB: profile_%s.db", 
            FG_CYAN, BG_BLUE + FG_WHITE, BOLD + currentProfile + RESET + BG_BLUE + FG_WHITE, 
            FG_CYAN, BG_BLUE + FG_WHITE, currentProfile);
        
        int visibleLen = header.replaceAll("\u001B\\[[;\\d]*m", "").length();
        int padHeader = Math.max(0, 80 - visibleLen);
        System.out.print(BG_BLUE + FG_WHITE + BOLD + header + " ".repeat(padHeader) + RESET + "\r\n\r\n");

        List<PasswordEntry> entries = database.getEntries();
        String paddingLeft = "  ";

        System.out.print(paddingLeft + BOLD + FG_MAGENTA + B_TL + "────┬──────────────────────┬──────────────────────┬──────────────────────" + B_TR + "\r\n" + RESET);
        System.out.print(paddingLeft + BOLD + FG_MAGENTA + B_V + FG_CYAN + " ID " + FG_MAGENTA + B_V + FG_CYAN + " Service              " + FG_MAGENTA + B_V + FG_CYAN + " Username             " + FG_MAGENTA + B_V + FG_CYAN + " Notes                " + FG_MAGENTA + B_V + "\r\n" + RESET);
        System.out.print(paddingLeft + BOLD + FG_MAGENTA + "├────┼──────────────────────┼──────────────────────┼──────────────────────┤\r\n" + RESET);

        if (entries.isEmpty()) {
            System.out.print(paddingLeft + FG_MAGENTA + B_V + FG_YELLOW + " -- │ No passwords saved yet. Press [a] to add new.                      " + FG_MAGENTA + B_V + "\r\n" + RESET);
        } else {
            for (int i = 0; i < entries.size(); i++) {
                PasswordEntry entry = entries.get(i);
                String service = truncate(entry.getService(), 20);
                String username = truncate(entry.getUsername(), 20);
                String notes = truncate(entry.getNotes(), 20);

                String line = String.format(" %-2d │ %-20s │ %-20s │ %-20s ", i + 1, service, username, notes);
                if (i == selectedIndex) {
                    String color = (focusedElement == 0) ? SELECT_COLOR : DIM_SELECT_COLOR;
                    System.out.print(paddingLeft + FG_MAGENTA + B_V + color + line + RESET + FG_MAGENTA + B_V + "\r\n" + RESET);
                } else {
                    System.out.print(paddingLeft + FG_MAGENTA + B_V + RESET + line + FG_MAGENTA + B_V + "\r\n" + RESET);
                }
            }
        }

        System.out.print(paddingLeft + BOLD + FG_MAGENTA + B_BL + "────┴──────────────────────┴──────────────────────┴──────────────────────" + B_BR + "\r\n" + RESET);

        String statusColor = isErrorStatus ? FG_RED : FG_GREEN;
        System.out.print("\r\n" + paddingLeft + statusColor + BOLD + "Status: " + RESET + statusMessage + "\r\n");
        
        // Render Footer Buttons
        StringBuilder footer = new StringBuilder();
        footer.append(paddingLeft);
        String[] actions = {"View", "Add", "Update", "Delete", "Gen", "Quit"};
        char[] hotkeys = {'v', 'a', 'u', 'd', 'g', 'q'};

        for(int i = 0; i < actions.length; i++) {
            boolean isFocused = (focusedElement == i + 1);
            if (isFocused) {
                footer.append(BG_MAGENTA).append(FG_WHITE).append(BOLD)
                      .append(" [").append(hotkeys[i]).append("] ").append(actions[i]).append(" ")
                      .append(RESET).append("  ");
            } else {
                footer.append(FG_MAGENTA)
                      .append("[").append(hotkeys[i]).append("] ").append(actions[i])
                      .append(RESET).append("  ");
            }
        }
        System.out.print(footer.toString() + "\r\n");
        System.out.flush();
    }

    private String truncate(String val, int maxLen) {
        if (val == null) return "";
        if (val.length() <= maxLen) return val;
        return val.substring(0, maxLen - 3) + "...";
    }

    private void mainLoop() throws Exception {
        focusedElement = 0; // 0=Table, 1=View, 2=Add, 3=Update, 4=Delete, 5=Gen, 6=Quit

        while (true) {
            drawDashboard();
            int key = readKey();

            isErrorStatus = false;
            statusMessage = "Select credentials and choose command.";

            switch (key) {
                case KEY_TAB:
                    focusedElement = (focusedElement + 1) % 7;
                    break;
                case KEY_SHIFT_TAB:
                    focusedElement = (focusedElement == 0) ? 6 : focusedElement - 1;
                    break;
                case KEY_RIGHT:
                    focusedElement = (focusedElement == 0) ? 1 : (focusedElement % 6) + 1;
                    break;
                case KEY_LEFT:
                    focusedElement = (focusedElement <= 1) ? 6 : focusedElement - 1;
                    break;
                case KEY_UP:
                    focusedElement = 0;
                    if (selectedIndex > 0) selectedIndex--;
                    break;
                case KEY_DOWN:
                    focusedElement = 0;
                    if (selectedIndex < database.getEntries().size() - 1) selectedIndex++;
                    break;
                case KEY_PGUP:
                case KEY_HOME:
                    focusedElement = 0;
                    selectedIndex = 0;
                    break;
                case KEY_PGDN:
                case KEY_END:
                    focusedElement = 0;
                    selectedIndex = Math.max(0, database.getEntries().size() - 1);
                    break;
                case KEY_ENTER:
                    if (focusedElement == 1) viewPassword();
                    else if (focusedElement == 2) addPassword();
                    else if (focusedElement == 3) updatePassword();
                    else if (focusedElement == 4) deletePassword();
                    else if (focusedElement == 5) generatePassword();
                    else if (focusedElement == 6) return;
                    // If focusedElement == 0 (table), enter could default to view
                    else if (focusedElement == 0) viewPassword();
                    break;
                case 'q':
                case 'Q':
                case KEY_ESC:
                    return;
                case 'v':
                case 'V':
                    viewPassword();
                    break;
                case 'a':
                case 'A':
                    addPassword();
                    break;
                case 'u':
                case 'U':
                    updatePassword();
                    break;
                case 'd':
                case 'D':
                    deletePassword();
                    break;
                case 'g':
                case 'G':
                    generatePassword();
                    break;
                case KEY_MOUSE_CLICK:
                    if (mouseReleased) {
                        int entriesCount = Math.max(1, database.getEntries().size());
                        int tableRowStart = 6;
                        int tableRowEnd = 6 + entriesCount - 1;
                        int footerRow = 6 + entriesCount + 3;

                        if (mouseY >= tableRowStart && mouseY <= tableRowEnd) {
                            int clickIndex = mouseY - tableRowStart;
                            if (clickIndex < database.getEntries().size()) {
                                selectedIndex = clickIndex;
                                focusedElement = 0;
                            }
                        } else if (mouseY == footerRow) {
                            int[] breakpoints = {13, 22, 34, 46, 55};
                            int clickedBtn = -1;
                            if (mouseX < breakpoints[0]) clickedBtn = 1;
                            else if (mouseX < breakpoints[1]) clickedBtn = 2;
                            else if (mouseX < breakpoints[2]) clickedBtn = 3;
                            else if (mouseX < breakpoints[3]) clickedBtn = 4;
                            else if (mouseX < breakpoints[4]) clickedBtn = 5;
                            else clickedBtn = 6;

                            focusedElement = clickedBtn;
                            if (clickedBtn == 1) viewPassword();
                            else if (clickedBtn == 2) addPassword();
                            else if (clickedBtn == 3) updatePassword();
                            else if (clickedBtn == 4) deletePassword();
                            else if (clickedBtn == 5) generatePassword();
                            else if (clickedBtn == 6) return;
                        }
                    }
                    break;
            }
        }
    }

    private void viewPassword() throws Exception {
        List<PasswordEntry> entries = database.getEntries();
        if (entries.isEmpty()) {
            setStatus("No credentials to view.", true);
            return;
        }

        PasswordEntry entry = entries.get(selectedIndex);
        List<String> lines = new ArrayList<>();
        lines.add("Service:  " + FG_CYAN + entry.getService() + RESET);
        lines.add("Username: " + FG_CYAN + entry.getUsername() + RESET);
        lines.add("Password: " + FG_GREEN + BOLD + entry.getPassword() + RESET);
        lines.add("Notes:    " + entry.getNotes());
        lines.add("");
        lines.add("Commands: " + BG_MAGENTA + FG_WHITE + BOLD + " [c] Copy " + RESET + " | " + BG_MAGENTA + FG_WHITE + BOLD + " [Enter] Back " + RESET);

        while (true) {
            drawBox(55, 10, "CREDENTIALS DETAILS", lines);
            int k = readKey();
            if (k == 'c' || k == 'C') {
                copyToClipboard(entry.getPassword());
                setStatus("Password copied to clipboard!", false);
                break;
            } else if (k == KEY_ENTER || k == KEY_ESC) {
                break;
            } else if (k == KEY_MOUSE_CLICK && mouseReleased) {
                if (mouseY == 11 && mouseX >= 15 && mouseX <= 25) { 
                    copyToClipboard(entry.getPassword());
                    setStatus("Password copied to clipboard!", false);
                    break;
                } else if (mouseY == 11 && mouseX >= 28) {
                    break;
                }
            }
        }
    }

    private void addPassword() throws Exception {
        drawBox(55, 4, "ADD PASSWORD", List.of("Enter details in the prompts below."));

        String service = readInputLine("Service: ", false);
        if (service == null || service.trim().isEmpty()) {
            setStatus("Cancelled or empty service.", true);
            return;
        }

        String username = readInputLine("Username: ", false);
        if (username == null) return;

        String password = readInputLine("Password (leave empty to auto-generate): ", false);
        if (password == null) return;
        if (password.isEmpty()) {
            password = PasswordGenerator.generate(16, true, true, true, true);
        }

        String notes = readInputLine("Notes: ", false);
        if (notes == null) return;

        database.getEntries().add(new PasswordEntry(service, username, password, notes));
        database.save(masterPassword);
        setStatus("Credentials added successfully!", false);
        selectedIndex = database.getEntries().size() - 1;
    }

    private void updatePassword() throws Exception {
        List<PasswordEntry> entries = database.getEntries();
        if (entries.isEmpty()) {
            setStatus("No credentials to update.", true);
            return;
        }

        PasswordEntry entry = entries.get(selectedIndex);
        drawBox(55, 7, "UPDATE PASSWORD", List.of(
            "Service:  " + entry.getService(),
            "Username: " + entry.getUsername(),
            "Notes:    " + entry.getNotes(),
            "Leave fields empty to keep existing values."
        ));

        String service = readInputLine("Service [" + entry.getService() + "]: ", false);
        if (service == null) return;
        if (!service.trim().isEmpty()) entry.setService(service);

        String username = readInputLine("Username [" + entry.getUsername() + "]: ", false);
        if (username == null) return;
        if (!username.trim().isEmpty()) entry.setUsername(username);

        String password = readInputLine("Password [Enter to keep]: ", false);
        if (password == null) return;
        if (!password.isEmpty()) entry.setPassword(password);

        String notes = readInputLine("Notes [" + entry.getNotes() + "]: ", false);
        if (notes == null) return;
        if (!notes.trim().isEmpty()) entry.setNotes(notes);

        database.save(masterPassword);
        setStatus("Credentials updated successfully!", false);
    }

    private void deletePassword() throws Exception {
        List<PasswordEntry> entries = database.getEntries();
        if (entries.isEmpty()) {
            setStatus("No credentials to delete.", true);
            return;
        }

        PasswordEntry entry = entries.get(selectedIndex);
        List<String> lines = new ArrayList<>();
        lines.add("Are you sure you want to delete credentials for:");
        lines.add("  Service: " + FG_CYAN + entry.getService() + RESET);
        lines.add("  User:    " + FG_CYAN + entry.getUsername() + RESET);
        lines.add("");
        lines.add(BG_MAGENTA + FG_WHITE + BOLD + " [Enter] Confirm Delete " + RESET + "  |  " + BG_MAGENTA + FG_WHITE + BOLD + " [Esc] Cancel " + RESET);

        drawBox(55, 9, "CONFIRM DELETE", lines);
        int k = readKey();
        if (k == KEY_ENTER) {
            database.getEntries().remove(selectedIndex);
            database.save(masterPassword);
            setStatus("Credentials deleted successfully.", false);
            if (selectedIndex >= database.getEntries().size() && selectedIndex > 0) {
                selectedIndex--;
            }
        } else {
            setStatus("Delete cancelled.", false);
        }
    }

    private void generatePassword() throws IOException {
        String lenStr = readInputLine("Password length [16]: ", false);
        if (lenStr == null) return;
        int len = 16;
        if (!lenStr.trim().isEmpty()) {
            try {
                len = Integer.parseInt(lenStr.trim());
            } catch (NumberFormatException e) {
                setStatus("Invalid length. Defaulted to 16.", true);
            }
        }

        String generated = PasswordGenerator.generate(len, true, true, true, true);
        List<String> lines = new ArrayList<>();
        lines.add("Generated Password:");
        lines.add("  " + FG_GREEN + BOLD + generated + RESET);
        lines.add("");
        lines.add(BG_MAGENTA + FG_WHITE + BOLD + " [c] Copy " + RESET + "  |  " + BG_MAGENTA + FG_WHITE + BOLD + " [Enter] Back " + RESET);

        while (true) {
            drawBox(55, 9, "GENERATE PASSWORD", lines);
            int k = readKey();
            if (k == 'c' || k == 'C') {
                copyToClipboard(generated);
                setStatus("Copied generated password to clipboard!", false);
                break;
            } else if (k == KEY_ENTER || k == KEY_ESC) {
                break;
            }
        }
    }

    private void setStatus(String msg, boolean error) {
        this.statusMessage = msg;
        this.isErrorStatus = error;
    }

    private void copyToClipboard(String str) {
        try {
            java.awt.datatransfer.StringSelection selection = new java.awt.datatransfer.StringSelection(str);
            java.awt.datatransfer.Clipboard clipboard = java.awt.Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.setContents(selection, selection);
        } catch (Throwable e) {
            // Silence
        }
    }
}
