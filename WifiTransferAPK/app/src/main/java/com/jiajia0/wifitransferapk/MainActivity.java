package com.jiajia0.wifitransferapk;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.widget.Toast;

import com.hwangjr.rxbus.RxBus;
import com.hwangjr.rxbus.annotation.Subscribe;
import com.hwangjr.rxbus.annotation.Tag;
import com.hwangjr.rxbus.thread.EventThread;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.Unbinder;
import timber.log.Timber;

public class MainActivity extends AppCompatActivity {
    Unbinder mUnbinder;//视图绑定
    AppShelfAdapter mAppShelfAdapter;//适配器

    @BindView(R.id.toolbar)
    Toolbar mToolbar;
    @BindView(R.id.fab)
    FloatingActionButton mFloatingActionButton;//开启Wifi
    @BindView(R.id.recyclerview)
    RecyclerView mRecyclerView;//展示APP的信息
    @BindView(R.id.content_main)
    SwipeRefreshLayout mSwipeRefreshLayout;//刷新

    List<InfoModel> mApps = new ArrayList<>();// 用来保存App的信息
    WifiAnimatorListener mWifiAnimatorListener = new WifiAnimatorListener(this);// Wifi监听动画
    APKManager mAPKManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mUnbinder = ButterKnife.bind(this); // 必须在setSupportActionBar之前
        setSupportActionBar(mToolbar);
        init();
        initView();
        initRecyclerView();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.item_menu, menu);
        return true;
    }

    /**
     * 初始化
     */
    private void init() {
        RxBus.get().register(this);
        Timber.plant(new Timber.DebugTree());
        mAPKManager = new APKManager(this);
    }

    /**
     * 初始化视图
     */
    private void initView() {
        // 设置Toolbar
        mToolbar.setOnMenuItemClickListener(new Toolbar.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                switch (item.getItemId()) {
                    case R.id.delete:
                        if (!mApps.isEmpty()) {
                            showDialog();
                        } else {
                            Toast.makeText(MainActivity.this, "暂无可删内容", Toast.LENGTH_SHORT).show();
                        }
                        break;
                }
                return false;
            }
        });


        // 开启Wifi
        mFloatingActionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ObjectAnimator objectAnimator = ObjectAnimator.ofFloat(mFloatingActionButton,"translationY",0,mFloatingActionButton.getHeight() * 2);
                objectAnimator.setDuration(200L);
                // 设置动画速度越来越快
                objectAnimator.setInterpolator(new AccelerateInterpolator());
                objectAnimator.addListener(mWifiAnimatorListener);
                objectAnimator.start();
            }
        });

        mSwipeRefreshLayout.setColorSchemeResources(
                android.R.color.holo_blue_bright,
                android.R.color.holo_green_light,
                android.R.color.holo_orange_light,
                android.R.color.holo_red_light
        );
        mSwipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                RxBus.get().post(Constants.RxBusEventType.LOAD_APK_LIST, 0);
            }
        });
    }

    private void initRecyclerView() {
        mAppShelfAdapter = new AppShelfAdapter(mApps, mAPKManager);
        mRecyclerView.setHasFixedSize(true);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        mRecyclerView.setAdapter(mAppShelfAdapter);
        mRecyclerView.addItemDecoration(new ItemButtomDecoration(this,10));
        RxBus.get().post(Constants.RxBusEventType.LOAD_APK_LIST, 0);
    }


    /**
     * @param type APK列表刷新
     */
    @Subscribe(thread = EventThread.IO, tags = {@Tag(Constants.RxBusEventType.LOAD_APK_LIST)})
    public void refreshAPKList(Integer type) {
        final List<InfoModel> listArr = new ArrayList<>();
        File dir = Constants.DIR;
        Timber.d("leafage" + "-refreshAPKList-" + Constants.DIR);

        if (!dir.exists()) {
            Timber.d("leafage-" + "目录不存在！");
        }

        // 获取所有的APK文件
        if (dir.exists() && dir.isDirectory()) {
            File[] fileNames = dir.listFiles();
            Timber.d("leafage" + fileNames);
            if (fileNames != null) {
                for (File fileName : fileNames) {
                    Timber.d("leafage" + fileName.getName());
                    listArr.add(mAPKManager.getAPKInfo(fileName));
                }
            }
        }

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mSwipeRefreshLayout.setRefreshing(false);
                mApps.clear();
                mApps.addAll(listArr);
                Timber.d("leafage-" + listArr.size());
                mAppShelfAdapter.notifyDataSetChanged();
            }
        });

    }

    // 显示是否删除APK
    private void showDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("温馨提示");
        builder.setMessage("确定全部删除么？");
        builder.setPositiveButton("确定", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                Timber.d("leafage" + "删除全部！");
                mAPKManager.deleteAll();
            }
        });
        builder.setNegativeButton("取消", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                Timber.d("leafage" + "取消删除!");
            }
        });
        builder.show();
    }

    // 对话框取消之后将Wifi标志升起
    @Subscribe( tags = {@Tag( Constants.RxBusEventType.POPUP_MENU_DIALOG_SHOW_DISMISS )} )
    public void onPopupMenuDialogDismiss(Integer type) {
        if ( type == Constants.RxBusEventType.MSG_DIALOG_DISMISS ) {
            WebService.stop(this);
            ObjectAnimator objectAnimator = ObjectAnimator.ofFloat(mFloatingActionButton, "translationY", mFloatingActionButton.getHeight() * 2, 0).setDuration(200L);
            objectAnimator.setInterpolator(new AccelerateInterpolator());
            objectAnimator.start();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        WebService.stop(this);
        if (mUnbinder != null) {
            mUnbinder.unbind();
        }
        RxBus.get().unregister(this);
    }

}
