package com.example.app;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

//Writes messages to the console and to logs/uhackedu.log, so errors can be checked after the app
//is closed. Covers the Java app and the output of the Python scripts.
public final class AppLog {
    static final File LOG_FILE = new File("logs/uhackedu.log");
    //Past this size the log is renamed to uhackedu.log.old and a fresh one started
    private static final long MAX_SIZE = 1_000_000;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static boolean checkedSize = false;

    private AppLog() {
    }

    public static synchronized void log(String message) {
        System.out.println(message);
        try {
            LOG_FILE.getParentFile().mkdirs();
            if (!checkedSize) {
                checkedSize = true;
                if (LOG_FILE.length() > MAX_SIZE) {
                    Files.move(LOG_FILE.toPath(), new File(LOG_FILE.getPath() + ".old").toPath(),
                            StandardCopyOption.REPLACE_EXISTING);
                }
            }
            Files.writeString(LOG_FILE.toPath(), LocalDateTime.now().format(TIME) + "  " + message + System.lineSeparator(),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.out.println("Couldn't write to " + LOG_FILE + ": " + e);
        }
    }
}
