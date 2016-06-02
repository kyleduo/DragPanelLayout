package com.kyleduo.dragpanellayout.demo;

import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewParent;
import android.widget.ImageView;
import android.widget.Toast;

import com.astuetz.PagerSlidingTabStrip;
import com.kyleduo.dragpanellayout.DragPanelLayout;

public class MainActivity extends AppCompatActivity {

	private DragPanelLayout mDragPanelLayout;
	private Toolbar mToolbar;

	@SuppressWarnings("ConstantConditions")
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		mToolbar = (Toolbar) findViewById(R.id.toolbar);
		setSupportActionBar(mToolbar);
		getSupportActionBar().setHomeButtonEnabled(true);
		getSupportActionBar().setDisplayHomeAsUpEnabled(true);
		getSupportActionBar().setHomeAsUpIndicator(R.drawable.back);

		mDragPanelLayout = (DragPanelLayout) findViewById(R.id.dpl);

		mToolbar.getBackground().setAlpha(0);

		findViewById(R.id.kdpl_content).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				toast("click content");
			}
		});
		mDragPanelLayout.setOnDragListener(new DragPanelLayout.OnDragListener() {
			@Override
			public void onDragStateChanged(DragPanelLayout.DragState oldState, DragPanelLayout.DragState newState) {

			}

			@Override
			public void onDragging(float progress) {
				mToolbar.getBackground().setAlpha((int) (progress * 255));
			}
		});
	}

	private void toast(CharSequence message) {
		Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
	}
}
