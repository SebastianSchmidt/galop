package io.github.sebastianschmidt.galop;

import com.google.inject.Guice;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

class EntryPoint {

    private static final Logger LOGGER = LogManager.getLogger();

    public static void main(final String[] args) {
        printWelcomeScreen();
        Guice.createInjector(new GalopModule()).getInstance(Starter.class).start(args);
    }

    private static void printWelcomeScreen() {
        LOGGER.info("Loading...\n\n" +
                "  _____________________ _______________   \n" +
                "  __  ____/__    |__  / __  __ \\__  __ \\  \n" +
                "  _  / __ __  /| |_  /  _  / / /_  /_/ /  \n" +
                "  / /_/ / _  ___ |  /___/ /_/ /_  ____/   \n" +
                "  \\____/  /_/  |_/_____/\\____/ /_/        \n\n" +
                getVersionLine() + "\n" +
                getDeveloperLine() + "\n\n");
    }

    private static String getVersionLine() {
        return center("Version: " + EntryPoint.class.getPackage().getImplementationVersion());
    }

    private static String getDeveloperLine() {
        return center("Developer: " + EntryPoint.class.getPackage().getImplementationVendor());
    }

    private static String center(final String s) {

        final int width = 42;

        String result = s;

        while (result.length() < width) {
            result = " " + result + " ";
        }

        return result;

    }

    private EntryPoint() {
        throw new AssertionError("No instances");
    }

}