package me.wjchen.uvc;

import java.io.IOException;
import java.util.List;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.Log;
import android.view.TextureView;


public class CameraPreviewNormal extends TextureView implements Camera.PreviewCallback, TextureView.SurfaceTextureListener {
	private Camera mCamera;
	private int mCameraId  = 0;
	private int mWidth     = 1280;
	private int mHeight    = 720;
	private int mScaleType = SCALE_TYPE_CENTER;
	private int mPreviewOrient = 0;
	
	private static final int SCALE_TYPE_CENTER      = 0;
	private static final int SCALE_TYPE_CENTER_CROP = 1;
	private static final int SCALE_TYPE_FIT         = 2;
    private static final String TAG = "CameraPreviewNomal";
    
    private int getCamId(int type) {
    	int n = Camera.getNumberOfCameras();
    	for (int i = 0; i < n; i++) {
    		CameraInfo cameraInfo = new CameraInfo();
    		Camera.getCameraInfo(i, cameraInfo);
    		if (cameraInfo.facing == type) {
    			return i;
    		}
    	}
    	return -1;
    }
    
    public CameraPreviewNormal(Context context, AttributeSet attrs) {
        super(context, attrs);
		setSurfaceTextureListener(this);
		int orient = context.getResources().getConfiguration().orientation;
		if (orient == Configuration.ORIENTATION_LANDSCAPE) {
			mPreviewOrient = 0;
		} else if (orient == Configuration.ORIENTATION_PORTRAIT) {
			mPreviewOrient = 90;
		}
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
				// camera id
				if (name.equals("camType")) {
					if (value.equals("front")) {
						mCameraId = getCamId(CameraInfo.CAMERA_FACING_FRONT);
					} else {
						mCameraId = getCamId(CameraInfo.CAMERA_FACING_BACK);
					}
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
		} // else
    }
    
	@Override
	public void onSurfaceTextureAvailable(SurfaceTexture surface, int arg1, int arg2) {
		if (mCamera == null) {
			mCamera = Camera.open(mCameraId);
		}
		if (mCamera != null) {
			Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
			Camera.getCameraInfo(mCameraId, cameraInfo);

			Camera.Parameters params = mCamera.getParameters();
			List<Camera.Size> preview_list = params.getSupportedPreviewSizes();
			int n = preview_list.size();
			if (n <= 0) {
				mCamera.setPreviewCallback(null);
				mCamera.release();
				mCamera = null;
				return;
			}

			boolean found = false;
			for (int i = 0; i < n; i++) {
				Camera.Size size = preview_list.get(i);
				if (size.width == mWidth && size.height == mHeight) {
					params.setPreviewSize(size.width, size.height);
					found = true;
					break;
				}
			}
			
			if (found == false) {
				Log.d(TAG, "set preview size to 640x480");
				params.setPreviewSize(640, 480);
			}

			Camera.Size size = mCamera.getParameters().getPreviewSize();
			int mPreviewWidth = size.width;
			int mPreviewHeight = size.height;

			// transform preview
			double winWidth = this.getWidth();
			double winHeight = this.getHeight();
			if (mPreviewOrient == 90 || mPreviewOrient == -90 || mPreviewOrient == 270) {
				winWidth = this.getHeight();
				winHeight = this.getWidth();
			}
			RectF previewRect = new RectF(0, 0, (int)winWidth - 1, (int)winHeight - 1);
			double ratioWidth = winWidth / mPreviewWidth * 1.0;
			double ratioHeight = winHeight / mPreviewHeight * 1.0;
			double ratio = 0.0;
			int dw, dh, cropDw, cropDh;
			int rectWidth, rectHeight;
			if (ratioWidth < ratioHeight) {
				ratio = ratioWidth;
				rectWidth = (int) winWidth;
				rectHeight = (int) (mPreviewHeight * ratio);
				dw = 0;
				dh = (int) ((winHeight - rectHeight) / 2); // 居中位移
				cropDw = (int) (((mPreviewWidth * ratioHeight) - winWidth));
				cropDh = 0;
			} else {
				ratio = ratioHeight;
				rectHeight = (int) winHeight;
				rectWidth = (int) (mPreviewWidth * ratio);
				dh = 0;
				dw = (int) ((winWidth - rectWidth) / 2); // 居中位移
				cropDw = 0;
				cropDh = (int) (((mPreviewHeight * ratioWidth) - winHeight));
			}

			RectF surfaceDimensions;
			if (mScaleType == SCALE_TYPE_CENTER) {
				surfaceDimensions = new RectF(0 + dw, 0 + dh, rectWidth - 1 + dw, rectHeight - 1 + dh); // 居中
			} else if (mScaleType == SCALE_TYPE_CENTER_CROP) {
				surfaceDimensions = new RectF(-cropDw, -cropDh, (int)winWidth-1, (int)winHeight-1); //裁剪
			} else {
				surfaceDimensions = new RectF(0, 0, (int)winWidth, (int)winHeight);
			}
			Matrix matrix = new Matrix();
			matrix.setRectToRect(previewRect, surfaceDimensions, Matrix.ScaleToFit.FILL);
			setTransform(matrix);

			// set camera param
			mCamera.setParameters(params);
			mCamera.setDisplayOrientation(mPreviewOrient);
			mCamera.setPreviewCallback(this);
		}

		try {
			mCamera.setPreviewTexture(null);
			SystemClock.sleep(50);
			mCamera.setPreviewTexture(surface);
			mCamera.startPreview();
		} catch (IOException e) {
			Log.d(TAG, e.toString());
		}
	}

	@Override
	public boolean onSurfaceTextureDestroyed(SurfaceTexture arg0) {
		if (mCamera != null) {
			mCamera.setPreviewCallback(null);
			mCamera.release();
			mCamera = null;
		}
		return false;
	}

	@Override
	public void onSurfaceTextureSizeChanged(SurfaceTexture arg0, int arg1, int arg2) {
	}

	@Override
	public void onSurfaceTextureUpdated(SurfaceTexture sf) {
	}

	@Override
	public void onPreviewFrame(byte[] data, Camera cam) {
		// preview data NV21
	}

}