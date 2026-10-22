package com.example.app;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import javafx.embed.swing.SwingFXUtils;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.scene.text.Text;
import javafx.stage.Stage;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;

import org.scilab.forge.jlatexmath.TeXConstants;
import org.scilab.forge.jlatexmath.TeXFormula;
import org.scilab.forge.jlatexmath.TeXIcon;
import javafx.scene.control.TextArea;

public class HelloController implements Initializable {
    @FXML
    private MediaView mediaView;
    private File file;
    private Media media;
    private MediaPlayer mediaPlayer;
    private Stage stage;
    private Scene scene;
    private Parent root;
    @FXML
    private TextField textBox1;
    @FXML
    private TextField textBox2;
    @FXML
    private TextField textBox3;
    @FXML
    private Button myButton;
    @FXML
    private TextField textField2;
    @FXML
    private TextArea outputTextArea;
    @FXML
    private VBox dynamicContent;
    @FXML
    private VBox dynamicContent2;
    @FXML
    private AnchorPane anchorPane;
    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        MediaPlayer mediaPlayer = MediaPlayerManager.getInstance().getMediaPlayer();
        mediaView.setMediaPlayer(mediaPlayer);
    }
    public void switchToScene1(ActionEvent event) throws IOException {
        // Switch back to the first scene
        FXMLLoader loader = new FXMLLoader(getClass().getResource("hello-view.fxml"));
        Parent root = loader.load();
        Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        Scene scene = new Scene(root);
        stage.setScene(scene);
        stage.show();
    }
    public void switchToScene3(ActionEvent event) throws IOException {
        // Switch back to the first scene
        FXMLLoader loader = new FXMLLoader(getClass().getResource("com/example/app/scene3.fxml"));
        Parent root = loader.load();
        Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        Scene scene = new Scene(root);
        stage.setScene(scene);
        stage.show();
    }
    private Parent scene2Root;
    public void switchToScene2FromScene3(ActionEvent event) throws IOException {
        if (scene2Root == null) {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("com/example/app/scene2.fxml"));
            scene2Root = loader.load();

            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            Scene scene = new Scene(scene2Root);
            stage.setScene(scene);
            stage.show();
        }
    }




    public void switchToScene2(ActionEvent event) throws IOException {
        VBox vbox = new VBox();
        vbox.setSpacing(10);

        String value1 = textBox1.getText();
        String value2 = textBox2.getText();
        String value3 = textBox3.getText();
        JsonCreation json = new JsonCreation();
        json.jsonCreate(value1, value2, value3);
        String aiOutputFile = "src/SHELLHACKS_2024_PROJECT/output.json";



        try (FileReader reader = new FileReader("src/SHELLHACKS_2024_PROJECT/output.json")) {
            //Parse JSON file into a Json Object
            JsonObject jsonObject = JsonParser.parseReader(reader).getAsJsonObject();

            //Create a JsonObject of the response thorugh the Response key found in the Json AI Output File
            JsonObject response = jsonObject.getAsJsonObject("Response");

            //Extract values from "response" through keys from the Json file
            String explanation = response.get("Explanation").getAsString();
            String question = response.get("Question").getAsString();
            String answer = response.get("Answer").getAsString();

            //Split values extracted into tokens separated by two dollars signs
            //$$ is a delimeter in LaTeX (typesetting system used to create mathematical expressions, equations, etc.)
            String[] explanationBig = explanation.split("\\$\\$");//makes explanation tokens
            String[] questionBig = question.split("\\$\\$");
            String[] answerBig = answer.split("\\$\\$");



            // Create a new Scene with the layout


            //Iterate through tokens to see what is a math formula or not
            //Convert to math image, then add to vbox
            //If not formula, add normal text to vbox

            int i = 0;
            for (String explanationToken : explanationBig) {

                if (i % 2 == 1) {

                    ImageView mathImage = printImage(explanationToken);
                    vbox.getChildren().add(mathImage);
                } else {

                    explanationToken = explanationToken.replace("$", ""); //Remove single dollar signs for simple math equations
                    Text normalText = new Text(explanationToken);
                    vbox.getChildren().add(normalText);
                }
                i++;
            }

        } catch (IOException e){
            System.out.println(e);
        }








        ProcessBuilder pb = new ProcessBuilder("src/SHELLHACKS_2024_PROJECT/default.exe");
        Process p = pb.start();

        Stage newWindow = new Stage();
        newWindow.setTitle("New Window");

        // Create a Label to display in the new window
        Label label = new Label("This is a new window");

        // Create a layout for the new window and add the label
        StackPane layout = new StackPane();

        //Add scroll pane to scene and set the stage

        Scene scene = new Scene(vbox, 800, 800);

        // Set the scene to the stage and show the new window
        newWindow.setScene(scene);
        newWindow.show();

        FXMLLoader loader = new FXMLLoader(getClass().getResource("scene2.fxml"));
        Parent root2 = loader.load();
        Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        Scene scene2 = new Scene(root2);
        stage.setScene(scene2);





    }


    public void followUpInput(ActionEvent event)throws IOException{
        JsonCreation json = new JsonCreation();
        VBox vbox = new VBox();
        vbox.setSpacing(10);
        try (FileReader reader = new FileReader("src/SHELLHACKS_2024_PROJECT/output.json")) {
            //Parse JSON file into a Json Object
            JsonObject jsonObject = JsonParser.parseReader(reader).getAsJsonObject();

            //Create a JsonObject of the response thorugh the Response key found in the Json AI Output File
            JsonObject response = jsonObject.getAsJsonObject("Response");
            String inputText = textField2.getText(); // Get the text from the TextField
            json.genJson(inputText);
            String Output = response.get("Output").getAsString();
            String[] OutputBig = Output.split("\\$\\$");
            int j = 0;
            for (String questionToken : OutputBig) {

                if (j % 2 == 1) {
                    ImageView mathImage = printImage(questionToken);
                    vbox.getChildren().add(mathImage);
                } else {
                    questionToken = questionToken.replace("$", "");
                    Text normalText = new Text(questionToken);
                    vbox.getChildren().add(normalText);
                }
                j++;//Iterate j to the next
            }
        }catch (IOException e){
            System.out.println(e);
        }


        ProcessBuilder pb = new ProcessBuilder("src/SHELLHACKS_2024_PROJECT/followup.exe");
        try {
            Process p = pb.start();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        Stage newWindow = new Stage();
        newWindow.setTitle("New Window");

        // Create a Label to display in the new window
        

        // Create a layout for the new window and add the label


        //Add scroll pane to scene and set the stage

        Scene scene = new Scene(vbox, 800, 800);

        // Set the scene to the stage and show the new window
        newWindow.setScene(scene);
        newWindow.show();



                // Implement your logic here, e.g., process the input or clear the field
        textField2.clear(); // Example of clearing the TextField after pressing Enter
    }
    public void loadJsonContent() {
        // Path to JSON file
        String aiOutputFile = "SHELLHACKS_2024_PROJECT/output.json";

        // StringBuilder to accumulate all the output content
        StringBuilder output = new StringBuilder();

        try (FileReader reader = new FileReader(aiOutputFile)) {
            // Parse JSON file into a JsonObject
            JsonObject jsonObject = JsonParser.parseReader(reader).getAsJsonObject();

            // Create a JsonObject of the response through the Response key found in the Json AI Output File
            JsonObject response = jsonObject.getAsJsonObject("Response");

            // Extract values from "response" through keys from the Json file
            String explanation = response.get("Explanation").getAsString();
            String question = response.get("Question").getAsString();
            String answer = response.get("Answer").getAsString();

            // Append the explanation, question, and answer content to the StringBuilder
            processTokens(output, explanation.split("\\$\\$"));
            processTokens(output, question.split("\\$\\$"));
            processTokens(output, answer.split("\\$\\$"));

            // Set the accumulated content into the TextArea
            outputTextArea.setText(output.toString());

        } catch (IOException e) {
            System.out.println(e);
        }
    }

     //Helper method to process tokens and append them to the StringBuilder
    private void processTokens(StringBuilder output, String[] tokens) {
        int i = 0;
        for (String token : tokens) {
            if (i % 2 == 1) {
                // Handle formulas differently, for now, append LaTeX formula placeholders
                output.append("[Formula: ").append(token).append("]").append("\n");
            } else {
                // Append normal text
                output.append(token.replace("$", "")).append("\n");  // Remove single dollar signs for simple math equations
            }
            i++;
        }
    }
     //Converts LaTeX to image
    public static ImageView printImage(String mathExpression) {
        try {
            TeXFormula formula = new TeXFormula(mathExpression);
            TeXIcon formulaIcon = formula.createTeXIcon(TeXConstants.STYLE_DISPLAY, 20);

            BufferedImage bufferedImage = new BufferedImage(
                    formulaIcon.getIconWidth(),
                    formulaIcon.getIconHeight(),
                    BufferedImage.TYPE_INT_ARGB
            );

            Graphics2D iconGraphics = bufferedImage.createGraphics();
            iconGraphics.setColor(Color.WHITE);
            iconGraphics.fillRect(0, 0, bufferedImage.getWidth(), bufferedImage.getHeight());
            formulaIcon.paintIcon(null, iconGraphics, 0, 0);
            iconGraphics.dispose();

            Image fxImage = SwingFXUtils.toFXImage(bufferedImage, null);
            return new ImageView(fxImage);
        } catch (Exception e) {
            System.out.println("Error creating LaTeX image: " + e);
            return new ImageView(); // Return empty ImageView on error
        }
    }
}
























