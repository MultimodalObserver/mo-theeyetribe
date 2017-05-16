package mo.eyetracker.visualization;

import com.theeyetribe.clientsdk.data.GazeData;
import com.theeyetribe.clientsdk.data.Point2D;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import javax.swing.JPanel;
import mo.visualization.test.Gradient;

public class FixationPanel extends JPanel {

    int fixationsLimit = 50;
    
    int diameterMaxLimit = 120;
    int diameterMinLimit = 40;
    
    boolean prevWasFixated = false;
    
    Fixation first;
    Fixation last;
    
    int globalFixationsCount = 0;
    int fixationsCount;
    
    BufferedImage image;
    int imageWidth, imageHeight;
    
    Color[] colors;
    int[] dataColorIndices;
    
    int[] sizes;

    public FixationPanel(int width, int height) {
        imageWidth = width;
        imageHeight = height;
        colors = Gradient.GRADIENT_GREEN_YELLOW_ORANGE_RED;
        setDoubleBuffered(true);
    }
    
    
            
    public static void main(String[] args) {
        
    }
    
    public void addGazeData(GazeData data) {
    
        if (data.isFixated && prevWasFixated) {
            
            accumulateFixation(data);
            prevWasFixated = true;
            
        } else if (data.isFixated && !prevWasFixated) {
            
            addNewFixation(data);
            prevWasFixated = true;
            
        } else {

            prevWasFixated = false;
            
        }
        
    }

    private void addNewFixation(GazeData data) {
        
        Fixation f = new Fixation();
        f.addPoint(data);
        f.addTime(data.timeStamp);        
        
        if (last == null) {
            
            first = f;
            
        } else {
            
            last.next = f;
            f.prev = last;
        }
        
        last = f;
        
        fixationsCount++;
        globalFixationsCount++;
        f.number = globalFixationsCount;
        
        if (fixationsCount > fixationsLimit) {
            Fixation second = first.next;
            first = second;
            second.prev = null;
            fixationsCount--;
        }
        
        updateColorsAndSizes();
        drawData();
        repaint();
    }

    private void accumulateFixation(GazeData data) {
        last.addTime(data.timeStamp);
        last.addPoint(data);
        
        updateColorsAndSizes();
        drawData();
        repaint();
    }
    
    void updateColorsAndSizes() {
        dataColorIndices = new int[fixationsLimit];
        sizes = new int[fixationsLimit];
        long minTime = 0, maxTime = 0;
        
        for (Fixation fix = first; fix != null; fix = fix.next) {
            if (fix.ellapsedTime > maxTime) {
                maxTime = fix.ellapsedTime;
            }
        }
        
        double timeRange = maxTime - minTime;
        
        int count = 0;
        for (Fixation fix = first; fix != null; fix = fix.next) {
            
            double norm = (fix.ellapsedTime - minTime) / timeRange; // 0 < norm < 1
            int index = (int) Math.floor(norm * (colors.length - 1));
            dataColorIndices[count] = index;
            
            int size = (int) (fix.ellapsedTime * (diameterMaxLimit - diameterMinLimit)
                    / timeRange) + diameterMinLimit;
            
            sizes[count] = size;
            
            count++;
        }
    }
    
    private void drawData() {
        if (image == null) {
            image = new BufferedImage(
                    imageWidth, imageHeight, BufferedImage.TYPE_INT_ARGB);
        }
        
        Graphics2D g = image.createGraphics();
        
        if (first != null){
        
            g.setColor(Color.white);
            g.fillRect(0, 0, imageWidth, imageHeight);
            
            int count = 0;
            for (Fixation fix = first; fix != null; fix = fix.next, count++) {
                if (fix.next != null) {
                    Point2D p = fix.getCenter();
                    Point2D pNext = fix.next.getCenter();
                
                    g.setColor(Color.blue);
                    g.setStroke(new BasicStroke(3));
                    g.drawLine((int) p.x, (int) p.y, (int) pNext.x, (int) pNext.y);
                }
            }
            
            count = 0;
            for (Fixation fix = first; fix != null; fix = fix.next, count++) {
                Point2D p = fix.getCenter();
                g.setColor(colors[dataColorIndices[count]]);
                g.fillOval((int) p.x - (sizes[count] / 2), (int) p.y - (sizes[count] / 2), sizes[count], sizes[count]);
                Font font = new Font("Arial", Font.BOLD, 16);
                
                g.setFont(font); 
                g.setColor(Color.black);
                
                FontMetrics fm = g.getFontMetrics();
                
                //g.
                g.drawString(fix.number + "", (int)p.x, (int)p.y);
            }
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        
        Graphics2D g2d = (Graphics2D) g;

        int width = this.getWidth();
        int height = this.getHeight();

        this.setOpaque(true);

        // clear the panel
        g2d.setColor(Color.white);
        g2d.fillRect(0, 0, width, height);

        // draw the heat map
        if (image == null) {
            // Ideally, we only call drawData in the constructor, or if we
            // change the data or gradients. We include this just to be safe.
            drawData();
        }

        // The data plot itself is drawn with 1 pixel per data point, and the
        // drawImage method scales that up to fit our current window size. This
        // is very fast, and is much faster than the previous version, which 
        // redrew the data plot each time we had to repaint the screen.
        g2d.drawImage(image,
                1, 1,
                width ,
                height ,
                0, 0,
                image.getWidth(), image.getHeight(),
                null);

    }

    private class Fixation {
        Ellipse2D circle;
        int number;
        
        long ellapsedTime;
        long firstTime;
        long lastTime;
        private boolean timeSeted = false;

        private Point2D sum;
        private int pointsCount;
    
        Fixation prev;
        Fixation next;
        
        void addPoint(GazeData data) {
            
            Point2D newPoint;
            
            if (data.hasSmoothedGazeCoordinates()) {
                newPoint = data.smoothedCoordinates;
            } else if (data.hasRawGazeCoordinates()) {
                newPoint = data.rawCoordinates;
            } else {
                newPoint = new Point2D(0, 0);
            }
            
            if (sum == null) {
                sum = newPoint;
            } else {
                sum = sum.add(newPoint);
            }
            
            pointsCount++;
        }
        
        void addTime(long time) {
            if (!timeSeted) {
                firstTime = lastTime = time;
                timeSeted = true;
            } else {
                lastTime = time;
                ellapsedTime = lastTime - firstTime;
            }
        }
        
        Point2D getCenter() {
            return sum.divide(pointsCount);
        }
        
        @Override
        public String toString() {
            return 
                    number + " " + ellapsedTime + " " + getCenter();
        }
    }

}
