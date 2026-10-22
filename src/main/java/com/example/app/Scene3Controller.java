//package com.example.app;
//
//
//
//import javafx.event.ActionEvent;
//import javafx.fxml.FXML;
//import javafx.fxml.FXMLLoader;
//import javafx.fxml.Initializable;
//import javafx.scene.Node;
//import javafx.scene.Parent;
//import javafx.scene.Scene;
//import javafx.scene.media.MediaPlayer;
//import javafx.scene.media.MediaView;
//import javafx.stage.Stage;
//
//import java.io.IOException;
//import java.net.URL;
//import java.util.ResourceBundle;
//
//public class Scene3Controller implements Initializable {
//
//    @FXML
//    private MediaView mediaView;
//    private Stage stage;
//    private Scene scene;
//    private Parent root;
//
//    @Override
//    public void initialize(URL url, ResourceBundle resourceBundle) {
//        // Reattach the shared MediaPlayer instance to the MediaView in this scene
//        MediaPlayer mediaPlayer = MediaPlayerManager.getInstance().getMediaPlayer();
//        mediaView.setMediaPlayer(mediaPlayer);
//    }
//
//    public void switchToScene3(ActionEvent event) throws IOException {
//        // Switch back to the first scene
//        FXMLLoader loader = new FXMLLoader(getClass().getResource("scene3.fxml"));
//        Parent root = loader.load();
//        Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
//        Scene scene = new Scene(root);
//        stage.setScene(scene);
//        stage.show();
//    }
//}