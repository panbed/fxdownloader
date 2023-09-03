package com.panbed.ytdownloader;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.json.*;
import org.apache.commons.io.*;

public class DownloaderController {
    public ImageView thumbPreview;
    public Button downloadButton;
    public Button urlButton;
    public TextField urlTextField;

    public boolean validURL = false;
    public Label titleLabel;
    public Label authorLabel;
    public ImageView thumbBackgroundPreview;

    public Tab downloaderTab;
    public Tab logTab;
    public TextArea logArea;
    public Button killButton;
    public TabPane tabPane;
    public Tab settingsTab;
    public ChoiceBox afChoiceBox;
    public ChoiceBox vfChoiceBox;

    // latest yt-dlp
    // https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp.exe

    // cool not youtube api thing:
    // https://www.youtube.com/oembed?url=http%3A//youtube.com/watch%3Fv%3DM3r2XDceM6A&format=json

    // maybe add later?
    // https://stackoverflow.com/questions/46508055/using-ffmpeg-to-cut-audio-from-to-position

    @FXML
    protected void showStatus(String status) {
        thumbBackgroundPreview.setImage(null);
        downloadButton.setDisable(true);
        switch (status) {
            case "default" -> { // default
                thumbPreview.setImage(new Image("default.png"));
                titleLabel.setText("fxdownloader");
                authorLabel.setText("Enter a URL, then select \"Download\"");
            }
            case "error" -> { // error
                thumbPreview.setImage(new Image("error.png"));
                titleLabel.setText("Unable to parse URL");
                authorLabel.setText("Double check your URL and try again");
            }
            case "loading" -> { // loading
                thumbPreview.setImage(new Image("loading.png"));
                titleLabel.setText("Let's peep this out...");
                authorLabel.setText("Attempting to get video info...");
            }
            case "question" -> { // question
                thumbPreview.setImage(new Image("question.png"));
                titleLabel.setText("Invalid YouTube URL");
                authorLabel.setText("Found a valid YouTube URL, but can't get any info from it. The video might be private, or it's just not a real video.");
            }
            default -> System.out.println("uhhhhhhhh default");
        }
    }

    @FXML
    protected void showThumbnail(String id) throws IOException {
        Image image = new Image(getVideoImageURL(id));
        thumbPreview.setImage(image);
        thumbBackgroundPreview.setImage(image);

        downloadButton.setDisable(false);
        validURL = true;

        JSONObject json = parseJSON(id);
        String title = json.getString("title");
        String author = json.getString("author_name");
        titleLabel.setText(title);
        authorLabel.setText(author);
    }

    @FXML
    protected void onSubmitClick() throws IOException {
        String id = getVideoID(urlTextField.getText());
        System.out.println(id);

        if (id != null) {
            showThumbnail(id);
        }
        else {
            showStatus("error");
        }
    }

    @FXML
    protected void onDownloadClick() throws IOException {
        String id = getVideoID(urlTextField.getText());

        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Choose directory to save to");
        directoryChooser.setInitialDirectory(new File(getJSONConfigAttr("last_directory")));
        File selectedDirectory = directoryChooser.showDialog(downloadButton.getScene().getWindow());

        if (selectedDirectory != null) setJSONConfigAttr("last_directory", selectedDirectory.getAbsolutePath());

        Task<Void> downloadVideoTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                downloadButton.setDisable(true);
                urlButton.setDisable(true);
                downloadVideo(selectedDirectory, id);
                return null;
            }
        };

        downloadVideoTask.setOnSucceeded(e -> {
            downloadButton.setDisable(false);
            urlButton.setDisable(false);
            System.out.println("done!");
            killButton.setDisable(true);
            tabPane.getSelectionModel().select(downloaderTab);
        });

        downloadVideoTask.setOnFailed(e -> {
            System.out.println("failure .........");
            downloadButton.setDisable(false);
            urlButton.setDisable(false);
            killButton.setDisable(true);
        });

        downloadVideoTask.setOnCancelled(e -> {
            System.out.println("canceled ............");
            downloadButton.setDisable(false);
            urlButton.setDisable(false);
            killButton.setDisable(true);
        });

        Thread thread = new Thread(downloadVideoTask);
        thread.start();
    }

    public void checkAndCreateFiles() {
        // if it doesnt exist then create a json config and store it somewhere in the home directory
        JSONObject jsonConfig = new JSONObject();
        System.out.println("need to get yt-dlp exe...");

        // TODO: fix filechooser, i have no idea why its not working right now it just says getScene() returns null
//        FileChooser fileChooser = new FileChooser();
//        fileChooser.setTitle("Select where the yt-dlp executable is located");
//        fileChooser.setInitialDirectory(new File(System.getProperty("user.home")));
//        File selectedExecutable = fileChooser.showOpenDialog(urlTextField.getScene().getWindow());
//
//        jsonConfig.put("ytdlp_location", selectedExecutable.getAbsolutePath());
        jsonConfig.put("ytdlp_location", "C:\\Users\\dev\\IdeaProjects\\ytDownloader\\src\\main\\resources\\programs\\yt-dlp.exe");
        jsonConfig.put("last_directory", System.getProperty("user.home"));
        jsonConfig.put("audio_format", "mp3");
        jsonConfig.put("video_format", "mp4");

        File directory = new File(String.format("%s/.fxdownloader", System.getProperty("user.home")));
        if (!directory.exists()) directory.mkdir();

        try {
            FileWriter file = new FileWriter(String.format("%s/.fxdownloader/config.json", System.getProperty("user.home")));
            file.write(jsonConfig.toString(2));
            file.close();
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        System.out.println("Created JSON config file at " + String.format("%s/.fxdownloader/config.json", System.getProperty("user.home")));
    }

    public void ytdlpCheck(JSONObject jsonObject) throws IOException {
        if (jsonObject.get("ytdlp_location").equals("")) {
            System.out.println("yt-dlp not found, running ytdlpCheck()...");
        }
    }

    public JSONObject getJSONObject() throws IOException {
        File configFile = new File(String.format("%s/.fxdownloader/config.json", System.getProperty("user.home")));
        if (!configFile.isFile()) {
            System.out.println("file doesnt exist, lets create it");
            checkAndCreateFiles();
        }
        String configString = new String(Files.readAllBytes(Paths.get(configFile.getAbsolutePath())));
        return new JSONObject(configString);
    }

    public String getJSONConfigAttr(String key) throws IOException {
        return getJSONObject().getString(key);
    }

    public void setJSONConfigAttr(String key, String value) throws IOException {
//        File configFile = new File(String.format("%s/.fxdownloader/config.json", System.getProperty("user.home")));
        JSONObject jsonObject = getJSONObject();
        jsonObject.put(key, value);

        try {
            FileWriter file = new FileWriter(String.format("%s/.fxdownloader/config.json", System.getProperty("user.home")));
            file.write(jsonObject.toString(2));
            file.close();
        }
        catch (IOException e) {
            System.out.println("error writing config change to json file, uhhhh well idk wat to do now");
            e.printStackTrace();
        }
    }

    public JSONObject parseJSON(String id) throws IOException {
        String url = String.format("https://www.youtube.com/oembed?url=http%%3A//youtube.com/watch%%3Fv%%3D%s&format=json", id);
        System.out.println(url);
        String json = IOUtils.toString(URI.create(url), StandardCharsets.UTF_8);
        return new JSONObject(json);
    }

    public boolean isLatestVersion() {
        AtomicBoolean isLatest = new AtomicBoolean(false);

        try {
            String exeLocation = getJSONConfigAttr("ytdlp_location");
            System.out.println(exeLocation);

            if (exeLocation.equalsIgnoreCase("")) {
                System.out.println("yt-dlp not found!");
            }


            String url = "https://api.github.com/repos/yt-dlp/yt-dlp/releases/latest"; // oh my god github has an api limit i forgot
            String json = IOUtils.toString(URI.create(url), StandardCharsets.UTF_8); // get latest version from github
            JSONObject jsonObject = new JSONObject(json);
            System.out.println(jsonObject.get("name"));
            String ytdlpGithubVersion = jsonObject.get("name").toString();
            final StringBuffer ytdlpVersionBuffer = new StringBuffer();

            Task<Void> checkLatestVersion = new Task<>() {
                @Override
                protected Void call() throws Exception {
                    ProcessBuilder pb = new ProcessBuilder(exeLocation, "--version").redirectErrorStream(true);
                    Process process = pb.start();

                    StringBuilder result = new StringBuilder(80);
                    try (BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                        while (true) {
                            String line = in.readLine();
                            if (line == null)
                                break;
                            result.append(line).append(System.getProperty("line.separator"));

                        }
                    }

                    ytdlpVersionBuffer.append("yt-dlp " + result);

                    return null;
                }
            };

            checkLatestVersion.setOnSucceeded(e -> {
                if (ytdlpGithubVersion.equalsIgnoreCase(ytdlpVersionBuffer.toString())) isLatest.set(true);
            });


        } catch (IOException e) {
            System.out.println("death (ioexception) ");
        } catch (JSONException e) {
            System.out.println("Unable to find latest version of yt-dlp!");
        }

        return isLatest.get();
    }

    public void updateLog(String text) {
        Platform.runLater(() -> {
            logArea.clear();
            logArea.appendText(text);
        });
    }

    public boolean downloadVideo(File location, String id) throws IOException {
        String exeLocation = getJSONConfigAttr("ytdlp_location");
        String directoryLocation;

        if (location != null) {
            directoryLocation = location.getAbsolutePath();
            ProcessBuilder pb = new ProcessBuilder(exeLocation, "-x", "--audio-format", (String) afChoiceBox.getSelectionModel().getSelectedItem(), id, "-P", String.format("home:%s", directoryLocation)).redirectErrorStream(true);
            Process process = pb.start();

            tabPane.getSelectionModel().select(logTab);

            killButton.setDisable(false);
            killButton.setOnAction(event -> {
                process.children().forEach(ProcessHandle::destroy);
                process.destroy();
                try {
                    Thread.sleep(25); // this is maybe bad i think
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                String coolSkull = "skull in progress";
                        // yo this    was    a crazy skull
                Platform.runLater(() -> {
                    logArea.appendText(String.format("%s\n\n** HERE LIES YT-DLP **\n\nTime of death: %s", coolSkull, new Date()));
                });

            });

            StringBuilder result = new StringBuilder(80);
            try (BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                while (true) {
                    String line = in.readLine();
                    if (line == null)
                        break;
                    result.append(line).append(System.getProperty("line.separator"));
                    updateLog(String.valueOf(result));
                }
            }

            return true;
        }

        return false;

//        try (InputStreamReader inputStreamReader = new InputStreamReader(process.getInputStream())) {
//            int c;
//            while ((c = inputStreamReader.read()) >= 0) {
//                updateLog(String.valueOf((char) c));
//            }
//        }
//        return result.toString();
    }

    public String getVideoID(String url) {
        // taken from: https://stackoverflow.com/questions/3452546/how-do-i-get-the-youtube-video-id-from-a-url
        String regex = "(?:youtube(?:-nocookie)?\\.com\\/(?:[^\\/\\n\\s]+\\/\\S+\\/|(?:v|e(?:mbed)?)\\/|\\S*?[?&]vi?=)|youtu\\.be\\/)([a-zA-Z0-9_-]{11})";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(url);

        if (matcher.find()) return matcher.group(1);

        return null;
    }

    // Download file stuff: https://stackoverflow.com/questions/18872611/download-file-from-server-in-java

    public String getVideoImageURL(String id) {
        return String.format("https://img.youtube.com/vi/%s/default.jpg", id);
    }

    public void initalizeChoiceBoxes() throws IOException {
        // supported audio formats according to yt-dlp github:
        // best (default), aac, alac, flac, m4a, mp3, opus, vorbis, wav
        afChoiceBox.getItems().addAll("mp3", "wav", "flac", "aac", "alac", "m4a", "opus", "vorbis", "best");
        vfChoiceBox.getItems().addAll("mp4", "webm", "flv", "3gp");

        afChoiceBox.setValue(getJSONConfigAttr("audio_format"));
        vfChoiceBox.setValue(getJSONConfigAttr("video_format")); // todo: fix video stuff, it still only does audio stuff for now

        afChoiceBox.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            System.out.println(afChoiceBox.getSelectionModel().getSelectedItem());
            try {
                setJSONConfigAttr("audio_format", (String) newValue);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        vfChoiceBox.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            try {
                setJSONConfigAttr("video_format", (String) newValue);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @FXML
    public void initialize() throws IOException {
        showStatus("default");

        JSONObject config = getJSONObject(); // get json obj, if it doesnt exist it gets created
//        System.out.println(isLatestVersion()); // ugh ok ill fix this later but i cant use github api since ill go over rate limits so maybe ill see if i can use built-in yt-dlp functions
        // maybe i can just live with the api limit and set islatest to true always if it fails or something
        initalizeChoiceBoxes();

//        ytdlpCheck(config); // this i need to also fix, maybe just make a tab for downloading yt-dlp?

        urlTextField.textProperty().addListener((observable -> {
            String url = urlTextField.getText();
//            showStatus("loading");
            new Thread(() -> Platform.runLater(() -> {
                if (getVideoID(url) != null) {
                    System.out.println("valid url found, lets try forcing the url to display");
                    try {
                        showThumbnail(getVideoID(url));
                    } catch (IOException e) {
                        // its a fake link!
                        System.out.println("Uh oh!");
                        showStatus("question");
                    }
                }
                else if (url.equals("")) {
                    showStatus("default");
                }
                else {
                    showStatus("error");
                }
            })).start();
        }));

    }

}