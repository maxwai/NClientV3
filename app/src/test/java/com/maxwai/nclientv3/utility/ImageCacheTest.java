package com.maxwai.nclientv3.utility;

import android.app.Application;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Looper;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.FutureTarget;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.MockResponse;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;

/** Real HTTP bytes, Glide decoding and disk persistence; no live site dependency. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 31, application = Application.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ImageCacheTest {
    private <T> T await(FutureTarget<T> target) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!target.isDone() && System.nanoTime() < deadline) {
            Shadows.shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(10);
        }
        assertTrue("Image request should finish", target.isDone());
        return target.get(1, TimeUnit.SECONDS);
    }
    @Test public void originalsPersistAcrossMemoryClearAndSupportOfflineResizingUntilCacheIsCleared() throws Exception {
        Context context = RuntimeEnvironment.getApplication(); ReadingFixtures.initialize(context);
        Bitmap source = Bitmap.createBitmap(160, 240, Bitmap.Config.ARGB_8888); source.eraseColor(0xff557766);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(); source.compress(Bitmap.CompressFormat.PNG, 100, bytes); source.recycle();
        MockWebServer server = new MockWebServer();
        server.enqueue(new MockResponse().setHeader("Content-Type", "image/png").setBody(new okio.Buffer().write(bytes.toByteArray())));
        server.start(); Uri uri = Uri.parse(server.url("/page.png").toString());
        try {
            FutureTarget<Bitmap> first = Glide.with(context).asBitmap().load(uri).apply(ImageCache.options(uri)).submit(80, 120);
            assertNotNull(await(first)); assertEquals(1, server.getRequestCount());
            Glide.with(context).clear(first); Glide.get(context).clearMemory(); server.shutdown();
            assertTrue(ImageCache.directory(context).isDirectory());
            FutureTarget<Bitmap> offline = Glide.with(context).asBitmap().load(uri).apply(ImageCache.options(uri))
                .onlyRetrieveFromCache(true).submit(120, 180);
            assertNotNull(await(offline)); assertEquals(1, server.getRequestCount()); Glide.with(context).clear(offline);
            java.io.File saved = new java.io.File(context.getFilesDir(), "download.png");
            java.nio.file.Files.write(saved.toPath(), bytes.toByteArray());
            java.util.concurrent.atomic.AtomicBoolean cleared = new java.util.concurrent.atomic.AtomicBoolean();
            ImageCache.clear(context, () -> cleared.set(true));
            com.maxwai.nclientv3.settings.ReadingHistoryStore.IO.submit(() -> {}).get(5, TimeUnit.SECONDS);
            Shadows.shadowOf(Looper.getMainLooper()).idle(); assertTrue(cleared.get()); assertTrue(saved.isFile());
            FutureTarget<Bitmap> missing = Glide.with(context).asBitmap().load(uri).apply(ImageCache.options(uri))
                .onlyRetrieveFromCache(true).submit(80, 120);
            try { await(missing); fail("A cleared image should no longer be available offline"); }
            catch (ExecutionException expected) { }
            Glide.with(context).clear(missing); assertEquals(1, server.getRequestCount());
        } finally { server.shutdown(); Glide.tearDown(); }
    }
}
