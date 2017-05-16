package mo.eyetracker.capture;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import mo.capture.RecordableConfiguration;
import mo.organization.Configuration;
import mo.organization.Participant;
import mo.organization.ProjectOrganization;

public class TheEyeTribeConfiguration implements RecordableConfiguration {

    private String id;
    private TheEyeTribeRecorder recorder;
    
    private static final Logger logger = Logger.getLogger(TheEyeTribeConfiguration.class.getName());
    
    public TheEyeTribeConfiguration(String id) {
        this.id = id;
    }

    @Override
    public void setupRecording(File stageFolder, ProjectOrganization org, Participant p) {
        recorder = new TheEyeTribeRecorder(stageFolder, this);
    }

    @Override
    public void startRecording() {
        recorder.start();
    }

    @Override
    public void cancelRecording() {
        recorder.cancel();
    }

    @Override
    public void pauseRecording() {
        recorder.pause();
    }

    @Override
    public void resumeRecording() {
        recorder.resume();
    }

    @Override
    public void stopRecording() {
        recorder.stop();
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public File toFile(File parent) {
        try {
            File f = new File(parent, "tet_"+id+".txt");
            f.createNewFile();
            return f;
        } catch (IOException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
        return null;
    }

    @Override
    public Configuration fromFile(File file) {
        String fileName = file.getName();
        if (fileName.contains("_") && fileName.contains(".")) {
            String name = fileName.substring(fileName.indexOf("_")+1, fileName.lastIndexOf("."));
            TheEyeTribeConfiguration config = new TheEyeTribeConfiguration(name);
            return config;
        }
        return null;
    }
    
}