package de.blau.android.easyedit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

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
import androidx.lifecycle.Lifecycle.State;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.filters.SdkSuppress;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;
import de.blau.android.App;
import de.blau.android.Logic;
import de.blau.android.Main;
import de.blau.android.Map;
import de.blau.android.R;
import de.blau.android.TestUtils;
import de.blau.android.osm.Node;
import de.blau.android.osm.Relation;
import de.blau.android.osm.Way;
import de.blau.android.prefs.AdvancedPrefDatabase;
import de.blau.android.prefs.Preferences;
import de.blau.android.propertyeditor.PropertyEditor;

/**
 * 1st attempts at testing lifecycle related aspects in easyedit modes
 * 
 * @author simon
 *
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class SaveResumeTest {

    Context                 context  = null;
    AdvancedPrefDatabase    prefDB   = null;
    Main                    main     = null;
    UiDevice                device   = null;
    Map                     map      = null;
    Logic                   logic    = null;
    private Instrumentation instrumentation;
    ActivityScenario<Main>  scenario = null;

    @Rule
    public ActivityScenarioRule<Main> activityScenarioRule = new ActivityScenarioRule<>(Main.class);

    /**
     * Pre-test setup
     */
    @Before
    public void setup() {
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        instrumentation = InstrumentationRegistry.getInstrumentation();
        context = instrumentation.getTargetContext();
        ActivityMonitor monitor = instrumentation.addMonitor(Main.class.getName(), null, false);
        scenario = ActivityScenario.launch(Main.class);
        // scenario.moveToState(State.STARTED);
        main = (Main) instrumentation.waitForMonitorWithTimeout(monitor, 30000);
        instrumentation.removeMonitor(monitor);
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
        scenario.moveToState(State.DESTROYED);
    }

    /**
     * Select way, create route, add a further segment - restart app
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

        scenario.recreate();

        assertTrue(TestUtils.findText(device, false, context.getString(R.string.actionmode_add_segment), 10000));

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
     * Select way, create reation - restart app
     */
    @SdkSuppress(minSdkVersion = 26)
    @Test
    public void createRelation() {
        map.getDataLayer().setVisible(true);
        TestUtils.unlock(device);
        TestUtils.zoomToLevel(device, main, 21);
        TestUtils.clickAtCoordinates(device, map, 8.3893820, 47.3895626, true);
        assertTrue(TestUtils.findText(device, false, "Path", 2000));
        assertTrue(TestUtils.clickText(device, false, "Path", false, false));
        Way way = App.getLogic().getSelectedWay();
        assertNotNull(way);
        assertEquals(104148456L, way.getOsmId());
        //
        assertTrue(TestUtils.findText(device, false, context.getString(R.string.actionmode_wayselect)));
        assertTrue(TestUtils.clickOverflowButton(device));
        TestUtils.scrollTo(context.getString(R.string.menu_relation));
        assertTrue(TestUtils.clickText(device, false, context.getString(R.string.menu_relation), true, false));
        TestUtils.scrollToEnd();
        assertTrue(TestUtils.clickText(device, false, context.getString(R.string.select_relation_type_other), true, false));
        assertTrue(TestUtils.findText(device, false, context.getString(R.string.menu_add_relation_member)));
        ActivityMonitor monitor = instrumentation.addMonitor(PropertyEditor.class.getName(), null, false);

        scenario.recreate();

        // finish
        TestUtils.clickButton(device, device.getCurrentPackageName() + ":id/simpleButton", true);
        Activity propertyEditor = instrumentation.waitForMonitorWithTimeout(monitor, 30000);
        assertNotNull(propertyEditor);
        TestUtils.sleep(5000);
        TestUtils.clickHome(device, true);
        List<Relation> relations = App.getLogic().getSelectedRelations();
        assertEquals(1, relations.size());
        Relation relation = relations.get(0);
        assertTrue(relation.getOsmId() < 0);
    }

}
