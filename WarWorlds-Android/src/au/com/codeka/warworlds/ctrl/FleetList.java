package au.com.codeka.warworlds.ctrl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import au.com.codeka.warworlds.R;
import au.com.codeka.warworlds.game.FleetMoveDialog;
import au.com.codeka.warworlds.game.FleetSplitDialog;
import au.com.codeka.warworlds.game.UniverseElementActivity;
import au.com.codeka.warworlds.model.Colony;
import au.com.codeka.warworlds.model.DesignManager;
import au.com.codeka.warworlds.model.Fleet;
import au.com.codeka.warworlds.model.ImageManager;
import au.com.codeka.warworlds.model.Planet;
import au.com.codeka.warworlds.model.ShipDesign;
import au.com.codeka.warworlds.model.ShipDesignManager;
import au.com.codeka.warworlds.model.Star;
import au.com.codeka.warworlds.model.StarImageManager;

/**
 * This control displays a list of fleets along with controls you can use to manage them (split
 * them, move them around, etc).
 */
public class FleetList extends FrameLayout {
    private FleetListAdapter mFleetListAdapter;
    private Fleet mSelectedFleet;
    private List<Fleet> mFleets;
    private Map<String, Star> mStars;
    private UniverseElementActivity mActivity;
    private boolean mIsInitialized;

    public FleetList(Context context, AttributeSet attrs) {
        super(context, attrs);

        View child = inflate(context, R.layout.fleet_list_ctrl, null);
        this.addView(child);
    }

    public void refresh(UniverseElementActivity activity, List<Fleet> fleets,
            Map<String, Star> stars) {
        mActivity = activity;
        mFleets = fleets;
        mStars = stars;

        initialize();

        // if we had a fleet selected, make sure we still have the same
        // fleet selected after we refresh
        if (mSelectedFleet != null) {
            Fleet selectedFleet = mSelectedFleet;
            mSelectedFleet = null;

            for (Fleet f : mFleets) {
                if (f.getKey().equals(selectedFleet.getKey())) {
                    mSelectedFleet = f;
                    break;
                }
            }
        }

        mFleetListAdapter.setFleets(stars, fleets);
    }

    private void initialize() {
        if (mIsInitialized) {
            return;
        }
        mIsInitialized = true;

        mFleetListAdapter = new FleetListAdapter();
        final ListView fleetList = (ListView) findViewById(R.id.ship_list);
        fleetList.setAdapter(mFleetListAdapter);

        // make sure we're aware of any changes to the designs
        ShipDesignManager.getInstance().addDesignsChangedListener(new DesignManager.DesignsChangedListener() {
            @Override
            public void onDesignsChanged() {
                if (mFleets != null && mFleetListAdapter != null) {
                    mFleetListAdapter.setFleets(mStars, mFleets);
                }
            }
        });

        mActivity.addUpdatedListener(new UniverseElementActivity.OnUpdatedListener() {
            @Override
            public void onStarUpdated(Star star, Planet selectedPlanet, Colony colony) {
                refresh(mActivity, star.getFleets(), mStars);
            }
            @Override
            public void onSectorUpdated() {
            }
        });

        fleetList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view,
                    int position, long id) {
                FleetListAdapter.ItemEntry entry =
                        (FleetListAdapter.ItemEntry) mFleetListAdapter.getItem(position);
                if (entry.type == FleetListAdapter.FLEET_ITEM_TYPE) {
                    mSelectedFleet = (Fleet) entry.value;
                    mFleetListAdapter.notifyDataSetChanged();
                }
            }
        });

        final Button splitBtn = (Button) findViewById(R.id.split_btn);
        splitBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Bundle args = new Bundle();
                Star star = mStars.get(mSelectedFleet.getStarKey());
                args.putLong("au.com.codeka.warworlds.SectorX", star.getSectorX());
                args.putLong("au.com.codeka.warworlds.SectorY", star.getSectorY());
                args.putInt("au.com.codeka.warworlds.OffsetX", star.getOffsetX());
                args.putInt("au.com.codeka.warworlds.OffsetY", star.getOffsetY());
                args.putString("au.com.codeka.warworlds.StarKey", mSelectedFleet.getStarKey());
                args.putString("au.com.codeka.warworlds.FleetKey", mSelectedFleet.getKey());
                mActivity.showDialog(FleetSplitDialog.ID, args);
            }
        });

        final Button moveBtn = (Button) findViewById(R.id.move_btn);
        moveBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Bundle args = new Bundle();
                args.putString("au.com.codeka.warworlds.StarKey", mSelectedFleet.getStarKey());
                args.putString("au.com.codeka.warworlds.FleetKey", mSelectedFleet.getKey());
                mActivity.showDialog(FleetMoveDialog.ID, args);
            }
        });
    }

    /**
     * Populates a solarsystem_fleet_row.xml view with details from the given fleet.
     */
    public static void populateFleetRow(View view, Fleet fleet) {
        ImageView icon = (ImageView) view.findViewById(R.id.ship_icon);
        TextView row1 = (TextView) view.findViewById(R.id.ship_row1);
        TextView row2 = (TextView) view.findViewById(R.id.ship_row2);
        TextView row3 = (TextView) view.findViewById(R.id.ship_row3);

        ShipDesignManager dm = ShipDesignManager.getInstance();
        ShipDesign design = dm.getDesign(fleet.getDesignName());

        Bitmap bm = dm.getDesignIcon(design);
        if (bm != null) {
            icon.setImageBitmap(bm);
        } else {
            icon.setImageBitmap(null);
        }

        row1.setText(design.getName());
        row2.setText(String.format("%d", fleet.getNumShips()));
        row2.setGravity(Gravity.RIGHT);
        row3.setVisibility(View.GONE);
    }

    /**
     * This adapter is used to populate the list of ship fleets that the current colony has.
     */
    private class FleetListAdapter extends BaseAdapter {
        private ArrayList<Fleet> mFleets;
        private Map<String, Star> mStars;
        private ArrayList<ItemEntry> mEntries;

        private static final int STAR_ITEM_TYPE = 0;
        private static final int FLEET_ITEM_TYPE = 1;

        public FleetListAdapter() {
            // whenever a new star bitmap is generated, redraw the screen
            StarImageManager.getInstance().addBitmapGeneratedListener(
                    new ImageManager.BitmapGeneratedListener() {
                @Override
                public void onBitmapGenerated(String key, Bitmap bmp) {
                    notifyDataSetChanged();
                }
            });
        }

        /**
         * Sets the list of fleets that we'll be displaying.
         */
        public void setFleets(Map<String, Star> stars, List<Fleet> fleets) {
            mFleets = new ArrayList<Fleet>(fleets);
            mStars = stars;

            Collections.sort(mFleets, new Comparator<Fleet>() {
                @Override
                public int compare(Fleet lhs, Fleet rhs) {
                    // sort by star, then by design, then by count
                    if (!lhs.getStarKey().equals(rhs.getStarKey())) {
                        Star lhsStar = mStars.get(lhs.getStarKey());
                        Star rhsStar = mStars.get(rhs.getStarKey());
                        return lhsStar.getName().compareTo(rhsStar.getName());
                    } else if (!lhs.getDesignName().equals(rhs.getDesignName())) {
                        return lhs.getDesignName().compareTo(rhs.getDesignName());
                    } else {
                        return lhs.getNumShips() - rhs.getNumShips();
                    }
                }
            });

            mEntries = new ArrayList<ItemEntry>();
            String lastStarKey = "";
            for (Fleet f : mFleets) {
                if (!f.getStarKey().equals(lastStarKey)) {
                    mEntries.add(new ItemEntry(STAR_ITEM_TYPE, mStars.get(f.getStarKey())));
                    lastStarKey = f.getStarKey();
                }
                mEntries.add(new ItemEntry(FLEET_ITEM_TYPE, f));
            }

            notifyDataSetChanged();
        }

        /**
         * We have two types of items, the star and the actual fleet.
         */
        @Override
        public int getViewTypeCount() {
            return 2;
        }

        @Override
        public int getItemViewType(int position) {
            if (mEntries == null)
                return 0;

            return mEntries.get(position).type;
        }

        @Override
        public int getCount() {
            if (mEntries == null)
                return 0;
            return mEntries.size();
        }

        @Override
        public Object getItem(int position) {
            if (mEntries == null)
                return null;
            return mEntries.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ItemEntry entry = mEntries.get(position);
            View view = convertView;

            if (view == null) {
                LayoutInflater inflater = (LayoutInflater) mActivity.getSystemService
                        (Context.LAYOUT_INFLATER_SERVICE);
                if (entry.type == STAR_ITEM_TYPE) {
                    view = inflater.inflate(R.layout.fleet_list_star_row, null);
                } else {
                    view = inflater.inflate(R.layout.fleet_list_row, null);
                }
            }

            if (entry.type == STAR_ITEM_TYPE) {
                Star star = (Star) entry.value;
                ImageView icon = (ImageView) view.findViewById(R.id.star_icon);
                TextView name = (TextView) view.findViewById(R.id.star_name);

                int imageSize = (int)(star.getSize() * star.getStarType().getImageScale() * 2);
                if (entry.bitmap == null) {
                    entry.bitmap = StarImageManager.getInstance().getBitmap(mActivity, star, imageSize);
                }
                if (entry.bitmap != null) {
                    icon.setImageBitmap(entry.bitmap);
                }

                name.setText(star.getName());
            } else {
                Fleet fleet = (Fleet) entry.value;
                populateFleetRow(view, fleet);

                if (mSelectedFleet != null && mSelectedFleet.getKey().equals(fleet.getKey())) {
                    view.setBackgroundColor(0xff0c6476);
                } else {
                    view.setBackgroundColor(0xff000000);
                }
            }

            return view;
        }

        public class ItemEntry {
            public int type;
            public Object value;
            public Bitmap bitmap;

            public ItemEntry(int type, Object value) {
                this.type = type;
                this.value = value;
                this.bitmap = null;
            }
        }
    }
}