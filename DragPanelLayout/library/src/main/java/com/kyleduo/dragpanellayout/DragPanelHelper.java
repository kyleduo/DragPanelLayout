package com.kyleduo.dragpanellayout;

import android.graphics.PointF;
import android.graphics.Rect;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.ListView;
import android.widget.ScrollView;

/**
 * Created by kyle on 16/4/25.
 */
public class DragPanelHelper {
	private static final String TAG = "DragPanelHelper";

	private View mCurrentScrollableView;
	private Rect mTempRect = new Rect();
	private PointF mInitialPoint = new PointF();

	public void tryCaptureScrollableView(View draggableView, MotionEvent event) {
		if (draggableView == null || !(draggableView instanceof ViewGroup)) {
			return;
		}

		final ViewGroup group = (ViewGroup) draggableView;
		mCurrentScrollableView = captureScrollableView(group, event);
	}

	private View captureScrollableView(ViewGroup viewGroup, MotionEvent event) {
		int count = viewGroup.getChildCount();

		for (int i = count - 1; i >= 0; i--) {
			View child = viewGroup.getChildAt(i);
			if (child instanceof ListView || child instanceof ScrollView || child instanceof RecyclerView || child instanceof WebView) {
				if (pointInView(child, (int) event.getRawX(), (int) event.getRawY())) {
					mInitialPoint.set(event.getRawX(), event.getRawY());
					return child;
				}
			} else if (child instanceof ViewGroup) {
				return captureScrollableView((ViewGroup) child, event);
			}
		}

		return null;
	}

	public boolean canDrag(DragPanelLayout.DragState state, MotionEvent event) {
		if (mCurrentScrollableView == null) {
			return true;
		}
		if (pointInView(mCurrentScrollableView, (int) event.getRawX(), (int) event.getRawY())) {
			return checkDrag(mCurrentScrollableView, state, (int) (event.getRawX() - mInitialPoint.x), (int) (event.getRawY() - mInitialPoint.y));
		}
		Log.i(TAG, "not in view");
		return true;
	}

	protected boolean checkDrag(View scrollableView, DragPanelLayout.DragState state, int dx, int dy) {
		if (state == DragPanelLayout.DragState.EXPANDED && dy < 0) {
			Log.i(TAG, "scroll list");
			return false;
		}
		if (state == DragPanelLayout.DragState.EXPANDED && dy > 0 && !isAtTop(scrollableView)) {
			Log.i(TAG, "do not drag");
			return false;
		}
		Log.i(TAG, "drag");
		return true;
	}

	private boolean pointInView(View view, int x, int y) {
		if (view == null) {
			return false;
		}

		Log.i(TAG, "x: " + x + " y: " + y);
//		Log.i(TAG, "left: " + view.getLeft() + " top: " + view.getTop() + " right: " + view.getRight() + " bottom: " + view.getBottom());

		int[] loc = new int[2];
		view.getLocationOnScreen(loc);
		Log.i(TAG, "left: " + loc[0] + " top: " + loc[1] + " right: " + (loc[0] + view.getMeasuredWidth()) + " bottom: " + (loc[1] + view.getMeasuredHeight()));


		return x > loc[0] && x < loc[0] + view.getMeasuredWidth()
				&& y > loc[1] && y < loc[1] + view.getMeasuredHeight();
	}

	private boolean isAtTop(View view) {
		if (view instanceof ListView) {
			ListView lv = (ListView) view;
			if (lv.getChildCount() == 0) {
				return true;
			}

			boolean atTop = lv.getChildCount() > 0 && lv.getFirstVisiblePosition() == 0 && lv.getChildAt(0).getTop() >= lv.getPaddingTop();
			Log.d(TAG, "atTop: " + atTop);
			return atTop;
		} else if (view instanceof ScrollView) {
			return view.getScrollY() == 0;
		}
		return true;
	}

	private boolean isAtBottom(View view) {
		return false;
	}
}
