package com.dkanada.openapk.activities;

import android.app.Activity;
import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.MenuItemCompat;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SearchView;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.dkanada.openapk.App;
import com.dkanada.openapk.R;
import com.dkanada.openapk.adapters.AppAdapter;
import com.dkanada.openapk.utils.AppPreferences;
import com.dkanada.openapk.utils.OtherUtils;
import com.dkanada.openapk.utils.DialogUtils;
import com.mikepenz.google_material_typeface_library.GoogleMaterial;
import com.mikepenz.materialdrawer.AccountHeader;
import com.mikepenz.materialdrawer.AccountHeaderBuilder;
import com.mikepenz.materialdrawer.Drawer;
import com.mikepenz.materialdrawer.DrawerBuilder;
import com.mikepenz.materialdrawer.holder.BadgeStyle;
import com.mikepenz.materialdrawer.model.DividerDrawerItem;
import com.mikepenz.materialdrawer.model.PrimaryDrawerItem;
import com.mikepenz.materialdrawer.model.interfaces.IDrawerItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class MainActivity extends ThemeActivity implements SearchView.OnQueryTextListener {
    private static final int MY_PERMISSIONS_REQUEST_WRITE_READ = 1;

    // settings
    private AppPreferences appPreferences;
    private PackageManager packageManager;
    private AppAdapter appInstalledAdapter;
    private AppAdapter appSystemAdapter;
    private AppAdapter appDisabledAdapter;
    private AppAdapter appHiddenAdapter;
    private AppAdapter appFavoriteAdapter;

    // configuration variables
    private Boolean doubleBackToExitPressedOnce = false;
    private Toolbar toolbar;
    private Activity activity;
    private Context context;
    private RecyclerView recyclerView;
    private Drawer drawer;
    private MenuItem searchItem;
    private SearchView searchView;
    private LinearLayout noResults;
    private ImageView icon;
    private SwipeRefreshLayout refresh;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        this.activity = this;
        this.context = this;

        appPreferences = App.getAppPreferences();
        packageManager = getPackageManager();

        setInitialConfiguration();
        OtherUtils.checkPermissions(context);

        recyclerView = (RecyclerView) findViewById(R.id.app_list);
        refresh = (SwipeRefreshLayout) findViewById(R.id.swipe_refresh);
        noResults = (LinearLayout) findViewById(R.id.no_results);

        icon = (ImageView) findViewById(R.id.no_results_icon);
        if (appPreferences.getTheme().equals("0")) {
            icon.setColorFilter(ContextCompat.getColor(getApplicationContext(), R.color.grey));
        }

        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(this);
        linearLayoutManager.setOrientation(LinearLayoutManager.VERTICAL);
        recyclerView.setHasFixedSize(true);
        recyclerView.setLayoutManager(linearLayoutManager);
        drawer = setNavigationDrawer(context, toolbar, recyclerView, false, appInstalledAdapter, appSystemAdapter, appDisabledAdapter, appHiddenAdapter, appFavoriteAdapter);

        // might be useful in the future
        if (!appPreferences.getInitialSetup()) {
            appPreferences.setInitialSetup(true);
        }

        refresh.setColorSchemeColors(appPreferences.getPrimaryColor());
        refresh.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                refresh.setRefreshing(true);
                new getInstalledApps().execute();
            }
        });

        refresh.post(new Runnable() {
            @Override
            public void run() {
                refresh.setRefreshing(true);
            }
        });
        new getInstalledApps().execute();
    }

    private void setInitialConfiguration() {
        toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setTitle(R.string.app_name);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            getWindow().setStatusBarColor(OtherUtils.dark(appPreferences.getPrimaryColor(), 0.8));
            toolbar.setBackgroundColor(appPreferences.getPrimaryColor());
            if (appPreferences.getNavigationColor()) {
                getWindow().setNavigationBarColor(appPreferences.getPrimaryColor());
            }
        }
    }

    class getInstalledApps extends AsyncTask<Void, String, Void> {
        private List<PackageInfo> appInstalledList = new ArrayList<>();
        private List<PackageInfo> appSystemList = new ArrayList<>();
        private List<PackageInfo> appDisabledList = new ArrayList<>();
        private List<PackageInfo> appHiddenList = new ArrayList<>();
        private List<PackageInfo> appFavoriteList = new ArrayList<>();

        @Override
        protected Void doInBackground(Void... params) {
            List<PackageInfo> packages = packageManager.getInstalledPackages(PackageManager.GET_META_DATA);
            for (PackageInfo packageInfo : packages) {
                if (!packageInfo.applicationInfo.enabled) {
                    appDisabledList.add(packageInfo);
                } else if ((packageInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) == 1) {
                    appSystemList.add(packageInfo);
                } else {
                    appInstalledList.add(packageInfo);
                }
            }

            appInstalledList = sortAdapter(appInstalledList);
            appSystemList = sortAdapter(appSystemList);
            appDisabledList = sortAdapter(appDisabledList);

            appInstalledAdapter = new AppAdapter(context, appInstalledList);
            appSystemAdapter = new AppAdapter(context, appSystemList);
            appDisabledAdapter = new AppAdapter(context, appDisabledList);

            appHiddenList = sortAdapter(appHiddenList);
            appHiddenAdapter = new AppAdapter(context, appHiddenList);

            List<String> appList = App.getAppPreferences().getFavoriteList();
            for (String app : appList) {
                try {
                    appFavoriteList.add(packageManager.getPackageInfo(app, 0));
                } catch (PackageManager.NameNotFoundException e) {
                    appList.remove(app);
                    App.getAppPreferences().setFavoriteList(appList);
                }
            }
            appFavoriteList = sortAdapter(appFavoriteList);
            appFavoriteAdapter = new AppAdapter(context, appFavoriteList);
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            switch (App.getCurrentAdapter()) {
                case 0:
                    recyclerView.swapAdapter(appInstalledAdapter, false);
                    OtherUtils.setToolbarTitle(context, getResources().getString(R.string.installed_apps));
                    break;
                case 1:
                    recyclerView.swapAdapter(appSystemAdapter, false);
                    OtherUtils.setToolbarTitle(context, getResources().getString(R.string.system_apps));
                    break;
                case 2:
                    recyclerView.swapAdapter(appDisabledAdapter, false);
                    OtherUtils.setToolbarTitle(context, getResources().getString(R.string.disabled_apps));
                    break;
                case 3:
                    recyclerView.swapAdapter(appHiddenAdapter, false);
                    OtherUtils.setToolbarTitle(context, getResources().getString(R.string.hidden_apps));
                    break;
                case 4:
                    recyclerView.swapAdapter(appFavoriteAdapter, false);
                    OtherUtils.setToolbarTitle(context, getResources().getString(R.string.favorite_apps));
                    break;
                default:
                    recyclerView.swapAdapter(appInstalledAdapter, false);
                    OtherUtils.setToolbarTitle(context, getResources().getString(R.string.installed_apps));
                    break;
            }
            drawer = setNavigationDrawer(context, toolbar, recyclerView, true, appInstalledAdapter, appSystemAdapter, appDisabledAdapter, appHiddenAdapter, appFavoriteAdapter);
            super.onPostExecute(aVoid);
            refresh.setRefreshing(false);
        }
    }

    public List<PackageInfo> sortAdapter(List<PackageInfo> list) {
        switch (appPreferences.getSortMethod()) {
            case "0":
                // compare by name
                Collections.sort(list, new Comparator<PackageInfo>() {
                    @Override
                    public int compare(PackageInfo one, PackageInfo two) {
                        return App.getPackageName(one).compareTo(App.getPackageName(two));
                    }
                });
                break;
            case "1":
                // compare by package
                Collections.sort(list, new Comparator<PackageInfo>() {
                    @Override
                    public int compare(PackageInfo one, PackageInfo two) {
                        return one.packageName.compareTo(two.packageName);
                    }
                });
                break;
            case "2":
                // compare by install date
                Collections.sort(list, new Comparator<PackageInfo>() {
                    @Override
                    public int compare(PackageInfo one, PackageInfo two) {
                        return Long.toString(two.firstInstallTime).compareTo(Long.toString(one.firstInstallTime));
                    }
                });
                break;
            case "3":
                // compare by last update
                Collections.sort(list, new Comparator<PackageInfo>() {
                    @Override
                    public int compare(PackageInfo one, PackageInfo two) {
                        return Long.toString(two.lastUpdateTime).compareTo(Long.toString(one.lastUpdateTime));
                    }
                });
                break;
            default:
                // compare by name
                Collections.sort(list, new Comparator<PackageInfo>() {
                    @Override
                    public int compare(PackageInfo one, PackageInfo two) {
                        return App.getPackageName(one).compareTo(App.getPackageName(two));
                    }
                });
                break;
        }
        return list;
    }

    public static Drawer setNavigationDrawer(final Context context, final Toolbar toolbar, final RecyclerView recyclerView, boolean badge, final AppAdapter appInstalledAdapter, final AppAdapter appSystemAdapter, final AppAdapter appDisabledAdapter, final AppAdapter appHiddenAdapter, final AppAdapter appFavoriteAdapter) {
        AppPreferences appPreferences = App.getAppPreferences();
        final Activity activity = (Activity) context;
        int header;

        // check for dark theme
        Integer badgeColor;
        BadgeStyle badgeStyle;
        if (appPreferences.getTheme().equals("0")) {
            badgeColor = ContextCompat.getColor(context, R.color.badge_light);
            badgeStyle = new BadgeStyle(badgeColor, badgeColor).withTextColor(context.getResources().getColor(R.color.text_light));
            header = R.drawable.header_day;
        } else {
            badgeColor = ContextCompat.getColor(context, R.color.badge_dark);
            badgeStyle = new BadgeStyle(badgeColor, badgeColor).withTextColor(context.getResources().getColor(R.color.text_dark));
            header = R.drawable.header_night;
        }

        AccountHeader headerResult = new AccountHeaderBuilder()
                .withActivity(activity)
                .withHeaderBackground(header)
                .build();

        DrawerBuilder drawerBuilder = new DrawerBuilder();
        drawerBuilder.withActivity(activity);
        drawerBuilder.withToolbar(toolbar);
        drawerBuilder.withAccountHeader(headerResult);
        drawerBuilder.withStatusBarColor(OtherUtils.dark(appPreferences.getPrimaryColor(), 0.8));

        if (badge) {
            String installedApps = Integer.toString(appInstalledAdapter.getItemCount());
            String systemApps = Integer.toString(appSystemAdapter.getItemCount());
            String disabledApps = Integer.toString(appDisabledAdapter.getItemCount());
            String hiddenApps = Integer.toString(appHiddenAdapter.getItemCount());
            String favoriteApps = Integer.toString(appFavoriteAdapter.getItemCount());
            drawerBuilder.addDrawerItems(
                    new PrimaryDrawerItem().withName(context.getResources().getString(R.string.installed_apps)).withIcon(GoogleMaterial.Icon.gmd_phone_android).withBadge(installedApps).withBadgeStyle(badgeStyle).withIdentifier(0),
                    new PrimaryDrawerItem().withName(context.getResources().getString(R.string.system_apps)).withIcon(GoogleMaterial.Icon.gmd_android).withBadge(systemApps).withBadgeStyle(badgeStyle).withIdentifier(1),
                    new PrimaryDrawerItem().withName(context.getResources().getString(R.string.disabled_apps)).withIcon(GoogleMaterial.Icon.gmd_remove_circle_outline).withBadge(disabledApps).withBadgeStyle(badgeStyle).withIdentifier(2),
                    new PrimaryDrawerItem().withName(context.getResources().getString(R.string.hidden_apps)).withIcon(GoogleMaterial.Icon.gmd_visibility_off).withBadge(hiddenApps).withBadgeStyle(badgeStyle).withIdentifier(3),
                    new PrimaryDrawerItem().withName(context.getResources().getString(R.string.favorite_apps)).withIcon(GoogleMaterial.Icon.gmd_star).withBadge(favoriteApps).withBadgeStyle(badgeStyle).withIdentifier(4),
                    new DividerDrawerItem(),
                    new PrimaryDrawerItem().withName(context.getResources().getString(R.string.settings)).withIcon(GoogleMaterial.Icon.gmd_settings).withSelectable(false).withIdentifier(5),
                    new PrimaryDrawerItem().withName(context.getResources().getString(R.string.about)).withIcon(GoogleMaterial.Icon.gmd_info).withSelectable(false).withIdentifier(6));
        } else {
            drawerBuilder.addDrawerItems(
                    new PrimaryDrawerItem().withName(context.getResources().getString(R.string.installed_apps)).withIcon(GoogleMaterial.Icon.gmd_phone_android).withIdentifier(0),
                    new PrimaryDrawerItem().withName(context.getResources().getString(R.string.system_apps)).withIcon(GoogleMaterial.Icon.gmd_android).withIdentifier(1),
                    new PrimaryDrawerItem().withName(context.getResources().getString(R.string.disabled_apps)).withIcon(GoogleMaterial.Icon.gmd_remove_circle_outline).withIdentifier(2),
                    new PrimaryDrawerItem().withName(context.getResources().getString(R.string.hidden_apps)).withIcon(GoogleMaterial.Icon.gmd_visibility_off).withIdentifier(3),
                    new PrimaryDrawerItem().withName(context.getResources().getString(R.string.favorite_apps)).withIcon(GoogleMaterial.Icon.gmd_star).withIdentifier(4),
                    new DividerDrawerItem(),
                    new PrimaryDrawerItem().withName(context.getResources().getString(R.string.settings)).withIcon(GoogleMaterial.Icon.gmd_settings).withSelectable(false).withIdentifier(5),
                    new PrimaryDrawerItem().withName(context.getResources().getString(R.string.about)).withIcon(GoogleMaterial.Icon.gmd_info).withSelectable(false).withIdentifier(6));
        }

        drawerBuilder.withOnDrawerItemClickListener(new Drawer.OnDrawerItemClickListener() {
            @Override
            public boolean onItemClick(View view, int position, IDrawerItem drawerItem) {
                switch (drawerItem.getIdentifier()) {
                    case 0:
                        recyclerView.setAdapter(appInstalledAdapter);
                        App.setCurrentAdapter(0);
                        OtherUtils.setToolbarTitle(context, context.getResources().getString(R.string.installed_apps));
                        break;
                    case 1:
                        recyclerView.setAdapter(appSystemAdapter);
                        App.setCurrentAdapter(1);
                        OtherUtils.setToolbarTitle(context, context.getResources().getString(R.string.system_apps));
                        break;
                    case 2:
                        recyclerView.setAdapter(appDisabledAdapter);
                        App.setCurrentAdapter(2);
                        OtherUtils.setToolbarTitle(context, context.getResources().getString(R.string.disabled_apps));
                        break;
                    case 3:
                        recyclerView.setAdapter(appHiddenAdapter);
                        App.setCurrentAdapter(3);
                        OtherUtils.setToolbarTitle(context, context.getResources().getString(R.string.hidden_apps));
                        break;
                    case 4:
                        recyclerView.setAdapter(appFavoriteAdapter);
                        App.setCurrentAdapter(4);
                        OtherUtils.setToolbarTitle(context, context.getResources().getString(R.string.favorite_apps));
                        break;
                    case 5:
                        context.startActivity(new Intent(context, SettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                        break;
                    case 6:
                        context.startActivity(new Intent(context, AboutActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                        break;
                    default:
                        break;
                }
                return false;
            }
        });
        return drawerBuilder.build();
    }

    @Override
    public boolean onQueryTextChange(String search) {
        if (search.isEmpty()) {
            ((AppAdapter) recyclerView.getAdapter()).getFilter().filter("");
        } else {
            ((AppAdapter) recyclerView.getAdapter()).getFilter().filter(search.toLowerCase());
        }
        if (recyclerView.getAdapter().getItemCount() == 0) {
            noResults.setVisibility(View.VISIBLE);
        } else {
            noResults.setVisibility(View.GONE);
        }
        return false;
    }

    @Override
    public boolean onQueryTextSubmit(String query) {
        return false;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_main, menu);

        searchItem = menu.findItem(R.id.search);
        SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);

        searchView = (SearchView) MenuItemCompat.getActionView(searchItem);
        searchView.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        searchView.setSearchableInfo(searchManager.getSearchableInfo(getComponentName()));
        searchView.setOnQueryTextListener(this);
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_WRITE_READ: {
                if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    DialogUtils.dialogMessage(context, getResources().getString(R.string.dialog_permissions), getResources().getString(R.string.dialog_permissions_description));
                }
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (drawer.isDrawerOpen()) {
            drawer.closeDrawer();
        } else if (searchItem.isVisible() && !searchView.isIconified()) {
            searchView.onActionViewCollapsed();
        } else if (appPreferences.getDoubleTap()) {
            if (doubleBackToExitPressedOnce) {
                super.onBackPressed();
                return;
            }
            this.doubleBackToExitPressedOnce = true;
            Toast.makeText(this, R.string.tap_exit, Toast.LENGTH_SHORT).show();
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    doubleBackToExitPressedOnce = false;
                }
            }, 2000);
        } else {
            super.onBackPressed();
            return;
        }
    }
}
