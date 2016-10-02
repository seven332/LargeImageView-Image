package com.hippo.largeimageview.image.example;

import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import com.hippo.largeimageview.LargeImageView;
import com.hippo.largeimageview.image.AutoSource2;
import com.hippo.streampipe.InputStreamPipe;

import java.io.IOException;
import java.io.InputStream;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        final LargeImageView view = (LargeImageView) findViewById(R.id.large_image_view);
        view.setImage(new AutoSource2(new ResourceInputStreamPipe(R.raw.gif)));
    }


    private class ResourceInputStreamPipe implements InputStreamPipe {

        private final int mId;
        private InputStream mStream;

        public ResourceInputStreamPipe(int id) {
            mId = id;
        }

        @Override
        public void obtain() {}

        @Override
        public void release() {}

        @NonNull
        @Override
        public InputStream open() throws IOException {
            if (mStream != null) {
                throw new IOException("Can't open twice");
            }
            return getResources().openRawResource(mId);
        }

        @Override
        public void close() {
            if (mStream != null) {
                try {
                    mStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                mStream = null;
            }
        }
    }
}
