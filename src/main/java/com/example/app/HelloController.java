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
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.media.MediaView;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.scene.text.TextFlow;
import javafx.stage.FileChooser;

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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.ResourceBundle;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.scilab.forge.jlatexmath.TeXConstants;
import org.scilab.forge.jlatexmath.TeXFormula;
import org.scilab.forge.jlatexmath.TeXIcon;

//One controller shared by every screen; each FXML only fills in the fields it uses
public class HelloController implements Initializable {
    private static final String OUTPUT_FILE = "src/SHELLHACKS_2024_PROJECT/output.json";
    //A copy of every lesson, saved by the Python scripts, for the Recent lessons list
    private static final String LESSONS_DIR = "src/SHELLHACKS_2024_PROJECT/lessons";
    private static final int RECENT_LESSON_COUNT = 5;
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
    @FXML
    private Label loadingLabel; //The message on loadingOverlay, on screens that can load different things
    @FXML
    private ToggleGroup levelGroup; //Beginner / Intermediate / Advanced
    @FXML
    private VBox recentCard;
    @FXML
    private VBox recentList;

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
    @FXML
    private ScrollPane practiceScroll;
    @FXML
    private Label scoreLabel;

    @FXML
    private VBox quizContent;
    @FXML
    private VBox flashcardContent;

    //Practice score for the current lesson; shared across screens, reset on a new lesson
    private static int questionsAnswered = 0;
    private static int questionsCorrect = 0;

    //Quiz progress: which question is showing, how many were right, and each pick (1-4)
    private static int quizIndex = 0;
    private static int quizCorrect = 0;
    private static final List<Integer> quizPicks = new ArrayList<>();

    //Flashcard deck: card numbers still to review (front of the list is showing), and how many
    //times "Study again" was pressed this round
    private static final List<Integer> deck = new ArrayList<>();
    private static boolean deckStarted = false;
    private static int deckRepeats = 0;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        playBackgroundVideo();
        if (errorLabel != null) {
            errorLabel.managedProperty().bind(errorLabel.textProperty().isNotEmpty()); //No gap when there's no error
        }
        if (recentList != null) {
            showRecentLessons();
        }
        if (lessonContent != null) {
            showLesson();
        }
        if (practiceContent != null) {
            showPractice();
        }
        if (quizContent != null) {
            showQuiz();
        }
        if (flashcardContent != null) {
            showFlashcards();
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

        Toggle selectedLevel = levelGroup.getSelectedToggle();
        String level = selectedLevel == null ? "Intermediate" : (String) selectedLevel.getUserData();
        new JsonCreation().jsonCreate(school, course, topic, level);
        Scene scene = root.getScene();
        loadingOverlay.setVisible(true);
        runInBackground(List.of("default.py"),
                () -> {
                    questionsAnswered = 0;
                    questionsCorrect = 0;
                    showScreen(scene, "scene2.fxml");
                },
                message -> {
                    loadingOverlay.setVisible(false);
                    errorLabel.setText("Couldn't build a lesson. " + message);
                });
    }

    //Start screen: the most recently used lessons, newest first
    private void showRecentLessons() {
        File[] files = new File(LESSONS_DIR).listFiles((dir, name) -> name.endsWith(".json"));
        if (files == null) {
            return;
        }
        Arrays.sort(files, Comparator.comparingLong(File::lastModified).reversed());
        DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("MMM d");
        for (File file : Arrays.copyOf(files, Math.min(RECENT_LESSON_COUNT, files.length))) {
            JsonObject lesson = readResponse(file.getPath());
            if (lesson == null) {
                continue;
            }
            Label topic = new Label(text(lesson, "Topic"));
            topic.getStyleClass().add("recent-topic");
            topic.setWrapText(true);
            String date = Instant.ofEpochMilli(file.lastModified()).atZone(ZoneId.systemDefault()).format(dateFormat);
            //e.g. "Calculus I  ·  Beginner  ·  Oct 22", skipping whatever wasn't saved
            List<String> parts = new ArrayList<>();
            for (String part : List.of(text(lesson, "Course"), text(lesson, "Level"), date)) {
                if (!part.isEmpty()) {
                    parts.add(part);
                }
            }
            Label meta = new Label(String.join("  ·  ", parts));
            meta.getStyleClass().add("recent-meta");

            VBox item = new VBox(topic, meta);
            item.getStyleClass().add("recent-item");
            item.setOnMouseClicked(e -> openRecentLesson(file));
            recentList.getChildren().add(item);
        }
        setShown(recentCard, !recentList.getChildren().isEmpty());
    }

    //Makes a saved lesson the current one and opens it; no Gemini request needed
    private void openRecentLesson(File lessonFile) {
        try {
            Files.copy(lessonFile.toPath(), Path.of(OUTPUT_FILE), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            errorLabel.setText("Couldn't open that lesson: " + e.getMessage());
            return;
        }
        questionsAnswered = 0;
        questionsCorrect = 0;
        showScreen(root.getScene(), "scene2.fxml");
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

        Label thinking = new Label("Thinking...");
        thinking.getStyleClass().add("thinking");
        lessonContent.getChildren().addAll(questionBubble(question), thinking);
        scrollToBottom();

        runInBackground(List.of("followup.py"),
                () -> {
                    lessonContent.getChildren().remove(thinking);
                    JsonObject response = readResponse();
                    if (response != null) {
                        lessonContent.getChildren().add(section("Answer", mathText(text(response, "Output"))));
                    }
                    setAsking(false);
                    scrollToBottom();
                },
                message -> {
                    thinking.setText("Couldn't get an answer. " + message);
                    setAsking(false);
                });
    }

    private void showLesson() {
        JsonObject response = readResponse();
        if (response == null) {
            return;
        }
        topicLabel.setText(text(response, "Topic"));
        //One card per section (Overview, Key ideas, ...); older lessons only have one paragraph
        JsonArray sections = response.has("Sections") ? response.getAsJsonArray("Sections") : new JsonArray();
        for (JsonElement element : sections) {
            lessonContent.getChildren().add(lessonSection(element.getAsJsonObject()));
        }
        if (sections.isEmpty()) {
            lessonContent.getChildren().add(section("Lesson", mathText(text(response, "Explanation"))));
        }
        lessonContent.getChildren().add(goDeeperBox());

        JsonArray videos = response.has("Videos") ? response.getAsJsonArray("Videos") : new JsonArray();
        if (!videos.isEmpty()) {
            HBox cards = new HBox(14);
            for (JsonElement element : videos) {
                cards.getChildren().add(videoCard(element.getAsJsonObject()));
            }
            lessonContent.getChildren().add(section("Watch next", cards));
        }

        //Earlier follow-up questions and answers, so they're still here after visiting the practice screen
        JsonArray followUps = response.has("FollowUps") ? response.getAsJsonArray("FollowUps") : new JsonArray();
        for (JsonElement element : followUps) {
            JsonObject turn = element.getAsJsonObject();
            lessonContent.getChildren().addAll(
                    questionBubble(text(turn, "question")),
                    section("Answer", mathText(text(turn, "answer"))));
        }
    }

    private static VBox lessonSection(JsonObject section) {
        return section(text(section, "heading"), mathText(text(section, "body")));
    }

    //Under the lesson: "Go deeper" adds a new section that goes further (new sections appear just
    //above these buttons), and "Save study notes" exports everything as a PDF
    private VBox goDeeperBox() {
        Button deeper = new Button("Go deeper ↓");
        Button notes = new Button("Save study notes (PDF)");
        Label hint = new Label("Go deeper adds a section with a harder example, where the ideas come from, or related topics.");
        hint.getStyleClass().add("thinking");
        hint.setWrapText(true);
        HBox buttons = new HBox(10, deeper, notes);
        buttons.setAlignment(Pos.CENTER);
        VBox box = new VBox(8, buttons, hint);
        box.setAlignment(Pos.CENTER);
        notes.setOnAction(e -> saveStudyNotes(notes, hint));

        deeper.setOnAction(e -> {
            deeper.setDisable(true);
            hint.setText("Writing a deeper section...");
            runInBackground(List.of("deeper.py"),
                    () -> {
                        JsonObject response = readResponse();
                        JsonArray sections = response == null ? new JsonArray() : response.getAsJsonArray("Sections");
                        if (sections != null && !sections.isEmpty()) {
                            int index = lessonContent.getChildren().indexOf(box);
                            lessonContent.getChildren().add(index,
                                    lessonSection(sections.get(sections.size() - 1).getAsJsonObject()));
                        }
                        hint.setText("Want more? Go deeper again.");
                        deeper.setDisable(false);
                    },
                    message -> {
                        hint.setText("Couldn't go deeper. " + message);
                        deeper.setDisable(false);
                    });
        });
        return box;
    }

    //Asks where to save, writes a study sheet with Gemini if the lesson doesn't have one yet
    //(one request), then builds the PDF and opens it
    private void saveStudyNotes(Button notes, Label hint) {
        JsonObject response = readResponse();
        if (response == null) {
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save study notes");
        chooser.setInitialFileName(text(response, "Topic").replaceAll("[^A-Za-z0-9 ]", "").trim() + " notes.pdf");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF", "*.pdf"));
        File file = chooser.showSaveDialog(root.getScene().getWindow());
        if (file == null) {
            return;
        }
        notes.setDisable(true);
        if (response.has("Summary")) {
            writeStudyNotes(file, notes, hint, "");
            return;
        }
        hint.setText("Writing your study sheet...");
        runInBackground(List.of("summary.py"),
                () -> writeStudyNotes(file, notes, hint, ""),
                //Still save the notes, just without the study sheet at the top
                message -> writeStudyNotes(file, notes, hint, " (without a study sheet: " + message + ")"));
    }

    private void writeStudyNotes(File file, Button notes, Label hint, String note) {
        try {
            NotesPdf.write(readResponse(), file);
            hint.setText("Saved " + file.getName() + note);
            openFile(file);
        } catch (Exception e) {
            hint.setText("Couldn't save the PDF: " + e.getMessage());
        }
        notes.setDisable(false);
    }

    private static void openFile(File file) {
        new Thread(() -> {
            try {
                Desktop.getDesktop().open(file);
            } catch (Exception e) {
                System.out.println("Couldn't open " + file + ": " + e);
            }
        }).start();
    }

    //The student's question, shown as a chat bubble on the right
    private static HBox questionBubble(String question) {
        Label asked = new Label(question);
        asked.getStyleClass().add("bubble-text");
        asked.setWrapText(true);
        asked.setMaxWidth(560);
        HBox bubble = new HBox(asked);
        bubble.getStyleClass().add("question-bubble");
        HBox row = new HBox(bubble);
        row.setAlignment(Pos.CENTER_RIGHT);
        return row;
    }

    //A clickable YouTube thumbnail card; opens the video in the browser
    private static VBox videoCard(JsonObject video) {
        String videoUrl = text(video, "url");
        String thumbnailUrl = text(video, "thumbnail");
        if (thumbnailUrl.isEmpty()) {
            //Older lessons only saved the link, but YouTube thumbnails follow a fixed pattern
            thumbnailUrl = "https://i.ytimg.com/vi/" + videoUrl.substring(videoUrl.indexOf("v=") + 2) + "/mqdefault.jpg";
        }

        ImageView thumbnail = new ImageView(new Image(thumbnailUrl, true)); //Loads in the background
        thumbnail.setPreserveRatio(true);
        Label play = new Label("▶");
        play.getStyleClass().add("play-badge");
        StackPane preview = new StackPane(thumbnail, play);

        Label title = new Label(text(video, "title"));
        title.getStyleClass().add("video-title");
        title.setWrapText(true);
        Label channel = new Label(text(video, "channel"));
        channel.getStyleClass().add("video-channel");
        setShown(channel, !channel.getText().isEmpty());

        VBox card = new VBox(preview, title, channel);
        card.getStyleClass().add("video-card");
        //Zero preferred width lets the row split its space evenly between the cards
        card.setMinWidth(0);
        card.setPrefWidth(0);
        HBox.setHgrow(card, Priority.ALWAYS);
        thumbnail.fitWidthProperty().bind(card.widthProperty().subtract(2));

        Rectangle corners = new Rectangle();
        corners.widthProperty().bind(thumbnail.fitWidthProperty());
        corners.heightProperty().bind(thumbnail.fitWidthProperty().multiply(9.0 / 16));
        corners.setArcWidth(22);
        corners.setArcHeight(22);
        preview.setClip(corners);

        card.setOnMouseClicked(e -> openInBrowser(videoUrl));
        return card;
    }

    private void showPractice() {
        JsonObject response = readResponse();
        if (response == null) {
            return;
        }
        topicLabel.setText(text(response, "Topic"));
        updateScore();
        practiceContent.getChildren().add(section("Question", mathText(text(response, "Question"))));

        //Hidden until the student picks an answer
        Label feedback = new Label();
        VBox solution = section("Solution", mathText(text(response, "Answer")));
        solution.getChildren().add(0, feedback);
        setShown(solution, false);

        int correct = response.has("correctAnswer") ? response.get("correctAnswer").getAsInt() : 0;
        JsonArray options = response.has("Options") ? response.getAsJsonArray("Options") : new JsonArray();
        VBox choices = answerChoices(options, correct, (picked, right) -> {
            questionsAnswered++;
            if (right) {
                questionsCorrect++;
            }
            updateScore();
            setFeedback(feedback, right, correct);
            if (!right && correct >= 1) {
                //Above the Easier / Next / Harder buttons, which are always last
                solution.getChildren().add(solution.getChildren().size() - 1, whyWrongBox(letter(picked)));
            }
            setShown(solution, true);
        });
        if (options.isEmpty()) {
            //Lessons made before options were split out have them inside the question text
            Button reveal = new Button("Show answer");
            reveal.getStyleClass().add("primary");
            reveal.setOnAction(e -> {
                setShown(solution, true);
                reveal.setDisable(true);
            });
            choices.getChildren().add(reveal);
        }
        solution.getChildren().add(nextQuestionButtons());
        practiceContent.getChildren().addAll(choices, solution);
    }

    //Four clickable answer cards. On a pick they lock, the right one turns green (and the pick red
    //if it was wrong), then onPicked gets the picked number (1-4) and whether it was right.
    private static VBox answerChoices(JsonArray options, int correct, BiConsumer<Integer, Boolean> onPicked) {
        List<VBox> cards = new ArrayList<>();
        for (int i = 0; i < options.size(); i++) {
            int number = i + 1;
            //A VBox lets the text wrap to the card's real width
            VBox option = new VBox(mathText(letter(number) + ")   " + options.get(i).getAsString()));
            option.getStyleClass().add("option");
            option.setOnMouseClicked(e -> {
                cards.forEach(card -> card.setDisable(true));
                boolean right = number == correct;
                if (correct >= 1 && correct <= cards.size()) {
                    cards.get(correct - 1).getStyleClass().add("correct");
                }
                if (!right) {
                    option.getStyleClass().add("wrong");
                }
                onPicked.accept(number, right);
            });
            cards.add(option);
        }
        VBox choices = new VBox(10);
        choices.getChildren().addAll(cards);
        return choices;
    }

    private static void setFeedback(Label feedback, boolean right, int correct) {
        if (right) {
            feedback.setText("Correct!");
        } else if (correct >= 1) {
            feedback.setText("Not quite. The answer is " + letter(correct) + ".");
        } else {
            feedback.setText("Here's how to solve it.");
        }
        feedback.getStyleClass().setAll(right ? "feedback-correct" : "feedback-wrong");
    }

    //1 -> 'A', 2 -> 'B', ...
    private static char letter(int number) {
        return (char) ('A' + number - 1);
    }

    //Lesson screen: generate a 5-question quiz, then open the quiz screen
    public void startQuiz(ActionEvent event) {
        generateQuiz(root.getScene());
    }

    private void generateQuiz(Scene scene) {
        errorLabel.setText("");
        loadingLabel.setText("Writing your quiz...");
        loadingOverlay.setVisible(true);
        runInBackground(List.of("quiz.py"),
                () -> {
                    quizIndex = 0;
                    quizCorrect = 0;
                    quizPicks.clear();
                    showScreen(scene, "quiz.fxml");
                },
                message -> {
                    loadingOverlay.setVisible(false);
                    errorLabel.setText("Couldn't make a quiz. " + message);
                });
    }

    //Quiz screen: the current question, or the results once all are answered
    private void showQuiz() {
        quizContent.getChildren().clear();
        JsonObject response = readResponse();
        JsonArray quiz = response != null && response.has("Quiz") ? response.getAsJsonArray("Quiz") : new JsonArray();
        if (response != null) {
            topicLabel.setText(text(response, "Topic"));
        }
        if (quiz.isEmpty()) {
            errorLabel.setText("No quiz found for this lesson. Go back and press Quiz me.");
            return;
        }
        if (quizIndex >= quiz.size()) {
            showQuizResults(quiz);
            return;
        }

        scoreLabel.setText("Question " + (quizIndex + 1) + " of " + quiz.size());
        JsonObject question = quiz.get(quizIndex).getAsJsonObject();
        int correct = question.get("correctAnswer").getAsInt();
        quizContent.getChildren().add(section("Question " + (quizIndex + 1), mathText(text(question, "Question"))));

        Label feedback = new Label();
        VBox solution = section("Solution", mathText(text(question, "Answer")));
        solution.getChildren().add(0, feedback);
        boolean last = quizIndex == quiz.size() - 1;
        Button next = new Button(last ? "See results" : "Next question →");
        next.getStyleClass().add("primary");
        next.setOnAction(e -> {
            quizIndex++;
            showQuiz();
            practiceScroll.setVvalue(0);
        });
        HBox actions = new HBox(next);
        actions.getStyleClass().add("practice-actions");
        solution.getChildren().add(actions);
        setShown(solution, false);

        VBox choices = answerChoices(question.getAsJsonArray("Options"), correct, (picked, right) -> {
            quizPicks.add(picked);
            if (right) {
                quizCorrect++;
            }
            setFeedback(feedback, right, correct);
            setShown(solution, true);
        });
        quizContent.getChildren().addAll(choices, solution);
    }

    private void showQuizResults(JsonArray quiz) {
        scoreLabel.setText("Score  " + quizCorrect + " / " + quiz.size());
        Label score = new Label(quizCorrect + " / " + quiz.size());
        score.getStyleClass().add("quiz-score");
        double ratio = (double) quizCorrect / quiz.size();
        Label message = new Label(ratio == 1 ? "Perfect score!"
                : ratio >= 0.6 ? "Nice work. Review the ones you missed below."
                : "Worth another look. Check the lesson, then retake the quiz.");
        message.getStyleClass().add("subtitle");
        quizContent.getChildren().add(section("Your score", new VBox(4, score, message)));

        //One row per question: check or cross, the question, and the right answer if missed
        VBox review = new VBox(12);
        for (int i = 0; i < quiz.size(); i++) {
            JsonObject question = quiz.get(i).getAsJsonObject();
            int correct = question.get("correctAnswer").getAsInt();
            int picked = i < quizPicks.size() ? quizPicks.get(i) : 0;
            boolean right = picked == correct;
            Label mark = new Label(right ? "✓" : "✗");
            mark.getStyleClass().add(right ? "result-right" : "result-wrong");
            mark.setMinWidth(Region.USE_PREF_SIZE); //Otherwise long questions squeeze the ✗ into "..."
            VBox details = new VBox(4, mathText((i + 1) + ".  " + text(question, "Question")));
            if (!right && correct >= 1) {
                Label answer = new Label("You picked " + letter(picked) + ", the answer is " + letter(correct) + ".");
                answer.getStyleClass().add("result-note");
                details.getChildren().add(answer);
            }
            HBox.setHgrow(details, Priority.ALWAYS);
            review.getChildren().add(new HBox(14, mark, details));
        }
        quizContent.getChildren().add(section("Review", review));

        Button retake = new Button("Retake this quiz");
        retake.setOnAction(e -> {
            quizIndex = 0;
            quizCorrect = 0;
            quizPicks.clear();
            showQuiz();
            practiceScroll.setVvalue(0);
        });
        Button fresh = new Button("New quiz");
        fresh.getStyleClass().add("primary");
        fresh.setOnAction(e -> generateQuiz(root.getScene()));
        HBox actions = new HBox(10, fresh, retake);
        quizContent.getChildren().add(actions);
    }

    //Lesson screen: open the lesson's flashcards, making them first if there aren't any yet
    public void openFlashcards(ActionEvent event) {
        JsonObject response = readResponse();
        if (response != null && response.has("Flashcards") && !response.getAsJsonArray("Flashcards").isEmpty()) {
            resetDeck();
            showScreen(root.getScene(), "flashcards.fxml");
        } else {
            generateFlashcards(root.getScene());
        }
    }

    private void generateFlashcards(Scene scene) {
        errorLabel.setText("");
        loadingLabel.setText("Making your flashcards...");
        loadingOverlay.setVisible(true);
        runInBackground(List.of("flashcards.py"),
                () -> {
                    resetDeck();
                    showScreen(scene, "flashcards.fxml");
                },
                message -> {
                    loadingOverlay.setVisible(false);
                    errorLabel.setText("Couldn't make flashcards. " + message);
                });
    }

    private static void resetDeck() {
        deck.clear();
        deckStarted = false;
        deckRepeats = 0;
    }

    //Flashcards screen: the card on top of the deck, or a summary once every card is done
    private void showFlashcards() {
        flashcardContent.getChildren().clear();
        JsonObject response = readResponse();
        JsonArray cards = response != null && response.has("Flashcards")
                ? response.getAsJsonArray("Flashcards") : new JsonArray();
        if (response != null) {
            topicLabel.setText(text(response, "Topic"));
        }
        if (cards.isEmpty()) {
            errorLabel.setText("No flashcards found for this lesson. Go back and press Flashcards.");
            return;
        }
        if (!deckStarted) {
            for (int i = 0; i < cards.size(); i++) {
                deck.add(i);
            }
            deckStarted = true;
        }
        if (deck.isEmpty()) {
            showDeckFinished(cards);
            return;
        }

        scoreLabel.setText(deck.size() + (deck.size() == 1 ? " card left" : " cards left"));
        JsonObject card = cards.get(deck.get(0)).getAsJsonObject();
        Label side = new Label("QUESTION");
        side.getStyleClass().add("section-title");
        VBox face = new VBox(16, side, flashcardText(text(card, "front")));
        face.getStyleClass().add("flashcard");
        Label hint = new Label("Click the card to see the answer");
        hint.getStyleClass().add("thinking");

        Button again = new Button("Study again");
        Button gotIt = new Button("Got it");
        gotIt.getStyleClass().add("primary");
        again.setDisable(true);
        gotIt.setDisable(true);
        again.setOnAction(e -> {
            deck.add(deck.remove(0)); //Back of the deck, so it comes up again later
            deckRepeats++;
            showFlashcards();
        });
        gotIt.setOnAction(e -> {
            deck.remove(0);
            showFlashcards();
        });

        //Each click flips between question and answer; marking unlocks once the answer was seen
        face.setOnMouseClicked(e -> {
            boolean showingAnswer = face.getStyleClass().contains("flipped");
            side.setText(showingAnswer ? "QUESTION" : "ANSWER");
            face.getChildren().set(1, flashcardText(text(card, showingAnswer ? "front" : "back")));
            if (showingAnswer) {
                face.getStyleClass().remove("flipped");
            } else {
                face.getStyleClass().add("flipped");
            }
            hint.setText("Did you know it? Click the card to flip it back.");
            again.setDisable(false);
            gotIt.setDisable(false);
        });

        HBox buttons = new HBox(12, again, gotIt);
        buttons.setAlignment(Pos.CENTER);
        VBox controls = new VBox(12, hint, buttons);
        controls.setAlignment(Pos.CENTER);
        flashcardContent.getChildren().addAll(face, controls);
    }

    private void showDeckFinished(JsonArray cards) {
        scoreLabel.setText("All done");
        Label done = new Label("All " + cards.size() + " cards done!");
        done.getStyleClass().add("quiz-score");
        Label note = new Label(deckRepeats == 0
                ? "You knew every card the first time."
                : "You marked cards to study again " + deckRepeats + (deckRepeats == 1 ? " time." : " times.")
                        + " Go through them once more to lock them in.");
        note.getStyleClass().add("subtitle");
        note.setWrapText(true);
        flashcardContent.getChildren().add(section("Nice work", new VBox(6, done, note)));

        Button again = new Button("Go through them again");
        again.getStyleClass().add("primary");
        again.setOnAction(e -> {
            resetDeck();
            showFlashcards();
        });
        Button fresh = new Button("New set");
        fresh.setOnAction(e -> generateFlashcards(root.getScene()));
        Button export = new Button("Export flashcards");
        export.setOnAction(e -> exportFlashcards(cards));
        flashcardContent.getChildren().add(new HBox(10, again, fresh, export));
    }

    //Bigger text than the lesson, centered on the card
    private static TextFlow flashcardText(String content) {
        TextFlow flow = mathText(content);
        flow.getStyleClass().add("flashcard-flow");
        flow.setTextAlignment(TextAlignment.CENTER);
        return flow;
    }

    //Saves the deck as a tab-separated file: one card per line, front<TAB>back.
    //Both Anki and Quizlet import this; $$...$$ becomes \(...\), which Anki renders as math.
    private void exportFlashcards(JsonArray cards) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save flashcards");
        chooser.setInitialFileName(text(readResponse(), "Topic").replaceAll("[^A-Za-z0-9 ]", "").trim() + " flashcards.txt");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Text file (tab-separated)", "*.txt"));
        File file = chooser.showSaveDialog(root.getScene().getWindow());
        if (file == null) {
            return;
        }
        StringBuilder lines = new StringBuilder();
        for (JsonElement element : cards) {
            JsonObject card = element.getAsJsonObject();
            lines.append(exportText(text(card, "front"))).append('\t')
                    .append(exportText(text(card, "back"))).append('\n');
        }
        try {
            Files.writeString(file.toPath(), lines.toString());
            errorLabel.setText("");
            scoreLabel.setText("Saved");
        } catch (IOException e) {
            errorLabel.setText("Couldn't save the file: " + e.getMessage());
        }
    }

    private static String exportText(String text) {
        //Tabs and newlines would break the one-card-per-line format
        return text.replaceAll("\\$\\$(.+?)\\$\\$", "\\\\($1\\\\)").replaceAll("[\\t\\r\\n]+", " ");
    }

    //"Why is B wrong?" after a wrong answer: asks Gemini about the student's specific choice.
    //followup.py already sends the current practice question and options as context.
    private VBox whyWrongBox(char picked) {
        Button why = new Button("Why is " + picked + " wrong?");
        Label status = new Label();
        status.getStyleClass().add("thinking");
        setShown(status, false);
        VBox box = new VBox(10, why, status);
        box.getStyleClass().add("practice-actions");

        why.setOnAction(e -> {
            why.setDisable(true);
            status.setText("Thinking...");
            setShown(status, true);
            new JsonCreation().genJson("On the practice problem, I picked " + picked + ". Why is that wrong?");
            runInBackground(List.of("followup.py"),
                    () -> {
                        JsonObject response = readResponse();
                        Label heading = new Label("WHY " + picked + " IS WRONG");
                        heading.getStyleClass().add("section-title");
                        box.getChildren().setAll(heading, mathText(response == null ? "" : text(response, "Output")));
                    },
                    message -> {
                        status.setText("Couldn't get an explanation. " + message);
                        why.setDisable(false);
                    });
        });
        return box;
    }

    //Easier / Next question / Harder, shown under the solution once a question is answered
    private VBox nextQuestionButtons() {
        Button easier = new Button("Easier");
        Button next = new Button("Next question");
        next.getStyleClass().add("primary");
        Button harder = new Button("Harder");
        Label status = new Label();
        status.getStyleClass().add("thinking");
        setShown(status, false);

        List<Button> all = List.of(easier, next, harder);
        easier.setOnAction(e -> loadNewQuestion("easier", all, status));
        next.setOnAction(e -> loadNewQuestion("same", all, status));
        harder.setOnAction(e -> loadNewQuestion("harder", all, status));

        HBox row = new HBox(10, easier, next, harder);
        row.setAlignment(Pos.CENTER_LEFT);
        VBox actions = new VBox(10, row, status);
        actions.getStyleClass().add("practice-actions");
        return actions;
    }

    private void loadNewQuestion(String difficulty, List<Button> buttons, Label status) {
        buttons.forEach(b -> b.setDisable(true));
        status.setText("Writing a new question...");
        setShown(status, true);
        runInBackground(List.of("practice.py", difficulty),
                () -> {
                    practiceContent.getChildren().clear();
                    showPractice();
                    practiceScroll.setVvalue(0);
                },
                message -> {
                    status.setText("Couldn't get a new question. " + message);
                    buttons.forEach(b -> b.setDisable(false));
                });
    }

    private void updateScore() {
        scoreLabel.setText(questionsAnswered == 0
                ? "No answers yet"
                : "Score  " + questionsCorrect + " / " + questionsAnswered);
    }

    //Scales the shared background video to cover the whole window, cropping the edges
    private void playBackgroundVideo() {
        if (mediaView == null || root == null) {
            return;
        }
        mediaView.mediaPlayerProperty().bind(MediaPlayerManager.getInstance().mediaPlayerProperty());
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
    //command is the script name followed by any arguments, e.g. ["practice.py", "harder"]
    //onFailure receives a message explaining what went wrong
    private void runInBackground(List<String> command, Runnable onSuccess, Consumer<String> onFailure) {
        Thread worker = new Thread(() -> {
            int exitCode = runPythonScript(command);
            Platform.runLater(() -> {
                if (exitCode == 0) {
                    onSuccess.run();
                } else {
                    onFailure.accept(errorMessage(exitCode));
                }
            });
        });
        worker.setDaemon(true);
        worker.start();
    }

    //Exit codes come from run_script() in default.py
    private static String errorMessage(int exitCode) {
        return switch (exitCode) {
            case 3 -> "You've used today's free Gemini requests. They reset at midnight Pacific time.";
            case 4 -> "Gemini is busy right now. Try again in a minute.";
            case 5 -> "Gemini rejected the API key. Check GEMINI_API_KEY in the .env file.";
            default -> "Something went wrong. Check your internet connection, and the console for details.";
        };
    }

    //Runs one of the AI scripts, waits for it to finish writing output.json, and returns its exit code
    private static int runPythonScript(List<String> command) {
        //Prefer the project's virtual environment, which has the AI libraries installed
        String venvPython = ".venv/Scripts/python.exe";
        String python = new File(venvPython).exists() ? venvPython : "python";
        String script = command.get(0);
        List<String> args = new ArrayList<>(List.of(python, "src/SHELLHACKS_2024_PROJECT/" + script));
        args.addAll(command.subList(1, command.size()));
        ProcessBuilder pb = new ProcessBuilder(args);
        pb.inheritIO(); //Show the script's output and errors in the console
        try {
            int exitCode = pb.start().waitFor();
            if (exitCode != 0) {
                System.out.println(script + " failed with exit code " + exitCode);
            }
            return exitCode;
        } catch (IOException e) {
            System.out.println("Couldn't run " + script + ": " + e);
            return -1;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return -1;
        }
    }

    //The current lesson
    private static JsonObject readResponse() {
        return readResponse(OUTPUT_FILE);
    }

    private static JsonObject readResponse(String path) {
        try (FileReader reader = new FileReader(path)) {
            return JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("Response");
        } catch (Exception e) {
            System.out.println("Couldn't read " + path + ": " + e);
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
        String[] tokens = NotesPdf.cleanLineBreaks(content).split("\\$\\$");
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
