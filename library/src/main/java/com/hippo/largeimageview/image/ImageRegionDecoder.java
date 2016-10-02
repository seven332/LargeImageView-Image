package com.hippo.largeimageview.image;

/*
 * Created by Hippo on 10/1/2016.
 */

import android.graphics.Bitmap;
import android.graphics.Rect;

import com.hippo.image.BitmapDecoder;
import com.hippo.image.BitmapRegionDecoder;
import com.hippo.largeimageview.RegionDecoder;

public class ImageRegionDecoder extends RegionDecoder {

    private BitmapRegionDecoder mDecoder;
    @BitmapDecoder.Config
    private final int mConfig;

    public ImageRegionDecoder(BitmapRegionDecoder decoder, @BitmapDecoder.Config int config) {
        mDecoder = decoder;
        mConfig = config;
    }

    @Override
    public int getWidth() {
        return mDecoder.getWidth();
    }

    @Override
    public int getHeight() {
        return mDecoder.getHeight();
    }

    @Override
    protected Bitmap decodeRegionInternal(Rect rect, int sample) {
        final BitmapRegionDecoder decoder = mDecoder;
        if (decoder != null) {
            return decoder.decodeRegion(rect, mConfig, sample);
        } else {
            return null;
        }
    }

    @Override
    public void recycle() {
        if (mDecoder != null) {
            mDecoder.recycle();
            mDecoder = null;
        }
    }
}
