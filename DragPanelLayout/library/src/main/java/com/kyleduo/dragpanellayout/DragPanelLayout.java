package com.kyleduo.dragpanellayout;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.TypedArray;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.v4.widget.ViewDragHelper;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

/**
 * Created by kyle on 16/4/21.
 */
public class DragPanelLayout extends ViewGroup {
	private static final String TAG = "DragPanelLayout";

	private static final DragState DEFAULT_PANEL_STATE = DragState.COLLAPSED;
	private static final PanelLayoutType DEFAULT_PANEL_LAYOUT_TYPE = PanelLayoutType.OVERLAY_CONTENT;

	/// state
	public enum DragState {
		COLLAPSED(0),
		EXPANDED(1),
		HIDDEN(2),
		DRAGGING(3),
		SETTLING(4);

		private int value;

		public static DragState create(int value) {
			switch (value) {
				case 0:
					return COLLAPSED;
				case 1:
					return EXPANDED;
				case 2:
					return HIDDEN;
				case 3:
					return DRAGGING;
				case 4:
					return SETTLING;
				default:
					throw new IllegalArgumentException("Panel state value is illegal");
			}
		}

		DragState(int value) {
			this.value = value;
		}
	}

	public enum PanelLayoutType {
		OVERLAY_CONTENT(0),
		ALIGN_EDGE(1),
		FOLLOW_CONTENT(2);

		private int value;

		public static PanelLayoutType create(int value) {
			switch (value) {
				case 0:
					return OVERLAY_CONTENT;
				case 1:
					return ALIGN_EDGE;
				case 2:
					return FOLLOW_CONTENT;
				default:
					throw new IllegalArgumentException("Panel state value is illegal");
			}
		}

		PanelLayoutType(int value) {
			this.value = value;
		}
	}

	public enum DragDirection {
		UP,
		DOWN
	}

	/// helper class
	private ViewDragHelper mViewDragHelper;

	/// config value
	private boolean mDragEnabled = true;
	/**
	 * Size of area where panel can not go in. Typically used when there are some view you wanna
	 * leave outside of the panel expanded area, in pixel.
	 */
	private int mForbiddenZoneSize;
	/**
	 * How to layout panel and effect content max height.
	 */
	private PanelLayoutType mPanelLayoutType;
	private int mDragBarHeight;
	/**
	 * Distance of contentView's parallax.
	 */
	private int mParallaxDistance;
	/**
	 * whether can trigger drag when drag content.
	 */
	private boolean mContentDragEnabled;
	private float mMinFlingVelocity;
	private OnDragListener mOnDragListener;

	/// inner control value
	/**
	 * Used for StateSaving.
	 */
	private DragState mLastStaticDragState;
	private DragState mDragState;
	/**
	 * Rate of progress of dragging, [0..1] 0 for collapsed, 1 for expanded
	 */
	private float mDraggingProgress;
	private int mScrollRange;
	private int mCollapsedYPosition;
	private int mHelperCollapsedYPosition;
	private float mDiacriticalPositionRatio = 0.3f;
	private float mFlingDiacriticalPositionRatioScale = 100000;
	private float mPreviousTouchY;
	private DragPanelHelper mDragPanelHelper;
	private boolean mShouldInterceptTouchEvent = true;

	/// views
	/**
	 * Content view of scroll, normally display by default.
	 */
	private View mContentView;
	/**
	 * View to be dragged.
	 */
	private View mDraggableView;
	/**
	 * Used when need trigger dragging from whole screen.
	 */
	private View mHelperDraggableView;

	public DragPanelLayout(Context context) {
		super(context);
		init(null);
	}

	public DragPanelLayout(Context context, AttributeSet attrs) {
		super(context, attrs);
		init(attrs);
	}

	public DragPanelLayout(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		init(attrs);
	}

	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	public DragPanelLayout(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
		super(context, attrs, defStyleAttr, defStyleRes);
		init(attrs);
	}

	private void init(AttributeSet attrs) {
		if (isInEditMode()) {
			mViewDragHelper = null;
			return;
		}

		mViewDragHelper = ViewDragHelper.create(this, 0.5f, new ViewDragHelperCallback());
		mPanelLayoutType = PanelLayoutType.ALIGN_EDGE;
		mDragPanelHelper = new com.kyleduo.dragpanellayout.DragPanelHelper();

		TypedArray ta = attrs == null ? null : getContext().obtainStyledAttributes(attrs, R.styleable.DragPanelLayout);
		if (ta != null) {

			mDragBarHeight = (int) ta.getDimension(R.styleable.DragPanelLayout_kdplDragBarHeight, mDragBarHeight);
			mDragState = DragState.create(ta.getInteger(R.styleable.DragPanelLayout_kdplPanelState, DEFAULT_PANEL_STATE.value));
			mPanelLayoutType = PanelLayoutType.create(ta.getInteger(R.styleable.DragPanelLayout_kdplPanelLayoutType, DEFAULT_PANEL_LAYOUT_TYPE.value));
			mParallaxDistance = (int) ta.getDimension(R.styleable.DragPanelLayout_kdplParallaxOffset, 0);
			mForbiddenZoneSize = (int) ta.getDimension(R.styleable.DragPanelLayout_kdplForbiddenZoneSize, 0);
			mContentDragEnabled = ta.getBoolean(R.styleable.DragPanelLayout_kdplContentDragEnabled, false);

			if (mDragState == DragState.DRAGGING || mDragState == DragState.SETTLING) {
				mDragState = DragState.COLLAPSED;
			}
			mLastStaticDragState = mDragState;

			ta.recycle();
		}
	}

	///////////////////////////////////////////////////////////
	/////////    View methods
	///////////////////////////////////////////////////////////
	@Override
	public Parcelable onSaveInstanceState() {
		Parcelable superState = super.onSaveInstanceState();

		SavedState ss = new SavedState(superState);
		if (mDragState != DragState.DRAGGING) {
			ss.mDragState = mDragState;
		} else {
			ss.mDragState = mLastStaticDragState;
		}
		return ss;
	}

	@Override
	public void onRestoreInstanceState(Parcelable state) {
		SavedState ss = (SavedState) state;
		super.onRestoreInstanceState(ss.getSuperState());
		mDragState = ss.mDragState != null ? ss.mDragState : DEFAULT_PANEL_STATE;
	}

	@Override
	protected ViewGroup.LayoutParams generateDefaultLayoutParams() {
		return new LayoutParams();
	}

	@Override
	protected ViewGroup.LayoutParams generateLayoutParams(ViewGroup.LayoutParams p) {
		return p instanceof MarginLayoutParams
				? new LayoutParams((MarginLayoutParams) p)
				: new LayoutParams(p);
	}

	@Override
	protected boolean checkLayoutParams(ViewGroup.LayoutParams p) {
		return p instanceof LayoutParams && super.checkLayoutParams(p);
	}

	@Override
	public ViewGroup.LayoutParams generateLayoutParams(AttributeSet attrs) {
		return new LayoutParams(getContext(), attrs);
	}

	@Override
	protected void onFinishInflate() {
		super.onFinishInflate();
		mDraggableView = findViewById(R.id.kdpl_draggable_panel);
		mContentView = findViewById(R.id.kdpl_content);
//		Not must be.
//		if (mDraggableView == null) {
//			throw new IllegalStateException("DraggableView can not be null");
//		}
		if (mContentView == null) {
			throw new IllegalStateException("ContentView can not be null");
		}

		if (mContentDragEnabled) {
			mHelperDraggableView = new View(getContext());
//			mHelperDraggableView.setBackgroundColor(0x88FFFFFF);
			addView(mHelperDraggableView, indexOfChild(mContentView) + 1);
		}
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		final int widthMode = MeasureSpec.getMode(widthMeasureSpec);
		final int widthSize = MeasureSpec.getSize(widthMeasureSpec);
		final int heightMode = MeasureSpec.getMode(heightMeasureSpec);
		final int heightSize = MeasureSpec.getSize(heightMeasureSpec);

		if (widthMode != MeasureSpec.EXACTLY) {
			throw new IllegalStateException("Width must have an exact value or MATCH_PARENT");
		} else if (heightMode != MeasureSpec.EXACTLY) {
			throw new IllegalStateException("Height must have an exact value or MATCH_PARENT");
		}

		int layoutWidth = widthSize - getPaddingLeft() - getPaddingRight();
		int layoutHeight = heightSize - getPaddingTop() - getPaddingBottom();

		for (int i = 0; i < getChildCount(); i++) {
			View child = getChildAt(i);
			LayoutParams lp = (LayoutParams) child.getLayoutParams();

			int width = layoutWidth;
			int height = layoutHeight;

			width -= lp.leftMargin + lp.rightMargin;
			height -= lp.topMargin + lp.bottomMargin;

			// Make sure panel view will shown when it does not overlay
			if (child == mContentView && mDragBarHeight > 0) {
				if (mPanelLayoutType == PanelLayoutType.FOLLOW_CONTENT && (widthSize - child.getMeasuredHeight()) < mDragBarHeight) {
					// If left space is not enough for drag bar, force type = ALIGN_EDGE, this
					// situation can only be confirmed at second measure time.
					mPanelLayoutType = PanelLayoutType.ALIGN_EDGE;
				}
				if (mPanelLayoutType == PanelLayoutType.ALIGN_EDGE) {
					height -= mDragBarHeight;
				}
			} else if (child == mDraggableView) {
				height -= mForbiddenZoneSize;
			} else if (child == mHelperDraggableView) {
				if (mPanelLayoutType == PanelLayoutType.FOLLOW_CONTENT) {
					height = mContentView.getMeasuredHeight();
				} else {
					height -= mDragBarHeight;
				}
			}

			int childWidthSpec;
			if (lp.width == LayoutParams.WRAP_CONTENT) {
				childWidthSpec = MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST);
			} else if (lp.width == LayoutParams.MATCH_PARENT) {
				childWidthSpec = MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY);
			} else {
				childWidthSpec = MeasureSpec.makeMeasureSpec(lp.width, MeasureSpec.EXACTLY);
			}

			int childHeightSpec;
			if (lp.height == LayoutParams.WRAP_CONTENT) {
				childHeightSpec = MeasureSpec.makeMeasureSpec(height, MeasureSpec.AT_MOST);
			} else {
				// Modify the height based on the weight.
				if (lp.weight > 0 && lp.weight < 1) {
					height = (int) (height * lp.weight);
				} else if (lp.height != LayoutParams.MATCH_PARENT) {
					height = lp.height;
				}
				childHeightSpec = MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY);
			}

			child.measure(childWidthSpec, childHeightSpec);

			if (child == mDraggableView) {
				if (mPanelLayoutType != PanelLayoutType.FOLLOW_CONTENT) {
					mScrollRange = mDraggableView.getMeasuredHeight() - mDragBarHeight;
				}

				mScrollRange = Math.max(mScrollRange, 0);
			}
		}

		setMeasuredDimension(widthSize, heightSize);
	}

	@Override
	protected void onLayout(boolean changed, int l, int t, int r, int b) {
		int left = getPaddingLeft();
		int top = getPaddingTop();

		for (int i = 0; i < getChildCount(); i++) {
			View child = getChildAt(i);
			int childWidth = child.getMeasuredWidth();
			int childHeight = child.getMeasuredHeight();

			if (child == mDraggableView) {
				if (mPanelLayoutType == PanelLayoutType.FOLLOW_CONTENT) {
					mCollapsedYPosition = top + mContentView.getMeasuredHeight()
							+ ((LayoutParams) mContentView.getLayoutParams()).bottomMargin
							+ ((LayoutParams) child.getLayoutParams()).topMargin;
				} else {
					mCollapsedYPosition = getMeasuredHeight() - mDragBarHeight;
				}

				if (mDragState == DragState.COLLAPSED) {
					top = mCollapsedYPosition;
				} else if (mDragState == DragState.EXPANDED) {
					top = mCollapsedYPosition - mScrollRange;
				} else if (mDragState == DragState.HIDDEN) {
					top = mCollapsedYPosition + getMeasuredHeight() - mCollapsedYPosition;
				}

				if (mPanelLayoutType == PanelLayoutType.FOLLOW_CONTENT) {
					mScrollRange = Math.max(mDraggableView.getMeasuredHeight() - (getMeasuredHeight() - mCollapsedYPosition), 0);
				}
			} else if (child == mContentView) {
				top = getPaddingTop();
				mHelperCollapsedYPosition = top;

				if (mDragState == DragState.EXPANDED && mParallaxDistance > 0) {
					top -= mParallaxDistance;
				}
			} else if (child == mHelperDraggableView) {
				top = mDraggableView.getTop() - mHelperDraggableView.getMeasuredHeight();
			}

			child.layout(left, top, left + childWidth, top + childHeight);
		}

		mDraggingProgress = computeProgress(mDraggableView.getTop());
	}

	///////////////////////////////////////////////////////////
	/////////    Drag control
	///////////////////////////////////////////////////////////


	@Override
	public boolean dispatchTouchEvent(MotionEvent ev) {
		int action = ev.getActionMasked();
		Log.d(TAG, "dispatchTouchEvent " + action);
		switch (action) {
			case MotionEvent.ACTION_DOWN:
				mShouldInterceptTouchEvent = true;
				mDragPanelHelper.tryCaptureScrollableView(mDraggableView, ev);
				break;
			case MotionEvent.ACTION_MOVE:
				Log.d(TAG, "mLastStaticDragState: " + mLastStaticDragState + " state: " + mDragState);
				DragState state = (mDragState == DragState.COLLAPSED || mDragState == DragState.EXPANDED) ? mDragState : mLastStaticDragState;
				boolean intercept = mDragPanelHelper.canDrag(state, ev);
				Log.d(TAG, "intercept: " + intercept);
				if (!intercept) {
					mShouldInterceptTouchEvent = false;
					if (mViewDragHelper.getViewDragState() == ViewDragHelper.STATE_DRAGGING) {
						mViewDragHelper.cancel();
						ev.setAction(MotionEvent.ACTION_DOWN);
					}
					return super.dispatchTouchEvent(ev);
				} else {
					if (!mShouldInterceptTouchEvent) {
						MotionEvent up = MotionEvent.obtain(ev);
						up.setAction(MotionEvent.ACTION_CANCEL);
						super.dispatchTouchEvent(up);
						up.recycle();
						ev.setAction(MotionEvent.ACTION_DOWN);
					}
					mShouldInterceptTouchEvent = true;
					return this.onTouchEvent(ev);
				}
		}
		return super.dispatchTouchEvent(ev);
	}

	@Override
	public boolean onInterceptTouchEvent(MotionEvent event) {
		Log.i(TAG, "onInterceptTouchEvent " + event.getActionMasked() + " mShouldInterceptTouchEvent: " + mShouldInterceptTouchEvent);
		if (!mDragEnabled || !isEnabled() || !mShouldInterceptTouchEvent) {
			mViewDragHelper.abort();
			return false;
		}
		int action = event.getActionMasked();
		switch (action) {
			case MotionEvent.ACTION_CANCEL:
			case MotionEvent.ACTION_UP:
				// If the dragView is still dragging when we get here, we need to call processTouchEvent
				// so that the view is settled
				// Added to make scrollable views work (tokudu)
				if (mViewDragHelper.getViewDragState() == ViewDragHelper.STATE_DRAGGING) {
					mViewDragHelper.processTouchEvent(event);
					return true;
				}
				// Check if this was a click on the faded part of the screen, and fire off the listener if there is one.
//				if (ady <= dragSlop
//						&& adx <= dragSlop
//						&& mSlideOffset > 0 && !isViewUnder(mSlideableView, (int) mInitialMotionX, (int) mInitialMotionY) && mFadeOnClickListener != null) {
//					playSoundEffect(android.view.SoundEffectConstants.CLICK);
//					mFadeOnClickListener.onClick(this);
//					return true;
//				}
				break;
		}

		return mViewDragHelper.shouldInterceptTouchEvent(event);
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		Log.w(TAG, "onTouchEvent " + event.getActionMasked());
		if (!mDragEnabled || !isEnabled()) {
			return false;
		}

		mViewDragHelper.processTouchEvent(event);
		return true;
	}

	@Override
	public void computeScroll() {
		if (mViewDragHelper.continueSettling(true)) {
			invalidate();
		}
	}

	private int computePosition(float scrollOffset) {
		return mCollapsedYPosition - (int) Math.floor(mScrollRange * scrollOffset);
	}

	private float computeProgress(int position) {
		return mScrollRange == 0 ? 0 : (float) (mCollapsedYPosition - position) / mScrollRange;
	}

	///////////////////////////////////////////////////////////
	/////////    internal methods
	///////////////////////////////////////////////////////////

	private void setPanelStateInternal(DragState state) {
		if (mDragState == state) {
			return;
		}
		DragState oldState = mDragState;
		if (oldState != DragState.DRAGGING && oldState != DragState.SETTLING) {
			mLastStaticDragState = oldState;
		}
		mDragState = state;
		dispatchOnPanelStateChanged(oldState, state);
	}

	private void fixPanelStateByPosition() {
		mDraggingProgress = computeProgress(mDraggableView.getTop());
		if (mDraggingProgress == 0) {
			setPanelStateInternal(DragState.COLLAPSED);
		} else if (mDraggingProgress == 1) {
			setPanelStateInternal(DragState.EXPANDED);
		}
	}

	private void dispatchOnPanelStateChanged(DragState oldState, DragState newState) {
		if (mOnDragListener != null) {
			mOnDragListener.onDragStateChanged(oldState, newState);
		}
	}

	private void dispatchOnDragging() {
		if (mOnDragListener != null) {
			mOnDragListener.onDragging(mDraggingProgress);
		}
	}

	///////////////////////////////////////////////////////////
	/////////    Getter & Setter
	///////////////////////////////////////////////////////////

	public final boolean isDragEnabled() {
		return mDraggableView != null && mDragEnabled;
	}

	public final void setDragEnabled(boolean dragEnabled) {
		mDragEnabled = dragEnabled;
	}

	public final DragState getDragState() {
		return mDragState;
	}

	public void setOnDragListener(OnDragListener onDragListener) {
		mOnDragListener = onDragListener;
	}

	///////////////////////////////////////////////////////////
	/////////    Inner Classes
	///////////////////////////////////////////////////////////

	/**
	 * Sub-class of ViewDragHelper.Callback
	 */
	private class ViewDragHelperCallback extends ViewDragHelper.Callback {
		@Override
		public boolean tryCaptureView(View child, int pointerId) {
			return mDraggableView == child || (mHelperDraggableView == child && mContentDragEnabled);
		}

		@Override
		public int clampViewPositionVertical(View child, int top, int dy) {
			int position = top;
			if (child == mDraggableView) {
				position = Math.max(position, mCollapsedYPosition - mScrollRange);
				position = Math.min(position, mCollapsedYPosition);
			} else if (child == mHelperDraggableView) {
				position = Math.max(position, mHelperCollapsedYPosition - mScrollRange);
				position = Math.min(position, mHelperCollapsedYPosition);
			}
			return position;
		}

		@Override
		public int getViewVerticalDragRange(View child) {
			return mScrollRange;
		}

		@Override
		public void onViewDragStateChanged(int state) {
			super.onViewDragStateChanged(state);
			switch (state) {
				case ViewDragHelper.STATE_DRAGGING:
					if (mDragState != DragState.DRAGGING) {
						setPanelStateInternal(DragState.DRAGGING);
					}
					break;
				case ViewDragHelper.STATE_SETTLING:
					setPanelStateInternal(DragState.SETTLING);
					break;
			}
		}

		@Override
		public void onViewReleased(View releasedChild, float xvel, float yvel) {
			super.onViewReleased(releasedChild, xvel, yvel);

			int collapsedPosition = mCollapsedYPosition;

			int targetPosition = collapsedPosition;
			float absv = Math.abs(yvel);
			float flingRatio = absv / mFlingDiacriticalPositionRatioScale;

			int currentPosition = mDraggableView.getTop();
			int expandDiacriticalPosition = (int) (collapsedPosition - mScrollRange * mDiacriticalPositionRatio); // when drag up extends this point, judge as expand.;
			int expandFlingDiacriticalPosition = (int) (collapsedPosition - mScrollRange * flingRatio); // when fling up extends this point, judge as expand.
			int collapseDiacriticalPosition = (int) (collapsedPosition - mScrollRange * (1 - mDiacriticalPositionRatio));
			int collapseFlingDiacriticalPosition = (int) (collapsedPosition - mScrollRange * (1 - flingRatio));

//			Log.d(TAG, "absv: " + absv + " currentPosition: " + currentPosition + " expandFlingDiacriticalPosition: " + expandFlingDiacriticalPosition);

			switch (mLastStaticDragState) {
				case COLLAPSED:
					if (yvel <= 0) {
						if (currentPosition < expandDiacriticalPosition || (currentPosition < expandFlingDiacriticalPosition && absv > mMinFlingVelocity)) {
							targetPosition = collapsedPosition - mScrollRange;
						} else {
							targetPosition = collapsedPosition;
						}
					} else {
						targetPosition = collapsedPosition;
					}
					break;
				case EXPANDED:
					if (yvel >= 0) {
						if (currentPosition > collapseDiacriticalPosition || (currentPosition > collapseFlingDiacriticalPosition && absv > mMinFlingVelocity)) {
							targetPosition = collapsedPosition;
						} else {
							targetPosition = collapsedPosition - mScrollRange;
						}
					} else {
						targetPosition = collapsedPosition - mScrollRange;
					}
					break;
			}


			int targetDy = targetPosition - currentPosition;
			if (releasedChild == mHelperDraggableView) {
				targetPosition = mHelperDraggableView.getTop() + targetDy;
			}

			mViewDragHelper.settleCapturedViewAt(releasedChild.getLeft(), targetPosition);
			postInvalidate();
		}

		@Override
		public void onViewPositionChanged(View changedView, int left, int top, int dx, int dy) {
			super.onViewPositionChanged(changedView, left, top, dx, dy);

			Log.e(TAG, "onViewPositionChanged: " + top);

			fixPanelStateByPosition();
			dispatchOnDragging();

			if (changedView == mHelperDraggableView) {
				mDraggableView.offsetTopAndBottom(dy);
			} else if (changedView == mDraggableView && mHelperDraggableView != null) {
				mHelperDraggableView.offsetTopAndBottom(dy);
			}
			if (mParallaxDistance > 0) {
				int targetTop = mHelperCollapsedYPosition - (int) Math.floor(mParallaxDistance * mDraggingProgress);
				int parallaxDy = targetTop - mContentView.getTop();
				mContentView.offsetTopAndBottom(parallaxDy);
			}
		}
	}

	/**
	 * LayoutParams that account of weight when measuring draggable view.
	 */
	public static class LayoutParams extends ViewGroup.MarginLayoutParams {
		private static final int[] ATTRS = new int[]{
				android.R.attr.layout_weight
		};

		public float weight = 0;

		public LayoutParams() {
			super(MATCH_PARENT, MATCH_PARENT);
		}

		public LayoutParams(int width, int height) {
			super(width, height);
		}

		public LayoutParams(int width, int height, float weight) {
			super(width, height);
			this.weight = weight;
		}

		public LayoutParams(android.view.ViewGroup.LayoutParams source) {
			super(source);
		}

		public LayoutParams(MarginLayoutParams source) {
			super(source);
		}

		public LayoutParams(LayoutParams source) {
			super(source);
		}

		public LayoutParams(Context c, AttributeSet attrs) {
			super(c, attrs);

			final TypedArray ta = c.obtainStyledAttributes(attrs, ATTRS);
			if (ta != null) {
				this.weight = ta.getFloat(0, 0);
				ta.recycle();
			}
		}
	}

	static class SavedState extends BaseSavedState {
		DragState mDragState;

		SavedState(Parcelable superState) {
			super(superState);
		}

		private SavedState(Parcel in) {
			super(in);
			String panelStateString = in.readString();
			try {
				mDragState = panelStateString != null ? Enum.valueOf(DragState.class, panelStateString)
						: DragState.COLLAPSED;
			} catch (IllegalArgumentException e) {
				mDragState = DragState.COLLAPSED;
			}
		}

		@Override
		public void writeToParcel(Parcel out, int flags) {
			super.writeToParcel(out, flags);
			out.writeString(mDragState == null ? null : mDragState.toString());
		}

		public static final Parcelable.Creator<SavedState> CREATOR =
				new Parcelable.Creator<SavedState>() {
					@Override
					public SavedState createFromParcel(Parcel in) {
						return new SavedState(in);
					}

					@Override
					public SavedState[] newArray(int size) {
						return new SavedState[size];
					}
				};
	}

	public interface OnDragListener {
		void onDragStateChanged(DragState oldState, DragState newState);

		void onDragging(float progress);
	}
}
