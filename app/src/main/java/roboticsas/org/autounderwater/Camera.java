package roboticsas.org.autounderwater;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.dnn.Net;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.List;

//
public class Camera extends AppCompatActivity implements CameraBridgeViewBase.CvCameraViewListener2{

    public static final String      TAG = "OpenCV::Activity";

    private CameraBridgeViewBase    mCameraBridgeViewBase;
    private Net                     net;
    private ColorBlobDetector       mDetector;
    private Scalar                  mBlobColorRgba;
    private Scalar                  mBlobColorHsv;
    private Size                    SPECTRUM_SIZE;
    private Scalar                  CONTOUR_COLOR;
    private Mat                     mRgba, mMask, mMix, ROIfront, mSpectrum;
    private Rect                    roi;
    private boolean                 mIsColorSelected = false;
    private boolean                 mIsBTConnected = false;
    private ArrayList<MatOfPoint>   mContour = new ArrayList<>();
    private DataCommunication       _communication;

    private int _width = 0, _height = 0, _cols = 0, _rows = 0;

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
        setContentView(R.layout.activity_camera);
        setSupportActionBar((Toolbar) findViewById(R.id.toolBar));

        String str = getIntent().getStringExtra(DeviceList.EXTRA_ADDRESS);
        if(str != null && !str.isEmpty()) {
            mIsBTConnected = true;
            _communication = new DataCommunication(getIntent(), this);

            _communication.BTExecute();
        }

        mCameraBridgeViewBase = findViewById(R.id.javaCameraView);
        mCameraBridgeViewBase.setMaxFrameSize(500, 500);
        mCameraBridgeViewBase.setVisibility(SurfaceView.VISIBLE);
        mCameraBridgeViewBase.setCvCameraViewListener(this);

    }

    @Override
    public void onCameraViewStarted(int width, int height) {
        mRgba       = new Mat(height, width, CvType.CV_8UC4);
        ROIfront    = new Mat(height, width, CvType.CV_8UC4);
        mMask       = new Mat();
        mMix        = new Mat();

        mDetector = new ColorBlobDetector();
        mSpectrum = new Mat();
        mBlobColorRgba = new Scalar(255);
        mBlobColorHsv = new Scalar(255);
        SPECTRUM_SIZE = new Size(200, 64);
        CONTOUR_COLOR = new Scalar(255,0,0,255);

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

        Point p1 = new Point((mRgba.cols() / 2) - 50, 50);
        Point p2 = new Point((mRgba.cols() / 2) + 100, mRgba.rows() - 50);

        mRgba = inputFrame.rgba();
        roi = new Rect(p1, p2);
        ROIfront = new Mat(inputFrame.gray(), roi);

        //Color detection
        if (mIsColorSelected) {
            mDetector.process(mRgba);
            List<MatOfPoint> _contours = mDetector.getContours();
            Log.e(TAG, "Contours count: " + _contours.size());
            Imgproc.drawContours(mRgba, _contours, -1, CONTOUR_COLOR);

            Mat colorLabel = mRgba.submat(4, 68, 4, 68);
            colorLabel.setTo(mBlobColorRgba);

            Mat spectrumLabel = mRgba.submat(4, 4 + mSpectrum.rows(), 70, 70 + mSpectrum.cols());
            mSpectrum.copyTo(spectrumLabel);
        }

//        if(_nuts!= null)
//            _communication.DataSend(_nuts.size()+"");

//        Imgproc.drawContours(mRgba, _nuts, -1, new Scalar(0, 255,0),
//                1, 8, new Mat(), 1, roi.tl());

//        Imgproc.rectangle(mRgba, p1, p2, Scalar.all(255), 2);
        return mRgba;
    }

    public void nutsDetection(){

        Imgproc.GaussianBlur(ROIfront, ROIfront, new Size(45, 45), 0);

        int size_erosi = 15;
        int size_derosi = 25;

        Imgproc.erode(ROIfront, mMask,
                Imgproc.getStructuringElement(Imgproc.MORPH_ERODE,
                        new Size(2 * size_erosi + 1, 2 * size_erosi + 1),
                        new Point(0, 0)));
        Imgproc.dilate(mMask, mMask,
                Imgproc.getStructuringElement(Imgproc.MORPH_DILATE,
                        new Size(2 * size_derosi + 1, 2 * size_derosi + 1),
                        new Point(0, 0)));

        int ths = 30;
        Imgproc.Canny(mMask, mMask, ths, ths/2);

        ArrayList<MatOfPoint> contours = new ArrayList<>();
        Mat _hierarchy = new Mat();

        Imgproc.findContours(mMask, contours, _hierarchy, Imgproc.RETR_TREE, Imgproc.CHAIN_APPROX_SIMPLE);

        ArrayList<MatOfPoint> _nuts = getNuts(contours);
    }

    public ArrayList<MatOfPoint> getNuts(ArrayList<MatOfPoint> contours){
        ArrayList<MatOfPoint> _nuts = null;


        for(MatOfPoint _m : contours){
            if(isNuts(_m)){
                if(_nuts == null)
                    _nuts = new ArrayList<>();
                _nuts.add(_m);
            }
        }
        return _nuts;
    }

    private Rect currentPosNuts;

    public boolean isNuts(MatOfPoint _m){
        Rect r = null;
        MatOfPoint2f _m2f = new MatOfPoint2f();
        MatOfPoint2f _approx2f = new MatOfPoint2f();
        MatOfPoint _approxContour = new MatOfPoint();

        _m.convertTo(_m2f, CvType.CV_32FC2);

        Imgproc.approxPolyDP(_m2f, _approx2f, 8, true);

        _approx2f.convertTo(_approxContour, CvType.CV_32S);

        Mat m2 = new Mat(mRgba, roi);

        if(_approxContour.size().height == 4) {
            r = Imgproc.boundingRect(_approxContour);

            if(currentPosNuts == null)
                currentPosNuts = r;

            if ((r.tl().x - currentPosNuts.tl().x) > .05 && (r.br().y - currentPosNuts.br().y) > .05) {

                Scalar c = new Scalar(255, 0, 0);
                Point[] pArray = _m.toArray();
                Imgproc.rectangle(m2, r.tl(), r.br(), c, 2);

//                Imgproc.putText(m2, _approxContour.size().height + "", r.tl(), 1, 2, c);
            }
        }

        if(r != null)
            currentPosNuts = r;
        return (r != null);
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

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.option_menu, menu);

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()){
            case R.id.konek_bt :
                finish();
                startActivity(new Intent(this, DeviceList.class));
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int cols = mRgba.cols();
        int rows = mRgba.rows();

        int xOffset = (mCameraBridgeViewBase.getWidth() - cols) / 2;
        int yOffset = (mCameraBridgeViewBase.getHeight() - rows) / 2;

        int x = (int)event.getX() - xOffset;
        int y = (int)event.getY() - yOffset;

        Log.i(TAG, "Touch image coordinates: (" + x + ", " + y + ")");

        if ((x < 0) || (y < 0) || (x > cols) || (y > rows)) return false;

        Rect touchedRect = new Rect();

        touchedRect.x = (x>4) ? x-4 : 0;
        touchedRect.y = (y>4) ? y-4 : 0;

        touchedRect.width = (x+4 < cols) ? x + 4 - touchedRect.x : cols - touchedRect.x;
        touchedRect.height = (y+4 < rows) ? y + 4 - touchedRect.y : rows - touchedRect.y;

        Mat touchedRegionRgba = mRgba.submat(touchedRect);

        Mat touchedRegionHsv = new Mat();
        Imgproc.cvtColor(touchedRegionRgba, touchedRegionHsv, Imgproc.COLOR_RGB2HSV_FULL);

        // Calculate average color of touched region
        mBlobColorHsv = Core.sumElems(touchedRegionHsv);
        int pointCount = touchedRect.width*touchedRect.height;
        for (int i = 0; i < mBlobColorHsv.val.length; i++)
            mBlobColorHsv.val[i] /= pointCount;

        mBlobColorRgba = converScalarHsv2Rgba(mBlobColorHsv);

        Log.i(TAG, "Touched rgba color: (" + mBlobColorRgba.val[0] + ", " + mBlobColorRgba.val[1] +
                ", " + mBlobColorRgba.val[2] + ", " + mBlobColorRgba.val[3] + ")");

        mDetector.setHsvColor(mBlobColorHsv);

        Imgproc.resize(mDetector.getSpectrum(), mSpectrum, SPECTRUM_SIZE);

        mIsColorSelected = true;

        touchedRegionRgba.release();
        touchedRegionHsv.release();

        return super.onTouchEvent(event);
    }


    private Scalar converScalarHsv2Rgba(Scalar hsvColor) {
        Mat pointMatRgba = new Mat();
        Mat pointMatHsv = new Mat(1, 1, CvType.CV_8UC3, hsvColor);
        Imgproc.cvtColor(pointMatHsv, pointMatRgba, Imgproc.COLOR_HSV2RGB_FULL, 4);

        return new Scalar(pointMatRgba.get(0, 0));
    }


//
//    //Deep Neural Network things
//    //Call on CameraViewStarted
//    private void getDatasets(){
//        String proto = getPath("MobileNetSSD_deploy.prototxt", this);
//        String weights = getPath("MobileNetSSD_deploy.caffemodel", this);
//        net = Dnn.readNetFromCaffe(proto, weights);
//
//        Log.i(TAG, "Network loaded successfully");
//    }
//
//    private static final String[] classNames = {"background",
//            "aeroplane", "bicycle", "bird", "boat",
//            "bottle", "bus", "car", "cat", "chair",
//            "cow", "diningtable", "dog", "horse",
//            "motorbike", "person", "pottedplant",
//            "sheep", "sofa", "train", "tvmonitor"};
//
//    //Call onCameraFrame
//    private Mat searchingObjectWithDNN(CameraBridgeViewBase.CvCameraViewFrame inputFrame){
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
//
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
//
//                if(classId == 5) {
//                    // Draw rectangle around detected object.
//                    Imgproc.rectangle(subFrame, new Point(xLeftBottom, yLeftBottom),
//                            new Point(xRightTop, yRightTop),
//                            new Scalar(0, 255, 0));
//                    String label = classNames[classId] + ": " + confidence;
//                    int[] baseLine = new int[1];
//                    Size labelSize = Imgproc.getTextSize(label, Core.FONT_HERSHEY_SIMPLEX, 0.5, 1, baseLine);
//                    // Draw background for label.
//                    Imgproc.rectangle(subFrame, new Point(xLeftBottom, yLeftBottom - labelSize.height),
//                            new Point(xLeftBottom + labelSize.width, yLeftBottom + baseLine[0]),
//                            new Scalar(255, 255, 255), Core.FILLED);
//                    // Write class name and confidence.
//                    Imgproc.putText(subFrame, label, new Point(xLeftBottom, yLeftBottom),
//                            Core.FONT_HERSHEY_SIMPLEX, 0.5, new Scalar(0, 0, 0));
//                }
//            }
//        }
//
//        return frame;
//    }
//
////     Upload file to storage and return a path.
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
