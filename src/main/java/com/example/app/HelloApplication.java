package com.example.app;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import javafx.application.Application;
import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import org.scilab.forge.jlatexmath.TeXConstants;
import org.scilab.forge.jlatexmath.TeXFormula;
import org.scilab.forge.jlatexmath.TeXIcon;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

public class HelloApplication extends Application {





        @Override
        public void start(Stage primaryStage) throws Exception {

            // Load the FXML UI
            FXMLLoader loader = new FXMLLoader(getClass().getResource("hello-view.fxml"));
            Parent root = loader.load();

            // Set up the scene
            Scene scene = new Scene(root);
            primaryStage.setScene(scene);
            primaryStage.setTitle("uHackEDU");


            // Set application icon
            File file = new File("src/main/resources/Screenshot 2024-09-28 151235 - Copy (2).png");
            Image icon = new Image(file.toURI().toString());
            primaryStage.getIcons().add(icon);

            // Get reference to controller


            // Load JSON content and add it to the VBox in the FXML


            primaryStage.show();
        }

        // Method to load JSON content and add it to the VBox


        public static void main(String[] args) {
            launch(args);
        }
    }

