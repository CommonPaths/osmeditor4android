package de.blau.android.easyedit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.Instrumentation.ActivityMonitor;
import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.filters.SdkSuppress;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.Until;
import de.blau.android.App;
import de.blau.android.Logic;
import de.blau.android.Main;
import de.blau.android.Map;
import de.blau.android.R;
import de.blau.android.TestUtils;
import de.blau.android.osm.Node;
import de.blau.android.osm.OsmElement;
import de.blau.android.osm.Relation;
import de.blau.android.osm.Tags;
import de.blau.android.osm.Way;
import de.blau.android.prefs.AdvancedPrefDatabase;
import de.blau.android.prefs.Preferences;
import de.blau.android.propertyeditor.PropertyEditor;
import de.blau.android.util.Coordinates;

@RunWith(AndroidJUnit4.class)
@LargeTest
public class WayActionsTest {

    Context                 context = null;
    AdvancedPrefDatabase    prefDB  = null;
    Main                    main    = null;
    UiDevice                device  = null;
    Map                     map     = null;
    Logic                   logic   = null;
    private Instrumentation instrumentation;

    @Rule
    public ActivityTestRule<Main> mActivityRule = new ActivityTestRule<>(Main.class);

    /**
     * Pre-test setup
     */
    @Before
    public void setup() {
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        instrumentation = InstrumentationRegistry.getInstrumentation();
        context = instrumentation.getTargetContext();
        main = mActivityRule.getActivity();
        Preferences prefs = new Preferences(context);
        TestUtils.removeImageryLayers(context);
        prefs.enableSimpleActions(true);
        main.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                main.showSimpleActionsButton();
            }
        });

        map = main.getMap();
        map.setPrefs(main, prefs);
        TestUtils.grantPermissons(device);
        TestUtils.dismissStartUpDialogs(device, main);
        logic = App.getLogic();
        logic.deselectAll();
        TestUtils.loadTestData(main, "test2.osm");
        App.getTaskStorage().reset();
        TestUtils.stopEasyEdit(main);
    }

    /**
     * Post-test teardown
     */
    @After
    public void teardown() {
        TestUtils.stopEasyEdit(main);
        TestUtils.zoomToLevel(device, main, 18);
        App.getTaskStorage().reset();
    }

    /**
     * Create a new way from menu and clicks at two more locations and finishing via home button, then square
     */
    @SdkSuppress(minSdkVersion = 26)
    @Test
    public void square() {
        map.getDataLayer().setVisible(true);
        TestUtils.zoomToLevel(device, main, 21);
        TestUtils.unlock(device);
        TestUtils.clickButton(device, device.getCurrentPackageName() + ":id/simpleButton", true);
        assertTrue(TestUtils.clickText(device, false, context.getString(R.string.menu_add_way), true, false));
        assertTrue(TestUtils.findText(device, false, context.getString(R.string.simple_add_way)));
        TestUtils.clickAtCoordinates(device, map, 8.3886384, 47.3892752, true);
        assertTrue(TestUtils.findText(device, false, context.getString(R.string.actionmode_createpath), 1000));

        TestUtils.clickAtCoordinates(device, map, 8.3887655, 47.3892752, true);
        try {
            Thread.sleep(1000); // NOSONAR
        } catch (InterruptedException e) {
        }
        TestUtils.clickAtCoordinates(device, map, 8.38877, 47.389202, true);
        TestUtils.clickButton(device, device.getCurrentPackageName() + ":id/simpleButton", true);
        assertTrue(TestUtils.findText(device, false, context.getString(R.string.tag_form_untagged_element)));
        TestUtils.clickHome(device, true);
        Way way = App.getLogic().getSelectedWay();
        assertNotNull(way);
        assertTrue(way.getOsmId() < 0);
        assertEquals(3, way.nodeCount());
        Coordinates[] coords = Coordinates.nodeListToCooardinateArray(map.getWidth(), map.getHeight(), map.getViewBox(), way.getNodes());
        Coordinates v1 = coords[0].subtract(coords[1]);
        Coordinates v2 = coords[2].subtract(coords[1]);
        double theta = Math.toDegrees(Math.acos(Coordinates.dotproduct(v1, v2) / (v1.length() * v2.length())));
        System.out.println("Original angle " + theta);
        assertEquals(92.33, theta, 0.1);
        assertTrue(TestUtils.findText(device, false, context.getString(R.string.actionmode_wayselect)));
        TestUtils.clickOverflowButton(device);
        TestUtils.clickText(device, false, "Straighten", false, false);
        device.wait(Until.findObject(By.res(device.getCurrentPackageName() + ":string/Done")), 1000);
        coords = Coordinates.nodeListToCooardinateArray(map.getWidth(), map.getHeight(), map.getViewBox(), way.getNodes());
        v1 = coords[0].subtract(coords[1]);
        v2 = coords[2].subtract(coords[1]);
        theta = Math.toDegrees(Math.acos(Coordinates.dotproduct(v1, v2) / (v1.length() * v2.length())));
        System.out.println("New angle " + theta);
        assertEquals(90.00, theta, 0.05);
        device.waitForIdle(1000);
        TestUtils.clickUp(device);
    }

    /**
     * Select, remove two nodes
     */
    @SdkSuppress(minSdkVersion = 26)
    @Test
    public void removeNodeFromWay() {
        map.getDataLayer().setVisible(true);
        TestUtils.unlock(device);
        TestUtils.zoomToLevel(device, main, 21);
        TestUtils.clickAtCoordinates(device, map, 8.3893820, 47.3895626, true);
        assertTrue(TestUtils.clickText(device, false, "Path", false, false));
        Way way = App.getLogic().getSelectedWay();
        List<Node> origWayNodes = new ArrayList<>(way.getNodes());
        assertNotNull(way);
        assertEquals(104148456L, way.getOsmId());
        //
        assertTrue(TestUtils.findText(device, false, context.getString(R.string.actionmode_wayselect)));
        assertTrue(TestUtils.clickOverflowButton(device));
        assertTrue(TestUtils.clickText(device, false, context.getString(R.string.menu_remove_node_from_way), true, false));
        assertTrue(TestUtils.findText(device, false, context.getString(R.string.menu_remove_node_from_way)));
        // delete an untagged way node somewhere in the middle
        int origSize = way.getNodes().size();
        Node testNode1 = way.getNodes().get(origSize - 4);
        TestUtils.clickAtCoordinatesWaitNewWindow(device, map, testNode1.getLon(), testNode1.getLat());
        assertEquals(OsmElement.STATE_DELETED, testNode1.getState());
        assertEquals(origSize - 1, way.getNodes().size());
        // delete the end node that is shared by some other ways
        TestUtils.clickAtCoordinates(device, map, 8.3893820, 47.3895626, true);
        assertTrue(TestUtils.clickText(device, false, "Path", false, false));
        assertTrue(TestUtils.findText(device, false, context.getString(R.string.actionmode_wayselect)));
        assertTrue(TestUtils.clickOverflowButton(device));
        assertTrue(TestUtils.clickText(device, false, context.getString(R.string.menu_remove_node_from_way), true, false));
        assertTrue(TestUtils.findText(device, false, context.getString(R.string.menu_remove_node_from_way)));
        origSize = way.getNodes().size();
        Node testNode2 = way.getLastNode();
        List<Way> ways = logic.getWaysForNode(testNode2);
        assertEquals(4, ways.size());
        assertTrue(ways.contains(way));
        TestUtils.clickAtCoordinatesWaitNewWindow(device, map, testNode2.getLon(), testNode2.getLat());
        assertEquals(OsmElement.STATE_UNCHANGED, testNode2.getState());
        assertEquals(origSize - 1, way.getNodes().size());
        ways = logic.getWaysForNode(testNode2);
        assertEquals(3, ways.size());
        assertFalse(ways.contains(way));
    }

    /**
     * Select way, create route, add a further segment
     */
    @SdkSuppress(minSdkVersion = 26)
    @Test
    public void createRoute() {
        map.getDataLayer().setVisible(true);
        TestUtils.unlock(device);
        TestUtils.zoomToLevel(device, main, 21);
        TestUtils.clickAtCoordinates(device, map, 8.3893820, 47.3895626, true);
        assertTrue(TestUtils.clickText(device, false, "Path", false, false));
        Way way = App.getLogic().getSelectedWay();
        assertNotNull(way);
        assertEquals(104148456L, way.getOsmId());
        //
        assertTrue(TestUtils.findText(device, false, context.getString(R.string.actionmode_wayselect)));
        assertTrue(TestUtils.clickOverflowButton(device));
        assertTrue(TestUtils.clickText(device, false, context.getString(R.string.menu_create_route), true, false));
        assertTrue(TestUtils.findText(device, false, context.getString(R.string.actionmode_add_segment)));

        // add 50059937
        Way way2 = (Way) App.getDelegator().getOsmElement(Way.NAME, 50059937L);
        assertNotNull(way2);
        Node way2Node = way2.getNodes().get(2);
        TestUtils.clickAtCoordinates(device, map, way2Node.getLon(), way2Node.getLat(), true);
        TestUtils.sleep();
        assertTrue(TestUtils.findText(device, false, context.getString(R.string.actionmode_add_segment)));

        TestUtils.clickUp(device);
        assertTrue(TestUtils.clickText(device, false, context.getString(R.string.cancel), true, false));

        ActivityMonitor monitor = instrumentation.addMonitor(PropertyEditor.class.getName(), null, false);
        // finish
        TestUtils.clickButton(device, device.getCurrentPackageName() + ":id/simpleButton", true);
        Activity propertyEditor = instrumentation.waitForMonitorWithTimeout(monitor, 30000);
        assertNotNull(propertyEditor);
        TestUtils.sleep(5000);
        assertTrue(TestUtils.clickHome(device, true));
        instrumentation.removeMonitor(monitor);

        List<Relation> rels = logic.getSelectedRelations();
        assertNotNull(rels);
        assertEquals(1, rels.size());
        final Relation route = rels.get(0);
        assertEquals(2, route.getMembers().size());
        assertNotNull(route.getMember(Way.NAME, 104148456L));
        assertNotNull(route.getMember(Way.NAME, 50059937L));
    }

    /**
     * Select way, select route, add a further segment, re-select first segment, add 2nd segment
     */
    @SdkSuppress(minSdkVersion = 26)
    @Test
    public void addToRoute() {
        map.getDataLayer().setVisible(true);
        TestUtils.unlock(device);
        TestUtils.zoomToLevel(device, main, 21);
        TestUtils.clickAtCoordinates(device, map, 8.3884403, 47.3884988, true);
        assertTrue(TestUtils.clickText(device, false, "Bergstrasse", false, false));
        Way way = App.getLogic().getSelectedWay();
        assertNotNull(way);
        assertEquals(119104094L, way.getOsmId());
        //
        assertTrue(TestUtils.findText(device, false, context.getString(R.string.actionmode_wayselect)));
        assertTrue(TestUtils.clickOverflowButton(device));
        assertTrue(TestUtils.clickText(device, false, context.getString(R.string.menu_add_to_route), true, false));
        assertTrue(TestUtils.clickText(device, false, "Bus 305", true, false));
        assertTrue(TestUtils.findText(device, false, context.getString(R.string.actionmode_add_segment)));

        // add 47001849
        Way way2 = (Way) App.getDelegator().getOsmElement(Way.NAME, 47001849L);
        assertNotNull(way2);
        Node way2Node = way2.getNodes().get(2);
        TestUtils.clickAtCoordinates(device, map, way2Node.getLon(), way2Node.getLat(), true);
        TestUtils.sleep();
        assertTrue(TestUtils.findText(device, false, context.getString(R.string.actionmode_reselect_first_segment)));

        // reselect 119104094 and then 47001849
        TestUtils.clickAtCoordinates(device, map, 8.3884403, 47.3884988, true);
        assertTrue(TestUtils.findText(device, false, context.getString(R.string.actionmode_add_segment)));
        TestUtils.clickAtCoordinates(device, map, way2Node.getLon(), way2Node.getLat(), true);
        TestUtils.sleep();

        ActivityMonitor monitor = instrumentation.addMonitor(PropertyEditor.class.getName(), null, false);
        // finish
        TestUtils.clickButton(device, device.getCurrentPackageName() + ":id/simpleButton", true);
        Activity propertyEditor = instrumentation.waitForMonitorWithTimeout(monitor, 30000);
        assertNotNull(propertyEditor);
        TestUtils.sleep(5000);
        TestUtils.clickText(device, false, context.getString(R.string.cancel), true, false);
        assertTrue(TestUtils.clickHome(device, true));
        instrumentation.removeMonitor(monitor);

        List<Relation> rels = logic.getSelectedRelations();
        assertNotNull(rels);
        assertEquals(1, rels.size());
        final Relation route = rels.get(0);
        assertEquals(2807173L, route.getOsmId());
        assertNotNull(route.getMember(Way.NAME, 119104094L));
        assertNotNull(route.getMember(Way.NAME, 47001849L));
    }

    /**
     * Select from way, select via node, re-select from, select to way
     */
    @SdkSuppress(minSdkVersion = 26)
    @Test
    public void createTurnRestriction() {
        map.getDataLayer().setVisible(true);
        TestUtils.unlock(device);
        TestUtils.zoomToLevel(device, main, 21);
        TestUtils.clickAtCoordinates(device, map, 8.3884403, 47.3884988, true);
        assertTrue(TestUtils.clickText(device, false, "Bergstrasse", false, false));
        Way way = App.getLogic().getSelectedWay();
        assertNotNull(way);
        assertEquals(119104094L, way.getOsmId());
        //
        assertTrue(TestUtils.findText(device, false, context.getString(R.string.actionmode_wayselect)));

        if (!TestUtils.clickMenuButton(device, context.getString(R.string.actionmode_restriction), false, true)) {
            assertTrue(TestUtils.clickOverflowButton(device));
            assertTrue(TestUtils.clickText(device, false, context.getString(R.string.actionmode_restriction), true, false));
        }

        assertTrue(TestUtils.findText(device, false, context.getString(R.string.actionmode_restriction_via)));

        // add via node 633468409
        Node via = (Node) App.getDelegator().getOsmElement(Node.NAME, 633468409L);
        assertNotNull(via);
        TestUtils.clickAtCoordinates(device, map, via.getLon(), via.getLat(), true);
        TestUtils.sleep();
        assertTrue(TestUtils.findText(device, false, context.getString(R.string.actionmode_restriction_restart_from)));

        // reselect 119104094 and then 47001849
        TestUtils.clickAtCoordinates(device, map, 8.3884403, 47.3884988, true);
        assertTrue(TestUtils.findText(device, false, context.getString(R.string.actionmode_restriction_to)));

        ActivityMonitor monitor = instrumentation.addMonitor(PropertyEditor.class.getName(), null, false);
        // click to way 49855525
        TestUtils.clickAtCoordinates(device, map, 8.3879168, 47.3883856, true);

        Activity propertyEditor = instrumentation.waitForMonitorWithTimeout(monitor, 30000);
        assertNotNull(propertyEditor);
        TestUtils.sleep(5000);
        assertTrue(TestUtils.clickText(device, false, "No left turn", true, false));
        assertTrue(TestUtils.findText(device, false, "No left turn"));
        assertTrue(TestUtils.clickHome(device, true));
        instrumentation.removeMonitor(monitor);

        List<Relation> rels = logic.getSelectedRelations();
        assertNotNull(rels);
        assertEquals(1, rels.size());
        final Relation restriction = rels.get(0);
        assertEquals(3, restriction.getMembers().size());
        assertEquals(1, restriction.getMembersWithRole(Tags.ROLE_FROM).size());
        // from will have a different id as it has been split assertEquals(119104094L, restriction.getMembersWithRole(Tags.ROLE_FROM).get(0).getElement().getOsmId());
        assertEquals(1, restriction.getMembersWithRole(Tags.ROLE_VIA).size());
        assertEquals(633468409L, restriction.getMembersWithRole(Tags.ROLE_VIA).get(0).getElement().getOsmId());
        assertEquals(1, restriction.getMembersWithRole(Tags.ROLE_TO).size());
        assertEquals(49855525L, restriction.getMembersWithRole(Tags.ROLE_TO).get(0).getElement().getOsmId());
    }
}
