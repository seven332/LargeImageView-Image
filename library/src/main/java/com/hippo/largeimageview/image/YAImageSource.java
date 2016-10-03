package com.hippo.largeimageview.image;

/*
 * Created by Hippo on 10/1/2016.
 */

import android.graphics.Bitmap;
import android.graphics.drawable.Animatable;
import android.os.AsyncTask;
import android.os.Process;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.hippo.image.ImageData;
import com.hippo.image.ImageRenderer;
import com.hippo.largeimageview.BitmapSource;
import com.hippo.yorozuya.thread.InfiniteThreadExecutor;
import com.hippo.yorozuya.thread.PriorityThreadFactory;

import java.util.Deque;
import java.util.LinkedList;
import java.util.concurrent.Executor;

public class YAImageSource extends BitmapSource implements Animatable, Runnable {

    private static final String LOG_TAG = YAImageSource.class.getSimpleName();

    private static final Executor sExecutor = new InfiniteThreadExecutor(
            0, // Don't keep core thread
            3000, // 3000ms
            new LinkedList<Runnable>(),
            new PriorityThreadFactory(LOG_TAG, Process.THREAD_PRIORITY_BACKGROUND)
    );

    @Nullable
    private ImageRenderer mRenderer;
    @Nullable
    private Bitmap mBitmap;
    private final boolean mAnimated;
    @Nullable
    private Task mTask;

    /** Whether the drawable has an animation callback posted. */
    private boolean mRunning;

    /** Whether the drawable should animate when visible. */
    private boolean mAnimating = true;

    private boolean mFirstSetVisible = true;

    @Nullable
    public static YAImageSource newInstance(@NonNull ImageData data) {
        final Bitmap bitmap;
        try {
            bitmap = Bitmap.createBitmap(data.getWidth(), data.getHeight(),
                    data.isOpaque() ? Bitmap.Config.RGB_565 : Bitmap.Config.ARGB_8888);
        } catch (OutOfMemoryError e) {
            // Out of memory
            if (!data.isReferenced()) {
                data.recycle();
            }
            return null;
        }
        final ImageRenderer renderer = data.createImageRenderer();
        // Render first frame to bitmap
        renderer.render(bitmap, 0, 0, 0, 0, data.getWidth(), data.getHeight(), 1, false, 0);
        return new YAImageSource(renderer, bitmap);
    }

    private YAImageSource(ImageRenderer renderer, Bitmap bitmap) {
        // Let YAImageSource to recycle bitmap
        super(bitmap, false);
        mRenderer = renderer;
        mBitmap = bitmap;
        mAnimated = renderer.getImageData().getFrameCount() > 1;
        if (mAnimated) {
            mTask = new Task();
            mTask.executeOnExecutor(sExecutor);
        } else {
            mTask = null;
        }
    }

    @Override
    public void recycle() {
        super.recycle();
        if (mTask != null) {
            mTask.addTask(Task.RECYCLE);
        } else {
            recycleInternal();
        }
    }

    @Override
    public boolean setVisible(boolean visible) {
        final boolean changed = super.setVisible(visible);
        if (mAnimated) {
            if (visible) {
                if (changed || mFirstSetVisible) {
                    mFirstSetVisible = false;
                    final boolean next = mRunning;
                    setFrame(next, mAnimating);
                }
            } else {
                unscheduleSelf(this);
            }
        }
        return changed;
    }

    @Override
    public void start() {
        mAnimating = true;

        if (mAnimated && !isRunning()) {
            // Start from 0th frame.
            setFrame(false, true);
        }
    }

    @Override
    public void stop() {
        mAnimating = false;

        if (mAnimated && isRunning()) {
            unscheduleSelf(this);
        }
    }

    @Override
    public boolean isRunning() {
        return mRunning;
    }

    @Override
    public void run() {
        setFrame(true, true);
    }

    // resetOrNext, false for reset, true for next
    private void setFrame(boolean resetOrNext, boolean animate) {
        // Check recycled
        if (mTask == null || mTask.isRecycled()) {
            return;
        }

        mAnimating = animate;
        unscheduleSelf(this);
        if (animate) {
            mRunning = true;
        }

        // Add task
        final int task = resetOrNext ? (animate ? Task.ADVANCE_ANIMATE : Task.ADVANCE) :
                (animate ? Task.RESET_ANIMATE : Task.RESET);
        mTask.addTask(task);
    }

    @Override
    public void unscheduleSelf(@NonNull Runnable what) {
        mRunning = false;
        super.unscheduleSelf(what);
    }

    private void reset() {
        if (mBitmap != null && mRenderer != null) {
            mRenderer.reset();
            // super.recycle() might be called, so get width and height from mBitmap
            mRenderer.render(mBitmap, 0, 0, 0, 0, mBitmap.getWidth(), mBitmap.getHeight(), 1, false, 0);
        }
    }

    private void advance() {
        if (mBitmap != null && mRenderer != null) {
            mRenderer.advance();
            // super.recycle() might be called, so get width and height from mBitmap
            mRenderer.render(mBitmap, 0, 0, 0, 0, mBitmap.getWidth(), mBitmap.getHeight(), 1, false, 0);
        }
    }

    private void recycleInternal() {
        if (mBitmap != null) {
            mBitmap.recycle();
            mBitmap = null;
        }
        if (mRenderer != null) {
            mRenderer.recycle();
            // Recycle ImageData if necessary
            final ImageData data = mRenderer.getImageData();
            if (!data.isReferenced()) {
                data.recycle();
            }
            mRenderer = null;
        }
    }

    // Task keeps till recycled
    private class Task extends AsyncTask<Void, Long, Void> {

        private static final int RESET = 0;
        private static final int RESET_ANIMATE = 1;
        private static final int ADVANCE = 2;
        private static final int ADVANCE_ANIMATE = 3;
        private static final int RECYCLE = 4;

        private boolean mRecycled;
        private final Deque<Integer> mTaskStack = new LinkedList<>();
        private final Deque<Long> mTimeStack = new LinkedList<>();
        private final Object mLock = new Object();

        public void addTask(int task) {
            if (mRecycled) {
                // Skip if recycled
                return;
            }

            synchronized (mLock) {
                if (task == RECYCLE) {
                    mTaskStack.clear();
                    mTimeStack.clear();
                    mRecycled = true;
                }
                mTaskStack.addLast(task);
                mTimeStack.addFirst(SystemClock.uptimeMillis());
                mLock.notify();
            }
        }

        public boolean isRecycled() {
            return mRecycled;
        }

        @Override
        protected Void doInBackground(Void... params) {
            for (;;) {
                final Integer task;
                final Long time;
                synchronized (mLock) {
                    task = mTaskStack.pollFirst();
                    time = mTimeStack.pollFirst();
                    if (task == null) {
                        try {
                            mLock.wait();
                        } catch (InterruptedException e) {
                            // Ignore
                        }
                        continue;
                    }
                }

                switch (task) {
                    case RESET:
                        reset();
                        publishProgress((Long) null);
                        break;
                    case RESET_ANIMATE:
                        reset();
                        publishProgress(time);
                        break;
                    case ADVANCE:
                        advance();
                        publishProgress((Long) null);
                        break;
                    case ADVANCE_ANIMATE:
                        advance();
                        publishProgress(time);
                        break;
                    case RECYCLE:
                        // mIBRenderer will be recycled in onPostExecute(),
                        // just return now.
                        return null;
                    default:
                        throw new IllegalStateException("Invalid task: " + task);
                }
            }
        }

        @Override
        protected void onProgressUpdate(Long... values) {
            invalidateSelf();
            final Long time = values[0];
            if (time != null && mRenderer != null) {
                scheduleSelf(YAImageSource.this, time + mRenderer.getCurrentDelay());
            }
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            recycleInternal();
        }
    }
}
