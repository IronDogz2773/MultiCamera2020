package lines1;

import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import java.util.ArrayList;
import java.util.List;
import org.opencv.core.Point;
import org.opencv.core.Scalar;

public class FindShapes
{
    static class Result {
        public Mat display;
        public Point[][] contours;
        public double[] target;
    }
    private static final Size BLUR_SIZE = new Size(5,5);

    static boolean isGoodContour(Point[] c) {
        if (c.length < 4) return false;
        return true;
     }

    public static Result processImage(Mat original, boolean enableDisplay)
    {   
        Result result = new Result();

        Mat filtered = new Mat();
        Mat blurred = new Mat();
        Mat hsv = new Mat();

        // blur image
        Imgproc.blur(original, blurred, BLUR_SIZE);

        //convert image to HSV color space and find green
        Imgproc.cvtColor(blurred, hsv, Imgproc.COLOR_BGR2HSV);
        Scalar minGreen = new Scalar(30, 80, 50);
        Scalar maxGreen = new Scalar(90, 255, 255);
        Core.inRange(hsv, minGreen, maxGreen, filtered);

        //find contours (non approximated)
        List<MatOfPoint> contours = new ArrayList<>();
        Imgproc.findContours(filtered, contours, new Mat(), Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_NONE);

        //System.out.println("contours count = " + contours.size());
        List<Point[]> found_contours = new ArrayList<>();
        for (int i = 0; i < contours.size(); i++) {
            //approximate contours
            Point[] c = contours.get(i).toArray();
            MatOfPoint2f approx = new MatOfPoint2f();
            Imgproc.approxPolyDP(new MatOfPoint2f(c),approx,3,true);
            c = approx.toArray();
            if (!isGoodContour(c)) {
                continue;
            }

            /*System.out.println("len =" + c.length);
            for (int j = 0; j < c.length; j++)
            {
                System.out.println("x = " + c[j].x + " y = " + c[j].y);
           
            }*/
            found_contours.add(c);
        }

        result.contours = new Point[found_contours.size()][];
        found_contours.toArray(result.contours);
        if (enableDisplay){
            //visualise results
            Mat display = original;
            // Mat display = new Mat();
            // Imgproc.cvtColor(filtered, display, Imgproc.COLOR_GRAY2BGR);
            // for (int i = 0; i < contours.size(); i++) {
            //   Imgproc.drawContours(display, contours, i, new Scalar(0, 0, 255), -1);
            //}
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
            //finds the min and max points in the picture
            for (int i = 1; i < c.length; i++) {
                if (c[i].x < c[min_i].x) min_i = i;
                if (c[i].x > c[max_i].x) max_i = i;
            }
            result.target = new double[2];
            //finds average of x's and y's
            result.target[0] = (c[min_i].x + c[max_i].x) / 2;                
            result.target[1] = (c[min_i].y + c[max_i].y) / 2;
        }

        return result;
    }
}