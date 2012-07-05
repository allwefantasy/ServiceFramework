package net.csdn.common.settings;

import net.csdn.common.collect.Tuple;
import net.csdn.env.Environment;

import static net.csdn.common.Strings.cleanPath;
import static net.csdn.common.settings.ImmutableSettings.settingsBuilder;

/**
 * BlogInfo: william
 * Date: 11-9-2
 * Time: 上午10:00
 */
public class InternalSettingsPreparer {

    public static Tuple<Settings, Environment> prepareSettings(Settings pSettings) {
        ImmutableSettings.Builder settingsBuilder = settingsBuilder().put(pSettings);

        if (settingsBuilder.get("cluster.name") == null) {
            settingsBuilder.put("cluster.name", "csdn_search");
        }

        Environment environment = new Environment(settingsBuilder.build());
        settingsBuilder.loadFromUrl(environment.resolveConfig("application.yml"));

        Settings v1 = settingsBuilder.build();

        environment = new Environment(v1);

        // put back the env settings
        settingsBuilder = settingsBuilder().put(v1);
        settingsBuilder.put("path.home", cleanPath(environment.homeFile().getAbsolutePath()));
        settingsBuilder.put("path.work", cleanPath(environment.workFile().getAbsolutePath()));
        settingsBuilder.put("path.work_with_cluster", cleanPath(environment.workWithClusterFile().getAbsolutePath()));
        settingsBuilder.put("path.data", cleanPath(environment.dataFile().getAbsolutePath()));
        settingsBuilder.put("path.data_with_cluster", cleanPath(environment.dataWithClusterFile().getAbsolutePath()));
        settingsBuilder.put("path.logs", cleanPath(environment.logsFile().getAbsolutePath()));


        return new Tuple<Settings, Environment>(settingsBuilder.build(), environment);

    }

}
