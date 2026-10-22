package com.example.app;

import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;

import java.io.File;

public class MediaPlayerManager {
    private static MediaPlayerManager instance;
    private MediaPlayer mediaPlayer;

    private MediaPlayerManager() {
        // Private constructor to prevent instantiation
        // Silent copy of the stock video: JavaFX halts playback if it can't open an audio device
        File file = new File("src/main/resources/com/example/app/background.mp4");
        Media media = new Media(file.toURI().toString());
        mediaPlayer = new MediaPlayer(media);
        mediaPlayer.setOnError(() -> System.out.println("Background video error: " + mediaPlayer.getError()));
        mediaPlayer.setCycleCount(MediaPlayer.INDEFINITE); // Loop the video
        mediaPlayer.play(); // Start playing
    }

    public static MediaPlayerManager getInstance() {
        if (instance == null) {
            instance = new MediaPlayerManager();
        }
        return instance;
    }

    public MediaPlayer getMediaPlayer() {
        return mediaPlayer;
    }
}
