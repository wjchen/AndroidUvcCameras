package me.wjchen.v4l;

import android.graphics.Bitmap;

public class V4lJni {
    /* load our native library */
    static {
        System.loadLibrary("v4ljni-common");
    }

    // 打开相机函数，失败返回0
    public static native long openCamera(int id);
    // frame_skip丢帧，降低视频解码cpu的占用
    public static native long openCameraExt(int id, int width, int height, int frame_skip);
    public static native long openCameraByFd(int fd,  int vid, int pid, int camid, String usbfs, 
    		int width, int height, int frame_skip);
    

    // handle为openCamera的返回值
    public static native void stopCamera(long handle);
    public static native int getWidth(int camid);
    public static native int getHeight(int camid);
    public static native int getFPS(int camid);

    // 返回ARGB_8888格式的bitmap, 显示视频专用, isFront是否开启前置摄像头左右镜像功能
    public static native int getCurrBitmap(int camid, Bitmap bmp, boolean isFront);
    
    // 获取当前的视频帧数据， 失败返回null
    public static native byte[] getCurrJpg(int camId);
    public static native byte[] getCurrBGR(int camId);
    
    // 设置摄像头输出旋转方向， 0逆时针90度， 1顺时针90度， 2不旋转, 3旋转180度
    public static native void setRotate(int camId, int cam_type);
    public static native int  getRotate(int camId);

    public static  byte[] BGR2Gray(byte[] bgr) {
    	int n = bgr.length;
    	if (n <= 0) {
    		return null;
    	}
    	byte[] gray = new byte[n/3];
    	int i, j;
    	for	(i = 0, j = 0; i < n; i+= 3, j++) {
    		int b = bgr[i]&0xff;
    		int g = bgr[i+1]&0xff;
    		int r = bgr[i+2]&0xff;
    		gray[j] = (byte) (((r*77)+(g*151)+(b*28)) >> 8);
    	}
    	return gray;
    }
}
