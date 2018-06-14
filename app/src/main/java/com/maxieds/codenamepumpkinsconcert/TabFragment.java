package com.maxieds.codenamepumpkinsconcert;

import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.widget.CompoundButtonCompat;
import android.text.Layout;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;
import android.widget.Switch;
import android.widget.TextView;

import java.util.Locale;

/**
 * <h1>Tab Fragment</h1>
 * Implements a Fragment for individual tab data in the application.
 *
 * @author  Maxie D. Schmidt
 * @since   12/31/17
 */
public class TabFragment extends Fragment {

    private static final String TAG = TabFragment.class.getSimpleName();

    /**
     * Definitions of the in-order tab indices.
     */
    public static final String ARG_PAGE = "ARG_PAGE";
    public static final int TAB_COVERT_MODE = 0;
    public static final int TAB_LIVE_PANEL = 1;
    public static final int TAB_TOOLS = 2;
    public static final int TAB_SETTINGS = 3;
    public static final int TAB_ABOUT = 4;

    /**
     * Local tab-specific data stored by the class.
     */
    private int tabNumber;
    private int layoutResRef;
    private View inflatedView;

    /**
     * Effectively the default constructor used to obtain a new tab of the specified index.
     * @param page
     * @return
     */
    public static TabFragment newInstance(int page) {
        Bundle args = new Bundle();
        args.putInt(ARG_PAGE, page);
        TabFragment fragment = new TabFragment();
        fragment.tabNumber = page;
        fragment.setArguments(args);
        switch(page) {
            case TAB_COVERT_MODE:
                fragment.layoutResRef = R.layout.covert_tab;
                break;
            case TAB_LIVE_PANEL:
                fragment.layoutResRef = R.layout.live_tab;
                break;
            case TAB_TOOLS:
                fragment.layoutResRef = R.layout.tools_tab;
                break;
            case TAB_SETTINGS:
                fragment.layoutResRef = R.layout.settings_tab;
                break;
            case TAB_ABOUT:
                fragment.layoutResRef = R.layout.about_tab;
                break;
            default:
                break;
        }
        return fragment;
    }

    /**
     * Called when the tab is created.
     * @param savedInstanceState
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        tabNumber = getArguments().getInt(ARG_PAGE);
    }

    /**
     * Inflates the layout and sets up the configuration of the widgets associated with each tab index.
     * @param inflater
     * @param container
     * @param savedInstanceState
     * @return View inflated tab
     */
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View view = inflater.inflate(layoutResRef, container, false);
        inflatedView = view;
        MainActivity.defaultInflater = inflater;
        if(tabNumber == TAB_COVERT_MODE) {}
        else if(tabNumber == TAB_LIVE_PANEL) {

            MainActivity.videoPreview = (SurfaceView) inflatedView.findViewById(R.id.camera_preview);
            MainActivity.videoPreviewBGOverlay = MainActivity.runningActivity.getResources().getDrawable(R.drawable.previewclock256);
            //MainActivity.videoPreview.setBackgroundColor(MainActivity.runningActivity.getResources().getColor(R.color.colorPrimaryDark));
            MainActivity.videoPreview.setBackgroundDrawable(MainActivity.videoPreviewBGOverlay);
            //MainActivity.videoPreview.getBackground().setAlpha(0);

            TextView tvLoggingMessages = new TextView(MainActivity.runningActivity); //(TextView) inflatedView.findViewById(R.id.textLogging);
            tvLoggingMessages.setTypeface(Typeface.MONOSPACE, Typeface.BOLD_ITALIC);
            tvLoggingMessages.setAllCaps(true);
            tvLoggingMessages.setBreakStrategy(Layout.BREAK_STRATEGY_HIGH_QUALITY);
            tvLoggingMessages.setEllipsize(TextUtils.TruncateAt.MIDDLE);
            tvLoggingMessages.setMaxLines(12);
            tvLoggingMessages.setTextColor(getResources().getColor(R.color.colorAccent));
            tvLoggingMessages.setTextSize(10);
            //tvLoggingMessages.setGravity(Gravity.BOTTOM);
            if(MainActivity.tvLoggingMessages != null)
                tvLoggingMessages.setText(MainActivity.tvLoggingMessages.getText().toString());
            tvLoggingMessages.setCompoundDrawables(MainActivity.runningActivity.getResources().getDrawable(R.drawable.log32), null, null, null);
            MainActivity.tvLoggingMessages = tvLoggingMessages;
        }
        else if(tabNumber == TAB_TOOLS) {
            // we can't easily modify the video settings when the recorder is running, so we don't need peripheral adapters for these,
            // but we do want to make sure that we can find them when we start a recording to set these video options:
            MainActivity.videoOptsAntiband = (Spinner) inflatedView.findViewById(R.id.videoOptsAntibandSpinner);
            MainActivity.videoOptsEffects = (Spinner) inflatedView.findViewById(R.id.videoOptsEffectsSpinner);
            MainActivity.videoOptsCameraFlash = (Spinner) inflatedView.findViewById(R.id.videoOptsCameraFlashSpinner);
            MainActivity.videoOptsFocus = (Spinner) inflatedView.findViewById(R.id.videoOptsFocusSpinner);
            MainActivity.videoOptsScene = (Spinner) inflatedView.findViewById(R.id.videoOptsSceneSpinner);
            MainActivity.videoOptsWhiteBalance = (Spinner) inflatedView.findViewById(R.id.videoOptsWhiteBalanceSpinner);
            MainActivity.videoOptsRotation = (Spinner) inflatedView.findViewById(R.id.videoOptsRotationSpinner);
        }
        else if(tabNumber == TAB_SETTINGS) {}
        else if(tabNumber == TAB_ABOUT) {

            String rawAboutStr = MainActivity.runningActivity.getString(R.string.apphtmlheader);
            rawAboutStr += MainActivity.runningActivity.getString(R.string.AboutHTML);
            rawAboutStr += getString(R.string.apphtmlfooter);
            rawAboutStr = rawAboutStr.replace("%%ABOUTLINKCOLOR%%", String.format("#%06x", MainActivity.runningActivity.getResources().getColor(R.color.colorAccent)));
            rawAboutStr = rawAboutStr.replace("%%appVersionName%%", MainActivity.runningActivity.getString(R.string.appVersionName));
            rawAboutStr = rawAboutStr.replace("%%appVersionCode%%", MainActivity.runningActivity.getString(R.string.appVersionCode));
            rawAboutStr = rawAboutStr.replace("%%appBuildConfig%%", MainActivity.runningActivity.getString(R.string.appBuildConfig));
            rawAboutStr = rawAboutStr.replace("%%appBuildTimestamp%%", MainActivity.runningActivity.getString(R.string.appBuildTimestamp));

            WebView wv = (WebView) inflatedView.findViewById(R.id.webViewAbout);
            wv.getSettings().setJavaScriptEnabled(false);
            wv.loadDataWithBaseURL(null, rawAboutStr, "text/html", "UTF-8", "");
            wv.setBackgroundColor(MainActivity.runningActivity.getTheme().obtainStyledAttributes(new int[] {R.color.colorAccentHighlight}).getColor(0, MainActivity.runningActivity.getResources().getColor(R.color.colorAccentHighlight)));
            wv.getSettings().setLoadWithOverviewMode(true);
            wv.getSettings().setUseWideViewPort(true);
            wv.getSettings().setLayoutAlgorithm(WebSettings.LayoutAlgorithm.TEXT_AUTOSIZING);
            wv.setInitialScale(10);

        }
        return inflatedView;

    }

    /**
     * Called when the tab view is destroyed.
     * (Nothing but the default behavior implemented here.)
     */
    @Override
    public void onDestroyView() {
        super.onDestroyView();
    }

    @Override
    public void onResume() {
        super.onResume();
    }

}

