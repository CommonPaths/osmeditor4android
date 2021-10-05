package de.blau.android.contract;

/**
 * Path constants for directories, files, extensions and similar.
 */
public final class Paths {
    /**
     * Private constructor to avoid instantation
     */
    private Paths() {
        // empty
    }

    public static final String DIRECTORY_PATH_EXTERNAL_SD_CARD   = "/external_sd";   // NOSONAR
    public static final String DIRECTORY_PATH_PICTURES           = "Pictures";
    public static final String DIRECTORY_PATH_SCRIPTS            = "Scripts";
    public static final String DIRECTORY_PATH_STORAGE            = "/storage";       // NOSONAR
    public static final String DIRECTORY_PATH_VESPUCCI           = "Vespucci";
    public static final String DIRECTORY_PATH_AUTOPRESET         = "autopreset";
    public static final String DIRECTORY_PATH_TILE_CACHE         = "/tiles/";        // NOSONAR
    public static final String DIRECTORY_PATH_TILE_CACHE_CLASSIC = "/andnav2/tiles/";// NOSONAR
    public static final String FILE_EXTENSION_IMAGE              = ".jpg";
}
