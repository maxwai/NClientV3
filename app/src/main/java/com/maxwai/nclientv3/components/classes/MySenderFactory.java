package com.maxwai.nclientv3.components.classes;

import android.content.Context;

import androidx.annotation.NonNull;

import com.google.auto.service.AutoService;

import org.acra.config.CoreConfiguration;
import org.acra.sender.ReportSender;
import org.acra.sender.ReportSenderFactory;

/**
 * @noinspection unused
 */
//TODO: test if this works
@AutoService(ReportSenderFactory.class)
public class MySenderFactory implements ReportSenderFactory {
    @NonNull
    @Override
    public ReportSender create(@NonNull Context context, @NonNull CoreConfiguration config) {
        return new MySender();
    }

    @Override
    public boolean enabled(@NonNull CoreConfiguration config) {
        return true;
    }
}
