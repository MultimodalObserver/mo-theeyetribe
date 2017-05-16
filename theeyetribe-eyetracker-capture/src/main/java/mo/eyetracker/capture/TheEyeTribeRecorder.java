package mo.eyetracker.capture;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.theeyetribe.clientsdk.IGazeListener;
import com.theeyetribe.clientsdk.data.GazeData;
import com.theeyetribe.clientsdk.data.Point2D;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;
import mo.organization.FileDescription;
import org.apache.commons.lang3.time.FastDateFormat;

public class TheEyeTribeRecorder implements IGazeListener {

    private final TETClient client;
    private TheEyeTribeConfiguration config;

    private static final Logger logger = Logger.getLogger(TheEyeTribeRecorder.class.getName());
    private File output;
    private FileOutputStream outputStream;
    private FileDescription desc;
    ObjectMapper mapper = new ObjectMapper();

    public TheEyeTribeRecorder(File stageFolder, TheEyeTribeConfiguration config) {
        client = new TETClient(null, null);
        client.connect();
        this.config = config;
        createFile(stageFolder);
    }

    private void createFile(File parent) {

        Date now = new Date();
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd_HH.mm.ss.SSS");

        String reportDate = df.format(now);

        output = new File(parent, reportDate + "_" + config.getId() + ".txt");
        try {
            output.createNewFile();
            outputStream = new FileOutputStream(output);
            desc = new FileDescription(output, TheEyeTribeRecorder.class.getName());
        } catch (FileNotFoundException ex) {
            logger.log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
    }

    private void deleteFile() {
        if (output.isFile()) {
            output.delete();
        }
        if (desc.getDescriptionFile().isFile()) {
            desc.deleteFileDescription();
        }
    }

    public void cancel() {
        stop();
        deleteFile();
    }

    public void start() {
        client.addGazeListener(this);
    }

    public void pause() {
        client.removeGazeListener(this);
    }

    public void resume() {
        client.addGazeListener(this);
    }

    public void stop() {
        client.removeGazeListener(this);
        client.disconnect();
        try {
            outputStream.close();
        } catch (IOException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public void onGazeUpdate(GazeData gd) {
        String str = gazeDataToString(gd);
        if (str != null) {
            try {
                outputStream.write((str + "\n").getBytes());
            } catch (IOException ex) {
                logger.log(Level.SEVERE, null, ex);
            }
        }
    }

    private static String gazeDataToString(GazeData gd) {
        String dataStr = "";
        FastDateFormat format = FastDateFormat.getInstance("yyyy-MM-dd HH:mm:ss.SSS");

        try {
            Date date = format.parse(gd.timeStampString);
            dataStr += "t:" + date.getTime();
        } catch (ParseException ex) {
            Logger.getLogger(TheEyeTribeRecorder.class.getName()).log(Level.SEVERE, null, ex);
            return null;
        }

        dataStr += " st:" + gd.state;
        dataStr += " fx:" + gd.isFixated;

        if (gd.hasSmoothedGazeCoordinates()) {
            dataStr += " sm:" + gd.smoothedCoordinates.x + ";" + gd.smoothedCoordinates.y;
        }

        if (gd.hasRawGazeCoordinates()) {
            dataStr += " rw:" + gd.rawCoordinates.x + ";" + gd.rawCoordinates.y;
        }

        if (gd.hasSmoothedGazeCoordinates()) {
            dataStr += " lsm:" + gd.leftEye.smoothedCoordinates.x + ";" + gd.leftEye.smoothedCoordinates.y;
        }

        if (gd.hasRawGazeCoordinates()) {
            dataStr += " lrw:" + gd.leftEye.rawCoordinates.x + ";" + gd.leftEye.rawCoordinates.y;
        }

        dataStr += " lpc:" + gd.leftEye.pupilCenterCoordinates.x + ";" + gd.leftEye.pupilCenterCoordinates.y;
        dataStr += " lps:" + gd.leftEye.pupilSize;

        if (gd.hasSmoothedGazeCoordinates()) {
            dataStr += " rsm:" + gd.rightEye.smoothedCoordinates.x + ";" + gd.rightEye.smoothedCoordinates.y;
        }

        if (gd.hasRawGazeCoordinates()) {
            dataStr += " rrw:" + gd.rightEye.rawCoordinates.x + ";" + gd.rightEye.rawCoordinates.y;
        }

        dataStr += " rpc:" + gd.rightEye.pupilCenterCoordinates.x + ";" + gd.rightEye.pupilCenterCoordinates.y;
        dataStr += " rps:" + gd.rightEye.pupilSize;

        return dataStr;
    }
}
