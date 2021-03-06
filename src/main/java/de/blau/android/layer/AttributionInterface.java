package de.blau.android.layer;

import android.graphics.Canvas;
import androidx.annotation.NonNull;
import de.blau.android.views.IMapView;

public interface AttributionInterface {

    /**
     * Draw attribution for the current layer
     * 
     * @param c Canvas we are drawing on
     * @param osmv IMapView calling us
     * @param offset current offset
     * @return new offset
     */
    int onDrawAttribution(@NonNull Canvas c, @NonNull IMapView osmv, int offset);
}
