package me.wjchen.uvc;


import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbInterface;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;
import android.util.AttributeSet;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import me.wjchen.uvc.USBMonitor.OnDeviceConnectListener;
import me.wjchen.uvc.USBMonitor.UsbControlBlock;
import me.wjchen.v4l.V4lJni;


public class CameraPreviewUVC extends SurfaceView implements SurfaceHolder.Callback, Runnable {
    private Thread mainLoop = null;
    private SurfaceHolder holder;
    private boolean shouldStop = false;
    private static final String TAG = "CameraPreview";
    private USBMonitor mUsbMonitor;
	private static final String DEFAULT_USBFS = "/dev/bus/usb";
	private long cameraHandle = 0;
	private List<UsbDevice> camList = null;
	
	private int mCameraId  = 0;
	private int mWidth     = 1280;
	private int mHeight    = 720;
	private int mScaleType = SCALE_TYPE_CENTER;
	private boolean mIsFront = false;
	
	private static final int SCALE_TYPE_CENTER      = 0;
	private static final int SCALE_TYPE_CENTER_CROP = 1;
	private static final int SCALE_TYPE_FIT         = 2;

    //////////////////////////////////////////////////////////
    public CameraPreviewUVC(Context context, AttributeSet attrs) {
        super(context, attrs);
        setFocusable(true);
        this.holder = getHolder();
        this.holder.addCallback(this);
		mUsbMonitor = new USBMonitor(context, mOnDeviceConnectListener);
		refreshDevice();

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
					mIsFront  = true;
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
        mUsbMonitor.register();
        mUsbMonitor.requestPermission(getCamDev(mCameraId));

        Canvas canvas = holder.lockCanvas();
        if (canvas != null) {
            canvas.drawRGB(0, 0, 0);
            holder.unlockCanvasAndPost(canvas);
        }
        
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
        if (cameraHandle != 0) {
        	V4lJni.stopCamera(cameraHandle);
        	cameraHandle = 0;
        }

        mUsbMonitor.unregister();
        mUsbMonitor.destroy();

        if (mainLoop != null) {
            mainLoop.interrupt();
            try { 
                mainLoop.join(); 
            } catch (InterruptedException e) { 
                e.printStackTrace(); 
            }
            mainLoop = null;
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
            if (cameraHandle == 0 || cameraId < 0) {
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
                    Log.e(TAG, "preview:"+previewWidth+"x"+previewHeight);
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

    
    private OnDeviceConnectListener mOnDeviceConnectListener = new OnDeviceConnectListener() {
		
		@Override
		public void onDisconnect(UsbDevice device, UsbControlBlock ctrlBlock) {
	        if (cameraHandle != 0) {
	        	V4lJni.stopCamera(cameraHandle);
	        	cameraHandle = 0;
	        }
		}
		
		@Override
		public void onDettach(UsbDevice device) {}
		
		@Override
		public void onConnect(UsbDevice device, UsbControlBlock ctrlBlock, boolean createNew) {
			int vid = device.getVendorId();
			int pid = device.getProductId();
			int fd = ctrlBlock.getFileDescriptor();
			String usbfs = getUSBFSName(ctrlBlock);
			int camid = getDevIdByName(device.getDeviceName());
			
			cameraHandle = V4lJni.openCameraByFd(fd, vid, pid, camid, usbfs, mWidth, mHeight, 0);
		}
		
		@Override
		public void onCancel() {}
		
		@Override
		public void onAttach(UsbDevice device) {}
	};
	
	private final void refreshDevice() {
		if (mUsbMonitor == null) {
			return;
		}
		camList = null;
		List<UsbDevice> allDev = mUsbMonitor.getDeviceList();

		if (allDev.size() <= 0) {
			return;
		}
		
		for (UsbDevice d : allDev) {
			boolean isVideoDev = false;

			if (d.getInterfaceCount() <= 0) {
				if (d.getDeviceClass() == 239 && d.getDeviceSubclass() == 2) {
					isVideoDev = true;
				}
			} else {
				for (int i = 0; i < d.getInterfaceCount(); i++) {
					UsbInterface in = d.getInterface(0);
					if (in == null) {
						continue;
					}
					if (in.getInterfaceClass() == 14) {
						isVideoDev = true;
						break;
					}
				}
			}

			if (isVideoDev) {
				if (camList == null) {
					camList = new ArrayList<UsbDevice>();
				}
				camList.add(d);
			}

		}
		if (camList == null) {
			System.out.println("v4l no device");
		} else {
			Collections.sort(camList, new Comparator<UsbDevice>(){
				@Override
				public int compare(UsbDevice left, UsbDevice right) {
					return (left.getDeviceName().compareTo(right.getDeviceName()));
				}
			});
		}
	}


	private final UsbDevice getCamDev(int camid) {
		if (camList == null) {
			return null;
		}
		if (camList.size() <= 0 || camid >= camList.size()) {
			return null;
		}

		return camList.get(camid);
	}
	
	private final int getDevIdByName(String name) {
		if (camList == null || camList.size() <= 0) {
			return -1;
		}
		for (int i = 0; i < camList.size(); i++) {
			if (camList.get(i).getDeviceName().equals(name)) {
				return i;
			}
		}
		return -1;
	}
	
	private final String getUSBFSName(final UsbControlBlock ctrlBlock) {
		String result = null;
		final String name = ctrlBlock.getDeviceName();
		final String[] v = !TextUtils.isEmpty(name) ? name.split("/") : null;
		if ((v != null) && (v.length > 2)) {
			final StringBuilder sb = new StringBuilder(v[0]);
			for (int i = 1; i < v.length - 2; i++)
				sb.append("/").append(v[i]);
			result = sb.toString();
		}
		if (TextUtils.isEmpty(result)) {
			Log.w(TAG, "failed to get USBFS path, try to use default path:" + name);
			result = DEFAULT_USBFS;
		}
		return result;
	}
}