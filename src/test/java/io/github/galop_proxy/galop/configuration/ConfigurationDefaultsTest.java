package io.github.galop_proxy.galop.configuration;

import org.junit.Test;

import java.lang.reflect.Constructor;

/**
 * Tests the class {@link ConfigurationDefaults}.
 */
public class ConfigurationDefaultsTest {

    @Test(expected = Exception.class)
    public void constructor_throwsException() throws Exception {
        final Constructor<ConfigurationDefaults> constructor = ConfigurationDefaults.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        constructor.newInstance();
    }

}
