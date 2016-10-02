package com.hippo.largeimageview.image;

/*
 * Created by Hippo on 10/1/2016.
 */

import android.graphics.Bitmap;
import android.os.Build;
import android.support.annotation.NonNull;

import com.hippo.image.BitmapDecoder;
import com.hippo.image.BitmapRegionDecoder;
import com.hippo.image.Image;
import com.hippo.image.ImageData;
import com.hippo.image.ImageInfo;
import com.hippo.largeimageview.AutoSource;
import com.hippo.largeimageview.BitmapSource;
import com.hippo.largeimageview.ImageSource;
import com.hippo.largeimageview.SkiaRegionDecoder;
import com.hippo.largeimageview.TiledBitmapSource;
import com.hippo.streampipe.InputStreamPipe;

import java.io.IOException;

public class AutoSource2 extends AutoSource {

    @BitmapDecoder.Config
    private final int mConfig;

    public AutoSource2(@NonNull InputStreamPipe pipe) {
        this(pipe, BitmapDecoder.CONFIG_AUTO);
    }

    public AutoSource2(@NonNull InputStreamPipe pipe, @BitmapDecoder.Config int config) {
        super(pipe);
        mConfig = config;
    }

    private Bitmap.Config configToConfig(@BitmapDecoder.Config int config, boolean opaque) {
        switch (config) {
            default:
            case BitmapDecoder.CONFIG_AUTO:
                return opaque ? Bitmap.Config.RGB_565 : Bitmap.Config.ARGB_8888;
            case BitmapDecoder.CONFIG_RGBA_8888:
                return Bitmap.Config.ARGB_8888;
            case BitmapDecoder.CONFIG_RGB_565:
                return Bitmap.Config.RGB_565;
        }
    }

    @Override
    protected ImageSource decode() {
        final InputStreamPipe pipe = getInputStreamPipe();
        if (pipe == null) {
            return null;
        }

        try {
            pipe.obtain();

            // Decode image info
            final ImageInfo info = new ImageInfo();
            if (!BitmapDecoder.decode(pipe.open(), info)) {
                // It is not image
                return null;
            }
            pipe.close();

            final int maxBitmapSize = getMaxBitmapSize();
            final int bitmapLimit = getBitmapLimit();

            if (info.frameCount != 1 && info.width <= maxBitmapSize && info.height <= maxBitmapSize) {
                // YAImageSource
                final ImageData data = Image.decode(pipe.open());
                if (data != null) {
                    data.setBrowserCompat(true);
                    return YAImageSource.newInstance(data);
                }
            } else if (info.width <= bitmapLimit && info.height <= bitmapLimit) {
                // BitmapSource
                final Bitmap bitmap = BitmapDecoder.decode(pipe.open(), mConfig);
                if (bitmap != null) {
                    return new BitmapSource(bitmap);
                }
            } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) {
                // TiledBitmapSource with ImageRegionDecoder
                final BitmapRegionDecoder decoder = BitmapRegionDecoder.newInstance(pipe.open());
                if (decoder != null) {
                    return new TiledBitmapSource(new ImageRegionDecoder(decoder, mConfig));
                }
            } else {
                // TiledBitmapSource with SkiaRegionDecoder
                final android.graphics.BitmapRegionDecoder decoder =
                        android.graphics.BitmapRegionDecoder.newInstance(pipe.open(), false);
                if (decoder != null) {
                    return new TiledBitmapSource(new SkiaRegionDecoder(decoder, configToConfig(mConfig, info.opaque)));
                }
            }
        } catch (IOException e) {
            return null;
        } catch (OutOfMemoryError e) {
            return null;
        } finally {
            pipe.close();
            pipe.release();
            clearInputStreamPipe();
        }

        return null;
    }
}
