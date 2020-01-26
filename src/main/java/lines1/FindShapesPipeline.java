package lines1;

import org.opencv.core.Mat;

import edu.wpi.first.networktables.NetworkTableInstance;
import edu.wpi.first.vision.VisionPipeline;

public class FindShapesPipeline implements VisionPipeline {
    public FindShapesPipeline()
    {
        NetworkTableInstance.getDefault().startClient("roborio-2773-frc.local");
    } 
    @Override
    public void process(Mat image) {
        double xc = image.cols() / 2;
        FindShapes.Result result = FindShapes.processImage(image, false);
        //System.out.println(result.contours.length);
        if (result.target != null) {
            double x = result.target[0];
            double alpha = 30 * (x - xc) / xc;
            //System.out.println("t=" + result.target[0] + "," + result.target[1]);
            System.out.println(alpha + " Degrees");
            NetworkTableInstance.getDefault().getEntry("/angle").forceSetDouble(alpha);
        }
    }
}
