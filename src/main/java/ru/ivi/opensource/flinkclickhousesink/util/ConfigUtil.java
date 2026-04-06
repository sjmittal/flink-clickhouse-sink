package ru.ivi.opensource.flinkclickhousesink.util;

import java.util.Arrays;
import java.util.List;

public final class ConfigUtil {

    public static final String DELIMITER = ",";

    private ConfigUtil() {

    }

    public static List<String> buildListFromString(String string) {
        return Arrays.asList(string.split(DELIMITER));
    }
}
