package com.kass.vocalanalysistool.view;

import com.kass.vocalanalysistool.common.Properties;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import javafx.fxml.FXML;
import javafx.scene.control.ProgressBar;

/**
 * Shows the loading screen whenever an audio file gets selected.
 *
 * @author Kassie Whitney
 * @version 9.3.25
 */
public class LoadingScreenController implements PropertyChangeListener {

    @FXML
    private ProgressBar myProgBar;

    protected void setMyMainSceneController(final SelectAudioFileController theScene) {
        theScene.addPropertyChangeListener(this);
    }

    protected double getProgressStatus() {
        return myProgBar.getProgress();
    }

    @Override
    public void propertyChange(final PropertyChangeEvent theEvent) {
        if(theEvent.getPropertyName().equals(Properties.UPDATE_PROGRESS.toString())) {
            myProgBar.setProgress((double) theEvent.getNewValue());
        }
    }
}
