package com.example.app;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.embed.swing.SwingFXUtils;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.media.MediaView;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

import java.awt.Desktop;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

import org.scilab.forge.jlatexmath.TeXConstants;
import org.scilab.forge.jlatexmath.TeXFormula;
import org.scilab.forge.jlatexmath.TeXIcon;

//One controller shared by all three screens; each FXML only fills in the fields it uses
public class HelloController implements Initializable {
    private static final String OUTPUT_FILE = "src/SHELLHACKS_2024_PROJECT/output.json";
    //LaTeX formulas are drawn in the same light color as the lesson text
    private static final java.awt.Color MATH_COLOR = new java.awt.Color(232, 240, 252);

    @FXML
    private StackPane root;
    @FXML
    private MediaView mediaView;

    //Start screen
    @FXML
    private TextField textBox1; //School
    @FXML
    private TextField textBox2; //Course
    @FXML
    private TextField textBox3; //Topic
    @FXML
    private Label errorLabel;
    @FXML
    private VBox loadingOverlay;

    //Lesson and practice screens
    @FXML
    private Label topicLabel;
    @FXML
    private ScrollPane lessonScroll;
    @FXML
    private VBox lessonContent;
    @FXML
    private TextField textField2; //Follow-up question
    @FXML
    private Button askButton;
    @FXML
    private VBox practiceContent;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        playBackgroundVideo();
        if (errorLabel != null) {
            errorLabel.managedProperty().bind(errorLabel.textProperty().isNotEmpty()); //No gap when there's no error
        }
        if (lessonContent != null) {
            showLesson();
        }
        if (practiceContent != null) {
            showPractice();
        }
    }

    public void switchToScene1(ActionEvent event) {
        showScreen(event, "hello-view.fxml");
    }

    public void switchToScene3(ActionEvent event) {
        showScreen(event, "scene3.fxml");
    }

    public void switchToScene2FromScene3(ActionEvent event) {
        showScreen(event, "scene2.fxml");
    }

    //Start screen: save the inputs, generate a lesson, then open the lesson screen
    public void switchToScene2(ActionEvent event) {
        String school = textBox1.getText().trim();
        String course = textBox2.getText().trim();
        String topic = textBox3.getText().trim();
        if (topic.isEmpty()) {
            errorLabel.setText("Enter a topic to get started.");
            return;
        }
        errorLabel.setText("");

        new JsonCreation().jsonCreate(school, course, topic);
        Scene scene = root.getScene();
        loadingOverlay.setVisible(true);
        runInBackground("default.py",
                () -> showScreen(scene, "scene2.fxml"),
                () -> {
                    loadingOverlay.setVisible(false);
                    errorLabel.setText("Couldn't build a lesson. Check your API keys and internet connection, then try again.");
                });
    }

    //Lesson screen: ask a follow-up and add the answer below the lesson
    public void followUpInput(ActionEvent event) {
        String question = textField2.getText().trim();
        if (question.isEmpty()) {
            return; //Nothing to ask
        }
        new JsonCreation().genJson(question);
        textField2.clear();
        setAsking(true);

        Label asked = new Label(question);
        asked.getStyleClass().add("bubble-text");
        asked.setWrapText(true);
        asked.setMaxWidth(560);
        HBox bubble = new HBox(asked);
        bubble.getStyleClass().add("question-bubble");
        HBox row = new HBox(bubble);
        row.setAlignment(Pos.CENTER_RIGHT);
        Label thinking = new Label("Thinking...");
        thinking.getStyleClass().add("thinking");
        lessonContent.getChildren().addAll(row, thinking);
        scrollToBottom();

        runInBackground("followup.py",
                () -> {
                    lessonContent.getChildren().remove(thinking);
                    JsonObject response = readResponse();
                    if (response != null) {
                        lessonContent.getChildren().add(section("Answer", mathText(text(response, "Output"))));
                    }
                    setAsking(false);
                    scrollToBottom();
                },
                () -> {
                    thinking.setText("Couldn't get an answer. Try asking again.");
                    setAsking(false);
                });
    }

    private void showLesson() {
        JsonObject response = readResponse();
        if (response == null) {
            return;
        }
        topicLabel.setText(text(response, "Topic"));
        lessonContent.getChildren().add(section("Lesson", mathText(text(response, "Explanation"))));

        JsonArray videos = response.has("Videos") ? response.getAsJsonArray("Videos") : new JsonArray();
        if (!videos.isEmpty()) {
            VBox links = new VBox(6);
            for (JsonElement element : videos) {
                JsonObject video = element.getAsJsonObject();
                String videoUrl = text(video, "url");
                Hyperlink link = new Hyperlink("▶  " + text(video, "title"));
                link.setWrapText(true);
                link.setOnAction(e -> openInBrowser(videoUrl));
                links.getChildren().add(link);
            }
            lessonContent.getChildren().add(section("Watch next", links));
        }
    }

    private void showPractice() {
        JsonObject response = readResponse();
        if (response == null) {
            return;
        }
        topicLabel.setText(text(response, "Topic"));
        practiceContent.getChildren().add(section("Question", mathText(text(response, "Question"))));

        //Hidden until the student picks an answer
        Label feedback = new Label();
        VBox solution = section("Solution", mathText(text(response, "Answer")));
        solution.getChildren().add(0, feedback);
        setShown(solution, false);

        int correct = response.has("correctAnswer") ? response.get("correctAnswer").getAsInt() : 0;
        JsonArray options = response.has("Options") ? response.getAsJsonArray("Options") : new JsonArray();
        //Each choice is a clickable card; a VBox lets the text wrap to the card's real width
        List<VBox> buttons = new ArrayList<>();
        for (int i = 0; i < options.size(); i++) {
            int number = i + 1;
            VBox option = new VBox(mathText((char) ('A' + i) + ")   " + options.get(i).getAsString()));
            option.getStyleClass().add("option");
            option.setOnMouseClicked(e -> {
                buttons.forEach(b -> b.setDisable(true));
                boolean right = number == correct;
                if (correct >= 1 && correct <= buttons.size()) {
                    buttons.get(correct - 1).getStyleClass().add("correct");
                }
                if (!right) {
                    option.getStyleClass().add("wrong");
                }
                if (right) {
                    feedback.setText("Correct!");
                } else if (correct >= 1) {
                    feedback.setText("Not quite. The answer is " + (char) ('A' + correct - 1) + ".");
                } else {
                    feedback.setText("Here's how to solve it.");
                }
                feedback.getStyleClass().setAll(right ? "feedback-correct" : "feedback-wrong");
                setShown(solution, true);
            });
            buttons.add(option);
        }

        VBox choices = new VBox(10);
        choices.getChildren().addAll(buttons);
        if (buttons.isEmpty()) {
            //Lessons made before options were split out have them inside the question text
            Button reveal = new Button("Show answer");
            reveal.getStyleClass().add("primary");
            reveal.setOnAction(e -> {
                setShown(solution, true);
                reveal.setDisable(true);
            });
            choices.getChildren().add(reveal);
        }
        practiceContent.getChildren().addAll(choices, solution);
    }

    //Scales the shared background video to cover the whole window, cropping the edges
    private void playBackgroundVideo() {
        if (mediaView == null || root == null) {
            return;
        }
        mediaView.setMediaPlayer(MediaPlayerManager.getInstance().getMediaPlayer());
        mediaView.setPreserveRatio(true);
        InvalidationListener cover = o -> {
            double width = root.getWidth();
            double height = root.getHeight();
            double scale = Math.max(width / 1920, height / 1080);
            mediaView.setFitWidth(1920 * scale);
            mediaView.setFitHeight(1080 * scale);
            mediaView.setLayoutX((width - 1920 * scale) / 2);
            mediaView.setLayoutY((height - 1080 * scale) / 2);
        };
        root.widthProperty().addListener(cover);
        root.heightProperty().addListener(cover);
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(root.widthProperty());
        clip.heightProperty().bind(root.heightProperty());
        root.setClip(clip);
    }

    //Swaps the window's content instead of opening a new window
    private void showScreen(ActionEvent event, String fxml) {
        showScreen(((Node) event.getSource()).getScene(), fxml);
    }

    private void showScreen(Scene scene, String fxml) {
        try {
            Parent next = FXMLLoader.load(getClass().getResource(fxml));
            scene.setRoot(next);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void setAsking(boolean asking) {
        textField2.setDisable(asking);
        askButton.setDisable(asking);
    }

    private void scrollToBottom() {
        Platform.runLater(() -> {
            lessonScroll.layout();
            lessonScroll.setVvalue(1.0);
        });
    }

    //Runs an AI script off the UI thread so the window stays responsive
    private void runInBackground(String script, Runnable onSuccess, Runnable onFailure) {
        Thread worker = new Thread(() -> {
            boolean succeeded = runPythonScript(script);
            Platform.runLater(succeeded ? onSuccess : onFailure);
        });
        worker.setDaemon(true);
        worker.start();
    }

    //Runs one of the AI scripts and waits for it to finish writing output.json
    private static boolean runPythonScript(String script) {
        //Prefer the project's virtual environment, which has the AI libraries installed
        String venvPython = ".venv/Scripts/python.exe";
        String python = new File(venvPython).exists() ? venvPython : "python";
        ProcessBuilder pb = new ProcessBuilder(python, "src/SHELLHACKS_2024_PROJECT/" + script);
        pb.inheritIO(); //Show the script's output and errors in the console
        try {
            int exitCode = pb.start().waitFor();
            if (exitCode != 0) {
                System.out.println(script + " failed with exit code " + exitCode);
            }
            return exitCode == 0;
        } catch (IOException e) {
            System.out.println("Couldn't run " + script + ": " + e);
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static JsonObject readResponse() {
        try (FileReader reader = new FileReader(OUTPUT_FILE)) {
            return JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("Response");
        } catch (Exception e) {
            System.out.println("Couldn't read " + OUTPUT_FILE + ": " + e);
            return null;
        }
    }

    private static String text(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value == null || value.isJsonNull() ? "" : value.getAsString();
    }

    //A card with a small heading, used for each block of the lesson
    private static VBox section(String title, Node body) {
        Label heading = new Label(title.toUpperCase());
        heading.getStyleClass().add("section-title");
        VBox box = new VBox(heading, body);
        box.getStyleClass().add("section");
        return box;
    }

    //Splits text on $$ (the LaTeX delimiter) so formulas render as images inline with the words
    private static TextFlow mathText(String content) {
        TextFlow flow = new TextFlow();
        String[] tokens = content.split("\\$\\$");
        for (int i = 0; i < tokens.length; i++) {
            if (i % 2 == 1) {
                flow.getChildren().add(printImage(tokens[i]));
            } else {
                Text normalText = new Text(tokens[i].replace("$", "")); //Remove single dollar signs for simple math equations
                normalText.getStyleClass().add("lesson-text");
                flow.getChildren().add(normalText);
            }
        }
        return flow;
    }

    private static void setShown(Node node, boolean shown) {
        node.setVisible(shown);
        node.setManaged(shown);
    }

    private static void openInBrowser(String url) {
        new Thread(() -> {
            try {
                Desktop.getDesktop().browse(URI.create(url));
            } catch (Exception e) {
                System.out.println("Couldn't open " + url + ": " + e);
            }
        }).start();
    }

    //Converts LaTeX to an image, drawn at 2x so it stays sharp on high-DPI screens
    public static ImageView printImage(String mathExpression) {
        try {
            TeXFormula formula = new TeXFormula(mathExpression);
            TeXIcon formulaIcon = formula.createTeXIcon(TeXConstants.STYLE_TEXT, 38);
            formulaIcon.setForeground(MATH_COLOR);

            BufferedImage bufferedImage = new BufferedImage(
                    Math.max(1, formulaIcon.getIconWidth()),
                    Math.max(1, formulaIcon.getIconHeight()),
                    BufferedImage.TYPE_INT_ARGB
            );
            Graphics2D iconGraphics = bufferedImage.createGraphics();
            iconGraphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            iconGraphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            formulaIcon.paintIcon(null, iconGraphics, 0, 0);
            iconGraphics.dispose();

            ImageView image = new ImageView(SwingFXUtils.toFXImage(bufferedImage, null));
            image.setFitWidth(bufferedImage.getWidth() / 2.0);
            image.setPreserveRatio(true);
            image.setSmooth(true);
            image.setTranslateY(formulaIcon.getIconDepth() / 2.0); //Line the formula's baseline up with the text
            return image;
        } catch (Exception e) {
            System.out.println("Error creating LaTeX image: " + e);
            return new ImageView(); // Return empty ImageView on error
        }
    }
}
