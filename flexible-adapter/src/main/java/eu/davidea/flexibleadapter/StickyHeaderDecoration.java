/*
 * Copyright 2014 Eduardo Barrenechea
 * Copyright 2016 Davide Steduto (Hyper-Optimized for FlexibleAdapter project)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package eu.davidea.flexibleadapter;

import android.graphics.Canvas;
import android.support.v4.view.ViewCompat;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.view.ViewGroup;

import java.util.HashMap;
import java.util.Map;

import eu.davidea.flexibleadapter.items.IHeader;

/**
 * A sticky header decoration for RecyclerView, to use only with {@link FlexibleAdapter}.
 */
public class StickyHeaderDecoration extends RecyclerView.ItemDecoration {

	private FlexibleAdapter mAdapter;
	private Map<IHeader, View> mHeaderCache;
	private int maxCachedHeaders;

	/**
	 * @param adapter the sticky header adapter to use
	 */
	public StickyHeaderDecoration(FlexibleAdapter adapter, int maxCachedHeaders) {
		mAdapter = adapter;
		mHeaderCache = new HashMap<IHeader, View>();
		this.maxCachedHeaders = maxCachedHeaders;
	}

	/**
	 * Gets the header view for the associated position. If it doesn't exist yet, it will be
	 * created, measured, and laid out.
	 *
	 * @param recyclerView the RecyclerView
	 * @param position     the adapter position to get the header view for
	 * @return Header view or null if the associated position and previous has no header
	 */
	private View getHeader(RecyclerView recyclerView, int position) {
		final IHeader key = mAdapter.getHeaderStickyOn(position);

		if (key != null && mHeaderCache.containsKey(key)) {
//			Log.v("getHeader", "Returning existing Header " + key);
			return mHeaderCache.get(key);
		} else if (key == null) {
//			Log.v("getHeader", "No Header");
			return null;
		} else {
			int headerPosition = mAdapter.getGlobalPositionOf(key);
			if (headerPosition < 0) return null;

			final RecyclerView.ViewHolder holder = mAdapter.onCreateViewHolder(recyclerView, mAdapter.getItemViewType(headerPosition));
			final View header = holder.itemView;
			mAdapter.onBindViewHolder(holder, headerPosition);

			int widthSpec = View.MeasureSpec.makeMeasureSpec(recyclerView.getWidth(), View.MeasureSpec.EXACTLY);
			int heightSpec = View.MeasureSpec.makeMeasureSpec(recyclerView.getHeight(), View.MeasureSpec.UNSPECIFIED);

			int childWidth = ViewGroup.getChildMeasureSpec(widthSpec,
					recyclerView.getPaddingLeft() + recyclerView.getPaddingRight(), header.getLayoutParams().width);
			int childHeight = ViewGroup.getChildMeasureSpec(heightSpec,
					recyclerView.getPaddingTop() + recyclerView.getPaddingBottom(), header.getLayoutParams().height);

			header.measure(childWidth, childHeight);
			header.layout(0, 0, header.getMeasuredWidth(), header.getMeasuredHeight());

			//TODO: Intercept taps on sticky views
//			recyclerView.addOnItemTouchListener(new RecyclerView.SimpleOnItemTouchListener() {
//				@Override
//				public boolean onInterceptTouchEvent(RecyclerView rv, MotionEvent e) {
//					// only use the "UP" motion event, discard all others
//					if (e.getAction() == MotionEvent.ACTION_UP) {
//						IHeader header = findHeaderViewUnder(e.getX(), e.getY());
//						if (header != null) {
//							Log.d("getHeader", "headerView has been touched!!!! " + header);
//							return true;
//						}
//					}
//					return false;
//				}
//			});

			if (mHeaderCache.keySet().size() == maxCachedHeaders)
				clearHeadersCache();
			mHeaderCache.put(key, header);
//			Log.v("getHeader", "Returning new Header " + key + " headerCache>>>" + mHeaderCache.keySet());
			return header;
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void onDrawOver(Canvas canvas, RecyclerView recyclerView, RecyclerView.State state) {
		//Not needed, handled by FlexibleAdapter (this decorator is removed if no sticky headers!):
		// if (!mAdapter.areHeadersShown() && !mAdapter.areHeadersSticky()) return;

		//Get or create the headerView for top position (index = 0)
		if (recyclerView.getChildCount() < 0) return;
		View child = recyclerView.getChildAt(0);
		int adapterPos = recyclerView.getChildAdapterPosition(child);
		View headerView = getHeader(recyclerView, adapterPos);
		//A header is found?
		if (headerView != null) {
			int top = getHeaderTop(recyclerView, child, adapterPos);
//			Log.v("onDrawOver", "adapterPos=" + adapterPos + " top=" + top);
			//Draw header!
			int left = child.getLeft();
			canvas.save();
			canvas.translate(left, top);
			headerView.draw(canvas);
			canvas.restore();
		}
	}

	/**
	 * Gets the new Header top and tries to compute the offscreen push if needed
	 *
	 * @param recyclerView the RecyclerView
	 * @param child        the current child item view
	 * @param adapterPos   the current Adapter position
	 * @return the new top (usually 0) of the header view, or the offset if next header pushes
	 * the previous offscreen
	 */
	private int getHeaderTop(RecyclerView recyclerView, View child, int adapterPos) {
		int top = Math.max(0, (int) ViewCompat.getY(child));

		//Check item availability
		IHeader current = mAdapter.getHeaderStickyOn(adapterPos);
		if (current == null || recyclerView.getChildCount() < 1)
			return top;

		//Get next(+1) view with header and compute the offscreen push if needed
		View nextItemView = recyclerView.getChildAt(1);
		int adapterPosHere = recyclerView.getChildAdapterPosition(nextItemView);
		IHeader next = mAdapter.getHeaderStickyOn(adapterPosHere);

		if (next != null && !next.equals(current)) {
			View nextHeaderView = getHeader(recyclerView, adapterPosHere);
			if (nextHeaderView == null) return top;

			int offset = (int) ViewCompat.getY(nextHeaderView) - nextHeaderView.getHeight();
			if (offset < 0) {
				return offset;//The new translated top, when pushing up
			}
		}
		return top;
	}

	/**
	 * Clears the header view cache. Headers will be recreated and
	 * rebound on list scroll after this method has been called.
	 */
	public void clearHeadersCache() {
		mHeaderCache.clear();
	}

	public IHeader findHeaderViewUnder(float x, float y) {
		for (Map.Entry<IHeader, View> entry : mHeaderCache.entrySet()) {
			View child = entry.getValue();
			final float translationX = ViewCompat.getTranslationX(child);
			final float translationY = ViewCompat.getTranslationY(child);

			if (x >= child.getLeft() + translationX &&
					x <= child.getRight() + translationX &&
					y >= child.getTop() + translationY &&
					y <= child.getBottom() + translationY) {
				return entry.getKey();
			}
		}
		return null;
	}

}