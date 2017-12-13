package roboticsas.org.autounderwater;

import android.content.Context;
import android.content.res.AssetManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.SurfaceView;
import android.view.WindowManager;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
//import org.opencv.dnn.Dnn;
import org.opencv.imgproc.Imgproc;
import org.opencv.dnn.Net;

import java.util.ArrayList;
//import org.opencv.dnn.Dnn;
//
//import java.io.BufferedInputStream;
//import java.io.File;
//import java.io.FileOutputStream;
//import java.io.IOException;


public class MainActivity extends AppCompatActivity implements CameraBridgeViewBase.CvCameraViewListener2{

    public static final String TAG = "OpenCV::Activity";

    private CameraBridgeViewBase mCameraBridgeViewBase;
//    private static final String[] classNames = {"background",
//            "aeroplane", "bicycle", "bird", "boat",
//            "bottle", "bus", "car", "cat", "chair",
//            "cow", "diningtable", "dog", "horse",
//            "motorbike", "person", "pottedplant",
//            "sheep", "sofa", "train", "tvmonitor"};
//    private Net net;

    private int _width = 0, _height = 0, _cols = 0, _rows = 0;

    private int subWidth = 25, subHeight = 25;

    private Mat mRgba, mMask, mCanny, ROIfront;
    private Rect roi;

    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status){
                case BaseLoaderCallback.SUCCESS:
                {
                    Log.d(TAG,"OpenCV Load Successfully");
                    mCameraBridgeViewBase.enableView();
                    break;
                }
                default:
                {
                    super.onManagerConnected(status);
                    break;
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.show_camera);

        mCameraBridgeViewBase = findViewById(R.id.javaCameraView);
        mCameraBridgeViewBase.setMaxFrameSize(500, 500);
        mCameraBridgeViewBase.setVisibility(SurfaceView.VISIBLE);
        mCameraBridgeViewBase.setCvCameraViewListener(this);

    }


    //Variable untuk Pengaturan/Pemilihan Warna
    private Scalar mLowerBound = new Scalar(0);
    private Scalar mUpperBound = new Scalar(0);

    @Override
    protected void onStart() {
        super.onStart();

        mLowerBound = new Scalar(7.204, 186.348, 204.73, 0.0);
        mUpperBound = new Scalar(57.204, 286.348, 305.0, 255);
    }


    @Override
    public void onCameraViewStarted(int width, int height) {
        mRgba = new Mat(height, width, CvType.CV_8UC4);
        mMask = new Mat(height, width, CvType.CV_8UC4);
        mCanny = new Mat(height, width, CvType.CV_8UC4);

        //Akibat dari di rotate camera menjadi potrait via coding..
        _width = height;
        _height = width;
        _cols = mRgba.rows();
        _rows = mRgba.cols();
    }

    @Override
    public void onCameraViewStopped() {
        mRgba.release();
        ROIfront.release();
    }

    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {

        Point p1    = new Point(0, 0);
        Point p2    = new Point(130, _width);
        roi         = new Rect(p1, p2);
        mRgba       = inputFrame.rgba();
        ROIfront    = new Mat(inputFrame.gray(), roi);
//        ROIfront = inputFrame.gray();
        Imgproc.GaussianBlur(ROIfront, ROIfront, new Size(15,15), 0);

        int size_erosi = 15;
        int size_derosi = 25;

        Imgproc.erode(ROIfront, mMask,
                Imgproc.getStructuringElement(Imgproc.MORPH_ERODE,
                        new Size(2*size_erosi + 1, 2*size_erosi+1),
                        new Point(0, 0)));
        Imgproc.dilate(mMask, mMask,
                Imgproc.getStructuringElement(Imgproc.MORPH_ERODE,
                        new Size(2*size_derosi + 1, 2*size_derosi+1),
                        new Point(0, 0)));

        Imgproc.Canny(mMask, mCanny, 12,6);

        ArrayList<MatOfPoint> contours = new ArrayList<>();
        Imgproc.findContours(mCanny, contours, new Mat(), Imgproc.RETR_TREE,Imgproc.CHAIN_APPROX_SIMPLE);

        Imgproc.drawContours(mRgba, contours, -1, Scalar.all(255),1);

//        Imgproc.rectangle(mRgba, p1, p2, Scalar.all(255), 2);
        return mRgba;
    }

    @Override
    protected void onPause() {
        super.onPause();
        if(mCameraBridgeViewBase != null)
            mCameraBridgeViewBase.disableView();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if(!OpenCVLoader.initDebug()){
            Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION, this, mLoaderCallback);
        }else{
            Log.d(TAG, "OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if(mCameraBridgeViewBase != null)
            mCameraBridgeViewBase.disableView();
    }


    //Deep Neural Network things
    //Call on CameraViewStarted
    //private void getDatasets(){
//        String proto = getPath("MobileNetSSD_deploy.prototxt", this);
//        String weights = getPath("MobileNetSSD_deploy.caffemodel", this);
//        net = Dnn.readNetFromCaffe(proto, weights);
//
//        Log.i(TAG, "Network loaded successfully");
//    }

    //Call onCameraFrame
//    private void searchingObjectWithDNN(CameraBridgeViewBase.CvCameraViewFrame inputFrame){
//        final int IN_WIDTH = 120;
//        final int IN_HEIGHT =120;
//        final float WH_RATIO = (float)IN_WIDTH / IN_HEIGHT;
//        final double IN_SCALE_FACTOR = 0.007843;
//        final double MEAN_VAL = 50.5;
//        final double THRESHOLD = 0.2;
//        // Get a new frame
//        Mat frame = inputFrame.rgba();
//
//        Imgproc.cvtColor(frame, frame, Imgproc.COLOR_RGBA2RGB);
//        // Forward image through network.
//
//        Mat blob = Dnn.blobFromImage(frame,IN_SCALE_FACTOR,
//                new Size(IN_WIDTH, IN_HEIGHT),
//                new Scalar(MEAN_VAL, MEAN_VAL, MEAN_VAL), false, true);
//
//        net.setInput(blob);
//        Mat detections = net.forward();
//        int cols = frame.cols();
//        int rows = frame.rows();
//        Size cropSize;
//        if ((float)cols / rows > WH_RATIO) {
//            cropSize = new Size(rows * WH_RATIO, rows);
//        } else {
//            cropSize = new Size(cols, cols / WH_RATIO);
//        }
//        int y1 = (int)(rows - cropSize.height) / 2;
//        int y2 = (int)(y1 + cropSize.height);
//        int x1 = (int)(cols - cropSize.width) / 2;
//        int x2 = (int)(x1 + cropSize.width);
//        Mat subFrame = frame.submat(y1, y2, x1, x2);
//        cols = subFrame.cols();
//        rows = subFrame.rows();
//        detections = detections.reshape(1, (int)detections.total() / 7);
//        for (int i = 0; i < detections.rows(); ++i) {
//            double confidence = detections.get(i, 2)[0];
//            if (confidence > THRESHOLD) {
//                int classId = (int)detections.get(i, 1)[0];
//                int xLeftBottom = (int)(detections.get(i, 3)[0] * cols);
//                int yLeftBottom = (int)(detections.get(i, 4)[0] * rows);
//                int xRightTop   = (int)(detections.get(i, 5)[0] * cols);
//                int yRightTop   = (int)(detections.get(i, 6)[0] * rows);
//                // Draw rectangle around detected object.
//                Imgproc.rectangle(subFrame, new Point(xLeftBottom, yLeftBottom),
//                        new Point(xRightTop, yRightTop),
//                        new Scalar(0, 255, 0));
//                String label = classNames[classId] + ": " + confidence;
//                int[] baseLine = new int[1];
//                Size labelSize = Imgproc.getTextSize(label, Core.FONT_HERSHEY_SIMPLEX, 0.5, 1, baseLine);
//                // Draw background for label.
//                Imgproc.rectangle(subFrame, new Point(xLeftBottom, yLeftBottom - labelSize.height),
//                        new Point(xLeftBottom + labelSize.width, yLeftBottom + baseLine[0]),
//                        new Scalar(255, 255, 255), Core.FILLED);
//                // Write class name and confidence.
//                Imgproc.putText(subFrame, label, new Point(xLeftBottom, yLeftBottom),
//                        Core.FONT_HERSHEY_SIMPLEX, 0.5, new Scalar(0, 0, 0));
//            }
//        }
//    }

    // Upload file to storage and return a path.
//    private String getPath(String file, Context context) {
//        AssetManager assetManager = context.getAssets();
//        BufferedInputStream inputStream;
//        try {
//            // Read data from assets.
//            inputStream = new BufferedInputStream(assetManager.open(file));
//            byte[] data = new byte[inputStream.available()];
//            inputStream.read(data);
//            inputStream.close();
//            // Create copy file in storage.
//            File outFile = new File(context.getFilesDir(), file);
//            FileOutputStream os = new FileOutputStream(outFile);
//            os.write(data);
//            os.close();
//            // Return a path to file which may be read in common way.
//            return outFile.getAbsolutePath();
//        } catch (IOException ex) {
//            Log.i(TAG, "Failed to upload a file");
//        }
//        return "";
//    }

}
