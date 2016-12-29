package me.wjchen.uvc;


import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.SystemClock;
import android.util.Log;
import android.util.AttributeSet;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import me.wjchen.v4l.V4lJni;


public class CameraPreviewV4L extends SurfaceView implements SurfaceHolder.Callback, Runnable {
    private Thread mainLoop = null;
    private SurfaceHolder holder;
    private long cameraHandle  = 0;
    private boolean shouldStop = false;
    private static final String TAG = "CameraPreview";
    
	private int mCameraId  = 0;
	private int mWidth     = 1280;
	private int mHeight    = 720;
	private volatile boolean mIsFront = false;
	private int mScaleType = SCALE_TYPE_CENTER;
	
	private static final int SCALE_TYPE_CENTER      = 0;
	private static final int SCALE_TYPE_CENTER_CROP = 1;
	private static final int SCALE_TYPE_FIT         = 2;


    public CameraPreviewV4L(Context context, AttributeSet attrs) {
        super(context, attrs);
        setFocusable(true);
        this.holder = getHolder();
        this.holder.addCallback(this);
        int n = attrs.getAttributeCount();
		if (n <= 0) {
			mCameraId = 0;
		} else {
			for (int i = 0; i < n; i++) {
				String name = attrs.getAttributeName(i);
				String value = attrs.getAttributeValue(i);
				if (name == null || value == null) {
					continue;
				}
				if (name.equals("camType") && value.equals("front")) {
					mIsFront = true;
				}
				// camera id
				if (name.equals("camId")) { 
					mCameraId = Integer.parseInt(value);
				}
				
				if (mCameraId < 0) {
					mCameraId = 0; 
				}
				// width 
				if (name.equals("width")) {
					mWidth = Integer.parseInt(value);
				}
				// height 
				if (name.equals("height")) {
					mHeight = Integer.parseInt(value);
				}
				if (mWidth <= 0 || mHeight <= 0) {
					Log.d(TAG, "Wrong preview size "+mWidth+"x"+mHeight);
				}
				// scaleType
				if (name.equals("scaleType")) {
					if (value.equals("center")) {
						mScaleType = SCALE_TYPE_CENTER;
					} else if (value.equals("centerCrop")) {
						mScaleType = SCALE_TYPE_CENTER_CROP;
					} else {
						mScaleType = SCALE_TYPE_FIT;
					}
				}
			}
		}
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        Canvas canvas = holder.lockCanvas();
        if (canvas != null) {
            canvas.drawRGB(0, 0, 0);
            holder.unlockCanvasAndPost(canvas);
        }
		if (cameraHandle == 0) {
			V4lJni.setRotate(mCameraId, 2); // 0逆时针90度， 1顺时针90度， 2不旋转, 3旋转180度
			cameraHandle = V4lJni.openCameraExt(mCameraId, 1280, 960, 0);
		}
        // 加载视频线程开始
        this.shouldStop = false;
        this.mainLoop = new Thread(this);
        this.mainLoop.start();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        this.shouldStop = true;

        if (mainLoop != null) {
            mainLoop.interrupt();
            try { 
                mainLoop.join(); 
            } catch (InterruptedException e) { 
                e.printStackTrace(); 
            }
            mainLoop = null;
        }
        
		if (cameraHandle != 0) {
			long tmp = cameraHandle;
			cameraHandle = 0;
			V4lJni.stopCamera(tmp);
		}
    }

    @Override
    public void run() {
        Bitmap bmp = null;
        int cameraId = mCameraId;
        int fps = 0;
        int previewWidth = 0;
        int previewHeight = 0;
 
        while (this.shouldStop == false) {
            if (cameraHandle == 0 ) {
                SystemClock.sleep(1000);
                continue;
            } else {
            	if (fps <= 0) {
                    fps = V4lJni.getFPS(cameraId);
                    if (fps <= 0) {
                        fps = 15;
                    }	
            	}
            	if (bmp == null) {
                    previewWidth = V4lJni.getWidth(cameraId);
                    previewHeight = V4lJni.getHeight(cameraId);
                    Log.e(TAG, "priview size:"+previewWidth+"x"+previewHeight);
                    if (previewWidth > 0 && previewHeight > 0) {
                        bmp = Bitmap.createBitmap(previewWidth, previewHeight, Bitmap.Config.ARGB_8888);
                    }
            	}
            }
            if (bmp == null || fps <= 0) {
            	SystemClock.sleep(100);
            	continue;
            }
            double startTime = System.currentTimeMillis();

            if(V4lJni.getCurrBitmap(cameraId, bmp, mIsFront) < 0) {
            	SystemClock.sleep(100);
                continue;
            }

            if (this.shouldStop) {
                this.shouldStop = false;
                break;
            }
            
            double interval = 1000/fps*1.0;
            double winWidth = this.getWidth();
            double winHeight = this.getHeight();
            double ratioWidth = winWidth / previewWidth*1.0;
            double ratioHeight = winHeight / previewHeight*1.0;
            double ratio = 0.0;
            int dw, dh, cropDw, cropDh;
            int rectWidth, rectHeight;
            if (ratioWidth < ratioHeight) {
                ratio = ratioWidth;
                rectWidth = (int)winWidth;
                rectHeight = (int)(previewHeight * ratio);
                dw = 0;
                dh = (int)((winHeight - rectHeight)/2);
                cropDw = (int)(((previewWidth*ratioHeight) - winWidth));
                cropDh = 0;
            } else {
                ratio = ratioHeight;
                rectHeight = (int)winHeight;
                rectWidth = (int)(previewWidth * ratio);
                dh = 0;
                dw = (int)((winWidth - rectWidth)/2);
                cropDw = 0;
                cropDh = (int)(((previewHeight*ratioWidth) - winHeight));
            }
            
            Rect rect;
            if (mScaleType == SCALE_TYPE_CENTER) {
            	rect = new Rect(0+dw, 0+dh, rectWidth-1+dw, rectHeight-1+dh); //居中
            } else if (mScaleType == SCALE_TYPE_CENTER_CROP) {
            	rect = new Rect(-cropDw, -cropDh, (int)winWidth-1, (int)winHeight-1);  //裁剪上部或右部
            } else {
            	rect = new Rect(0, 0, (int)winWidth, (int)winHeight); //拉伸
            }


            Canvas canvas = holder.lockCanvas();
            if (canvas != null && bmp != null) {
                canvas.drawBitmap(bmp, null, rect, null);
                holder.unlockCanvasAndPost(canvas);
            }

            if (this.shouldStop) {
                this.shouldStop = false;
                break;
            }

            double totalTime = System.currentTimeMillis() - startTime;
            if (totalTime < interval) {
                SystemClock.sleep((long)(interval - totalTime));
            }
        }
        if (bmp != null) {
            bmp.recycle();
            bmp = null;	
        }
    }
}