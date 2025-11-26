package com.maxwai.nclientv3.components;

import androidx.appcompat.app.AppCompatActivity;

import com.maxwai.nclientv3.settings.Global;

public abstract class ThreadAsyncTask<Param, Progress, Result> {

    private final AppCompatActivity activity;
    /** @noinspection FieldCanBeLocal*/
    private Thread thread;
    public ThreadAsyncTask(AppCompatActivity activity) {
        this.activity = activity;
    }

    public final void execute(Param params) {
        thread = new AsyncThread(params);
        thread.start();
    }

    protected void onPreExecute() {
    }

    protected void onPostExecute(Result result) {
    }

    protected void onProgressUpdate(Progress value) {
    }

    protected abstract Result doInBackground(Param param);

    protected final void publishProgress(Progress value) {
        if (!Global.isDestroyed(activity))
            activity.runOnUiThread(() -> onProgressUpdate(value));
    }

    class AsyncThread extends Thread {

        final Param param;

        AsyncThread(Param param) {
            this.param = param;
        }

        @Override
        public void run() {
            if (!Global.isDestroyed(activity))
                activity.runOnUiThread(ThreadAsyncTask.this::onPreExecute);
            Result result = doInBackground(param);
            if (!Global.isDestroyed(activity))
                activity.runOnUiThread(() -> onPostExecute(result));
        }
    }

}
