package team2773.find_targets;

import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import java.util.ArrayList;
import java.util.List;
import org.opencv.core.Point;
import org.opencv.core.Scalar;

public class FindShapes {
    /**
     *
     */
    private static final int MAX_CONTOURS_LENGTH = 10;
    private static final int MAX_POINTS_CONTOUR = 12;
    private static final int MIN_POINTS_CONTOUR = 4;
    private static final int MIN_CONTOUR_AREA = 300;
    private static final Size BLUR_SIZE = new Size(5, 5);

    static class Target {
        public Point center;
    }

    // package of variables used to return final product
    static class Result {
        public Mat display;
        public Point[][] contours;
        public Target target;
    }

    // calculates area of the contour
    static double contourArea(Point[] c) {
        double sum = (c[0].y + c[c.length - 1].y) * (c[0].x - c[c.length - 1].x);
        for (int i = 1; i < c.length; i++) {
            sum += (c[i].y + c[i - 1].y) * (c[i].x - c[i - 1].x);
        }
        return sum * .5;
    }

    static double contourHeight(Point[] c) {
        double min = c[0].y;
        double max = c[0].y;
        for (int i = 1; i < c.length; i++) {
            if (c[i].y < min) {
                min = c[i].y;
            }
            if (c[i].y > max) {
                max = c[i].y;
            }
        }
        return max - min;
    }

    static boolean isInBounds(Point[] c, double width, double height) {
        final double BORDER = 3;
        for (Point p : c) {
            if (p.x < BORDER || p.x >= width - BORDER || p.y < BORDER || p.y >= height - BORDER)
                return false;
        }
        return true;
    }

    static boolean isKmeansFit(Point[] c) {
        final int K = 4;

        // Create matrix of coordinates
        Mat p = new Mat(c.length, 2, CvType.CV_32FC1);
        for (int i = 0; i < c.length; i++) {
            p.put(i, 0, c[i].x);
            p.put(i, 1, c[i].y);
        }

        // Create array of labels for later
        Mat labels = new Mat(c.length, 1, CvType.CV_32SC1);
        for (int i = 0; i < c.length; i++) {
            labels.put(i, 0, 0);
        }
        Mat centers = new Mat();

        TermCriteria criteria = new TermCriteria(TermCriteria.EPS | TermCriteria.MAX_ITER, 10, 0.1);
        Core.kmeans(p, K, labels, criteria, 2, Core.KMEANS_PP_CENTERS, centers);

        // compress contour labels
        int[] compressedLabels = new int[c.length];
        int k = 0;
        int[] t = new int[1]; // make OpenCV get happy
        for (int i = 0; i < labels.rows(); i++) {
            labels.get(i, 0, t);
            int label = t[0];
            if (k == 0 || compressedLabels[k - 1] != label)
                compressedLabels[k++] = label;
        }
        if (compressedLabels[0] == compressedLabels[k - 1])
            k--;

        // True if square shape
        if (k == 4) {
            Point p1 = getPointFromRow(centers, compressedLabels[0]);
            Point p2 = getPointFromRow(centers, compressedLabels[1]);
            Point p3 = getPointFromRow(centers, compressedLabels[2]);
            Point p4 = getPointFromRow(centers, compressedLabels[3]);
            double ratio = (p1.y - p3.y) / (p1.x - p3.x);
            if (ratio < 1.5)
                return false;
            return isAngleAbout90(p1, p2, p3) && isAngleAbout90(p2, p3, p4) && isAngleAbout90(p3, p4, p1);
        }

        // True if half-hex shape
        if (k == 6 && compressedLabels[1] == compressedLabels[5] && compressedLabels[2] == compressedLabels[4]) {
            return true;
        }

        return false;
    }

    private static Point getPointFromRow(Mat m, int row) {
        return new Point(m.get(row, 0)[0], m.get(row, 1)[0]);
    }

    private static boolean isAngleAbout90(Point p1, Point p2, Point p3) {
        double x0 = p2.x - p1.x;
        double y0 = p2.y - p1.y;
        double x1 = p3.x - p2.x;
        double y1 = p3.y - p2.y;

        double alpha = 180 * Math.atan2(-x0 * y1 + x1 * y0, x0 * x1 + y0 * y1) / Math.PI;
        double E = 15;
        return Math.abs(Math.abs(alpha) - 90) < E;
    }

    // determines if the contour is large enough
    static boolean isGoodContour(Point[] c, double width, double height) {
        if (c.length < MIN_POINTS_CONTOUR || c.length > MAX_POINTS_CONTOUR)
            return false;
        if (!isInBounds(c, width, height))
            return false;
        if (Math.abs(contourArea(c)) < MIN_CONTOUR_AREA)
            return false;
        if (!isKmeansFit(c))
            return false;
        return true;
    }

    // modifies image and returns Result
    public static Result processImage(Mat original, boolean enableDisplay) {
        Result result = new Result();

        Mat filtered = new Mat();
        Mat blurred = new Mat();
        Mat hsv = new Mat();

        // blur image
        Imgproc.blur(original, blurred, BLUR_SIZE);

        // convert image to HSV color space and find green
        Imgproc.cvtColor(blurred, hsv, Imgproc.COLOR_BGR2HSV);
        Scalar minGreen = new Scalar(30, 80, 50);
        Scalar maxGreen = new Scalar(90, 255, 255);
        Core.inRange(hsv, minGreen, maxGreen, filtered);

        // find contours (non approximated)
        List<MatOfPoint> contours = new ArrayList<>();
        Imgproc.findContours(filtered, contours, new Mat(), Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_NONE);

        List<Point[]> found_contours = new ArrayList<>();
        int contours_length = contours.size();
        if (contours_length > MAX_CONTOURS_LENGTH) {
            contours_length = MAX_CONTOURS_LENGTH;
        }
        for (int i = 0; i < contours_length; i++) {
            // approximate contours
            Point[] c = contours.get(i).toArray();
            double h = contourHeight(c);
            MatOfPoint2f approx = new MatOfPoint2f();
            double approxEpsilon = h * .05;
            Imgproc.approxPolyDP(new MatOfPoint2f(c), approx, approxEpsilon, true);
            c = approx.toArray();
            if (!isGoodContour(c, original.cols(), original.rows())) {
                continue;
            }

            /*
             * System.out.println("len =" + c.length); for (int j = 0; j < c.length; j++) {
             * System.out.println("x = " + c[j].x + " y = " + c[j].y);
             * 
             * }
             */
            found_contours.add(c);
        }

        result.contours = new Point[found_contours.size()][];
        found_contours.toArray(result.contours);
        if (enableDisplay) {
            // visualise results
            Mat display = original;
            // Mat display = new Mat();
            // Imgproc.cvtColor(filtered, display, Imgproc.COLOR_GRAY2BGR);
            // for (int i = 0; i < contours.size(); i++) {
            // Imgproc.drawContours(display, contours, i, new Scalar(0, 0, 255), -1);
            // }
            List<MatOfPoint> contours2 = new ArrayList<MatOfPoint>();
            for (int i = 0; i < found_contours.size(); i++) {
                MatOfPoint m = new MatOfPoint(found_contours.get(i));
                contours2.add(m);
            }
            for (int i = 0; i < found_contours.size(); i++) {
                Imgproc.drawContours(display, contours2, i, new Scalar(0, 128, 255), -1);
            }
            result.display = display;
        }
        if (found_contours.size() >= 1) {
            Point[] c = found_contours.get(0);
            int min_i = 0, max_i = 0;
            // finds the min and max points in the picture
            for (int i = 1; i < c.length; i++) {
                if (c[i].x < c[min_i].x)
                    min_i = i;
                if (c[i].x > c[max_i].x)
                    max_i = i;
            }
            result.target = new Target();
            // finds average of x's and y's
            double cx = (c[min_i].x + c[max_i].x) / 2;
            double cy = (c[min_i].y + c[max_i].y) / 2;
            result.target.center = new Point(cx, cy);
        }

        return result;
    }
}