package leewin.macs.uk.ac.hw.witalk;

import java.util.ArrayList;
import java.util.List;

import leewin.macs.uk.ac.hw.witalk.PinnedHeaderExpandableListView.OnHeaderUpdateListener;
import leewin.macs.uk.ac.hw.witalk.StickyLayout.OnGiveUpTouchEventListener;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView.LayoutParams;
import android.widget.BaseExpandableListAdapter;
import android.widget.Button;
import android.widget.ExpandableListView;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity implements
        ExpandableListView.OnChildClickListener,
        ExpandableListView.OnGroupClickListener,
        OnHeaderUpdateListener, OnGiveUpTouchEventListener,
        WifiP2pManager.ChannelListener, DeviceActionListener,
        WifiP2pManager.PeerListListener {

    public static final String TAG = "MainActivity";

    private PinnedHeaderExpandableListView expandableListView;
    private StickyLayout stickyLayout;
    private ArrayList<Group> groupList;
    private ArrayList<List<WifiP2pDevice>> childList;

    private MyexpandableListAdapter adapter;

    private ViewGroup mHeaderView;

    private GoogleProgressBar mProgressBar;

    private WifiP2pManager manager;
    private ArrayList<WifiP2pDevice> peers;
    private final IntentFilter intentFilter = new IntentFilter();
    private WifiP2pManager.Channel channel;
    private boolean retryChannel = false;
    private boolean isWifiP2pEnabled = false;
    private BroadcastReceiver receiver = null;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        expandableListView = (PinnedHeaderExpandableListView) findViewById(R.id.expandablelist);
        stickyLayout = (StickyLayout)findViewById(R.id.sticky_layout);
        mProgressBar = (GoogleProgressBar) findViewById(R.id.google_progress);

        // add necessary intent values to be matched.

        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);

        manager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        channel = manager.initialize(this, getMainLooper(), null);
        initData();

        adapter = new MyexpandableListAdapter(this);
        expandableListView.setAdapter(adapter);

        // 展开所有group
        for (int i = 0, count = expandableListView.getCount(); i < count; i++) {
            expandableListView.expandGroup(i);
        }

        expandableListView.setOnHeaderUpdateListener(this);
        expandableListView.setOnChildClickListener(this);
        expandableListView.setOnGroupClickListener(this);
        stickyLayout.setOnGiveUpTouchEventListener(this);

    }

    /** register the BroadcastReceiver with the intent values to be matched */
    @Override
    public void onResume() {
        super.onResume();
        receiver = new WiFiDirectBroadcastReceiver(manager, channel, this);
        registerReceiver(receiver, intentFilter);
    }

    @Override
    public void onPause() {
        super.onPause();
        unregisterReceiver(receiver);
    }

    /**
     * @param isWifiP2pEnabled the isWifiP2pEnabled to set
     */
    public void setIsWifiP2pEnabled(boolean isWifiP2pEnabled) {
        this.isWifiP2pEnabled = isWifiP2pEnabled;
    }

    /***
     * InitData
     */
    void initData() {
        groupList = new ArrayList<Group>();
        Group group = null;
        for (int i = 0; i < 1; i++) {
            group = new Group();
            group.setTitle("Available Peers: ");
            groupList.add(group);
        }

        childList = new ArrayList<List<WifiP2pDevice>>();
        peers = new ArrayList<WifiP2pDevice>();

    }

    @Override
    public void onPeersAvailable(WifiP2pDeviceList peerList) {
        if (mProgressBar != null && mProgressBar.isActivated()) {
            mProgressBar.setVisibility(View.GONE);
        }
        childList.clear();
        peers.clear();
        peers.addAll(peerList.getDeviceList());
        childList.add(peers);
        adapter.notifyDataSetChanged();
        if (peers.size() == 0) {
            Log.d(MainActivity.TAG, "No devices found");
            return;
        }

    }

    public void refreshPeople(View view) {
        if (!isWifiP2pEnabled) {
            Toast.makeText(MainActivity.this, R.string.p2p_off_warning,
                    Toast.LENGTH_SHORT).show();
        }
        adapter.onInitiateDiscovery();
        manager.discoverPeers(channel, new WifiP2pManager.ActionListener() {

            @Override
            public void onSuccess() {
                Toast.makeText(MainActivity.this, "Discovery Initiated",
                        Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onFailure(int reasonCode) {
                Toast.makeText(MainActivity.this, "Discovery Failed : " + reasonCode,
                        Toast.LENGTH_SHORT).show();
            }
        });
        adapter.notifyDataSetChanged();
    }

    @Override
    public void connect(WifiP2pConfig config) {
        manager.connect(channel, config, new WifiP2pManager.ActionListener() {

            @Override
            public void onSuccess() {
                // WiFiDirectBroadcastReceiver will notify us. Ignore for now.
            }

            @Override
            public void onFailure(int reason) {
                Toast.makeText(MainActivity.this, "Connect failed. Retry.",
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void disconnect() {
        manager.removeGroup(channel, new WifiP2pManager.ActionListener() {

            @Override
            public void onFailure(int reasonCode) {
                Log.d(TAG, "Disconnect failed. Reason :" + reasonCode);

            }

            @Override
            public void onSuccess() {
                Toast.makeText(MainActivity.this,"Disconnect successfully.",Toast.LENGTH_SHORT).show();
            }

        });
    }

    @Override
    public void onChannelDisconnected() {
        // we will try once more
        if (manager != null && !retryChannel) {
            Toast.makeText(this, "Channel lost. Trying again", Toast.LENGTH_LONG).show();
            adapter.resetData();
            retryChannel = true;
            manager.initialize(this, getMainLooper(), this);
        } else {
            Toast.makeText(this,
                    "Severe! Channel is probably lost permanently. Try Disable/Re-Enable P2P.",
                    Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void cancelDisconnect() {

        /*
         * A cancel abort request by user. Disconnect i.e. removeGroup if
         * already connected. Else, request WifiP2pManager to abort the ongoing
         * request
         */
        if (manager != null) {
            if (adapter.getDevice() == null
                    || adapter.getDevice().status == WifiP2pDevice.CONNECTED) {
                disconnect();
            } else if (adapter.getDevice().status == WifiP2pDevice.AVAILABLE
                    || adapter.getDevice().status == WifiP2pDevice.INVITED) {

                manager.cancelConnect(channel, new WifiP2pManager.ActionListener() {

                    @Override
                    public void onSuccess() {
                        Toast.makeText(MainActivity.this, "Aborting connection",
                                Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onFailure(int reasonCode) {
                        Toast.makeText(MainActivity.this,
                                "Connect abort request failed. Reason Code: " + reasonCode,
                                Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }

    }

    /**
     * Update UI for this device.
     *
     * @param p2pDevice WifiP2pDevice object
     */
    public void updateThisDevice(WifiP2pDevice p2pDevice) {
        WifiP2pDevice device = p2pDevice;
        peers.add(device);
        adapter.notifyDataSetChanged();
    }


    /***
     * 数据源
     *
     * @author Administrator
     *
     */

    class MyexpandableListAdapter extends BaseExpandableListAdapter {
        private Context context;
        private LayoutInflater inflater;
        private WifiP2pDevice device;

        public MyexpandableListAdapter(Context context) {
            this.context = context;
            inflater = LayoutInflater.from(context);
        }

        // 返回父列表个数
        @Override
        public int getGroupCount() {
            return groupList.size();
        }

        // 返回子列表个数
        @Override
        public int getChildrenCount(int groupPosition) {
            if (childList.size() == 0){
                return 0;
            } else return childList.get(groupPosition).size();
        }

        @Override
        public Object getGroup(int groupPosition) {

            return groupList.get(groupPosition);
        }

        @Override
        public Object getChild(int groupPosition, int childPosition) {
            return childList.get(groupPosition).get(childPosition);
        }

        @Override
        public long getGroupId(int groupPosition) {
            return groupPosition;
        }

        @Override
        public long getChildId(int groupPosition, int childPosition) {
            return childPosition;
        }

        @Override
        public boolean hasStableIds() {

            return true;
        }

        @Override
        public View getGroupView(int groupPosition, boolean isExpanded,
                                 View convertView, ViewGroup parent) {
            GroupHolder groupHolder = null;
            if (convertView == null) {
                groupHolder = new GroupHolder();
                convertView = inflater.inflate(R.layout.group, null);
                groupHolder.textView = (TextView) convertView
                        .findViewById(R.id.group);
                groupHolder.imageView = (ImageView) convertView
                        .findViewById(R.id.image);
                convertView.setTag(groupHolder);
            } else {
                groupHolder = (GroupHolder) convertView.getTag();
            }

            groupHolder.textView.setText(((Group) getGroup(groupPosition))
                    .getTitle());
            if (isExpanded)// ture is Expanded or false is not isExpanded
                groupHolder.imageView.setImageResource(R.drawable.expanded);
            else
                groupHolder.imageView.setImageResource(R.drawable.collapse);
            return convertView;
        }

        @Override
        public View getChildView(int groupPosition, int childPosition,
                                 boolean isLastChild, View convertView, ViewGroup parent) {
            ChildHolder childHolder = null;
            final String player_name = ((WifiP2pDevice) getChild(groupPosition,
                    childPosition)).deviceName;
            if (convertView == null) {
                childHolder = new ChildHolder();
                convertView = inflater.inflate(R.layout.child, null);

                childHolder.deviceName = (TextView) convertView
                        .findViewById(R.id.deviceName);
                childHolder.deviceStatus = (TextView) convertView
                        .findViewById(R.id.deviceStatus);
                childHolder.imageView = (ImageView) convertView
                        .findViewById(R.id.image);
                Button button = (Button) convertView
                        .findViewById(R.id.btn_connect);
                button.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Intent intent = new Intent(MainActivity.this,AudioActivity.class);
                        intent.putExtra("player_name", player_name);
                        startActivity(intent);
                    }
                });

                convertView.setTag(childHolder);
            } else {
                childHolder = (ChildHolder) convertView.getTag();
            }

            childHolder.deviceName.setText(((WifiP2pDevice) getChild(groupPosition,
                    childPosition)).deviceName);
            childHolder.deviceStatus.setText(getDeviceStatus(((WifiP2pDevice) getChild(groupPosition,
                    childPosition)).status));
            return convertView;
        }

        @Override
        public boolean isChildSelectable(int groupPosition, int childPosition) {
            return true;
        }

        /**
         * @return this device
         */
        public WifiP2pDevice getDevice() {
            return device;
        }

        private String getDeviceStatus(int deviceStatus) {
            Log.d(MainActivity.TAG, "Peer status :" + deviceStatus);
            switch (deviceStatus) {
                case WifiP2pDevice.AVAILABLE:
                    return "Available";
                case WifiP2pDevice.INVITED:
                    return "Invited";
                case WifiP2pDevice.CONNECTED:
                    return "Connected";
                case WifiP2pDevice.FAILED:
                    return "Failed";
                case WifiP2pDevice.UNAVAILABLE:
                    return "Unavailable";
                default:
                    return "Unknown";
            }
        }

        public void resetData() {
            childList.clear();
            initData();
            notifyDataSetChanged();
        }

        public void onInitiateDiscovery() {
            if (mProgressBar != null && mProgressBar.isActivated()) {
                mProgressBar.setVisibility(View.GONE);
            }
            mProgressBar.setIndeterminateDrawable(new FoldingCirclesDrawable.Builder(MainActivity.this).build());
        }

    }

    class GroupHolder {
        TextView textView;
        ImageView imageView;
    }

    class ChildHolder {
        TextView deviceName;
        TextView deviceStatus;
        ImageView imageView;
    }


    @Override
    public boolean onGroupClick(final ExpandableListView parent, final View v,
                                int groupPosition, final long id) {

        return false;
    }

    @Override
    public boolean onChildClick(ExpandableListView parent, View v,
                                int groupPosition, int childPosition, long id) {
        Toast.makeText(MainActivity.this,
                childList.get(groupPosition).get(childPosition).deviceName, Toast.LENGTH_SHORT)
                .show();

        return false;
    }

    @Override
    public View getPinnedHeader() {
        if (mHeaderView == null) {
            mHeaderView = (ViewGroup) getLayoutInflater().inflate(R.layout.group, null);
            mHeaderView.setLayoutParams(new LayoutParams(
                    LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        }
        return mHeaderView;
    }

    @Override
    public void updatePinnedHeader(int firstVisibleGroupPos) {
        Group firstVisibleGroup = (Group) adapter.getGroup(firstVisibleGroupPos);
        TextView textView = (TextView) getPinnedHeader().findViewById(R.id.group);
        textView.setText(firstVisibleGroup.getTitle());
    }

    @Override
    public boolean giveUpTouchEvent(MotionEvent event) {
        if (expandableListView.getFirstVisiblePosition() == 0) {
            View view = expandableListView.getChildAt(0);
            if (view != null && view.getTop() >= 0) {
                return true;
            }
        }
        return false;
    }

}