package mo.eyetracker.visualization;


import com.theeyetribe.clientsdk.data.GazeData;
import javafx.application.Platform;
import javafx.scene.Node;
import mo.visualization.Playable;
import org.apache.commons.io.input.ReversedLinesFileReader;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.Charset;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

public class EyeTribeFixPlayer implements Playable {

    private long start, currentTime;
    private long end = -1;
    private boolean stopped = false;

    private GazeData current;
    private GazeData next;

    private RandomAccessFile file;
    
    private GazeData currentEvent, nextEvent;
    private FixationPanel pane;
    private AtomicBoolean isPlaying = new AtomicBoolean(false);
    private AtomicBoolean isStopped = new AtomicBoolean(false);
    private AtomicBoolean isPaused = new AtomicBoolean(false);

    private static final Logger logger = Logger.getLogger(EyeTribeFixPlayer.class.getName());

    public EyeTribeFixPlayer(File file) {
        try {
            this.file = new RandomAccessFile(file, "r");
            readLastTime(file);
            currentEvent = readNextEventFromFile();
            if (currentEvent != null) {
                start = currentEvent.timeStamp;
                nextEvent = readNextEventFromFile();
            }

            pane = new FixationPanel(1920, 1080);

            /*Platform.runLater(() -> {
                Stage stage = new Stage();
                stage.setTitle("EyeTribe Fixation Visualization");
                stage.setScene(new Scene(pane, 800, 600));
                stage.show();

                if (currentEvent != null) {
                    pane.addGazeData(currentEvent);
                }
            });*/

        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Error initializing EyeTribeFixPlayer", ex);
        }
    }
    
    public Node getPaneNode() {
        return pane;
    }

    private void readLastTime(File f) throws IOException {
        try (ReversedLinesFileReader rev = new ReversedLinesFileReader(f, Charset.defaultCharset())) {
            String lastLine;
            do {
                lastLine = rev.readLine();
                if (lastLine == null) {
                    return;
                }
            } while (lastLine.trim().isEmpty());

            GazeData lastEvent = parseEventFromLine(lastLine);
            if (lastEvent != null) {
                end = lastEvent.timeStamp;
            }
        }
    }

    private GazeData parseDataFromLine(String line) {
        String[] parts = line.split(" ");
        GazeData data = new GazeData();
        for (String part : parts) {

            try {
                String[] keyNValue = part.split(":");
                String k = keyNValue[0];
                String v = keyNValue[1];

                switch (k) {
                    case "t":
                        data.timeStamp = Long.parseLong(v);
                        break;
                    case "fx":
                        data.isFixated = Boolean.parseBoolean(v);
                        break;
                    case "sm":
                        data.smoothedCoordinates.x = Double.parseDouble(v.split(";")[0]);
                        data.smoothedCoordinates.y = Double.parseDouble(v.split(";")[1]);
                        break;
                    case "rw":
                        data.rawCoordinates.x = Double.parseDouble(v.split(";")[0]);
                        data.rawCoordinates.y = Double.parseDouble(v.split(";")[1]);
                        break;
                    default:
                        break;
                }
            } catch (Exception e) {
                logger.log(
                        Level.WARNING,
                        "Error reading part <{0}> line <{1}>:{2}",
                        new Object[]{part, line, e});
            }
        }

        return data;
    }
    
    private GazeData parseEventFromLine(String line) {
        try {
            String[] parts = line.split(" ");
            GazeData data = new GazeData();
            for (String part : parts) {
                String[] keyValue = part.split(":");
                if (keyValue.length != 2) {
                    continue;
                }

                switch (keyValue[0]) {
                    case "t":
                        data.timeStamp = Long.parseLong(keyValue[1]);
                        break;
                    case "sm":
                        String[] coords = keyValue[1].split(";");
                        data.smoothedCoordinates.x = Double.parseDouble(coords[0]);
                        data.smoothedCoordinates.y = Double.parseDouble(coords[1]);
                        break;
                    case "fx":
                        data.isFixated = Boolean.parseBoolean(keyValue[1]);
                        break;
                }
            }
            return data;
        } catch (Exception ex) {
            logger.log(Level.WARNING, "Error parsing event from line: " + line, ex);
            return null;
        }
    }
    
    private GazeData readNextEventFromFile() {
        try {
            String line = file.readLine();
            if (line != null) {
                return parseEventFromLine(line);
            }
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Error reading next event from file", ex);
        }
        return null;
    }


    @Override
    public void seek(long requestedMillis) {
        if (requestedMillis < start
                || requestedMillis > end
                || requestedMillis == current.timeStamp
                ||
                (requestedMillis > current.timeStamp && requestedMillis < next.timeStamp)) {
            return;
        }

        if (requestedMillis == next.timeStamp) {
            current = next;
            next = getNext();
            return;
        }

        GazeData data = current;

        if (requestedMillis < current.timeStamp) {
            try {
                file.seek(0);
                data = getNext();

            } catch (IOException ex) {
                logger.log(Level.SEVERE, null, ex);
            }
        }

        long marker;
        try {
            marker = file.getFilePointer();

            GazeData nextLocal = getNext();
            if (nextLocal == null) {
                return;
            }

            while (!(nextLocal.timeStamp > requestedMillis)) {
                data = nextLocal;

                marker = file.getFilePointer();
                nextLocal = getNext();

                if (nextLocal == null) { 
                    return;
                }
            }

            file.seek(marker);
            current = data;
            next = nextLocal;
        } catch (IOException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public void play(long millis) {
        if (isPlaying.get()) {
            if (isPaused.get()) {
                isPaused.set(false);
            }
            return;
        }

        if (isStopped.get()) {
            resetPlayback();
            isStopped.set(false);
        }

        isPlaying.set(true);
        isPaused.set(false);

        new Thread(() -> {
            try {
                while (!isStopped.get()
                        && currentEvent != null
                        && currentTime <= end) {
                    while (isPaused.get() && !isStopped.get()) {
                        Thread.sleep(100); 
                    }
                    if (isStopped.get()) {
                        break;
                    }

                    Platform.runLater(() -> pane.addGazeData(currentEvent));

                    currentEvent = nextEvent;
                    nextEvent = readNextEventFromFile();
                    if (nextEvent == null) {
                        break;
                    }

                    long delay = Math.max(0, nextEvent.timeStamp - currentEvent.timeStamp);
                    Thread.sleep(delay);

                    currentTime = nextEvent.timeStamp;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                isPlaying.set(false);
            }
        }).start();
    }

    private GazeData getNext() {
        GazeData d = null;
        try {
            String line = file.readLine();
            if (line != null) {
                d = parseDataFromLine(line);
            }
        } catch (IOException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
        return d;
    }

    @Override
    public long getStart() {
        return start;
    }

    @Override
    public long getEnd() {
        return end;
    }

    @Override
    public void pause() {
        if (isPlaying.get()) {
            isPaused.set(true);
        }
    }

    @Override
    public void stop() {
        isStopped.set(true);
        isPlaying.set(false);
        isPaused.set(false);
    }

    private void resetPlayback() {
        try {
            file.seek(0);
            currentTime = 0;
            currentEvent = readNextEventFromFile();
            nextEvent = readNextEventFromFile();

            pane.reset();
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Error resetting file pointer", e);
        }
    }
   

    public void sync(boolean bln) {
        boolean isSync = bln;
    }
    
    
    /*
    
    // Original play method, works with the Gameloop in the original version of MO.
    
    @Override
    public void play(long millis) {
        if ((millis >= start) && (millis <= end)) {
            seek(millis);
            if (current.timeStamp == millis) {
                double x = current.smoothedCoordinates.x;
                double y = current.smoothedCoordinates.y;

                if (current.state != GazeData.STATE_TRACKING_FAIL
                        && current.state != GazeData.STATE_TRACKING_LOST
                        && !(x == 0 && y == 0)) {

                    panel.addGazeData(current);
                }
            }
        }
    }
    */
}
