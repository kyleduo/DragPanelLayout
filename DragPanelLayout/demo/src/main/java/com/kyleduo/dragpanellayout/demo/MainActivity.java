package com.kyleduo.dragpanellayout.demo;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import com.kyleduo.dragpanellayout.DragPanelLayout;

public class MainActivity extends AppCompatActivity {

	private DragPanelLayout mDragPanelLayout;

	@SuppressWarnings("ConstantConditions")
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		mDragPanelLayout = (DragPanelLayout) findViewById(R.id.dpl);
		final ImageView iv = (ImageView) findViewById(R.id.kdpl_content);
		iv.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				toast("click content");
			}
		});
		findViewById(R.id.text).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				if (mDragPanelLayout.getDragState() == DragPanelLayout.DragState.COLLAPSED) {
					mDragPanelLayout.expand();
				} else if (mDragPanelLayout.getDragState() == DragPanelLayout.DragState.EXPANDED) {
					mDragPanelLayout.collapse();
				}
				toast("click text");
			}
		});
		mDragPanelLayout.setOnDragListener(new DragPanelLayout.OnDragListener() {
			@Override
			public void onDragStateChanged(DragPanelLayout.DragState oldState, DragPanelLayout.DragState newState) {

			}

			@Override
			public void onDragging(float progress) {
				Log.d("ss", "pro: " + progress);
				iv.setAlpha(progress);
//				iv.setScaleX(2 - progress);
//				iv.setScaleY(2 - progress);
			}
		});
	}

	private void toast(CharSequence message) {
		Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
	}
}
