
package youten.redo.y1camerapreview;

import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.hardware.Camera;
import android.hardware.Camera.Parameters;
import android.os.Build;
import android.os.Bundle;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.TextView;

/**
 * Camera#setPreviewCallback無印は今時ありえないよね、WithBuffer必須ですよのSample<br>
 * UI説明<br>
 * 左上：Camera Previewそのまま
 * 右下：YUV420SP→ARGB変換後のデータをCanvas#drawBitmapしたもの
 * 右上：YUV420SP→ARGBの変換処理のJavaレイヤ換算での時間、FPS
 *
 * @author youten
 */
public class MainActivity extends Activity {
    private static final String TAG = "YUV420SP";
    private static final String FORMAT_FPS = "YUV420SP->ARGB %d fps\nAve. %.3fms\nmin %.3fms max %.3fms";
    private static int PREVIEW_WIDTH = 640;
    private static int PREVIEW_HEIGHT = 480;
    // private int mSurfaceWidth = 640;
    // private int mSurfaceHeight = 480;

    private SurfaceView mPreviewSurfaceView;
    private SurfaceView mFilterSurfaceView;
    private Camera mCamera;

    // for filter
    // YUV420SP→ARGB変換後の描画先Surfaceのバッファは面倒なのでサイズ固定。
    private int[] mRGBData = new int[PREVIEW_WIDTH * PREVIEW_HEIGHT];
    private Paint mPaint = new Paint();

    // for fps
    // 適当処理時間・FPS表示領域用タイマ等メンバ変数
    private TextView mFpsTextView;
    private long mSumEffectTime;
    private long mMinEffectTime = 0;
    private long mMaxEffectTime = 0;
    private long mFrames;
    private long mPrivFrames;
    private String mFpsString;
    private Timer mFpsTimer;

    /**
     * Camera Previewの流し込み先SurfaceViewのCallback
     */
    private SurfaceHolder.Callback mPreviewSurfaceListener = new SurfaceHolder.Callback() {

        public void surfaceCreated(SurfaceHolder holder) {
            // あればFront Cameraを使うつもりの適当コード。
            // Front CameraのPreviewの特性として左右が反転します。
            if (Camera.getNumberOfCameras() > 1) {
                mCamera = Camera.open(1);
            } else {
                mCamera = Camera.open(0);
            }
            if (mCamera != null) {
                mCamera.setPreviewCallbackWithBuffer(mPreviewCallback);
                try {
                    mCamera.setPreviewDisplay(holder);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            // mSurfaceWidth = width;
            // mSurfaceHeight = height;
            if (mCamera != null) {
                // init preview
                mCamera.stopPreview();
                Parameters params = mCamera.getParameters();
                // ﾋｬｯﾊｰ、対応していないデバイスは知るか、VGA固定だー！
                params.setPreviewSize(PREVIEW_WIDTH, PREVIEW_HEIGHT);
                mCamera.setParameters(params);
                mCamera.startPreview();

                // AndroidのCameraのからPreviewを取得した際の標準フォーマットYUV420SPは
                // Yチャンネルが1画素8bit、UとVチャンネルがそれぞれ2x2画素毎に8bit含まれるので、1/4+1/4=1/2。
                // その結果必要なバッファ量は画素数の3/2倍になるのでこういう書き方をします。
                mCamera.addCallbackBuffer(new byte[PREVIEW_WIDTH * PREVIEW_HEIGHT * 3 / 2]);
            }

            // start timer
            mFrames = 0;
            mPrivFrames = 0;
            mSumEffectTime = 0;
            mFpsTimer = new Timer();
            mFpsTimer.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    // nano sec.で取得してるので適当に見やすく桁あわせ
                    if ((mPrivFrames > 0) && (mSumEffectTime > 0)) {
                        long frames = mFrames - mPrivFrames;
                        mFpsString = String.format(FORMAT_FPS, frames,
                                ((double) mSumEffectTime) / (frames * 1000000.0),
                                ((double) mMinEffectTime) / (1000000.0), ((double) mMaxEffectTime) / (1000000.0));
                        mSumEffectTime = 0;
                        mMinEffectTime = 0;
                        mMaxEffectTime = 0;
                        runOnUiThread(new Runnable() {
                            public void run() {
                                mFpsTextView.setText(mFpsString);
                            }
                        });
                    }
                    mPrivFrames = mFrames;
                }
            }, 0, 1000); // 1000ms periodic
        }

        public void surfaceDestroyed(SurfaceHolder holder) {
            // stop timer
            if (mFpsTimer != null) {
                mFpsTimer.cancel();
                mFpsTimer = null;
            }

            // deinit preview
            if (mCamera != null) {
                mCamera.stopPreview();
                mCamera.setPreviewCallbackWithBuffer(null);
                mCamera.release();
                mCamera = null;
            }
        }

    };

    /**
     * Camera Previewの取得Callback。
     */
    private Camera.PreviewCallback mPreviewCallback = new Camera.PreviewCallback() {

        public void onPreviewFrame(byte[] data, Camera camera) {
            // 何も考えずにバッファをaddしてますがこの後変換処理してるので本当は良くないです。
            // 理想は3面以上のバッファを持って別Threadで画像処理タスクのキューイング管理が必要。
            if (camera != null) {
                camera.addCallbackBuffer(data);
            }

            // YUV420SP→ARGB変換。
            // しつこくかいてるけど"YUV"と"YUV420SP"は結構示す範囲が違うので正確に表記すべき。
            long before = System.nanoTime();
            JavaFilter.decodeYUV420SP(mRGBData, data, PREVIEW_WIDTH, PREVIEW_HEIGHT);
            long after = System.nanoTime();
            updateEffectTimes(after - before);

            // int[]なARGB列に変換ができたらCanvas#drawBitmapで描画します。
            if (mFilterSurfaceView != null) {
                SurfaceHolder holder = mFilterSurfaceView.getHolder();
                Canvas canvas = holder.lockCanvas();
                // canvas.save();
                // canvas.scale(mSurfaceWidth / PREVIEW_WIDTH, mSurfaceHeight / PREVIEW_HEIGHT);
                canvas.drawBitmap(mRGBData, 0, PREVIEW_WIDTH, 0, 0, PREVIEW_WIDTH, PREVIEW_HEIGHT, false, mPaint);
                // canvas.restore();
                holder.unlockCanvasAndPost(canvas);
                mFrames++;
            }
        }
    };

    /**
     * 処理時間を更新。
     *
     * @param elapsed 経過時間
     */
    private void updateEffectTimes(long elapsed) {
        if (elapsed <= 0) {
            return;
        }
        if ((mMinEffectTime == 0) || (elapsed < mMinEffectTime)) {
            mMinEffectTime = elapsed;
        }
        if ((mMaxEffectTime == 0) || (mMaxEffectTime < elapsed)) {
            mMaxEffectTime = elapsed;
        }
        mSumEffectTime += elapsed;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        init();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        deinit();
    }

    @SuppressWarnings("deprecation")
    private void init() {
        mPreviewSurfaceView = (SurfaceView) findViewById(R.id.preview_surface);
        SurfaceHolder holder = mPreviewSurfaceView.getHolder();
        holder.addCallback(mPreviewSurfaceListener);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
            holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        }

        mFilterSurfaceView = (SurfaceView) findViewById(R.id.filter_surface);
        mFilterSurfaceView.setZOrderOnTop(true);
        mFpsTextView = (TextView) findViewById(R.id.fps_text);
    }

    private void deinit() {
        SurfaceHolder holder = mPreviewSurfaceView.getHolder();
        holder.removeCallback(mPreviewSurfaceListener);

        mPreviewSurfaceView = null;
        mFilterSurfaceView = null;
        mFpsTextView = null;
    }

}
