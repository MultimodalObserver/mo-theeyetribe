package mo.eyetracker.visualization;

import com.theeyetribe.clientsdk.data.GazeData;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.Charset;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.SwingUtilities;
import mo.core.ui.dockables.DockableElement;
import mo.core.ui.dockables.DockablesRegistry;
import mo.visualization.Playable;
import org.apache.commons.io.input.ReversedLinesFileReader;

public class EyeTribeFixPlayer implements Playable {

    private long start;
    private long end = -1;
    private boolean stopped = false;

    private GazeData current;
    private GazeData next;

    private RandomAccessFile file;
    private FixationPanel panel;

    private static final Logger logger = Logger.getLogger(EyeTribeFixPlayer.class.getName());

    public EyeTribeFixPlayer(File file) {
        try {
            readLastTime(file);

            this.file = new RandomAccessFile(file, "r");
            current = getNext();
            if (current != null) {
                start = current.timeStamp;
                next = getNext();
            }

            panel = new FixationPanel(1920, 1080);
            SwingUtilities.invokeLater(() -> {
                try {
                    DockableElement d = new DockableElement();
                    d.add(panel);
                    DockablesRegistry.getInstance().addDockableInProjectGroup("", d);
                } catch (Exception ex) {
                    logger.log(Level.SEVERE, null, ex);
                }
            });

        } catch (FileNotFoundException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
    }

    private void readLastTime(File f) {
        try (ReversedLinesFileReader rev = new ReversedLinesFileReader(f, Charset.defaultCharset())) {
            String lastLine = null;
            do {
                lastLine = rev.readLine();
                if (lastLine == null) {
                    return;
                }
            } while (lastLine.trim().isEmpty());
            GazeData e = parseDataFromLine(lastLine);
            end = e.timeStamp;
            rev.close();
        } catch (IOException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
    }

    private GazeData parseDataFromLine(String line) {
        String[] parts = line.split(" ");
        GazeData data = new GazeData();
        for (String part : parts) {

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
        }

        return data;
    }

    @Override
    public void pause() {
    }

    @Override
    public void seek(long requestedMillis) {
        if (requestedMillis < start
                || requestedMillis > end
                || requestedMillis == current.timeStamp
                || (requestedMillis > current.timeStamp
                && requestedMillis < next.timeStamp)) {
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
            if (next == null) {
                return;
            }

            while (!(nextLocal.timeStamp > requestedMillis)) {
                data = nextLocal;

                marker = file.getFilePointer();
                nextLocal = getNext();

                if (nextLocal == null) { // no more events (end of file)
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

    private GazeData getNext() {
        GazeData d = null;
        try {
            do {
                String line = file.readLine();
                if (line != null) {
                    d = parseDataFromLine(line);
                }
            } while (d == null);
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
    public void stop() {
        stopped = true;
        pause();
    }
}
