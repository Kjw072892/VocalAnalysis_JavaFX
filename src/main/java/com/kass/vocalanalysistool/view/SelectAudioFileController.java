package com.kass.vocalanalysistool.view;


import com.kass.vocalanalysistool.common.Properties;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.image.Image;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

public class SelectAudioFileController implements PropertyChangeListener {

    /**
     * The logger object for debugging.
     */
    private final Logger logger = Logger.getLogger(this.getClass().getName());

    /**
     * Used to execute property change events.
     */
    private final PropertyChangeSupport myChanges = new PropertyChangeSupport(this);

    /**
     * The open file button
     */
    @FXML
    private Button myOpenFileButton;

    /**
     * The exit button
     */
    @FXML
    private Button myExitButton;

    /**
     * Used to initialize certain features.
     */
    @FXML
    private void initialize() {

    }

    /**
     * Opens the filechooser window where the user is able to choose the audio file.
     */
    @FXML
    private void handleOpenFile() throws IOException {

        logger.setLevel(Level.INFO);

        final FileChooser fileChooser = new FileChooser();

        fileChooser.setTitle("Select Audio File");

        final File file = fileChooser.showOpenDialog(myOpenFileButton.getScene().getWindow());

        if (file != null) {
            final String path = file.getAbsolutePath();

            logger.info(() -> "Path: " + path);

            final Stage thisStage = (Stage) myOpenFileButton.getScene().getWindow();
            thisStage.close();

            final FXMLLoader loadingScreenFXML = new FXMLLoader(getClass().getResource(
                    "/com/kass/vocalanalysistool/gui/LoadingScreen.fxml"));
            final Scene loadingScreenScene = new Scene(loadingScreenFXML.load());
            final LoadingScreenController loadingScreenController = loadingScreenFXML.getController();
            loadingScreenController.setMyMainSceneController(this);
            final Stage loadingScreenStage = new Stage();
            loadingScreenStage.initStyle(StageStyle.UNDECORATED);
            loadingScreenStage.setScene(loadingScreenScene);
            loadingScreenStage.getIcons().add(new Image(Objects.requireNonNull
                    (getClass().getResourceAsStream
                            ("/com/kass/vocalanalysistool/icons/vocal_analysis_icon.png"))));
            loadingScreenStage.setResizable(false);
            loadingScreenStage.setAlwaysOnTop(true);
            loadingScreenStage.show();

            final Task<Void> task = getThreadedTask(path, loadingScreenController, loadingScreenStage);

            final Thread worker = new Thread(task, "PythonRunner");
            worker.setDaemon(true);
            worker.start();


        }
    }

    private Task<Void> getThreadedTask(String path, LoadingScreenController loadingScreenController, Stage loadingScreenStage) {
        final Task<Void> task = new Task<>() {
            @Override
            protected Void call() {
                runPythonScript(path);
                myChanges.firePropertyChange(Properties.UPDATE_PROGRESS.toString(), 0,
                        (double) 1);
                return null;
            }
        };

        task.setOnSucceeded(theEvent -> {

            if (loadingScreenController.getProgressStatus() == (double) 1) {
                myChanges.removePropertyChangeListener(loadingScreenController);
                loadingScreenStage.close();
            }

            try {
                final FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/kass" +
                        "/vocalanalysistool/gui/AudioData.fxml"));
                final Parent root = loader.load();

                //Passing the data to the AudioDataController class
                final AudioDataController dataController = loader.getController();
                //dataController.setResults(results);

                final Stage audioDataController = new Stage();
                audioDataController.setTitle("Analysis Results");
                audioDataController.setScene(new Scene(root));
                audioDataController.show();
                audioDataController.setResizable(false);
                audioDataController.getIcons().add(new Image(Objects.requireNonNull
                        (getClass().getResourceAsStream
                                ("/com/kass/vocalanalysistool/icons/vocal_analysis_icon.png"))));

            } catch (final IOException theException) {

                logger.log(Level.SEVERE, "No File Found!", theException);

            }
        });

        task.setOnFailed(theEvent -> {
            myChanges.removePropertyChangeListener(loadingScreenController);
            loadingScreenStage.close();
            logger.log(Level.SEVERE, "Processing failed", task.getException());

        });
        return task;
    }

    /**
     * Handles the exit of the stage
     */
    @FXML
    private void handleExit() {
        Stage exitStage = (Stage) myExitButton.getScene().getWindow();
        exitStage.close();
    }

    /**
     * Gets the temporary location of the python file for execution.
     *
     * @param resourcePath the path of the resource file.
     * @param suffix       the file extension type.
     * @return returns the path of the temporary file.
     * @throws IOException Thrown if the input stream is null
     */
    private Path extractResourceToTemp(final String resourcePath, final String suffix) throws IOException {
        final Path tmp = Files.createTempFile("vat_", suffix);
        tmp.toFile().deleteOnExit();
        try (final InputStream in = getClass().getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IOException("Resource not found");
            }
            Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
        }

        return tmp;
    }

    /**
     * Gets the directory of the application.
     *
     * @return the path of the directory that the application is installed on.
     */
    private Path getAppDir() {
        return Path.of(System.getProperty("user.dir")).toAbsolutePath();
    }

    /**
     * Runs the python script to extract praat data from the chosen audio file.
     *
     * @param theFilePath the file path of the audio file.
     */
    private void runPythonScript(final String theFilePath) {
        try {
            final Path pythonScript = extractResourceToTemp(
                    "/VocalAnalysisToolKit/Vocal_Analysis_Script.py", ".py");
            final Path setupBat = extractResourceToTemp("/pythonInstall.bat", ".bat");

            final Path appDir = getAppDir();

            myChanges.firePropertyChange(Properties.UPDATE_PROGRESS.toString(), 0,
                    (double) 10);

            myChanges.firePropertyChange(Properties.UPDATE_PROGRESS.toString(), 0,
                    (double) 16 / 100);
            // 1) Run setup in appDir so .venv is created at appDir\.venv
            final ProcessBuilder setupPB = new ProcessBuilder("cmd.exe", "/c",
                    setupBat.toString());
            setupPB.directory(appDir.toFile());
            setupPB.redirectErrorStream(true);
            final Process setupProc = setupPB.start();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(setupProc.getInputStream()))) {
                String ln;
                while ((ln = r.readLine()) != null) logger.info("[setup] " + ln);
            }
            int setupExit = setupProc.waitFor();
            if (setupExit != 0) {
                logger.severe("Environment setup failed (exit " + setupExit + "); aborting.");
                return;
            }

            myChanges.firePropertyChange(Properties.UPDATE_PROGRESS.toString(), 0,
                    (double) 32 / 100);
            // 2) Resolve venv python; do not silently fall back
            final Path venvPy = appDir.resolve(".venv").resolve("Scripts").resolve("python.exe");
            if (!Files.exists(venvPy)) {
                throw new IllegalStateException("Venv python not found at " + venvPy + ". Ensure setup ran in " + appDir);
            }
            final String pythonExe = venvPy.toString();

            // Helper to run a short python/pip command and log all output
            Function<String[], Integer> run = (args) -> {
                try {
                    Process p = getProcess(new ProcessBuilder(args), appDir);
                    try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                        String s;
                        while ((s = br.readLine()) != null) logger.info("[pip] " + s);
                    }
                    return p.waitFor();
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Subprocess failed: " + String.join(" ", args), e);
                    return -1;
                }
            };

            myChanges.firePropertyChange(Properties.UPDATE_PROGRESS.toString(), 0,
                    (double) 48 / 100);
            // 3) Ensure matplotlib is installed in the venv
            final Path req = appDir.resolve("requirements.txt");
            int code;
            if (Files.exists(req)) {
                logger.info("Installing requirements from: " + req);
                code = run.apply(new String[]{pythonExe, "-m", "pip", "install", "-r", req.toString()});
                if (code != 0)
                    throw new IllegalStateException("pip install -r failed with code " + code);
            } else {
                // Minimal guarantee
                logger.info("requirements.txt not found in " + appDir + " — installing matplotlib explicitly.");
                code = run.apply(new String[]{pythonExe, "-m", "pip", "install", "matplotlib"});
                if (code != 0)
                    throw new IllegalStateException("pip install matplotlib failed with code " + code);
            }

            myChanges.firePropertyChange(Properties.UPDATE_PROGRESS.toString(), 0,
                    (double) 64 / 100);
            // 4) Probe: show interpreter & matplotlib version (fail fast if missing)
            code = run.apply(new String[]{pythonExe, "-c",
                    "import sys; print('[PyProbe] exe:', sys.executable); " +
                            "import importlib, pkgutil; " +
                            "m = importlib.util.find_spec('matplotlib'); " +
                            "print('[PyProbe] matplotlib present:', bool(m)); " +
                            "import matplotlib; print('[PyProbe] matplotlib version:', matplotlib.__version__)"
            });
            if (code != 0)
                throw new IllegalStateException("Probe failed; matplotlib not importable.");

            myChanges.firePropertyChange(Properties.UPDATE_PROGRESS.toString(), 0,
                    (double) 95 / 100);
            // 5) Run your real script
            Process process = getProcess(new ProcessBuilder(pythonExe,
                    pythonScript.toString(), theFilePath), appDir);
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) logger.info("[Python] " + line);
            }
            int exit = process.waitFor();
            if (exit != 0) logger.severe("Python script exited with code " + exit);
            myChanges.firePropertyChange(Properties.UPDATE_PROGRESS.toString(), 0, (double) 1);
        } catch (final IOException | InterruptedException theEvent) {
            logger.log(Level.SEVERE, "Failed to run Python script", theEvent);
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Helper method to condense code
     *
     * @param theCommandArgs the arguments of which process builder is executing
     * @param theAppDir      The path of the application directory
     * @return Returns a process object to execute the commands.
     * @throws IOException Thrown if the path is invalid.
     */
    private static Process getProcess(final ProcessBuilder theCommandArgs,
                                      final Path theAppDir) throws IOException {

        theCommandArgs.directory(theAppDir.toFile());
        theCommandArgs.redirectErrorStream(true);

        final Map<String, String> env = theCommandArgs.environment();
        env.remove("PYTHONHOME");
        env.remove("PYTHONPATH");
        env.put("MPLBACKEND", "Agg");
        env.putIfAbsent("PYTHONIOENCODING", "utf-8");



        return theCommandArgs.start();
    }




    public void addPropertyChangeListener(final PropertyChangeListener theListener) {
        myChanges.addPropertyChangeListener(theListener);
    }


    /**
     * Handles property change events.
     *
     * @param theEvent A PropertyChangeEvent object describing the event source
     *                 and the property that has changed.
     */
    @Override
    public void propertyChange(final PropertyChangeEvent theEvent) {

    }
}
