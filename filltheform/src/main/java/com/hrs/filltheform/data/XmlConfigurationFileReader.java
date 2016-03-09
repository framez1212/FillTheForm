/*
 * Copyright (C) 2015 HRS GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hrs.filltheform.data;

import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.util.Xml;

import com.hrs.filltheform.common.ConfigurationItem;
import com.hrs.filltheform.common.reader.ConfigurationReader;
import com.hrs.filltheform.service.ServiceConfiguration;
import com.hrs.filltheform.util.LogUtil;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

/**
 * XmlConfigurationFileReader is responsible for parsing the data from the xml configuration file and setting up the ServiceConfiguration.
 * One configuration item is defined with <id>value</id>. It is recommended to group more configuration items inside the appropriate <profile name="any_name"></profile> tag.
 */
public class XmlConfigurationFileReader implements ConfigurationReader {

    private static final String TAG = XmlConfigurationFileReader.class.getSimpleName();
    private static final String CONFIGURATION_VARIABLE_PATTERN = "&(\\w+);";

    private final ServiceConfiguration configuration;
    private final Context appContext;

    public XmlConfigurationFileReader(Context context, ServiceConfiguration configuration) {
        this.appContext = context;
        this.configuration = configuration;
    }

    public void readConfigurationFile(@ConfigurationSource int source, @NonNull String configurationFilePath) {
        try {
            XmlPullParserFactory pullParserFactory = XmlPullParserFactory.newInstance();
            XmlPullParser parser = pullParserFactory.newPullParser();

            InputStream inputStream = getInputStream(source, configurationFilePath);

            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
            parser.setFeature(Xml.FEATURE_RELAXED, true);
            parser.setInput(inputStream, null);

            parseConfigurationFile(parser);

        } catch (XmlPullParserException | IOException | IllegalArgumentException e) {
            configuration.onReadingFailed(e.toString());
            LogUtil.e(TAG, e.toString());
        }
    }

    private InputStream getInputStream(@ConfigurationSource int source, @NonNull String configurationFilePath) throws IOException, IllegalArgumentException {
        if (TextUtils.isEmpty(configurationFilePath)) {
            throw new IllegalArgumentException("Configuration file path is empty");
        }
        if (source == SOURCE_ASSETS) {
            return appContext.getAssets().open(configurationFilePath);
        } else {
            Uri uri;
            if (source == SOURCE_EXTERNAL_STORAGE) {
                File sdcard = Environment.getExternalStorageDirectory();
                File file = new File(sdcard, configurationFilePath);
                uri = Uri.fromFile(file);
            } else {
                uri = Uri.parse(configurationFilePath);
            }
            return appContext.getContentResolver().openInputStream(uri);
        }
    }

    private void parseConfigurationFile(XmlPullParser parser) throws XmlPullParserException, IOException {
        int eventType = parser.getEventType();
        String profile = null;
        String name;

        while (eventType != XmlPullParser.END_DOCUMENT) {
            switch (eventType) {
                case XmlPullParser.START_DOCUMENT:
                    break;
                case XmlPullParser.START_TAG:
                    name = parser.getName();
                    if (name.equalsIgnoreCase("package")) {
                        configuration.addPackage(parser.nextText());
                    } else if (name.equalsIgnoreCase("profile")) {
                        profile = parser.getAttributeValue(null, "name");
                    } else if (profile != null || !isParentName(name)) {
                        String label = parser.getAttributeValue(null, "label");
                        ConfigurationItem configurationItem = new ConfigurationItem(name, profile, parser.nextText());
                        configurationItem.setLabel(label);
                        configuration.addConfigurationItem(configurationItem);
                    }
                    break;
                case XmlPullParser.END_TAG:
                    name = parser.getName();
                    if (name.equalsIgnoreCase("profile") && profile != null) {
                        profile = null;
                    }
                    break;
                default:
                    break;
            }
            eventType = parser.next();
        }

        configuration.onReadingCompleted();
    }

    private boolean isParentName(String name) {
        switch (name.toUpperCase(Locale.ENGLISH)) {
            case "FILLTHEFORMCONFIG":
            case "PACKAGES":
            case "PROFILES":
                return true;
            default:
                return false;
        }
    }

    @Override
    public String getConfigurationVariablePattern() {
        return CONFIGURATION_VARIABLE_PATTERN;
    }
}
