package ru.ivi.opensource.flinkclickhousesink.model;

import com.google.common.base.Preconditions;
import org.apache.commons.lang3.StringUtils;
import ru.ivi.opensource.flinkclickhousesink.util.ConfigUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import static ru.ivi.opensource.flinkclickhousesink.util.ConfigUtil.buildListFromString;

public class ClickHouseClusterSettings {

    public static final String CLICKHOUSE_HOSTS = "clickhouse.access.hosts";
    public static final String CLICKHOUSE_USERS = "clickhouse.access.users";
    public static final String CLICKHOUSE_PASSWORDS = "clickhouse.access.passwords";
    public static final String CLICKHOUSE_DBS = "clickhouse.access.dbs";

    private final List<String> hostsWithPorts;
    private final List<String> users;
    private final List<String> passwords;
    private final List<String> databases;
    private final List<String> credentials;

    public ClickHouseClusterSettings(Map<String, String> parameters) {
        Preconditions.checkNotNull(parameters);

        String hostsString = parameters.get(CLICKHOUSE_HOSTS);
        Preconditions.checkNotNull(hostsString);

        hostsWithPorts = buildListFromString(hostsString);
        Preconditions.checkArgument(hostsWithPorts.size() > 0);

        users = buildListFromString(parameters.get(CLICKHOUSE_USERS));
        Preconditions.checkArgument(hostsWithPorts.size() == users.size());

        passwords = buildListFromString(parameters.get(CLICKHOUSE_PASSWORDS));
        Preconditions.checkArgument(hostsWithPorts.size() == passwords.size());

        databases = buildListFromString(parameters.get(CLICKHOUSE_DBS));
        Preconditions.checkArgument(hostsWithPorts.size() == databases.size());

        credentials = new ArrayList<>();
        int i = 0;
        for (String user: users) {
            credentials.add(buildCredentials(user, passwords.get(i)));
            i++;
        }
    }


    private static String buildCredentials(String user, String password) {
        Base64.Encoder x = Base64.getEncoder();
        String credentials = String.join(":", user, password);
        return new String(x.encode(credentials.getBytes()));
    }

    public String getHostUrl(int i) {
        return hostsWithPorts.get(i);
    }

    public List<String> getHostsWithPorts() {
        return hostsWithPorts;
    }

    public String getUser(int i) {
        return users.get(i);
    }

    public String getPassword(int i) {
        return passwords.get(i);
    }

    public String getDatabase(int i) {
        return databases.get(i);
    }

    public String getCredentials(int i) {
        return credentials.get(i);
    }


    @Override
    public String toString() {
        return "ClickHouseClusterSettings{" +
                "hostsWithPorts=" + hostsWithPorts +
                ", databases=" + databases +
                ", credentials=" + credentials  +
                '}';
    }
}
