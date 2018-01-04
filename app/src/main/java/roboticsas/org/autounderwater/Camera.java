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
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

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
import java.util.Iterator;
import java.util.List;
import java.util.Random;

//
public class Camera extends AppCompatActivity implements CameraBridgeViewBase.CvCameraViewListener2{

    public static final String      TAG = "OpenCV::Activity";

    CheckBox specT;
    TextView dataHistory;
    ScrollView scrollView;

    private CameraBridgeViewBase    mCameraBridgeViewBase;
    private Net                     net;
    private ColorBlobDetector       mDetector;
    private Scalar                  mBlobColorRgba;
    private Scalar                  mBlobColorHsv;
    private Size                    SPECTRUM_SIZE;
    private Scalar                  CONTOUR_COLOR;
    private Mat                     mRgba, mMask, mMix, RoILine, mSpectrum;
    private Rect                    _roi;
    private boolean                 mIsColorSelected = false;
    private boolean                 mIsBTConnected = false;
    private ArrayList<MatOfPoint>   mContour = new ArrayList<>();
    private DataCommunication       _communication;
    private Rect                    currentPosNuts;
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

        specT = findViewById(R.id.specT);
        dataHistory = findViewById(R.id.dataHistory);
        scrollView = findViewById(R.id.scrollView);

        mCameraBridgeViewBase = findViewById(R.id.javaCameraView);
//        mCameraBridgeViewBase.setMaxFrameSize(500, 500);
        mCameraBridgeViewBase.setVisibility(SurfaceView.VISIBLE);
        mCameraBridgeViewBase.setCvCameraViewListener(this);
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

        String str = getIntent().getStringExtra(DeviceList.EXTRA_ADDRESS);
        if(str != null && !str.isEmpty()) {
            mIsBTConnected = true;
            _communication = new DataCommunication(getIntent(), this);

            _communication.BTExecute();
        }else mIsBTConnected = false;


    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if(mCameraBridgeViewBase != null)
            mCameraBridgeViewBase.disableView();
    }

    @Override
    public void onCameraViewStarted(int width, int height) {
        mRgba       = new Mat(height, width, CvType.CV_8UC4);
        RoILine    = new Mat(height, width, CvType.CV_8UC4);
        mMask       = new Mat();
        mMix        = new Mat();

        mDetector = new ColorBlobDetector();
        mSpectrum = new Mat();
        mBlobColorRgba = new Scalar(255);
        mBlobColorHsv = new Scalar(255);
        SPECTRUM_SIZE = new Size(200, 64);
        CONTOUR_COLOR = new Scalar(255,0,0,255);

        //widget
        specT.setEnabled(true);

        //Akibat dari di rotate camera menjadi potrait via coding..
        _width = height;
        _height = width;
        _cols = mRgba.rows();
        _rows = mRgba.cols();
    }

    @Override
    public void onCameraViewStopped() {
        mRgba.release();
        RoILine.release();
    }

    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {

        Point p1 = new Point((mRgba.cols() / 2) - 50, 50);
        Point p2 = new Point((mRgba.cols() / 2) + 100, mRgba.rows() - 50);

        mRgba = inputFrame.rgba();
        _roi = new Rect(p1, p2);
        RoILine = new Mat(inputFrame.rgba(), _roi);

        //Color detection
        if (mIsColorSelected) {
            mDetector.process(RoILine);
            List<MatOfPoint> _contours = mDetector.getContours();
            Log.e(TAG, "Contours count: " + _contours.size());
//            Imgproc.drawContours(RoILine, _contours, -1, CONTOUR_COLOR, 3);

            if(specT.isChecked()) {
                int row[] = {50, 100};
                int col[] = {150, 210};

                Mat colorLabel = mRgba.submat(row[0], row[1]+10, col[0], col[1]);
                colorLabel.setTo(mBlobColorRgba);

                Mat spectrumLabel = mRgba.submat(row[0], row[0] + mSpectrum.rows(), col[1]+20, col[1]+20 + mSpectrum.cols());
                mSpectrum.copyTo(spectrumLabel);
            }

            Imgproc.rectangle(RoILine, mDetector.getRectangle().tl(), mDetector.getRectangle().br(), new Scalar(255, 0, 255), 2);
            Imgproc.line(RoILine, new Point(0, RoILine.height()/2),
                    new Point(RoILine.width(), RoILine.height()/2), new Scalar(125, 255, 255), 3);
            Imgproc.line(RoILine, new Point(RoILine.width()/2, RoILine.height()/2)
                    , new Point(RoILine.width()/2, mDetector.getCenterR().y), new Scalar(125, 255, 255), 3);
            Imgproc.circle(RoILine, new Point(RoILine.width()/2, mDetector.getCenterR().y), 3,
                    new Scalar(125, 255, 255), 2);


            if(mIsBTConnected = true) {
               // _communication.DataSend(mDetector.getCenterR() + "");

                scrollView.post(new Runnable() {
                    @Override
                    public void run() {
                        scrollView.fullScroll(ScrollView.FOCUS_DOWN);
                    }
                });
                tvAppend(mDetector.getCenterR().y+"");
            }
        }

        Imgproc.rectangle(mRgba, p1, p2, Scalar.all(255), 2);

        return mRgba;
    }

    public void nutsDetection(){

        Imgproc.GaussianBlur(RoILine, RoILine, new Size(45, 45), 0);

        int size_erosi = 15;
        int size_derosi = 25;

        Imgproc.erode(RoILine, mMask,
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

    public boolean isNuts(MatOfPoint _m){
        Rect r = null;
        MatOfPoint2f _m2f = new MatOfPoint2f();
        MatOfPoint2f _approx2f = new MatOfPoint2f();
        MatOfPoint _approxContour = new MatOfPoint();

        _m.convertTo(_m2f, CvType.CV_32FC2);

        Imgproc.approxPolyDP(_m2f, _approx2f, 8, true);

        _approx2f.convertTo(_approxContour, CvType.CV_32S);

        Mat m2 = new Mat(mRgba, _roi);

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

    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.option_menu, menu);

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()){
            case R.id.konek_bt :
                startActivity(new Intent(this, DeviceList.class));
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    //untuk PoI touchListener
    int _x;
    int _y;
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int cols = _rows;
        int rows = _cols;

        int xOffset = (mCameraBridgeViewBase.getWidth() - cols) / 2;
        int yOffset = (mCameraBridgeViewBase.getHeight() - rows) / 2;

        int x = (int) (map(event.getY(), 400, 1800, 0, mRgba.height()) - yOffset) + 785;
        int y = (int) (map(event.getX(), 0, 1070, mRgba.width(), 0) - xOffset) - 90;
        _x = x;
        _y = y;
        Log.i(TAG, "Touch image coordinates: (" + x + ", " + mRgba.cols() + ")");


        if ((x < 0) || (y < 0)) return false;
        if ((x > mRgba.cols()) || (y > mRgba.rows())) return false;

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

    private float map(float value, float low1, float high1, float low2, float high2){
        return low2 + (value - low1) * (high2 - low2) / (high1 - low1);
    }

    private void tvAppend(final String data){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {

                dataHistory.append("\n" + data);
            }
        });
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
