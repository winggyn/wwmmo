package au.com.codeka.warworlds.client.game.fleets;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.squareup.picasso.Picasso;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.annotation.Nullable;

import au.com.codeka.warworlds.client.R;
import au.com.codeka.warworlds.client.ctrl.ExpandableStarListAdapter;
import au.com.codeka.warworlds.client.util.NumberFormatter;
import au.com.codeka.warworlds.client.game.world.EmpireManager;
import au.com.codeka.warworlds.client.game.world.ImageHelper;
import au.com.codeka.warworlds.client.game.world.StarCollection;
import au.com.codeka.warworlds.common.proto.Design;
import au.com.codeka.warworlds.common.proto.Empire;
import au.com.codeka.warworlds.common.proto.Fleet;
import au.com.codeka.warworlds.common.proto.Star;
import au.com.codeka.warworlds.common.sim.DesignHelper;

/**
 * Represents an {@link ExpandableStarListAdapter} which shows a list of fleets under each star.
 */
public class FleetExpandableStarListAdapter extends ExpandableStarListAdapter<Fleet> {
  private final LayoutInflater inflater;
  private final long myEmpireId;

  private boolean multiSelect;
  @Nullable private Long selectedFleetId;

  /** A list of fleets that are currently selected, contains more than one in multi-select mode. */
  private final Set<Long> selectedFleetIds = new HashSet<>();

  /** A list of fleets which are currently disabled (you can't select them). */
  private final Set<Long> disabledFleetIds = new HashSet<>();

  public FleetExpandableStarListAdapter(LayoutInflater inflater, StarCollection stars) {
    super(stars);
    this.inflater = inflater;
    myEmpireId = Preconditions.checkNotNull(EmpireManager.i.getMyEmpire()).id;
  }

  @Nullable
  public Long getSelectedFleetId() {
    return selectedFleetId;
  }

  public Collection<Long> getSelectedFleetIds() {
    return selectedFleetIds;
  }

  /** Disable all fleets that match the given predicate. */
  public void disableFleets(Predicate<Fleet> predicate) {
    disabledFleetIds.clear();
    for (int i = 0; i < getGroupCount(); i++) {
      Star star = getStar(i);
      for (Fleet fleet : star.fleets) {
        if (fleet.empire_id != null && fleet.empire_id.equals(myEmpireId)) {
          if (predicate.apply(fleet)) {
            disabledFleetIds.add(fleet.id);
          }
        }
      }
    }
  }

  /** After disabling fleets, this will re-enable all fleets again. */
  public void enableAllFleets() {
    disabledFleetIds.clear();
  }

  /**
   * Sets whether or not we'll allow multi-select.
   *
   * <p>When in multi-select mode, {@link #getSelectedFleetId()} will return the first
   * selected fleet, and {@link #getSelectedFleetIds()} will return all of them. When not in
   * multi-select mode, {@link #getSelectedFleetIds()} will return a list of the one selected fleet.
   */
  public void setMultiSelect(boolean multiSelect) {
    this.multiSelect = multiSelect;
    if (!multiSelect) {
      selectedFleetIds.clear();
      if (selectedFleetId != null) {
        selectedFleetIds.add(selectedFleetId);
      }
    }
  }

  public void setSelectedFleetId(@Nullable Long starId, @Nullable Long fleetId) {
    selectedFleetId = fleetId;
    if (multiSelect) {
      if (!selectedFleetIds.contains(fleetId)) {
        selectedFleetIds.add(fleetId);
      }
    } else {
      selectedFleetIds.clear();
      selectedFleetIds.add(fleetId);
    }
  }

  public void onItemClick(int groupPosition, int childPosition) {
    Fleet fleet = getChild(groupPosition, childPosition);
    if (multiSelect) {
      if (!selectedFleetIds.remove(fleet.id)) {
        selectedFleetIds.add(fleet.id);
      }
    } else {
      selectedFleetId = fleet.id;
      selectedFleetIds.clear();
      selectedFleetIds.add(fleet.id);
    }
    notifyDataSetChanged();
  }

  @Override
  public int getNumChildren(Star star) {
    int numFleets = 0;
    for (int i = 0; i < star.fleets.size(); i++) {
      Long empireID = star.fleets.get(i).empire_id;
      if (empireID != null && empireID == myEmpireId) {
        numFleets++;
      }
    }

    return numFleets;
  }

  @Override
  public Fleet getChild(Star star, int index) {
    int fleetIndex = 0;
    for (int i = 0; i < star.fleets.size(); i++) {
      Long empireID = star.fleets.get(i).empire_id;
      if (empireID != null && empireID == myEmpireId) {
        if (fleetIndex == index) {
          return star.fleets.get(i);
        }
        fleetIndex++;
      }
    }

    // Shouldn't get here...
    return null;
  }

  @Override
  protected long getChildId(Star star, int childPosition) {
    return star.fleets.get(childPosition).id;
  }

  @Override
  public View getStarView(Star star, View convertView, ViewGroup parent) {
    View view = convertView;
    if (view == null) {
      view = inflater.inflate(R.layout.fleets_star_row, parent, false);
    }

    ImageView starIcon = (ImageView) view.findViewById(R.id.star_icon);
    TextView starName = (TextView) view.findViewById(R.id.star_name);
    TextView starType = (TextView) view.findViewById(R.id.star_type);
    TextView fightersTotal = (TextView) view.findViewById(R.id.fighters_total);
    TextView nonFightersTotal = (TextView) view.findViewById(R.id.nonfighters_total);

    if (star == null) {
      starIcon.setImageBitmap(null);
      starName.setText("");
      starType.setText("");
      fightersTotal.setText("...");
      nonFightersTotal.setText("...");
    } else {
      Picasso.with(view.getContext())
          .load(ImageHelper.getStarImageUrl(view.getContext(), star, 16, 16))
          .into(starIcon);

      starName.setText(star.name);
      starType.setText(star.classification.toString());

      Empire myEmpire = Preconditions.checkNotNull(EmpireManager.i.getMyEmpire());
      float numFighters = 0.0f;
      float numNonFighters = 0.0f;

      for (Fleet fleet : star.fleets) {
        if (fleet.empire_id == null || !fleet.empire_id.equals(myEmpire.id)) {
          continue;
        }

        if (fleet.design_type == Design.DesignType.FIGHTER) {
          numFighters += fleet.num_ships;
        } else {
          numNonFighters += fleet.num_ships;
        }
      }

      fightersTotal
          .setText(String.format(Locale.ENGLISH, "%s", NumberFormatter.format(numFighters)));
      nonFightersTotal
          .setText(String.format(Locale.ENGLISH, "%s", NumberFormatter.format(numNonFighters)));
    }
    return view;
  }

  @Override
  public View getChildView(Star star, int index, View convertView, ViewGroup parent) {
    View view = convertView;
    if (view == null) {
      view = inflater.inflate(R.layout.fleets_fleet_row, parent, false);
    }

    if (star != null) {
      Fleet fleet = getChild(star, index);
      if (fleet != null) {
        FleetListHelper.populateFleetRow(
            (ViewGroup) view,star, fleet, DesignHelper.getDesign(fleet.design_type));

        if (disabledFleetIds.contains(fleet.id)) {
          view.setBackgroundResource(R.color.list_item_disabled);
        } else if (selectedFleetIds.contains(fleet.id)) {
          view.setBackgroundResource(R.color.list_item_selected);
        } else {
          view.setBackgroundResource(android.R.color.transparent);
        }
      }
    }

    return view;
  }
}