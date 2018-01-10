package roboticsas.org.autounderwater;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;

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
import org.opencv.imgproc.Imgproc;

import java.util.Iterator;
import java.util.List;

//
public class Camera extends AppCompatActivity implements CameraBridgeViewBase.CvCameraViewListener2{

    public static final String      TAG = "OpenCV::Activity";

    CheckBox specT, detT;
    RadioGroup radGroup;
    RadioButton lineDetMode, nutsDetMode;
    TextView dataHistory;
    ScrollView scrollView;

    private static final String myPreferences = "myPrefs";
    private static final String color_H[] = { "HueLine", "HueNuts" };
    private static final String color_S[] = { "SaturationLine", "SaturationNuts" };
    private static final String color_V[] = { "ValueLine", "ValueNuts" };
    private static final String isSelected = "isSelected";

    private CameraBridgeViewBase    mCameraBridgeViewBase;
//    private Net                     net;
    private ColorBlobDetector       mDetectorLine;
    private ColorBlobDetector       mDetectorNuts;
    private Scalar                  mBlobColorRgba;
    private Scalar                  mBlobColorHsv;
    private Size                    SPECTRUM_SIZE;
//    private Scalar                  CONTOUR_COLOR;
    private Mat                     mRgba, RoILine, RoINuts, mSpectrum;
    private boolean                 mIsColorSelected = false;
    private boolean                 mIsBTConnected = false;
    private DataCommunication       _communication;
//    private Rect                    currentPosNuts;
    private int  _cols = 0, _rows = 0, radioSel = 0;
    
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

        specT       = findViewById(R.id.specT);
        detT        = findViewById(R.id.detT);
        dataHistory = findViewById(R.id.dataHistory);
        scrollView  = findViewById(R.id.scrollView);
        radGroup    = findViewById(R.id.radGroup);
        lineDetMode = findViewById(R.id.linedetectMode);
        nutsDetMode = findViewById(R.id.nutsdetectMode);

        radGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup radioGroup, int i) {
                switch (i){
                    case R.id.linedetectMode :
                        radioSel = 1;
                        break;
                    case R.id.nutsdetectMode :
                        radioSel = 2;
                        break;
                }
            }
        });

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
        mRgba           = new Mat(height, width, CvType.CV_8UC4);
        RoILine         = new Mat(height, width, CvType.CV_8UC4);
        RoINuts         = new Mat(height, width, CvType.CV_8UC4);

        mDetectorLine   = new ColorBlobDetector();
        mDetectorNuts   = new ColorBlobDetector();
        mSpectrum       = new Mat();
        mBlobColorRgba  = new Scalar(255);
        mBlobColorHsv   = new Scalar(255);
        SPECTRUM_SIZE   = new Size(200, 64);
//        CONTOUR_COLOR = new Scalar(255,0,0,255);

        //widget
        specT.setEnabled(true);


        SharedPreferences sharedPreferences = getSharedPreferences(myPreferences, Context.MODE_PRIVATE);
        mIsColorSelected = sharedPreferences.getBoolean(isSelected, false);

        if(mIsColorSelected){
            mBlobColorHsv = new Scalar(sharedPreferences.getFloat(color_H[0], 0),
                    sharedPreferences.getFloat(color_S[0], 0),
                    sharedPreferences.getFloat(color_V[0], 0));

            mDetectorLine.setHsvColor(mBlobColorHsv);

            mBlobColorHsv = new Scalar(sharedPreferences.getFloat(color_H[1], 0),
                    sharedPreferences.getFloat(color_S[1], 0),
                    sharedPreferences.getFloat(color_V[1], 0));

            mDetectorNuts.setHsvColor(mBlobColorHsv);
        }


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

        Point point1[] = {
                new Point(450, 50),
                new Point(150, 50)
        };
        Point point2[] = {
                new Point(450 + 150, mRgba.rows() - 50),
                new Point(150 + 250, mRgba.rows() - 50)
        };

        Rect _roiLine   = new Rect(point1[0], point2[0]);
        Rect _roiNuts   = new Rect(point1[1], point2[1]);
        RoILine         = new Mat(inputFrame.rgba(), _roiLine);
        RoINuts         = new Mat(inputFrame.rgba(), _roiNuts);
        mRgba           = inputFrame.rgba();

        //Color detection
        if (mIsColorSelected) {
            mDetectorLine.process(RoILine);
            List<MatOfPoint> _contoursLine = mDetectorLine.getContours();
            Log.e(TAG, "Contours count: " + _contoursLine.size());
//            Imgproc.drawContours(RoILine, _contoursLine, -1, CONTOUR_COLOR, 3);

            mDetectorNuts.process(RoINuts);
            List<MatOfPoint> _contoursNuts = mDetectorNuts.getContours();
            Log.e(TAG, "Contours count: " + _contoursNuts.size());
//            Imgproc.drawContours(RoILine, _contoursNuts, -1, CONTOUR_COLOR, 3);


            if(specT.isChecked()) {
                int row[] = {(int) point1[0].y, (int) (point1[0].y+55)};
                int col[] = {(int) (point2[0].x), (int) (point2[0].x+50)};

                Mat colorLabel = mRgba.submat(row[0], row[1]+10, col[0], col[1]);
                colorLabel.setTo(mBlobColorRgba);

                Mat spectrumLabel = mRgba.submat(row[0], row[0] + mSpectrum.rows(), col[1]+20, col[1]+20 + mSpectrum.cols());
                mSpectrum.copyTo(spectrumLabel);
            }


            //Mode digunakan untuk mengetahui dalam mode apakah robot/wahana sedang berjalan
            //Mode 0    : menjadi tanda bahwa robot harus menjadtuhkan bawaan
            //Mode 1    : menjadi tanda bahwa robot harus mengikuti garis
            //Mode 2    : menjadi tanda bahwa robot harus mengambil bawaan

            int mode;
            
            if(!_contoursLine.isEmpty()){
                mode = 1;
                Imgproc.rectangle(RoILine, mDetectorLine.getRectangle().tl(), mDetectorLine.getRectangle().br(), new Scalar(255, 0, 255), 2);
                Imgproc.line(RoILine, new Point(0, RoILine.height()/2),
                        new Point(RoILine.width(), RoILine.height()/2), new Scalar(125, 255, 255), 3);
                Imgproc.line(RoILine, new Point(RoILine.width()/2, RoILine.height()/2)
                        , new Point(RoILine.width()/2, mDetectorLine.getCenterR().y), new Scalar(125, 255, 255), 3);
                Imgproc.circle(RoILine, new Point(RoILine.width()/2, mDetectorLine.getCenterR().y), 3,
                        new Scalar(125, 255, 255), 2);

            }else mode = 0;

            if(!_contoursNuts.isEmpty()){
                mode = 2;
                Imgproc.rectangle(RoINuts, mDetectorNuts.getRectangle().tl(), mDetectorNuts.getRectangle().br(), new Scalar(255, 0, 255), 2);
                Imgproc.line(RoINuts, new Point(0, RoINuts.height()/2),
                        new Point(RoINuts.width(), RoINuts.height()/2), new Scalar(125, 255, 255), 3);
                Imgproc.line(RoINuts, new Point(mDetectorNuts.getCenterR().x, RoINuts.height()/2)
                        , mDetectorNuts.getCenterR(), new Scalar(125, 255, 255), 3);
                Imgproc.circle(RoINuts, mDetectorNuts.getCenterR(), 3,
                        new Scalar(125, 255, 255), 2);

            }


            if(mIsBTConnected) {
                String data = "";
                switch(mode){
                    case 0 :
                        data = "0:0:0#";
                        break;
                    case 1 :
                        data = mDetectorLine.getCenterR().y + ":" + "0" + ":" + mode +"#";
                        break;
                    case 2 :
                        data = mDetectorNuts.getCenterR().y + ":" + mDetectorNuts.getCenterR().x + ":" + mode +"#";
                        break;
                }
                _communication.DataSend(data);

                scrollView.post(new Runnable() {
                    @Override
                    public void run() {
                        scrollView.fullScroll(ScrollView.FOCUS_DOWN);
                    }
                });
                tvAppend(data);
            }
        }

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (detT.isChecked()) radGroup.setVisibility(View.VISIBLE);
                else {
                    radGroup.setVisibility(View.GONE);
                    radioSel = 0;
                }
            }
        });

        Imgproc.rectangle(mRgba, point1[0], point2[0], Scalar.all(255), 2);
        Imgproc.rectangle(mRgba, point1[1], point2[1], Scalar.all(255), 2);

        return mRgba;
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

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int cols = _rows;
        int rows = _cols;

        int xOffset = (mCameraBridgeViewBase.getWidth() - cols) / 2;
        int yOffset = (mCameraBridgeViewBase.getHeight() - rows) / 2;

        int x = (int) (map(event.getY(), 400, 1800, 0, mRgba.height()) - yOffset) + 785;
        int y = (int) (map(event.getX(), 0, 1070, mRgba.width(), 0) - xOffset) - 90;

        Log.i(TAG, "Touch image coordinates: (" + x + ", " + y + ")");


        if ((x < 0) || (y < 0)) return false;
        if ((x > mRgba.cols()) || (y > mRgba.rows())) return false;

        Rect touchedRect = new Rect();

        touchedRect.x = (x > 4) ? x - 4 : 0;
        touchedRect.y = (y > 4) ? y - 4 : 0;

        touchedRect.width = (x + 4 < cols) ? x + 4 - touchedRect.x : cols - touchedRect.x;
        touchedRect.height = (y + 4 < rows) ? y + 4 - touchedRect.y : rows - touchedRect.y;

        Mat touchedRegionRgba = mRgba.submat(touchedRect);

        Mat touchedRegionHsv = new Mat();
        Imgproc.cvtColor(touchedRegionRgba, touchedRegionHsv, Imgproc.COLOR_RGB2HSV_FULL);
        // Calculate average color of touched region
        mBlobColorHsv = Core.sumElems(touchedRegionHsv);
        int pointCount = touchedRect.width * touchedRect.height;
        for (int i = 0; i < mBlobColorHsv.val.length; i++)
            mBlobColorHsv.val[i] /= pointCount;

        mBlobColorRgba = converScalarHsv2Rgba(mBlobColorHsv);

        Log.i(TAG, "Touched rgba color: (" + mBlobColorRgba.val[0] + ", " + mBlobColorRgba.val[1] +
                ", " + mBlobColorRgba.val[2] + ", " + mBlobColorRgba.val[3] + ")");

        if(detT.isChecked()) {
            SharedPreferences sharedPreferences = getSharedPreferences(myPreferences, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = sharedPreferences.edit();
            if(radioSel == 1) {
                mDetectorLine.setHsvColor(mBlobColorHsv);

                editor.putFloat(color_H[0], (float) mBlobColorHsv.val[0]);
                editor.putFloat(color_S[0], (float) mBlobColorHsv.val[1]);
                editor.putFloat(color_V[0], (float) mBlobColorHsv.val[2]);
                Imgproc.resize(mDetectorLine.getSpectrum(), mSpectrum, SPECTRUM_SIZE);
            }
            if(radioSel == 2){
                mDetectorNuts.setHsvColor(mBlobColorHsv);

                editor.putFloat(color_H[1], (float) mBlobColorHsv.val[0]);
                editor.putFloat(color_S[1], (float) mBlobColorHsv.val[1]);
                editor.putFloat(color_V[1], (float) mBlobColorHsv.val[2]);
                Imgproc.resize(mDetectorNuts.getSpectrum(), mSpectrum, SPECTRUM_SIZE);
            }

            mIsColorSelected = true;

            editor.putBoolean(isSelected, mIsColorSelected);
            editor.apply();
            editor.commit();

        }

        touchedRegionRgba.release();
        touchedRegionHsv.release();

        return super.onTouchEvent(event);
    }

    @NonNull
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
                if(dataHistory.getText().toString().length() > 1000)
                    dataHistory.setText("");
            }
        });
    }


//    public void nutsDetection(Mat inputFrame){
//
//
//        Mat gray = new Mat();
//        Imgproc.cvtColor(inputFrame, gray, Imgproc.COLOR_RGBA2GRAY);
//        Mat RoIGray = new Mat(gray, _roiNuts);
//        Imgproc.GaussianBlur(RoIGray, RoIGray, new Size(15, 15), 0);
//
//        int size_erosi = 2;
//        int size_derosi = 4;
//
//        Imgproc.erode(RoIGray, mMask,
//                Imgproc.getStructuringElement(Imgproc.MORPH_ERODE,
//                        new Size(2 * size_erosi + 1, 2 * size_erosi + 1),
//                        new Point(0, 0)));
//        Imgproc.dilate(mMask, mMask,
//                Imgproc.getStructuringElement(Imgproc.MORPH_DILATE,
//                        new Size(2 * size_derosi + 1, 2 * size_derosi + 1),
//                        new Point(0, 0)));
//
//        int ths = 30;
//        Imgproc.Canny(mMask, mMask, ths, ths/2);
//
//        ArrayList<MatOfPoint> contours = new ArrayList<>();
//
//        Imgproc.findContours(mMask, contours, new Mat(), Imgproc.RETR_TREE, Imgproc.CHAIN_APPROX_SIMPLE);
//
//        ArrayList<MatOfPoint> _nuts = getNuts(contours);
//        Imgproc.drawContours(RoINuts, _nuts, -1, Scalar.all(255), 2);
//
//    }
//
//    public ArrayList<MatOfPoint> getNuts(ArrayList<MatOfPoint> contours){
//        ArrayList<MatOfPoint> _nuts = null;
//
//        for(MatOfPoint _m : contours){
//            if(isNuts(_m)){
//                if(_nuts == null)
//                    _nuts = new ArrayList<>();
//                _nuts.add(_m);
//            }
//        }
//        return _nuts;
//    }
//
//    public boolean isNuts(MatOfPoint _m){
//        Rect r = null;
//        MatOfPoint2f _m2f = new MatOfPoint2f();
//        MatOfPoint2f _approx2f = new MatOfPoint2f();
//        MatOfPoint _approxContour = new MatOfPoint();
//
//        _m.convertTo(_m2f, CvType.CV_32FC2);
//
//        Imgproc.approxPolyDP(_m2f, _approx2f, 8, true);
//
//        _approx2f.convertTo(_approxContour, CvType.CV_32S);
//
//        if(_approxContour.size().height == 6) {
//            r = Imgproc.boundingRect(_approxContour);
//
//            if(currentPosNuts == null)
//                currentPosNuts = r;
//
////            if ((r.tl().x - currentPosNuts.tl().x) > .05 && (r.br().y - currentPosNuts.br().y) > .05) {
//
//            Scalar c = new Scalar(255, 0, 0);
//            Point[] pArray = _m.toArray();
//            Imgproc.rectangle(RoINuts, r.tl(), r.br(), c, 2);
////            }
//        }
//
//        if(r != null)
//            currentPosNuts = r;
//        return (r != null);
//    }

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
