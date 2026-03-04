package net.jaraonthe.java.lsp_display_examples;

/**
 * General application settings.
 *
 * @author Jakob Rathbauer <jakob@jaraonthe.net>
 */
public class Settings
{
    // Based on (my own work) https://github.com/jaraonthe-dot-net/ASB/blob/main/src/net/jaraonthe/java/asb/Settings.java

    public enum Mode
    {
        MAIN,
        HELP,
        ABOUT
    }

    private Settings.Mode mode;
    private String modeArg;

    /**
     * The TCP port on which to serve this language server. Null: Do not use TCP.
     */
    private Integer port = null;


    private Settings()
    {
        // nothing
    }

    /**
     * @return The application mode
     */
    public Settings.Mode mode()
    {
        return this.mode;
    }

    /**
     * @return The TCP port on which to serve this language server. Null: Do not
     * use TCP.
     */
    public Integer port()
    {
        return port;
    }

    /**
     * Parses CLI args into application settings.
     *
     * @param args as provided to the main method.
     * @throws IllegalArgumentException if something is wrong with the provided
     *                                  arguments
     */
    static Settings fromArgs(String[] args) throws IllegalArgumentException
    {
        Settings settings = new Settings();

        for (int i = 0; i < args.length; i++) {
            if (args[i].isBlank()) {
                // Just to be safe
                continue;
            }

            switch (args[i]) {
                case "-p":
                case "--port":
                    i++;
                    if (i >= args.length) {
                        throw new IllegalArgumentException(
                            "Expected port after " + args[i - 1] + " argument"
                        );
                    }

                    try {
                        int port = Integer.parseInt(args[i]);
                        if (port <= 0 || port > 65535) {
                            throw new IllegalArgumentException("Invalid port number given");
                        }
                        settings.port = port;
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException("Given port is not a number");
                    }
                    break;

                case "-h":
                case "--help":
                    settings.setMode(Settings.Mode.HELP, args[i]);
                    break;

                case "--about":
                    settings.setMode(Settings.Mode.ABOUT, args[i]);
                    break;

                default:
                    throw new IllegalArgumentException(
                        "Unknown CLI argument \"" + args[i] + "\". See --help"
                    );
            }
        }
        if (settings.mode == null) {
            settings.setMode(Settings.Mode.MAIN, "");
        }
        return settings;
    }

    private void setMode(Settings.Mode mode, String arg) throws IllegalArgumentException
    {
        if (this.mode != null) {
            throw new IllegalArgumentException(
                "Cannot use " + arg + " as " + this.modeArg + " has already been used"
            );
        }
        this.mode = mode;
        this.modeArg = arg;
    }
}
