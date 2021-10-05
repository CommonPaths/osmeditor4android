package de.blau.android.easyedit;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import androidx.test.uiautomator.UiDevice;
import de.blau.android.App;
import de.blau.android.Logic;
import de.blau.android.Main;
import de.blau.android.Map;
import de.blau.android.R;
import de.blau.android.SignalHandler;
import de.blau.android.TestUtils;
import de.blau.android.osm.ApiTest;
import de.blau.android.osm.Node;
import de.blau.android.prefs.AdvancedPrefDatabase;
import de.blau.android.prefs.Preferences;

@RunWith(AndroidJUnit4.class)
@LargeTest
public class ExtendedSelectionTest {

    Context              context = null;
    AdvancedPrefDatabase prefDB  = null;
    Main                 main    = null;
    UiDevice             device  = null;
    Map                  map     = null;
    Logic                logic   = null;

    @Rule
    public ActivityTestRule<Main> mActivityRule = new ActivityTestRule<>(Main.class);

    /**
     * Pre-test setup
     */
    @Before
    public void setup() {
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        main = mActivityRule.getActivity();
        Preferences prefs = new Preferences(context);
        TestUtils.removeImageryLayers(context);
        map = main.getMap();
        map.setPrefs(main, prefs);
        TestUtils.grantPermissons(device);
        TestUtils.dismissStartUpDialogs(device, main);
        logic = App.getLogic();
        logic.deselectAll();
        TestUtils.loadTestData(main, "test2.osm");
        TestUtils.stopEasyEdit(main);
    }

    /**
     * Post-test teardown
     */
    @After
    public void teardown() {
        TestUtils.stopEasyEdit(main);
        TestUtils.zoomToLevel(device, main, 18);
    }

    /**
     * Select node, select 2nd node, de-select
     */
    @Test
    public void selectNodes() {
        TestUtils.zoomToLevel(device, main, 18); // if we are zoomed in too far we might not get the selection popups
        map.getDataLayer().setVisible(true);
        TestUtils.unlock(device);
        TestUtils.clickAtCoordinates(device, map, 8.38782, 47.390339, true);
        Assert.assertTrue(TestUtils.clickText(device, false, "Toilets", false, false));
        Node node = App.getLogic().getSelectedNode();
        Assert.assertNotNull(node);
        Assert.assertEquals(3465444349L, node.getOsmId());
        int origLon = node.getLon();
        int origLat = node.getLat();
        Assert.assertTrue(TestUtils.findText(device, false, context.getString(R.string.actionmode_nodeselect)));
        Assert.assertTrue(TestUtils.clickOverflowButton(device));
        Assert.assertTrue(TestUtils.clickText(device, false, "Extend selection", true, false));
        Assert.assertTrue(TestUtils.findText(device, false, context.getString(R.string.actionmode_multiselect)));
        // double clicking doesn't currently work reliably in tests TestUtils.doubleClickAtCoordinates(device, map, 8.3877977, 47.3897371, true);
        TestUtils.clickAtCoordinates(device, map, 8.3877977, 47.3897371, true);
        Assert.assertTrue(TestUtils.clickText(device, false, "Excrement", false, false));
        Assert.assertEquals(2, logic.getSelectedNodes().size());
        TestUtils.zoomToLevel(device, main, 22);
        TestUtils.drag(device, map, 8.3877977, 47.3897371, 8.3879, 47.38967, true, 100);
        
        int deltaLon = node.getLon() - origLon;
        int deltaLat = node.getLat() - origLat;
 
        Assert.assertEquals(8.3879 - 8.3877977, deltaLon/1E7D, 0.00001);
        Assert.assertEquals(47.38967 - 47.3897371, deltaLat/1E7D, 0.00001);
        TestUtils.clickUp(device);
    }
}
