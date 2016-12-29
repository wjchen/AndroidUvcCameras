package me.wjchen.uvc;

import java.io.IOException;

import android.app.Service;
import android.content.Intent;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

public class CameraService extends Service {

	private static int mCameraId = 0;
	private static Camera mCamera = null;
	private static int mWidth = 640;
	private static int mHeight = 480;
	private static int mPreviewOrient = 0; // 0 for landscape
	private static int mCameraType = 0;
	private static Handler handler = null;
	private static SurfaceTexture mSurface = null;

	private static final String TAG = "CameraService";
	private static final int FACE_FOUND_MSG = 1;

	public static void setCameraId(int id) {
		mCameraId = id;
	}

	public static void setWidthHeight(int width, int height) {
		mWidth = width;
		mHeight = height;
	}

	public static void setPreviewOrientation(int orient) {
		mPreviewOrient = orient;
	}
	
	public static void startCamera() {
		if (mCamera == null) {
			mCamera = Camera.open(mCameraId);
		}
		if (mCamera != null) {
			Camera.Parameters params = mCamera.getParameters();
			params.setPreviewSize(mWidth, mHeight);
			mCamera.setParameters(params); // 参数
			mCamera.setDisplayOrientation(mPreviewOrient); // 方向
			mSurface = new SurfaceTexture(mCameraId); // 显示视频的view
			mCamera.setPreviewCallback(mCameraCb); // 预览回调
			try {
				mCamera.setPreviewTexture(mSurface); // 显示视频的view
			} catch (IOException e) {
				e.printStackTrace();
			}
			mCamera.startPreview();
		} else {
			Log.e(TAG, "open camera " + mCameraId + " failed");
		}
	}

	public static void stopCamera() {
		if (mCamera != null) {
			try {
				mCamera.setPreviewTexture(null);
			} catch (IOException e) {
				e.printStackTrace();
			}
			mCamera.setPreviewCallback(null);
			mCamera.release();
			mCamera = null;
		}
		mSurface = null;
	}



	private static void handlerDestroy() {
		if (handler != null) {
			handler.removeMessages(FACE_FOUND_MSG);
			handler = null;
		}
	}

	public static void destroyAll() {

		handlerDestroy();
		stopCamera();
	}

	@Override
	public void onCreate() {
		super.onCreate();

	}

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		destroyAll();
	}
	
	// 获取相机类型, 0逆时针90度， 1顺时针90度， 2不旋转, 3旋转180度
	private static int getCameraType(int orient, int camid) {
		int type = 0;
		boolean isFront = false;
		
		CameraInfo cameraInfo = new CameraInfo();
		Camera.getCameraInfo(camid, cameraInfo);
		if (cameraInfo.facing == CameraInfo.CAMERA_FACING_FRONT) {
			isFront = true;
		}

		if (orient == 90) {
			if (isFront) {
				type = 0;
			} else {
				type = 1;
			}
		} else if (orient == 0) {
			type = 2;
		} else if (orient == 180) {
			type = 3;
		} else if (orient == 270) {
			if (isFront) {
				type = 1;
			} else {
				type = 0;
			}
		}
		return type;
	}

	private static Camera.PreviewCallback mCameraCb = new Camera.PreviewCallback() {

		@Override
		public void onPreviewFrame(byte[] data, Camera cam) {
//			Camera.Parameters parameters = cam.getParameters();
//			Camera.Size size = parameters.getPreviewSize();
//			Log.d(TAG, "data length:" + data.length);
//			int width = size.width;
//			int height = size.height;
			mCameraType = getCameraType(mPreviewOrient, mCameraId);
			Log.d(TAG, "camera type "+ mCameraType);
			
		}
	};
}
