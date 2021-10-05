package de.blau.android.easyedit;

import static de.blau.android.util.Geometry.isInside;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import android.content.Context;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.view.ActionMode;
import ch.poole.osm.josmfilterparser.Condition;
import ch.poole.osm.josmfilterparser.JosmFilterParser;
import de.blau.android.App;
import de.blau.android.R;
import de.blau.android.osm.BoundingBox;
import de.blau.android.osm.Node;
import de.blau.android.osm.OsmElement;
import de.blau.android.osm.OsmElement.ElementType;
import de.blau.android.osm.Relation;
import de.blau.android.osm.RelationMember;
import de.blau.android.osm.Storage;
import de.blau.android.osm.StorageDelegator;
import de.blau.android.osm.Tags;
import de.blau.android.osm.Way;
import de.blau.android.presets.Preset;
import de.blau.android.presets.Preset.PresetItem;
import de.blau.android.presets.PresetElementPath;
import de.blau.android.presets.PresetRole;
import de.blau.android.search.Wrapper;
import de.blau.android.util.SerializableState;
import de.blau.android.util.Snack;
import de.blau.android.util.ThemeUtils;
import de.blau.android.util.Util;
import de.blau.android.util.collections.MultiHashMap;

/**
 * Callback for adding new members to a Relation
 * 
 * @author Simon
 *
 */
public class EditRelationMembersActionModeCallback extends BuilderActionModeCallback {
    private static final String DEBUG_TAG = "EditRelation...";

    private static final int MENUITEM_REVERT = 1;

    private static final String RELATION_ID_KEY    = "relation id";
    private static final String NEW_MEMBERS_KEY    = "new members";
    private static final String REMOVE_MEMBERS_KEY = "remove members";
    private static final String PRESET_PATH_KEY    = "preset path";

    private final List<RelationMember>               newMembers     = new ArrayList<>();
    private final MultiHashMap<Long, RelationMember> removeMembers  = new MultiHashMap<>();
    private Relation                                 relation       = null;
    private MenuItem                                 revertItem     = null;
    private PresetElementPath                        presetPath     = null;
    private PresetItem                               relationPreset = null;

    /**
     * Construct a new callback from saved state
     * 
     * @param manager the current EasyEditManager instance
     * @param state the saved state
     */
    public EditRelationMembersActionModeCallback(@NonNull EasyEditManager manager, @NonNull SerializableState state) {
        super(manager);
        StorageDelegator delegator = App.getDelegator();
        List<RelationMember> savedNewMembers = state.getList(NEW_MEMBERS_KEY);
        if (savedNewMembers != null) {
            for (RelationMember member : savedNewMembers) {
                OsmElement element = delegator.getOsmElement(member.getType(), member.getRef());
                if (element != null) {
                    member.setElement(element);
                }
                newMembers.add(member);
            }
        }
        List<RelationMember> savedRemoveMembers = state.getList(REMOVE_MEMBERS_KEY);
        if (savedRemoveMembers != null) {
            for (RelationMember member : savedRemoveMembers) {
                removeMembers.add(member.getRef(), member);
            }
        }
        Long relationId = state.getLong(RELATION_ID_KEY);
        if (relationId != null) {
            relation = (Relation) delegator.getOsmElement(Relation.NAME, relationId);
        }
    }

    /**
     * Construct a new AddRelationMemberActionModeCallback from a list of selected OsmElements
     * 
     * @param manager the current EasyEditManager instance
     * @param selection a List containing OsmElements
     */
    public EditRelationMembersActionModeCallback(@NonNull EasyEditManager manager, @Nullable PresetElementPath presetPath,
            @NonNull List<OsmElement> selection) {
        super(manager);
        this.presetPath = presetPath;
        determineRelationPreset();
        for (OsmElement e : selection) {
            addElement(e);
        }
    }

    /**
     * Construct a new AddRelationMemberActionModeCallback starting with a single OsmElement
     * 
     * @param manager the current EasyEditManager instance
     * @param element the OsmElement
     */
    public EditRelationMembersActionModeCallback(@NonNull EasyEditManager manager, @Nullable PresetElementPath presetPath, @NonNull OsmElement element) {
        super(manager);
        this.presetPath = presetPath;
        determineRelationPreset();
        addElement(element);
    }

    /**
     * Construct a new AddRelationMemberActionModeCallback starting with an existing Relation and potentially a new
     * OsmElement to add
     * 
     * @param manager the current EasyEditManager instance
     * @param relation the existing Relation
     * @param element the OsmElement to add or null
     */
    public EditRelationMembersActionModeCallback(@NonNull EasyEditManager manager, @NonNull Relation relation, @Nullable OsmElement element) {
        super(manager);
        this.relation = relation;
        determineRelationPreset();
        if (element != null) {
            addElement(element);
        }
    }

    /**
     * Construct a new AddRelationMemberActionModeCallback starting with an existing Relation and a list of selected
     * OsmElements
     * 
     * @param manager the current EasyEditManager instance
     * @param relation the existing Relation
     * @param selection a List containing OsmElements
     */
    public EditRelationMembersActionModeCallback(@NonNull EasyEditManager manager, @NonNull Relation relation, @NonNull List<OsmElement> selection) {
        super(manager);
        this.relation = relation;
        determineRelationPreset();
        for (OsmElement e : selection) {
            addElement(e);
        }
    }

    /**
     * Add an OsmElement to an existing/ to be created Relation
     * 
     * Creates a new RelationMember if a preset can be determined, and there is exactly one matching role then it will
     * be set to that, otherwise it will be left empty
     * 
     * @param element the OsmElement to add
     */
    private void addElement(@NonNull OsmElement element) {
        for (RelationMember member : removeMembers.getValues()) {
            if (member.getType().equals(element.getName()) && member.getRef() == element.getOsmId()) {
                removeMembers.removeItem(member.getRef(), member);
                return;
            }
        }
        RelationMember member = new RelationMember("", element);
        if (relationPreset != null) {
            List<PresetRole> roles = relationPreset.getRoles(main, element, null);
            if (roles != null) {
                if (roles.size() == 1) {
                    // exactly one match
                    member.setRole(roles.get(0).getRole());
                } else {
                    // TODO maybe ask user
                }
            }
        }
        newMembers.add(member);
        highlight(element);
    }

    /**
     * Remove an OsmElement from the freshly added or existing members
     * 
     * @param element the OsmElement to remove
     */
    private void removeElement(@NonNull OsmElement element) {
        try {
            for (RelationMember member : newMembers) {
                if (member.getType().equals(element.getName()) && member.getRef() == element.getOsmId()) {
                    newMembers.remove(member);
                    return;
                }
            }
            if (relation != null) {
                RelationMember member = relation.getMember(element);
                if (member != null) {
                    RelationMember removeMember = new RelationMember(member);
                    removeMember.setElement(null);
                    removeMembers.add(removeMember.getRef(), removeMember);
                }
            }
        } finally {
            highlightAll();
            main.invalidateMap();
        }
    }

    @Override
    public boolean onCreateActionMode(ActionMode mode, Menu menu) {
        helpTopic = R.string.help_addrelationmember;
        if (relation != null) {
            mode.setTitle(R.string.menu_edit_relation);
        } else {
            mode.setTitle(R.string.menu_relation);
        }
        mode.setSubtitle(R.string.menu_add_relation_member);
        super.onCreateActionMode(mode, menu);
        logic.setReturnRelations(false); // no relations, setClickabl might override this

        determineRelationPreset();

        // menu setup
        menu = replaceMenu(menu, mode, this);
        revertItem = menu.add(Menu.NONE, MENUITEM_REVERT, Menu.NONE, R.string.tag_menu_revert)
                .setIcon(ThemeUtils.getResIdFromAttribute(main, R.attr.menu_undo));
        arrangeMenu(menu); // needed at least once
        if (relation != null && !relation.allDownloaded()) {
            Snack.toastTopWarning(main, R.string.toast_members_not_downloaded);
        }
        highlightAll();
        setClickableElements();
        return true;
    }

    /**
     * If possible set the relation preset item
     */
    public void determineRelationPreset() {
        // determine the Preset for the relation
        if (relation != null) {
            relationPreset = Preset.findBestMatch(App.getCurrentPresets(main), relation.getTags());
        } else if (presetPath != null) {
            relationPreset = (PresetItem) Preset.getElementByPath(App.getCurrentRootPreset(main).getRootGroup(), presetPath);
        }
    }

    /**
     * Highlight all relevant elements
     */
    private void highlightAll() {
        logic.setSelectedRelationNodes(null);
        logic.setSelectedRelationWays(null);
        logic.setSelectedRelationRelations(null);
        if (relation != null) {
            for (RelationMember member : relation.getMembers()) {
                Set<RelationMember> toRemove = removeMembers.get(member.getRef());
                if (member.downloaded() && (toRemove.isEmpty() || !contains(toRemove, member))) {
                    highlight(member.getElement());
                }
            }
        }
        for (RelationMember member : newMembers) {
            if (member.downloaded()) {
                highlight(member.getElement());
            }
        }
    }

    /**
     * Check if member is in a Collection of members
     * 
     * @param members the Set of RelationMember
     * @param member the RelationMember
     */
    private boolean contains(Collection<RelationMember> members, RelationMember member) {
        for (RelationMember removeMember : members) {
            if (!member.downloaded() || (member.getRef() == removeMember.getRef() && member.getType().equals(removeMember.getType())
                    && member.getRole().equals(removeMember.getRole()))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Pretend that the element is already a member and highlight it
     * 
     * @param element the element
     */
    private void highlight(@NonNull OsmElement element) {
        switch (element.getName()) {
        case Way.NAME:
            logic.addSelectedRelationWay((Way) element);
            return;
        case Node.NAME:
            logic.addSelectedRelationNode((Node) element);
            return;
        case Relation.NAME:
            logic.addSelectedRelationRelation((Relation) element);
            return;
        default:
            Log.e(DEBUG_TAG, "Element has unknown type " + element.getOsmId() + " " + element.getName());
        }
    }

    @Override
    public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
        setClickableElements();
        if (relation != null) {
            List<Way> relationWays = logic.getSelectedRelationWays();
            List<Node> relationNodes = logic.getSelectedRelationNodes();
            // new members might be downloaded
            for (RelationMember rm : relation.getMembers()) {
                if (rm.downloaded()) {
                    switch (rm.getType()) {
                    case Way.NAME:
                        if (relationWays != null && !relationWays.contains(rm.getElement())) {
                            logic.addSelectedRelationWay((Way) rm.getElement());
                        }
                        break;
                    case Node.NAME:
                        if (relationNodes != null && !relationNodes.contains(rm.getElement())) {
                            logic.addSelectedRelationNode((Node) rm.getElement());
                        }
                        break;
                    case Relation.NAME:
                    default:
                        // do nothing
                    }
                }
            }
        }
        menu = replaceMenu(menu, mode, this);
        boolean updated = super.onPrepareActionMode(mode, menu);
        updated |= ElementSelectionActionModeCallback.setItemVisibility(!newMembers.isEmpty(), revertItem, false);
        if (updated) {
            arrangeMenu(menu);
        }
        return updated;
    }

    @Override
    public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
        if (!super.onActionItemClicked(mode, item) && item.getItemId() == MENUITEM_REVERT && !newMembers.isEmpty()) {
            RelationMember member = newMembers.get(newMembers.size() - 1);
            OsmElement element = member.getElement();
            if (element.getName().equals(Way.NAME)) {
                logic.removeSelectedRelationWay((Way) element);
            } else if (element.getName().equals(Node.NAME)) {
                logic.removeSelectedRelationNode((Node) element);
            } else if (element.getName().equals(Relation.NAME)) {
                logic.removeSelectedRelationRelation((Relation) element);
            }
            newMembers.remove(newMembers.size() - 1);
            setClickableElements();
            main.invalidateMap();
            if (newMembers.isEmpty()) {
                item.setVisible(false);
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean handleElementClick(OsmElement element) {
        // due to clickableElements, only valid elements can be clicked
        super.handleElementClick(element);
        List<Way> relationWays = logic.getSelectedRelationWays();
        List<Node> relationNodes = logic.getSelectedRelationNodes();
        if ((relationWays != null && relationWays.contains(element)) || (relationNodes != null && relationNodes.contains(element))) {
            new AlertDialog.Builder(main).setTitle(R.string.duplicate_relation_member_title).setMessage(R.string.duplicate_relation_member_message)
                    .setPositiveButton(R.string.duplicate_route_segment_button, (dialog, which) -> addElement(element))
                    .setNegativeButton(R.string.duplicate_relation_member_remove_button, (dialog, which) -> removeElement(element))
                    .setNeutralButton(R.string.cancel, null).show();
        } else {
            addElement(element);
            setClickableElements();
        }
        mode.invalidate();
        main.invalidateMap();
        return true;
    }

    /**
     * Calculate and set the elements the user can click
     */
    private void setClickableElements() {
        BoundingBox viewBox = main.getMap().getViewBox();
        List<PresetRole> roles = null;
        if (relationPreset != null) {
            roles = relationPreset.getRoles();
        }

        if (roles != null) {
            for (PresetRole role : roles) {
                if (role.appliesTo(ElementType.RELATION)) {
                    logic.setReturnRelations(true);
                    break;
                }
            }
            Set<OsmElement> elements = new HashSet<>();
            final Storage currentStorage = App.getDelegator().getCurrentStorage();
            elements.addAll(currentStorage.getNodes(viewBox));
            elements.addAll(currentStorage.getWays(viewBox));
            Set<OsmElement> clickable = new HashSet<>();
            Wrapper wrapper = new Wrapper(main);
            Map<String, Condition> conditionCache = new HashMap<>();
            for (OsmElement e : elements) {
                for (PresetRole role : roles) {
                    if (role.appliesTo(e.getName())) {
                        String memberExpression = role.getMemberExpression();
                        if (memberExpression != null) {
                            memberExpression = memberExpression.trim();
                            wrapper.setElement(e);
                            Condition condition = conditionCache.get(memberExpression); // NOSONAR
                            if (condition == null) {
                                try {
                                    JosmFilterParser parser = new JosmFilterParser(new ByteArrayInputStream(memberExpression.getBytes()));
                                    condition = parser.condition();
                                    conditionCache.put(memberExpression, condition);
                                } catch (ch.poole.osm.josmfilterparser.ParseException | IllegalArgumentException ex) {
                                    Log.e(DEBUG_TAG, "member_expression " + memberExpression + " caused " + ex.getMessage());
                                    try {
                                        JosmFilterParser parser = new JosmFilterParser(new ByteArrayInputStream("".getBytes()));
                                        condition = parser.condition();
                                        conditionCache.put(memberExpression, condition);
                                    } catch (ch.poole.osm.josmfilterparser.ParseException | IllegalArgumentException ex2) {
                                        Log.e(DEBUG_TAG, "member_expression dummy caused " + ex2.getMessage());
                                    }
                                }
                            }
                            if (condition != null && condition.eval(Wrapper.toJosmFilterType(e), wrapper, e.getTags())) {
                                clickable.add(e);
                                break;
                            }
                        } else {
                            clickable.add(e);
                            break;
                        }
                    }
                }
            }
            logic.setClickableElements(clickable);
        } else {
            ArrayList<OsmElement> excludes = new ArrayList<>();
            for (RelationMember member : newMembers) {
                excludes.add(member.getElement());
            }
            if (relation != null) {
                logic.setSelectedRelationMembers(relation);
                excludes.addAll(relation.getMemberElements());
            }
            logic.setClickableElements(logic.findClickableElements(viewBox, excludes));
        }
    }

    @Override
    public void onDestroyActionMode(ActionMode mode) {
        super.onDestroyActionMode(mode);
        logic.setClickableElements(null);
        logic.setReturnRelations(true);
        logic.deselectAll();
    }

    @Override
    public void finishBuilding() {
        if (!newMembers.isEmpty() || !removeMembers.isEmpty()) { // something was actually added
            if (relation == null) {
                if (relationPreset != null && (relationPreset.hasKeyValue(Tags.KEY_TYPE, Tags.VALUE_MULTIPOLYGON)
                        || relationPreset.hasKeyValue(Tags.KEY_TYPE, Tags.VALUE_BOUNDARY))) {
                    List<RelationMember> multipolygonMembers = setMultipolygonRoles(main, newMembers, true);
                    newMembers.clear();
                    newMembers.addAll(multipolygonMembers);
                    if (outersHaveSameTags(newMembers)) {
                        moveOuterTags();
                        return;
                    } else {
                        Snack.toastTopWarning(main, R.string.toast_outer_rings_differing_tags);
                    }
                }
                relation = logic.createRelationFromMembers(main, null, newMembers);
                main.performTagEdit(relation, presetPath, null, false);
            } else {
                // determine the actual members in the relation we need to delete
                List<RelationMember> toRemove = new ArrayList<>();
                for (RelationMember member : relation.getMembers()) {
                    if (contains(removeMembers.getValues(), member)) {
                        toRemove.add(member);
                    }
                }
                logic.updateRelationMembers(main, relation, toRemove, newMembers);
                final List<RelationMember> members = relation.getMembers();
                setMultipolygonRoles(main, members, false); // update roles
                if (outersHaveTags(relation.getTags(), members)) {
                    removeTagsFromMembers(relation.getTags(), relation.getMembersWithRole(Tags.ROLE_OUTER));
                    return;
                }
                main.performTagEdit(relation, null, false, false);
            }
        } else {
            Snack.toastTopWarning(main, R.string.toast_nothing_changed);
        }
        if (relation != null) {
            main.startSupportActionMode(new RelationSelectionActionModeCallback(manager, relation));
        } else {
            manager.finish();
        }
    }

    /**
     * Move tags from the outer members to the multi-polygon relation, asking for confirmation first
     */
    private void moveOuterTags() {
        // create relation first, roles have been set now
        relation = logic.createRelationFromMembers(main, null, newMembers);
        AlertDialog alertDialog = new AlertDialog.Builder(main).setTitle(R.string.move_outer_tags_title).setMessage(R.string.move_outer_tags_message)
                .setPositiveButton(R.string.move, (dialog, which) -> {
                    List<RelationMember> outers = relation.getMembersWithRole(Tags.ROLE_OUTER);
                    HashMap<String, String> outerTags = null;
                    Map<String, String> emptyTags = new HashMap<>();
                    for (RelationMember outer : outers) {
                        if (outer.downloaded()) {
                            if (outerTags == null) {
                                outerTags = new HashMap<>(outer.getElement().getTags());
                            }
                            App.getLogic().setTags(main, outer.getType(), outer.getRef(), emptyTags, false);
                        }
                    }
                    if (outerTags != null) {
                        // add MP type to tags
                        outerTags.put(Tags.KEY_TYPE, Tags.VALUE_MULTIPOLYGON);
                    }
                    App.getLogic().setTags(main, Relation.NAME, relation.getOsmId(), outerTags, false);
                }).setNeutralButton(R.string.leave_as_is, null).create();
        alertDialog.setOnDismissListener(dialog -> {
            main.performTagEdit(relation, presetPath, null, false);
            main.startSupportActionMode(new RelationSelectionActionModeCallback(manager, relation));
        });
        alertDialog.show();
    }

    /**
     * Remove duplicate tags from the outer members, asking for confirmation first
     */
    private void removeTagsFromMembers(@NonNull Map<String, String> tags, @NonNull List<RelationMember> outers) {
        AlertDialog alertDialog = new AlertDialog.Builder(main).setTitle(R.string.remove_duplicate_outer_tags_title)
                .setMessage(R.string.remove_duplicate_outer_tags_message).setPositiveButton(R.string.remove, (dialog, which) -> {
                    for (RelationMember outer : outers) {
                        if (outer.downloaded()) {
                            Map<String, String> outerTags = new HashMap<>(outer.getElement().getTags());
                            for (Entry<String, String> tag : tags.entrySet()) {
                                final String key = tag.getKey();
                                final String outerValue = outerTags.get(key);
                                if (outerValue != null && outerValue.equals(tag.getValue())) {
                                    outerTags.remove(key);
                                }
                            }
                            App.getLogic().setTags(main, outer.getType(), outer.getRef(), outerTags, false);
                        }
                    }
                }).setNeutralButton(R.string.leave_as_is, null).create();
        alertDialog.setOnDismissListener(dialog -> {
            main.performTagEdit(relation, null, false, false);
            main.startSupportActionMode(new RelationSelectionActionModeCallback(manager, relation));
        });
        alertDialog.show();
    }

    /**
     * Check if all outer members of a multi-polygon have the same tags
     * 
     * @param members a List of the members
     * @return true if all outer members have the same tags
     */
    private boolean outersHaveSameTags(@NonNull List<RelationMember> members) {
        Map<String, String> tags = null;
        for (RelationMember member : members) {
            if (Tags.ROLE_OUTER.equals(member.getRole())) {
                if (tags == null) {
                    tags = member.downloaded() ? member.getElement().getTags() : null;
                } else if (member.downloaded() && !tags.equals(member.getElement().getTags())) {
                    return false;
                }
            }
        }
        return tags != null;
    }

    /**
     * Check if any of the outer members has some specific tags
     * 
     * @param members a List of the members
     * @return true if at least one of the members has some of these tags
     */
    private boolean outersHaveTags(@NonNull Map<String, String> tags, @NonNull List<RelationMember> members) {
        for (RelationMember member : members) {
            if (Tags.ROLE_OUTER.equals(member.getRole()) && member.downloaded()) {
                for (Entry<String, String> tag : tags.entrySet()) {
                    if (member.getElement().hasTagWithValue(tag.getKey(), tag.getValue())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Try to determine if rings are outer or inner rings
     * 
     * This simply tests one node from a ring if it is inside another ring, this can go wrong in multiple ways.
     * 
     * @param context (optional) Android Context for error messages
     * @param origMembers List of RelationMembers
     * @param force overwrite existing roles
     * 
     * @return a List of RelationMembers with inner / outer role set as far as could be determined
     */
    static List<RelationMember> setMultipolygonRoles(@Nullable Context context, @NonNull List<RelationMember> origMembers, boolean force) {
        List<RelationMember> sortedMembers = Util.sortRelationMembers(origMembers);
        List<RelationMember> other = new ArrayList<>();
        List<List<RelationMember>> rings = new ArrayList<>();
        List<List<RelationMember>> partialRings = new ArrayList<>();
        List<RelationMember> currentRing = null;
        Way previousRingSegment = null;
        for (RelationMember rm : sortedMembers) {
            if (rm.downloaded() && Way.NAME.equals(rm.getType())) {
                Way currentRingSegment = ((Way) rm.getElement());
                boolean closed = currentRingSegment.isClosed();
                if (currentRing == null) { // start ring
                    currentRing = new ArrayList<>();
                    currentRing.add(rm);
                    if (closed) {
                        rings.add(currentRing);
                        currentRing = null;
                    }
                } else if (closed) {
                    // incomplete ring
                    partialRings.add(currentRing);
                    currentRing = new ArrayList<>();
                    currentRing.add(rm);
                    rings.add(currentRing);
                    currentRing = null;
                } else {
                    final Node currentFirstNode = currentRingSegment.getFirstNode();
                    final Node currentLastNode = currentRingSegment.getLastNode();
                    final Node previousFirstNode = previousRingSegment.getFirstNode();
                    final Node previousLastNode = previousRingSegment.getLastNode();
                    if (currentFirstNode.equals(previousFirstNode) || currentFirstNode.equals(previousLastNode) || currentLastNode.equals(previousFirstNode)
                            || currentLastNode.equals(previousLastNode)) {
                        currentRing.add(rm);
                        final Way firstSegment = (Way) currentRing.get(0).getElement();
                        final Node firstFirstNode = firstSegment.getFirstNode();
                        final Node firstLastNode = firstSegment.getLastNode();
                        if (firstFirstNode.equals(currentFirstNode) || firstFirstNode.equals(currentLastNode) || firstLastNode.equals(currentFirstNode)
                                || firstLastNode.equals(currentLastNode)) {
                            rings.add(currentRing);
                            currentRing = null;
                        }
                    } else { // incomplete ring, restart
                        partialRings.add(currentRing);
                        currentRing = new ArrayList<>();
                        currentRing.add(rm);
                    }
                }
                previousRingSegment = currentRingSegment;
            } else {
                other.add(rm);
            }
        }
        final int ringCount = rings.size();
        List<List<RelationMember>> rings2 = new ArrayList<>(rings);
        for (int i = 0; i < ringCount; i++) {
            List<RelationMember> ring = rings.get(i);
            // this avoids iterating over rings we have already processed at the price of creating a shallow copy of
            // rings2
            for (List<RelationMember> ring2 : new ArrayList<>(rings2)) {
                if (ring2.equals(ring)) {
                    continue;
                }
                Node ring2Node = ((Way) ring2.get(0).getElement()).getFirstNode();
                try {
                    if (isInside(getNodesForRing(ring).toArray(new Node[ring.size()]), ring2Node)) {
                        setRole(context, Tags.ROLE_OUTER, ring, force);
                        setRole(context, Tags.ROLE_INNER, ring2, force);
                        rings2.remove(ring);
                        rings2.remove(ring2);
                    } else {
                        Node ringNode = ((Way) ring.get(0).getElement()).getFirstNode();
                        if (isInside(getNodesForRing(ring2).toArray(new Node[ring2.size()]), ringNode)) {
                            setRole(context, Tags.ROLE_INNER, ring, force);
                            setRole(context, Tags.ROLE_OUTER, ring2, force);
                            rings2.remove(ring);
                            rings2.remove(ring2);
                        }
                    }
                } catch (IllegalArgumentException iae) {
                    Log.e(DEBUG_TAG, "Ring not well formed");
                }
            }
            final String role = ring.get(0).getRole();
            if (role == null || "".equals(role)) {
                setRole(context, Tags.ROLE_OUTER, ring, true);
                rings2.remove(ring);
            }
        }
        List<RelationMember> result = new ArrayList<>();
        for (List<RelationMember> ring : rings) {
            result.addAll(ring);
        }
        for (List<RelationMember> ring : partialRings) {
            result.addAll(ring);
        }
        if (!partialRings.isEmpty()) {
            if (context != null) {
                Snack.toastTopWarning(context, R.string.toast_multipolygon_has_incomplete_rings);
            }
            Log.w(DEBUG_TAG, "Incomplete multi-polgon rings");
        }
        result.addAll(other);
        return result;
    }

    /**
     * Set a role value for all members in a List
     * 
     * @param context an Android Context or null
     * @param role the role value to set
     * @param members the List
     * @param force if true overwrite existing roles
     */
    private static void setRole(@Nullable Context context, @NonNull String role, @NonNull List<RelationMember> members, boolean force) {
        boolean warningShown = false;
        for (RelationMember member : members) {
            String current = member.getRole();
            if (current != null && !"".equals(current) && !role.equals(current)) {
                if (force) {
                    Log.w(DEBUG_TAG, "Changing role from " + current + " to " + role);
                } else {
                    if (context != null && !warningShown) {
                        warningShown = true;
                        Snack.toastTopWarning(context, R.string.toast_multipolygon_inconsistent_roles);
                    }
                    continue; // skip this one
                }
            }
            member.setRole(role);
        }
    }

    /**
     * Create a list of Nodes for the ring ordered correctly
     * 
     * @param ring the input ring
     * @return a List of Nodes
     */
    private static List<Node> getNodesForRing(@NonNull List<RelationMember> ring) {
        final Way firstRingWay = (Way) ring.get(0).getElement();
        if (firstRingWay.isClosed()) {
            List<Node> nodes = firstRingWay.getNodes();
            return nodes.subList(0, nodes.size() - 1); // de-dup last node
        }
        List<Node> result = new ArrayList<>();
        List<Node> nodes = new ArrayList<>(firstRingWay.getNodes());
        final int ringNodeCount = ring.size();
        for (int i = 0; i < ringNodeCount; i++) {
            List<Node> nextNodes = new ArrayList<>(((Way) ring.get((i + 1) % ringNodeCount).getElement()).getNodes());
            // order of nodes is important
            Node firstNode = nodes.get(0);
            if (firstNode.equals(nextNodes.get(0)) || firstNode.equals(nextNodes.get(nextNodes.size() - 1))) {
                Collections.reverse(nodes);
            }
            result.addAll(nodes.subList(0, nodes.size() - 1)); // de-dup last node
            nodes = nextNodes;
        }
        return result;
    }

    @Override
    protected boolean hasData() {
        return !newMembers.isEmpty();
    }

    @Override
    public void saveState(SerializableState state) {
        List<RelationMember> savedNewMembers = new ArrayList<>();
        for (RelationMember member : newMembers) {
            RelationMember toSave = new RelationMember(member);
            toSave.setElement(null);
            savedNewMembers.add(toSave);
        }
        state.putList(NEW_MEMBERS_KEY, savedNewMembers);
        state.putList(REMOVE_MEMBERS_KEY, new ArrayList<>(removeMembers.getValues()));
        if (relation != null) {
            state.putLong(RELATION_ID_KEY, relation.getOsmId());
        }
        if (presetPath != null) {
            state.putSerializable(PRESET_PATH_KEY, presetPath);
        }
    }
}
