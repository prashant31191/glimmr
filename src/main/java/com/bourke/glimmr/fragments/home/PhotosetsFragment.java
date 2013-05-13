package com.bourke.glimmrpro.fragments.home;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import com.actionbarsherlock.app.SherlockDialogFragment;
import com.androidquery.AQuery;
import com.bourke.glimmrpro.R;
import com.bourke.glimmrpro.activities.BaseActivity;
import com.bourke.glimmrpro.activities.PhotosetViewerActivity;
import com.bourke.glimmrpro.common.Constants;
import com.bourke.glimmrpro.common.TextUtils;
import com.bourke.glimmrpro.event.Events.IPhotosetsReadyListener;
import com.bourke.glimmrpro.event.Events.PhotosetItemLongClickDialogListener;
import com.bourke.glimmrpro.fragments.base.BaseFragment;
import com.bourke.glimmrpro.fragments.photoset.AddToPhotosetDialogFragment;
import com.bourke.glimmrpro.tasks.LoadPhotosetsTask;
import com.googlecode.flickrjandroid.people.User;
import com.googlecode.flickrjandroid.photosets.Photoset;
import com.googlecode.flickrjandroid.photosets.Photosets;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class PhotosetsFragment extends BaseFragment
        implements IPhotosetsReadyListener,
                   PhotosetItemLongClickDialogListener {

    private static final String TAG = "Glimmr/PhotosetsFragment";

    private LoadPhotosetsTask mTask;
    private final List<Photoset> mPhotosets = new ArrayList<Photoset>();

    private View mLayoutNoConnection;
    private AdapterView mAdapterView;  /* Will either be a GridView or ListView
                                          depending on screen size */
    private SetListAdapter mAdapter;

    private User mUserToView;

    public static PhotosetsFragment newInstance(User userToView) {
        PhotosetsFragment f = new PhotosetsFragment();
        f.mUserToView = userToView;
        return f;
    }

    @Override
    protected void startTask() {
        super.startTask();
        mTask = new LoadPhotosetsTask(this, mUserToView);
        mTask.execute(mOAuth);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        /* If sw600dp-land, use gridview layout, otherwise use list style
         * layout */
        if (getResources().getBoolean(R.bool.sw600dp_land)) {
            mLayout = (RelativeLayout) inflater.inflate(
                    R.layout.gridview_fragment, container, false);
            mAdapterView = (GridView) mLayout.findViewById(R.id.gridview);
        } else {
            mLayout = (RelativeLayout) inflater.inflate(
                    R.layout.listview_fragment, container, false);
            mAdapterView = (ListView) mLayout.findViewById(R.id.list);
        }

        mLayoutNoConnection =
            (LinearLayout) mLayout.findViewById(R.id.no_connection_layout);
        mAq = new AQuery(mActivity, mLayout);

        initAdapterView();

        return mLayout;
    }

    private void initAdapterView() {
        mAdapter = new SetListAdapter(mActivity, R.layout.photoset_cover_item,
                (ArrayList<Photoset>)mPhotosets);
        mAdapterView.setOnItemClickListener(
                new AdapterView.OnItemClickListener() {
                    @Override
                    public void onItemClick(AdapterView<?> parent, View view,
                            int position, long id) {
                        PhotosetViewerActivity.startPhotosetViewer(mActivity,
                            mPhotosets.get(position).getId());
                    }
                });
        mAdapterView.setOnItemLongClickListener(
                new AdapterView.OnItemLongClickListener() {
                    @Override
                    public boolean onItemLongClick(AdapterView<?> parent,
                            View v, int position, long id) {
                        if (position < mPhotosets.size()) {
                            SherlockDialogFragment d =
                                PhotosetItemLongClickDialog.newInstance(
                                    mActivity, PhotosetsFragment.this,
                                    mPhotosets.get(position));
                            d.show(mActivity.getSupportFragmentManager(),
                                "photoset_item_long_click");
                        } else {
                            Log.e(getLogTag(), String.format(
                                    "Cannot call showGridItemContextMenu, " +
                                    "mPhotosets.size(%d) != position:(%d)",
                                    mPhotosets.size(), position));
                        }
                    /* True indicates we're finished with event and triggers
                     * haptic feedback */
                    return true;
                }
            });

        mAdapterView.setAdapter(mAdapter);
    }

    @Override
    public void onPhotosetsReady(Photosets photoSets) {
        if (Constants.DEBUG) Log.d(getLogTag(), "onPhotosetListReady");
        mActivity.setSupportProgressBarIndeterminateVisibility(Boolean.FALSE);
        if (photoSets == null) {
            mLayoutNoConnection.setVisibility(View.VISIBLE);
            mAdapterView.setVisibility(View.GONE);
        } else {
            mAdapterView.setVisibility(View.VISIBLE);
            mLayoutNoConnection.setVisibility(View.GONE);
            mPhotosets.clear();
            mPhotosets.addAll(photoSets.getPhotosets());
            mAdapter.notifyDataSetChanged();
        }
    }

    /**
     * Override as no pagination
     */
    @Override
    protected void refresh() {
        mPhotosets.clear();
        mAdapter.notifyDataSetChanged();
        startTask();
    }

    @Override
    public void onLongClickDialogSelection(Photoset photoset, int which) {
        Log.d(TAG, "onLongClickDialogSelection()");
        FragmentTransaction ft =
            mActivity.getSupportFragmentManager().beginTransaction();
        ft.setCustomAnimations(android.R.anim.fade_in,
                android.R.anim.fade_out);
        if (photoset != null) {
            Fragment prev = mActivity.getSupportFragmentManager()
                .findFragmentByTag(AddToPhotosetDialogFragment.TAG);
            if (prev != null) {
                ft.remove(prev);
            }
            ft.addToBackStack(null);

            SherlockDialogFragment newFragment =
                AddToPhotosetDialogFragment.newInstance(photoset);
            newFragment.show(ft, AddToPhotosetDialogFragment.TAG);
        } else {
            Log.e(TAG, "onLongClickDialogSelection: photoset is null");
        }
    }

    @Override
    protected String getLogTag() {
        return TAG;
    }

    class SetListAdapter extends ArrayAdapter<Photoset> {

        public SetListAdapter(BaseActivity activity, int textViewResourceId,
                ArrayList<Photoset> objects) {
            super(activity, textViewResourceId, objects);
        }

        @Override
        public View getView(final int position, View convertView,
                ViewGroup parent) {
            ViewHolder holder;

            if (convertView == null) {
                convertView = mActivity.getLayoutInflater().inflate(
                        R.layout.photoset_cover_item, null);
                holder = new ViewHolder();
                holder.imageItem = (ImageView)
                    convertView.findViewById(R.id.imageItem);
                holder.imageOverlay = (LinearLayout)
                    convertView.findViewById(R.id.imageOverlay);
                holder.photosetNameText = (TextView)
                    convertView.findViewById(R.id.photosetNameText);
                holder.numImagesInSetText = (TextView)
                    convertView.findViewById(R.id.numImagesInSetText);
                holder.numImagesIcon = (ImageView)
                    convertView.findViewById(R.id.numImagesIcon);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            mTextUtils.setFont(holder.photosetNameText,
                    TextUtils.FONT_ROBOTOBOLD);

            final Photoset photoset = mPhotosets.get(position);

            /* Don't load image if flinging past it */
            if (mAq.shouldDelay(position, convertView, parent,
                        photoset.getPrimaryPhoto().getMediumUrl())) {
                Bitmap placeholder = mAq.getCachedImage(R.drawable.blank);
                mAq.id(holder.imageItem).image(placeholder);
                holder.imageOverlay.setVisibility(View.INVISIBLE);
            } else {
                /* Fetch the set cover photo */
                holder.imageOverlay.setVisibility(View.VISIBLE);
                mAq.id(holder.imageItem).image(
                        photoset.getPrimaryPhoto().getMediumUrl(),
                        Constants.USE_MEMORY_CACHE, Constants.USE_FILE_CACHE,
                        0, 0, null, AQuery.FADE_IN_NETWORK);

                holder.photosetNameText.setText(
                        photoset.getTitle().toUpperCase(Locale.getDefault()));
                holder.numImagesInSetText.setText(
                        ""+photoset.getPhotoCount());
            }
            return convertView;
        }

        class ViewHolder {
            ImageView imageItem;
            ImageView numImagesIcon;
            TextView photosetNameText;
            TextView numImagesInSetText;
            LinearLayout imageOverlay;
        }
    }

    static class PhotosetItemLongClickDialog extends SherlockDialogFragment {
        private PhotosetItemLongClickDialogListener mListener;
        private Context mContext;
        private Photoset mPhotoset;

        public static PhotosetItemLongClickDialog newInstance(Context context,
                PhotosetItemLongClickDialogListener listener,
                Photoset photoset) {
            PhotosetItemLongClickDialog d = new PhotosetItemLongClickDialog();
            d.mListener = listener;
            d.mContext = context;
            d.mPhotoset = photoset;
            return d;
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
            builder.setItems(R.array.photoset_item_long_click_dialog_items,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        mListener.onLongClickDialogSelection(mPhotoset, which);
                    }
                });
            return builder.create();
        }
    }

}
