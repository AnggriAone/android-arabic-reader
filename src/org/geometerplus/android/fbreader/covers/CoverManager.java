/*
 * Copyright (C) 2010-2012 Geometer Plus <contact@geometerplus.com>
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
 * 02110-1301, USA.
 */

package org.geometerplus.android.fbreader.covers;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import android.app.Activity;
import android.graphics.Bitmap;
import android.widget.ImageView;

import org.geometerplus.zlibrary.core.image.ZLImage;
import org.geometerplus.zlibrary.core.image.ZLLoadableImage;

import org.geometerplus.zlibrary.ui.android.image.ZLAndroidImageManager;
import org.geometerplus.zlibrary.ui.android.image.ZLAndroidImageData;

import org.geometerplus.fbreader.tree.FBTree;

public class CoverManager {
	static final Object NULL_BITMAP = new Object();

	@SuppressWarnings("serial")
	final Map<FBTree.Key,Object> CachedBitmaps =
		Collections.synchronizedMap(new LinkedHashMap<FBTree.Key,Object>(10, 0.75f, true) {
			@Override
			protected boolean removeEldestEntry(Map.Entry<FBTree.Key,Object> eldest) {
				return size() > 3 * HoldersCounter;
			}
		});

	// Copied from ZLAndroidImageLoader
	private static class MinPriorityThreadFactory implements ThreadFactory {
		private final ThreadFactory myDefaultThreadFactory = Executors.defaultThreadFactory();

		public Thread newThread(Runnable r) {
			final Thread th = myDefaultThreadFactory.newThread(r);
			th.setPriority(Thread.MIN_PRIORITY);
			return th;
		}
	}
	private static final int IMAGE_RESIZE_THREADS_NUMBER = 1; // TODO: how many threads ???
	private final ExecutorService myPool = Executors.newFixedThreadPool(IMAGE_RESIZE_THREADS_NUMBER, new MinPriorityThreadFactory());

	private final Activity myActivity;
	private final int myCoverWidth;
	private final int myCoverHeight;

	volatile int HoldersCounter = 0;

	public CoverManager(Activity activity, int coverWidth, int coverHeight) {
		myActivity = activity;
		myCoverWidth = coverWidth;
		myCoverHeight = coverHeight;
	}

	void runOnUiThread(Runnable runnable) {
		myActivity.runOnUiThread(runnable);
	}

	void setupCoverView(ImageView coverView) {
		coverView.getLayoutParams().width = myCoverWidth;
		coverView.getLayoutParams().height = myCoverHeight;
		coverView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
		coverView.requestLayout();
	}

	Bitmap getBitmap(ZLImage image) {
		final ZLAndroidImageManager mgr = (ZLAndroidImageManager)ZLAndroidImageManager.Instance();
		final ZLAndroidImageData data = mgr.getImageData(image);
		if (data == null) {
			return null;
		}
		return data.getBitmap(2 * myCoverWidth, 2 * myCoverHeight);
	}

	void setCoverForView(CoverHolder holder, ZLLoadableImage image) {
		synchronized (holder) {
			final Object coverBitmap = CachedBitmaps.get(holder.Key);
			if (coverBitmap == NULL_BITMAP) {
				return;
			} else if (coverBitmap != null) {
				holder.CoverView.setImageBitmap((Bitmap)coverBitmap);
			} else if (holder.coverBitmapTask == null) {
				holder.coverBitmapTask = myPool.submit(holder.new CoverBitmapRunnable(image));
			}
		}
	}

	public boolean trySetCoverImage(CoverHolder holder, FBTree tree) {
		Object coverBitmap = CachedBitmaps.get(holder.Key);
		if (coverBitmap == NULL_BITMAP) {
			return false;
		}

		if (coverBitmap == null) {
			final ZLImage cover = tree.getCover();
			if (cover instanceof ZLLoadableImage) {
				final ZLLoadableImage img = (ZLLoadableImage)cover;
				if (img.isSynchronized()) {
					setCoverForView(holder, img);
				} else {
					img.startSynchronization(holder.new CoverSyncRunnable(img));
				}
			} else if (cover != null) {
				coverBitmap = getBitmap(cover);
			}
		}
		if (coverBitmap != null) {
			holder.CoverView.setImageBitmap((Bitmap)coverBitmap);
			return true;
		}
		return false;
	}
}