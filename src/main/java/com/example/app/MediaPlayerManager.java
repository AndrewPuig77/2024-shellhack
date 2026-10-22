package com.example.app;

import javafx.animation.PauseTransition;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.util.Duration;

import java.io.File;

public class MediaPlayerManager {
    //Loading the video sometimes fails at random (ERROR_MEDIA_INVALID); a fresh player usually works
    private static final int MAX_RETRIES = 3;

    private static MediaPlayerManager instance;
    //Screens bind their MediaView to this, so a replacement player shows up on every screen
    private final ObjectProperty<MediaPlayer> mediaPlayer = new SimpleObjectProperty<>();
    private int retries = 0;

    private MediaPlayerManager() {
        // Private constructor to prevent instantiation
        start();
    }

    private void start() {
        try {
            // Silent copy of the stock video: JavaFX halts playback if it can't open an audio device
            File file = new File("src/main/resources/com/example/app/background.mp4");
            MediaPlayer player = new MediaPlayer(new Media(file.toURI().toString()));
            player.setOnError(() -> {
                AppLog.log("Background video error: " + player.getError());
                player.dispose();
                retryLater();
            });
            player.setCycleCount(MediaPlayer.INDEFINITE); // Loop the video
            player.play(); // Start playing
            mediaPlayer.set(player);
        } catch (Exception e) {
            AppLog.log("Background video error: " + e);
            retryLater();
        }
    }

    private void retryLater() {
        if (retries >= MAX_RETRIES) {
            AppLog.log("Giving up on the background video");
            return;
        }
        retries++;
        PauseTransition wait = new PauseTransition(Duration.seconds(1));
        wait.setOnFinished(e -> start());
        wait.play();
    }

    public static MediaPlayerManager getInstance() {
        if (instance == null) {
            instance = new MediaPlayerManager();
        }
        return instance;
    }

    public ObjectProperty<MediaPlayer> mediaPlayerProperty() {
        return mediaPlayer;
    }
}
