package com.kass.vocalanalysistool.view;


import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.image.Image;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

public class SelectAudioFileController {
    Logger logger = Logger.getLogger(this.getClass().getName());

    @FXML
    private Button openFileButton;

    @FXML
    private Button exitButton;

    @FXML
    public void initialize() {

    }

    @FXML
    private void handleOpenFile() throws IOException {

        logger.setLevel(Level.INFO);

        FileChooser fileChooser = new FileChooser();

        fileChooser.setTitle("Select Audio File");

        File file = fileChooser.showOpenDialog(openFileButton.getScene().getWindow());

        if (file != null) {
            String path = file.getAbsolutePath();

            logger.info(() -> "Path: " + path);

            runPythonScript(path);

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

                final Stage selectAudioFileControllerScene =
                        (Stage) openFileButton.getScene().getWindow();
                selectAudioFileControllerScene.close();

            } catch (final IOException e) {

                logger.log(Level.SEVERE, "No File Found!", e);

            }
        }
    }

    @FXML
    private void handleExit() {
        Stage exitStage = (Stage) exitButton.getScene().getWindow();
        exitStage.close();
    }


    private Path extractResourceToTemp(final String resourcePath, final String suffix) throws IOException {
        final Path tmp = Files.createTempFile("vat_", suffix);
        tmp.toFile().deleteOnExit();
        try (InputStream in = getClass().getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IOException("Resource not found");
            }
            Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
        }

        return tmp;
    }

    private Path getAppDir() {
        return Path.of(System.getProperty("user.dir")).toAbsolutePath();
    }

    private void runPythonScript(final String theFilePath) {
        try {
            final Path pythonScript = extractResourceToTemp(
                    "/VocalAnalysisToolKit/Vocal_Analysis_Script.py", ".py");
            final Path setupBat = extractResourceToTemp("/pythonInstall.bat", ".bat");

            final Path appDir = getAppDir(); // where .venv should live

            // 1) Run setup in appDir so .venv is created at appDir\.venv
            ProcessBuilder setupPB = new ProcessBuilder("cmd.exe", "/c", setupBat.toString());
            setupPB.directory(appDir.toFile());
            setupPB.redirectErrorStream(true);
            Process setupProc = setupPB.start();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(setupProc.getInputStream()))) {
                String ln;
                while ((ln = r.readLine()) != null) logger.info("[setup] " + ln);
            }
            int setupExit = setupProc.waitFor();
            if (setupExit != 0) {
                logger.severe("Environment setup failed (exit " + setupExit + "); aborting.");
                return;
            }

            // 2) Resolve venv python; do not silently fall back
            final Path venvPy = appDir.resolve(".venv").resolve("Scripts").resolve("python.exe");
            if (!Files.exists(venvPy)) {
                throw new IllegalStateException("Venv python not found at " + venvPy + ". Ensure setup ran in " + appDir);
            }
            final String pythonExe = venvPy.toString();

            // Helper to run a short python/pip command and log all output
            java.util.function.Function<String[], Integer> run = (args) -> {
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

            // 3) Ensure matplotlib is installed in the venv
            Path req = appDir.resolve("requirements.txt");
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

            // 5) Run your real script
            Process process = getProcess(new ProcessBuilder(pythonExe, pythonScript.toString(), theFilePath), appDir);
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) logger.info("[Python] " + line);
            }
            int exit = process.waitFor();
            if (exit != 0) logger.severe("Python script exited with code " + exit);

        } catch (IOException | InterruptedException e) {
            logger.log(Level.SEVERE, "Failed to run Python script", e);
            Thread.currentThread().interrupt();
        }
    }

    private static Process getProcess(ProcessBuilder args, Path appDir) throws IOException {
        args.directory(appDir.toFile());
        args.redirectErrorStream(true);
        // Keep env clean & headless
        Map<String, String> env = args.environment();
        env.remove("PYTHONHOME");
        env.remove("PYTHONPATH");
        env.put("MPLBACKEND", "Agg");
        env.putIfAbsent("PYTHONIOENCODING", "utf-8");
        return args.start();
    }

}
