package de.blau.android.contract;

public final class MimeTypes {

    public static final String ALL_IMAGE_FORMATS = "image/*";
    public static final String JPEG              = "image/jpeg";
    public static final String GPX               = "application/gpx+xml";
    public static final String GEOJSON           = "application/geo+json";
    public static final String TEXTXML           = "text/xml";

    // types and subtypes
    public static final String IMAGE_TYPE                = "image";
    public static final String PNG_SUBTYPE               = "png";
    public static final String BMP_SUBTYPE               = "bmp";
    public static final String APPLICATION_TYPE          = "application";
    public static final String JSON_SUBTYPE              = "json";
    public static final String WMS_EXCEPTION_XML_SUBTYPE = "vnd.ogc.se_xml";
    public static final String TEXT_TYPE                 = "text";

    /**
     * Private constructor
     */
    private MimeTypes() {
        // nothing
    }
}
