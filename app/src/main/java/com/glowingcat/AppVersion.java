/*
 * (c) 2026 Glowing Cat Software
 */
package com.glowingcat;

import java.io.InputStream;
import java.util.Properties;

/**
 * Provides the application version, read from version.properties
 * which is populated by Maven resource filtering at build time.
 */
public class AppVersion {

    private static final String VERSION;

    static {
        String v = "DEVELOPMENT"; // fallback
        try (InputStream is = AppVersion.class.getResourceAsStream("/version.properties")) {
            if (is != null) {
                Properties props = new Properties();
                props.load(is);
                String val = props.getProperty("version");
                if (val != null && !val.isBlank() && !val.contains("${")) {
                    v = val.trim();
                }
            }
        } catch (Exception e) {
            // Use fallback
        }
        VERSION = v;
    }

    /** Returns the application version string (e.g. "1.2.3"). */
    public static String get() {
        return VERSION;
    }
}
