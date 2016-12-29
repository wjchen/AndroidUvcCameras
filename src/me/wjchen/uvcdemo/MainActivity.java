package me.wjchen.uvcdemo;

import java.io.IOException;

import android.app.Activity;
import android.os.Bundle;
import me.wjchen.utils.ShellExe;



public class MainActivity extends Activity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_home_page);
        try {
			ShellExe.execRootCommand("chmod 666 /dev/video*");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	protected void onStart() {
		super.onStart();
	}

	@Override
	protected void onResume() {
		super.onResume();
	}

	@Override
	protected void onPause() {
		super.onPause();
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
	}
}
