package com.example.app;

import org.json.JSONObject;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class JsonCreation {
    public void jsonCreate(String schoolInput, String courseInput, String topicInput) {
        JSONObject jsonObject = new JSONObject();// New JSON object to take in the response from user
        //jsonObject.put("Input", text);
        // First input from user will be these three keys and values
        // then their response as a whole string
        jsonObject.put("School", schoolInput);
        jsonObject.put("Course", courseInput);
        jsonObject.put("Topic", topicInput);

        String directory = "src/SHELLHACKS_2024_PROJECT/";
        String fileName = "input.json";

        String path = String.valueOf(Paths.get(directory));
        try {
            Files.createDirectories(Path.of(path));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        Path filePath = Paths.get(directory, fileName);
        try {
            Files.write(filePath, jsonObject.toString(4).getBytes());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void genJson(String genInput) {
        JSONObject jsonObject = new JSONObject();// New JSON object to take in the response from user
        //jsonObject.put("Input", text);
        // All inputs from user will start with the key being "Input",
        // then their response as a whole string
        jsonObject.put("Input", genInput);

        String directory = "src/SHELLHACKS_2024_PROJECT/";
        String fileName = "input.json";

        String path = String.valueOf(Paths.get(directory));
        try {
            Files.createDirectories(Path.of(path));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        Path filePath = Paths.get(directory, fileName);
        try {
            Files.write(filePath, jsonObject.toString(4).getBytes());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
