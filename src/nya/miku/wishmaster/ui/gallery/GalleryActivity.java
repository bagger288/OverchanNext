/*
 * Overchan Android (Meta Imageboard Client)
 * Copyright (C) 2014-2016  miku-nyan <https://github.com/miku-nyan>
 *     
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package nya.miku.wishmaster.ui.gallery;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.commons.lang3.tuple.Triple;

import nya.miku.wishmaster.R;
import nya.miku.wishmaster.api.interfaces.CancellableTask;
import nya.miku.wishmaster.api.interfaces.ProgressListener;
import nya.miku.wishmaster.api.models.AttachmentModel;
import nya.miku.wishmaster.api.models.BoardModel;
import nya.miku.wishmaster.common.Async;
import nya.miku.wishmaster.common.Logger;
import nya.miku.wishmaster.lib.ClickableToast;
import nya.miku.wishmaster.lib.gallery.FixedSubsamplingScaleImageView;
import nya.miku.wishmaster.lib.gallery.JSWebView;
import nya.miku.wishmaster.lib.gallery.Jpeg;
import nya.miku.wishmaster.lib.gallery.TextureVideoView;
import nya.miku.wishmaster.lib.gallery.TouchGifView;
import nya.miku.wishmaster.lib.gallery.WebViewFixed;
import nya.miku.wishmaster.lib.gallery.verticalviewpager.VerticalViewPagerFixed;
import nya.miku.wishmaster.lib.gifdrawable.GifDrawable;
import android.graphics.PixelFormat;
import nya.miku.wishmaster.lib.UriFileUtils;
import nya.miku.wishmaster.ui.AppearanceUtils;
import nya.miku.wishmaster.ui.Attachments;
import nya.miku.wishmaster.ui.CompatibilityImpl;
import nya.miku.wishmaster.ui.CompatibilityUtils;
import nya.miku.wishmaster.ui.ReverseImageSearch;
import nya.miku.wishmaster.ui.downloading.DownloadingService;
import nya.miku.wishmaster.ui.presentation.BoardFragment;
import nya.miku.wishmaster.ui.settings.ApplicationSettings;
import nya.miku.wishmaster.ui.tabs.UrlHandler;
import nya.miku.wishmaster.ui.theme.ThemeUtils;
import android.annotation.SuppressLint;
import android.app.ActionBar;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Point;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import androidx.core.view.MotionEventCompat;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.util.SparseArray;
import android.animation.ValueAnimator;
import android.view.GestureDetector;
import android.view.ScaleGestureDetector;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebResourceError;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.ui.PlayerControlView;
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout;
import com.google.android.exoplayer2.upstream.DefaultDataSource;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource;
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory;

public class GalleryActivity extends Activity implements View.OnClickListener, View.OnLongClickListener {
    private static final String TAG = "GalleryActivity";
    
    public static final String EXTRA_SETTINGS = "settings";
    public static final String EXTRA_ATTACHMENT = "attachment";
    public static final String EXTRA_SAVED_ATTACHMENTHASH = "attachmenthash";
    public static final String EXTRA_BOARDMODEL = "boardmodel";
    public static final String EXTRA_PAGEHASH = "pagehash";
    public static final String EXTRA_LOCALFILENAME = "localfilename";
    public static final String EXTRA_FROMTHREAD = "fromthread";
    public static final String EXTRA_FROMDIALOG = "fromdialog";
    
    @SuppressLint("InlinedApi")
    private static final int BINDING_FLAGS = Context.BIND_AUTO_CREATE | Context.BIND_IMPORTANT;
    
    private static final int REQUEST_HANDLE_INTERACTIVE_EXCEPTION = 1;
    
    private static final String initialTitleText = new String(new char[128]).replaceAll(".", " ");

    private LayoutInflater inflater;
    private ExecutorService tnDownloadingExecutor;
    
    private boolean fromThread;
    private boolean fromDialog;
    private BoardModel boardModel;
    private String chan;
    
    private ProgressBar progressBar;
    private ViewPager viewPager;
    private WebView webView;
    private TextView navigationInfo;
    private SparseArray<View> instantiatedViews;
    
    private BroadcastReceiver broadcastReceiver;
    private ServiceConnection serviceConnection;
    private GalleryRemote remote;
    
    private GallerySettings settings;
    private List<Triple<AttachmentModel, String, String>> attachments = null;
    private int currentPosition = 0;
    private int previousPosition = -1;
    
    private boolean firstScroll = true;
    
    private Menu menu;
    private boolean loadingError;
    private boolean currentLoaded;
    private TextView titleView;
    
    private static class ProgressHandler extends Handler {
        private final WeakReference<GalleryActivity> reference;
        
        public ProgressHandler(GalleryActivity activity) {
            reference = new WeakReference<GalleryActivity>(activity);
        }
        
        @Override
        public void handleMessage(Message msg) {
            GalleryActivity activity = reference.get();
            if (activity == null) return;
            int progress = msg.arg1;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                if (progress < Window.PROGRESS_END) {
                    if (activity.progressBar.getVisibility() == View.GONE) activity.progressBar.setVisibility(View.VISIBLE);
                    activity.progressBar.setProgress(progress);
                } else {
                    if (activity.progressBar.getVisibility() == View.VISIBLE) activity.progressBar.setVisibility(View.GONE);
                }
            } else {
                activity.setProgress(progress);
            }
        }
    }
    
    private ProgressListener progressListener = new ProgressListener() {
        private long maxValue = Window.PROGRESS_END;
        private Handler progressHandler = new ProgressHandler(GalleryActivity.this);
        
        @Override
        public void setProgress(final long value) {
            progressHandler.obtainMessage(0, (int)(Window.PROGRESS_END * value / maxValue), 0).sendToTarget();
        }
        
        @Override
        public void setMaxValue(long value) {
            if (value > 0) maxValue = value;
        }
        
        @Override
        public void setIndeterminate() {
        }
        
    };
    
    private void hideProgress() {
        progressListener.setMaxValue(1);
        progressListener.setProgress(1);
    }
    
    private abstract class AbstractGetterCallback extends GalleryGetterCallback.Stub {
        private final CancellableTask task;
        public AbstractGetterCallback(CancellableTask task) {
            this.task = task;
        }
        @Override
        public boolean isTaskCancelled() throws RemoteException {
            return task.isCancelled();
        }
        @Override
        public void setProgress(long value) throws RemoteException {
            progressListener.setProgress(value);
        }
        @Override
        public void setProgressIndeterminate() throws RemoteException {
            progressListener.setIndeterminate();
        }
        @Override
        public void setProgressMaxValue(long value) throws RemoteException {
            progressListener.setMaxValue(value);
        }
    }
    
    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) requestWindowFeature(Window.FEATURE_PROGRESS);
        settings = getIntent().getParcelableExtra(EXTRA_SETTINGS);
        if (settings == null) settings = GallerySettings.fromSettings(
                new ApplicationSettings(PreferenceManager.getDefaultSharedPreferences(getApplication()), getResources()));
        settings.getTheme().setTo(this, R.style.Transparent);
        getWindow().setFormat(PixelFormat.RGBA_8888);
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) CompatibilityImpl.setActionBarNoIcon(this);
        
        inflater = getLayoutInflater();
        instantiatedViews = new SparseArray<View>();
        tnDownloadingExecutor = Executors.newFixedThreadPool(4, Async.LOW_PRIORITY_FACTORY);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH && settings.fullscreenGallery()) {
            setContentView(R.layout.gallery_layout_fullscreen);
            GalleryFullscreen.initFullscreen(this);
        } else {
            setContentView(R.layout.gallery_layout);
        }
        
        progressBar = (ProgressBar) findViewById(android.R.id.progress);
        progressBar.setMax(Window.PROGRESS_END);
        viewPager = (ViewPager) findViewById(R.id.gallery_viewpager);
        navigationInfo = (TextView) findViewById(R.id.gallery_navigation_info);
        for (int id : new int[] { R.id.gallery_navigation_previous, R.id.gallery_navigation_next }) findViewById(id).setOnClickListener(this);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            ActionBar actionBar = getActionBar();
            actionBar.setDisplayShowTitleEnabled(false);
            actionBar.setDisplayShowCustomEnabled(true);
            View customView = getLayoutInflater().inflate(R.layout.action_bar_title, null);
            titleView = (TextView)customView.findViewById(R.id.action_bar_title);
            titleView.setText(initialTitleText);
            titleView.setOnClickListener(this);
            titleView.setOnLongClickListener(this);
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                titleView.setPadding(10, 0, 0, 0);
                titleView.setLines(1);
            }
            actionBar.setCustomView(customView);
        }
        
        bindService(new Intent(this, GalleryBackend.class), new ServiceConnection() {
            { serviceConnection = this; }
            
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                GalleryBinder galleryBinder = GalleryBinder.Stub.asInterface(service);
                try {
                    GalleryInitData initData = new GalleryInitData(getIntent(), savedInstanceState);
                    boardModel = initData.boardModel;
                    fromThread = initData.fromThread;
                    fromDialog = initData.fromDialog;
                    chan = boardModel.chan;
                    remote = new GalleryRemote(galleryBinder, galleryBinder.initContext(initData));
                    GalleryInitResult initResult = remote.getInitResult();
                    if (initResult != null) {
                        attachments = initResult.attachments;
                        currentPosition = initResult.initPosition;
                        if (initResult.shouldWaitForPageLoaded) waitForPageLoaded(savedInstanceState);
                    } else {
                        attachments = Collections.singletonList(Triple.of(initData.attachment, initData.attachmentHash, (String)null));
                        currentPosition = 0;
                    }
                    
                    viewPager.setAdapter(new GalleryAdapter());
                    viewPager.setCurrentItem(currentPosition);
                    viewPager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
                        @Override
                        public void onPageSelected(int position) {
                            currentPosition = position;
                            updateItem();
                        }
                    });
                } catch (Exception e) {
                    Logger.e(TAG, e);
                    finish();
                }
            }
            
            @Override
            public void onServiceDisconnected(ComponentName name) {
                Logger.e(TAG, "backend service disconnected");
                remote = null;
                System.exit(0);
            }
        }, BINDING_FLAGS);
        
        GalleryExceptionHandler.init();
    }
    
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(EXTRA_SAVED_ATTACHMENTHASH, attachments.get(currentPosition).getMiddle());
    }
    
    private void waitForPageLoaded(Bundle savedInstanceState) {
        final String savedHash = savedInstanceState != null ? savedInstanceState.getString(EXTRA_SAVED_ATTACHMENTHASH) : null;
        if (savedHash != null) registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.getAction() != null && intent.getAction().equals(BoardFragment.BROADCAST_PAGE_LOADED)) {
                    unregisterReceiver(this);
                    broadcastReceiver = null;
                    
                    Intent activityIntent = getIntent();
                    String pagehash = activityIntent.getStringExtra(EXTRA_PAGEHASH);
                    if (pagehash != null && remote.isPageLoaded(pagehash)) {
                        startActivity(activityIntent.putExtra(EXTRA_SAVED_ATTACHMENTHASH, savedHash));
                        finish();
                    }
                }
            }
        }, new IntentFilter(BoardFragment.BROADCAST_PAGE_LOADED));
    }
    
    private void stopWebView() {
        if (webView != null) {
            webView.destroy();
            webView = null;
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        BroadcastReceiver receiver = broadcastReceiver;
        if (receiver != null) unregisterReceiver(receiver);
        GalleryItemViewTag tag = getCurrentTag();
        if (tag != null && tag.exoPlayer != null) {
            try {
                tag.exoPlayer.pause();
            } catch (Exception e) {
                Logger.e(TAG, "cannot pause exoplayer", e);
            }
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopWebView();
        if (instantiatedViews != null) {
            for (int i=0; i<instantiatedViews.size(); ++i) {
                View v = instantiatedViews.valueAt(i);
                if (v != null) {
                    Object tag = v.getTag();
                    if (tag != null && tag instanceof GalleryItemViewTag) {
                        recycleTag((GalleryItemViewTag) tag, true);
                    }
                }
            }
        }
        tnDownloadingExecutor.shutdown();
        if (serviceConnection != null) unbindService(serviceConnection);
    }
    
    @Override
    public boolean onLongClick(View v) {
        if (v.getId() == R.id.action_bar_title) {
            if (fromDialog || fromThread) {
                remote.tryScrollParent(attachments.get(currentPosition).getRight(), fromDialog);
            }
            finish();
            return true;
        }
        return false;
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.gallery_navigation_previous) {
            if (currentPosition > 0) {
                viewPager.setCurrentItem(--currentPosition);
                updateItem();
            }
        } else if (id == R.id.gallery_navigation_next) {
            if (currentPosition < attachments.size() - 1) {
                viewPager.setCurrentItem(++currentPosition);
                updateItem();
            }
        } else if (id == R.id.action_bar_title) {
            GalleryItemViewTag tag = getCurrentTag();
            String info = remote.getAttachmentInfoString(new GalleryAttachmentInfo(tag.attachmentModel, tag.attachmentHash));
            Toast toast = Toast.makeText(this, info, Toast.LENGTH_LONG);
            toast.setGravity(Gravity.BOTTOM|Gravity.CENTER, 0, navigationInfo.getHeight() * 10 / 8);
            toast.show();
        }
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        this.menu = menu;
        MenuItem itemUpdate = menu.add(Menu.NONE, R.id.menu_update, 1, R.string.menu_update);
        MenuItem itemSave = menu.add(Menu.NONE, R.id.menu_save_attachment, 2, R.string.menu_save_attachment);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            itemUpdate.setIcon(ThemeUtils.getActionbarIcon(getTheme(), getResources(), R.attr.actionRefresh));
            itemSave.setIcon(ThemeUtils.getActionbarIcon(getTheme(), getResources(), R.attr.actionSave));
            CompatibilityImpl.setShowAsActionIfRoom(itemUpdate);
            CompatibilityImpl.setShowAsActionIfRoom(itemSave);
        } else {
            itemUpdate.setIcon(R.drawable.ic_menu_refresh);
            itemSave.setIcon(android.R.drawable.ic_menu_save);
        }
        menu.add(Menu.NONE, R.id.menu_open_external, 3, R.string.menu_open).setIcon(R.drawable.ic_menu_set_as);
        menu.add(Menu.NONE, R.id.menu_share, 4, R.string.menu_share).setIcon(android.R.drawable.ic_menu_share);
        menu.add(Menu.NONE, R.id.menu_share_link, 5, R.string.menu_share_link).setIcon(android.R.drawable.ic_menu_share);
        menu.add(Menu.NONE, R.id.menu_reverse_search, 6, R.string.menu_reverse_search).setIcon(android.R.drawable.ic_menu_search);
        menu.add(Menu.NONE, R.id.menu_open_browser, 7, R.string.menu_open_browser).setIcon(R.drawable.ic_menu_browser);
        updateMenu();
        
        return true;
    }
    
    private void updateMenu() {
        if (this.menu == null) return;
        View current = instantiatedViews.get(currentPosition);
        if (current == null) {
            Logger.e(TAG, "VIEW == NULL");
            return;
        }
        GalleryItemViewTag tag = (GalleryItemViewTag) current.getTag();
        boolean externalVideo = tag.attachmentModel.type == AttachmentModel.TYPE_VIDEO && settings.doNotDownloadVideos();
        menu.findItem(R.id.menu_update).setVisible(!currentLoaded && !externalVideo || loadingError);
        menu.findItem(R.id.menu_save_attachment).setVisible((externalVideo && !loadingError) ||
                (currentLoaded && tag.attachmentModel.type != AttachmentModel.TYPE_OTHER_NOTFILE));
        menu.findItem(R.id.menu_open_external).setVisible(currentLoaded && (tag.attachmentModel.type == AttachmentModel.TYPE_OTHER_FILE ||
                tag.attachmentModel.type == AttachmentModel.TYPE_AUDIO || tag.attachmentModel.type == AttachmentModel.TYPE_VIDEO));
        menu.findItem(R.id.menu_open_external).setTitle(tag.attachmentModel.type != AttachmentModel.TYPE_OTHER_FILE ?
                R.string.menu_open_player : R.string.menu_open);
        menu.findItem(R.id.menu_share).setVisible(currentLoaded && tag.attachmentModel.type != AttachmentModel.TYPE_OTHER_NOTFILE);
        menu.findItem(R.id.menu_reverse_search).setVisible(
                tag.attachmentModel.type == AttachmentModel.TYPE_IMAGE_STATIC || tag.attachmentModel.type == AttachmentModel.TYPE_IMAGE_GIF);
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menu_update) {
            updateItem();
            return true;
        } else if (id == R.id.menu_save_attachment) {
            if (!CompatibilityUtils.hasAccessStorage(this)) return true;
            downloadAttachment();
            return true;
        } else if (id == R.id.menu_open_external) {
            openExternal();
            return true;
        } else if (id == R.id.menu_share) {
            share();
            return true;
        } else if (id == R.id.menu_share_link) {
            shareLink();
            return true;
        } else if (id == R.id.menu_reverse_search) {
            reverseSearch();
            return true;
        } else if (id == R.id.menu_open_browser) {
            openBrowser();
            return true;
        }
        return false;
    }
    
    private GalleryItemViewTag getCurrentTag() {
        View current = instantiatedViews.get(currentPosition);
        if (current == null) {
            Logger.e(TAG, "VIEW == NULL (position=" + currentPosition + ")");
            return null;
        }
        return (GalleryItemViewTag) current.getTag();
    }
    
    private void downloadAttachment() {
        GalleryItemViewTag tag = getCurrentTag();
        if (tag == null) return;
        DownloadingService.DownloadingQueueItem queueItem = new DownloadingService.DownloadingQueueItem(tag.attachmentModel, boardModel);
        String fileName = Attachments.getAttachmentLocalFileName(tag.attachmentModel, boardModel, settings.isDownloadOriginalNames());
        String itemName = Attachments.getAttachmentLocalShortName(tag.attachmentModel, boardModel, settings.isDownloadOriginalNames());
        if (DownloadingService.isInQueue(queueItem)) {
            Toast.makeText(this, getString(R.string.notification_download_already_in_queue, itemName), Toast.LENGTH_LONG).show();
        } else {
            final Intent downloadIntent = new Intent(this, DownloadingService.class);
            downloadIntent.putExtra(DownloadingService.EXTRA_DOWNLOADING_ITEM, queueItem);
            if (new File(new File(settings.getDownloadDirectory(), chan), fileName).exists()) {
                ClickableToast.showText(this, getString(R.string.notification_download_already_exists, fileName), new ClickableToast.OnClickListener() {
                    @Override
                    public void onClick() {
                        startService(downloadIntent);
                    }
                });
            } else {
                startService(downloadIntent);
            }
        }
    }
    
    private void openExternal() {
        GalleryItemViewTag tag = getCurrentTag();
        if (tag == null) return;
        String mime;
        switch (tag.attachmentModel.type) {
            case AttachmentModel.TYPE_VIDEO:
                mime = "video/*";
                break;
            case AttachmentModel.TYPE_AUDIO:
                mime = "audio/*";
                break;
            default:
                mime = "*/*";
                break;
        }
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(UriFileUtils.getContentUri(this, tag.file), mime);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(intent);
    }
    
    private void share() {
        GalleryItemViewTag tag = getCurrentTag();
        if (tag == null) return;
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        String extension = Attachments.getAttachmentExtention(tag.attachmentModel);
        switch (tag.attachmentModel.type) {
            case AttachmentModel.TYPE_IMAGE_GIF:
                shareIntent.setType("image/gif");
                break;
            case AttachmentModel.TYPE_IMAGE_SVG:
                shareIntent.setType("image/svg+xml");
                break;
            case AttachmentModel.TYPE_IMAGE_STATIC:
                if (extension.equalsIgnoreCase(".png")) {
                    shareIntent.setType("image/png");
                } else if (extension.equalsIgnoreCase(".jpg") || extension.equalsIgnoreCase(".jpg")) {
                    shareIntent.setType("image/jpeg");
                } else {
                    shareIntent.setType("image/*");
                }
                break;
            case AttachmentModel.TYPE_VIDEO:
                if (extension.equalsIgnoreCase(".mp4")) {
                    shareIntent.setType("video/mp4");
                } else if (extension.equalsIgnoreCase(".webm")) {
                    shareIntent.setType("video/webm");
                } else if (extension.equalsIgnoreCase(".avi")) {
                    shareIntent.setType("video/avi");
                } else if (extension.equalsIgnoreCase(".mov")) {
                    shareIntent.setType("video/quicktime");
                } else if (extension.equalsIgnoreCase(".mkv")) {
                    shareIntent.setType("video/x-matroska");
                } else if (extension.equalsIgnoreCase(".flv")) {
                    shareIntent.setType("video/x-flv");
                } else if (extension.equalsIgnoreCase(".wmv")) {
                    shareIntent.setType("video/x-ms-wmv");
                } else {
                    shareIntent.setType("video/*");
                }
                break;
            case AttachmentModel.TYPE_AUDIO:
                if (extension.equalsIgnoreCase(".mp3")) {
                    shareIntent.setType("audio/mpeg");
                } else if (extension.equalsIgnoreCase(".mp4")) {
                    shareIntent.setType("audio/mp4");
                } else if (extension.equalsIgnoreCase(".ogg")) {
                    shareIntent.setType("audio/ogg");
                } else if (extension.equalsIgnoreCase(".webm")) {
                    shareIntent.setType("audio/webm");
                } else if (extension.equalsIgnoreCase(".flac")) {
                    shareIntent.setType("audio/flac");
                } else if (extension.equalsIgnoreCase(".wav")) {
                    shareIntent.setType("audio/vnd.wave");
                } else {
                    shareIntent.setType("audio/*");
                }
                break;
            case AttachmentModel.TYPE_OTHER_FILE:
                shareIntent.setType("application/octet-stream");
                break;
        }
        Logger.d(TAG, shareIntent.getType());
        shareIntent.putExtra(Intent.EXTRA_STREAM, UriFileUtils.getContentUri(this, tag.file));
        startActivity(Intent.createChooser(shareIntent, getString(R.string.share_via)));
    }
    
    private void shareLink() {
        GalleryItemViewTag tag = getCurrentTag();
        if (tag == null) return;
        String absoluteUrl = remote.getAbsoluteUrl(tag.attachmentModel.path);
        if (absoluteUrl == null) return;
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, absoluteUrl);
        startActivity(Intent.createChooser(shareIntent, getString(R.string.share_via)));
    }
    
    private void reverseSearch() {
        GalleryItemViewTag tag = getCurrentTag();
        if (tag == null) return;
        String absoluteUrl = remote.getAbsoluteUrl(tag.attachmentModel.path);
        if (absoluteUrl == null) return;
        ReverseImageSearch.openDialog(this, absoluteUrl);
    }
    
    private void openBrowser() {
        GalleryItemViewTag tag = getCurrentTag();
        if (tag == null) return;
        String absoluteUrl = remote.getAbsoluteUrl(tag.attachmentModel.path);
        if (absoluteUrl == null) return;
        UrlHandler.launchExternalBrowser(this, absoluteUrl);
    }
    
    private class GalleryAdapter extends PagerAdapter {
        private boolean firstTime = true;
        private final Runnable finishCallback = new Runnable() {
            @Override
            public void run() {
                GalleryActivity.this.finish();
            }
        };
        
        @Override
        public int getCount() {
            return attachments.size();
        }
        
        @Override
        public boolean isViewFromObject(View view, Object object) {
            return view == object;
        }
        
        @Override
        public Object instantiateItem(ViewGroup container, int position) {
            View v = inflater.inflate(R.layout.gallery_item, container, false);
            GalleryItemViewTag tag = new GalleryItemViewTag();
            tag.attachmentModel = attachments.get(position).getLeft();
            tag.attachmentHash = attachments.get(position).getMiddle();
            tag.thumbnailView = (ImageView) v.findViewById(R.id.gallery_thumbnail_preview);
            
            int tnWidth = Math.min(container.getMeasuredWidth(), tag.attachmentModel.width * 2);
            if (tnWidth > 0) tag.thumbnailView.getLayoutParams().width = tnWidth;
            
            tag.layout = (FrameLayout) v.findViewById(R.id.gallery_item_layout);
            tag.errorView = v.findViewById(R.id.gallery_error);
            tag.errorText = (TextView) tag.errorView.findViewById(R.id.frame_error_text);
            tag.errorText.setTextColor(Color.WHITE);
            tag.loadingView = v.findViewById(R.id.gallery_loading);
            v.setTag(tag);
            instantiatedViews.put(position, v);
            
            String hash = tag.attachmentHash;
            Bitmap bmp = remote.getBitmapFromMemory(hash);
            if (bmp != null) {
                tag.thumbnailView.setImageBitmap(bmp);
            } else {
                tnDownloadingExecutor.execute(new AsyncThumbnailDownloader(position, hash, tag.attachmentModel.thumbnail));
            }
            if (settings.swipeToCloseGallery()) v = VerticalViewPagerFixed.wrap(v, finishCallback, settings.fullscreenGallery());
            container.addView(v);
            if (firstTime && position == currentPosition) {
                updateItem();
                firstTime = false;
            }
            return v;
        }
        
        @Override
        public void destroyItem(ViewGroup container, int position, Object object) {
            View v = (View) object;
            Object tag = v.getTag();
            if (tag != null && tag instanceof View) tag = ((View) tag).getTag();
            if (tag != null && tag instanceof GalleryItemViewTag) recycleTag((GalleryItemViewTag) tag, true);
            container.removeView(v);
            instantiatedViews.delete(position);
        }
        
        private class AsyncThumbnailDownloader implements Runnable {
            private final int position;
            private final String hash;
            private final String url;
            
            public AsyncThumbnailDownloader(int position, String hash, String url) {
                this.position = position;
                this.hash = hash;
                this.url = url;
            }
            
            @Override
            public void run() {
                Bitmap bmp = remote.getBitmap(hash, url);
                if (bmp != null) {
                    View v = instantiatedViews.get(position);
                    if (v != null) {
                        final ImageView tnView = ((GalleryItemViewTag) v.getTag()).thumbnailView;
                        final Bitmap bmpSet = bmp;
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (tnView != null) {
                                    tnView.setImageBitmap(bmpSet);
                                }
                            }
                        });
                    }
                }
            }
        }
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_HANDLE_INTERACTIVE_EXCEPTION && resultCode == RESULT_OK) updateItem();
    }
    
    private void setTitle(String title) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            final String text = title.replaceAll(".(?!$)", "$0\u200b");
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    titleView.setText(text);
                }
            });
        } else {
            super.setTitle(title);
        }
    }

    private void updateItem() {
        stopWebView();
        AttachmentModel attachment = attachments.get(currentPosition).getLeft();
        if (settings.scrollThreadFromGallery() && !firstScroll && !fromDialog) remote.tryScrollParent(attachments.get(currentPosition).getRight(), false);
        firstScroll = false;
        String navText = attachment.size == -1 ? (currentPosition + 1) + "/" + attachments.size() :
                (currentPosition + 1) + "/" + attachments.size() + " (" + Attachments.getAttachmentSizeString(attachment, getResources()) + ")";
        navigationInfo.setText(navText);
        setTitle(Attachments.getAttachmentDisplayName(attachment));
        
        if (previousPosition != -1) {
            View previous = instantiatedViews.get(previousPosition);
            if (previous != null) {
                GalleryItemViewTag tag = (GalleryItemViewTag) previous.getTag();
                tag.thumbnailView.setVisibility(View.VISIBLE);
                tag.layout.setVisibility(View.GONE);
                tag.errorView.setVisibility(View.GONE);
                tag.loadingView.setVisibility(View.GONE);
                recycleTag(tag, true);
            }
        }
        previousPosition = currentPosition;
        
        GalleryItemViewTag tag = getCurrentTag();
        if (tag == null) return;
        currentLoaded = false;
        loadingError = false;
        updateMenu();
        tag.downloadingTask = new AttachmentGetter(tag);
        tag.loadingView.setVisibility(View.VISIBLE);
        hideProgress();
        Async.runAsync((Runnable) tag.downloadingTask);
    }
    
    private class AttachmentGetter extends CancellableTask.BaseCancellableTask implements Runnable {
        private final GalleryItemViewTag tag;
        public AttachmentGetter(GalleryItemViewTag tag) {
            this.tag = tag;
        }
        
        @Override
        public void run() {
            if (tag.attachmentModel.type == AttachmentModel.TYPE_OTHER_NOTFILE ||
                (tag.attachmentModel.type == AttachmentModel.TYPE_VIDEO && settings.doNotDownloadVideos() && !settings.useInternalVideoPlayer())) {
                setExternalLink(tag);
                return;
            } else if (tag.attachmentModel.path == null || tag.attachmentModel.path.length() == 0) {
                showError(tag, getString(R.string.gallery_error_incorrect_attachment));
                return;
            }
            final String[] exception = new String[1];
            boolean localOnly = (tag.attachmentModel.type == AttachmentModel.TYPE_VIDEO &&
                    (settings.useWebViewVideoPlayer() || settings.useExoPlayer()) && settings.doNotDownloadVideos());
            File file = remote.getAttachment(new GalleryAttachmentInfo(tag.attachmentModel, tag.attachmentHash), localOnly, new AbstractGetterCallback(this) {
                @Override
                public void showLoading() {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            tag.loadingView.setVisibility(View.VISIBLE);
                        }
                    });
                }
                @Override
                public void onException(String message) {
                    exception[0] = message;
                }
                @Override
                public void onInteractiveException(GalleryInteractiveExceptionHolder holder) {
                    if (holder.e == null) return;
                    exception[0] = getString(R.string.error_interactive_cancelled_format, holder.e.getServiceName());
                    startActivityForResult(new Intent(GalleryActivity.this, GalleryInteractiveExceptionHandler.class).
                            putExtra(GalleryInteractiveExceptionHandler.EXTRA_INTERACTIVE_EXCEPTION, holder.e), REQUEST_HANDLE_INTERACTIVE_EXCEPTION);
                }
            });
            
            if (isCancelled()) return;
            if (file == null) {
                if (!localOnly) {
                    showError(tag, exception[0]);
                    return;
                }
            } else {
                currentLoaded = true;
                tag.file = file;
            }
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (isCancelled()) return;
                    hideProgress();
                    updateMenu();
                }
            });
            switch (tag.attachmentModel.type) {
                case AttachmentModel.TYPE_IMAGE_STATIC:
                    setStaticImage(tag, file);
                    break;
                case AttachmentModel.TYPE_IMAGE_GIF:
                    setGif(tag, file);
                    break;
                case AttachmentModel.TYPE_IMAGE_SVG:
                    setSvg(tag, file);
                    break;
                case AttachmentModel.TYPE_VIDEO:
                    if (settings.useWebViewVideoPlayer()) {
                        setWebView(tag, file);
                    } else if (settings.useSystemVideoPlayer()) {
                        setVideo(tag, file);
                    } else {
                        setExoPlayer(tag, file);
                    }
                    break;
                case AttachmentModel.TYPE_AUDIO:
                    setAudio(tag, file);
                    break;
                case AttachmentModel.TYPE_OTHER_FILE:
                    setOtherFile(tag, file);
                    break;
            }
        }
        
    }
    
    private void showError(final GalleryItemViewTag tag, final String message) {
        if (tag.downloadingTask.isCancelled()) return;
        loadingError = true;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (tag.downloadingTask.isCancelled()) return;
                hideProgress();
                updateMenu();
                tag.layout.setVisibility(View.GONE);
                recycleTag(tag, true);
                tag.thumbnailView.setVisibility(View.GONE);
                tag.loadingView.setVisibility(View.GONE);
                tag.errorView.setVisibility(View.VISIBLE);
                tag.errorText.setText(fixErrorMessage(message));
            }
            private String fixErrorMessage(String message) {
                if (message == null || message.length() == 0) {
                    return getString(R.string.error_unknown);
                }
                return message;
            }
        });
    }
    
    private void recycleTag(GalleryItemViewTag tag, boolean cancelTask) {
        if (tag.layout != null) {
            for (int i=0; i<tag.layout.getChildCount(); ++i) {
                View v = tag.layout.getChildAt(i);
                if (v instanceof FixedSubsamplingScaleImageView) {
                    ((FixedSubsamplingScaleImageView) v).recycle();
                } else if (v != null && v.getId() == R.id.gallery_video_container) {
                    try {
                        View vv = v.findViewById(R.id.gallery_video_view);
                        if (vv instanceof TextureVideoView) {
                            ((TextureVideoView) vv).stopPlayback();
                        } else if (vv instanceof VideoView) {
                            ((VideoView) vv).stopPlayback();
                        }
                    } catch (Exception e) {
                        Logger.e(TAG, "cannot release videoview", e);
                    }
                } else if (v != null) {
                    Object gifTag = v.getTag();
                    if (gifTag != null && gifTag instanceof GifDrawable) {
                        ((GifDrawable) gifTag).recycle();
                    }
                }
            }
            tag.layout.removeAllViews();
        }
        
        if (cancelTask && tag.downloadingTask != null) tag.downloadingTask.cancel();
        if (tag.timer != null) tag.timer.cancel();
        if (tag.audioPlayer != null) {
            try {
                tag.audioPlayer.release();
            } catch (Exception e) {
                Logger.e(TAG, "cannot release audio mediaplayer", e);
            } finally {
                tag.audioPlayer = null;
            }
        }
        if (tag.exoPlayer != null) {
            try {
                tag.exoPlayer.stop();
                tag.exoPlayer.release();
            } catch (Exception e) {
                Logger.e(TAG, "cannot release exoplayer", e);
            } finally {
                tag.exoPlayer = null;
            }
        }
        if (tag.exoController != null) {
            try {
                tag.exoController.setPlayer(null);
            } catch (Exception e) {}
            tag.exoController = null;
        }
        if (tag.indicatorHandler != null && tag.indicatorRunnable != null) {
            tag.indicatorHandler.removeCallbacks(tag.indicatorRunnable);
        }
        tag.indicatorHandler = null;
        tag.indicatorRunnable = null;
        
        System.gc();
    }
    
    private void showExoIndicator(final GalleryItemViewTag tag, final TextView indicator, String text) {
        if (indicator == null) return;
        indicator.setText(text);
        indicator.setVisibility(View.VISIBLE);
        if (tag.indicatorHandler == null) {
            tag.indicatorHandler = new Handler();
        }
        if (tag.indicatorRunnable != null) {
            tag.indicatorHandler.removeCallbacks(tag.indicatorRunnable);
        }
        tag.indicatorRunnable = new Runnable() {
            @Override
            public void run() {
                indicator.setVisibility(View.GONE);
            }
        };
        tag.indicatorHandler.postDelayed(tag.indicatorRunnable, 800);
    }

    private static void clampExoTranslation(float[] transX, float[] transY, float scale, float width, float height) {
        if (scale <= 1.0f || width <= 0 || height <= 0) {
            transX[0] = 0f;
            transY[0] = 0f;
            return;
        }
        float scaledW = width * scale;
        float scaledH = height * scale;
        float minX = width - scaledW;
        float maxX = 0f;
        transX[0] = Math.max(minX, Math.min(maxX, transX[0]));

        float minY = height - scaledH;
        float maxY = 0f;
        transY[0] = Math.max(minY, Math.min(maxY, transY[0]));
    }

    private static void applyExoTransform(View view, float scale, float transX, float transY) {
        if (view == null) return;
        view.setPivotX(0f);
        view.setPivotY(0f);
        view.setScaleX(scale);
        view.setScaleY(scale);
        view.setTranslationX(transX);
        view.setTranslationY(transY);
    }

    private void animateExoZoom(final View targetView, final float[] currentScale, final float[] currentTransX, final float[] currentTransY,
                                final float targetScale, final float focusX, final float focusY) {
        if (targetView == null) return;
        final float startScale = currentScale[0];
        final float startTransX = currentTransX[0];
        final float startTransY = currentTransY[0];
        final float width = targetView.getWidth();
        final float height = targetView.getHeight();

        final float targetTransX;
        final float targetTransY;
        if (targetScale <= 1.01f) {
            targetTransX = 0f;
            targetTransY = 0f;
        } else {
            float[] tX = new float[]{focusX - (focusX - startTransX) * (targetScale / Math.max(startScale, 0.01f))};
            float[] tY = new float[]{focusY - (focusY - startTransY) * (targetScale / Math.max(startScale, 0.01f))};
            clampExoTranslation(tX, tY, targetScale, width, height);
            targetTransX = tX[0];
            targetTransY = tY[0];
        }

        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(220);
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float f = (Float) animation.getAnimatedValue();
                currentScale[0] = startScale + (targetScale - startScale) * f;
                currentTransX[0] = startTransX + (targetTransX - startTransX) * f;
                currentTransY[0] = startTransY + (targetTransY - startTransY) * f;
                if (currentScale[0] <= 1.01f && f >= 1.0f) {
                    currentScale[0] = 1.0f;
                    currentTransX[0] = 0f;
                    currentTransY[0] = 0f;
                }
                applyExoTransform(targetView, currentScale[0], currentTransX[0], currentTransY[0]);
            }
        });
        animator.start();
    }
    
    private void setStaticImage(final GalleryItemViewTag tag, final File file) {
        if (!settings.useScaleImageView() || Build.VERSION.SDK_INT < Build.VERSION_CODES.GINGERBREAD_MR1 || Jpeg.isNonStandardGrayscaleImage(file)) {
            setWebView(tag, file);
            return;
        }
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    FixedSubsamplingScaleImageView iv = new FixedSubsamplingScaleImageView(GalleryActivity.this);
                    iv.setInitCallback(new FixedSubsamplingScaleImageView.InitedCallback() {
                        @Override
                        public void onInit() {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    tag.thumbnailView.setVisibility(View.GONE);
                                    tag.loadingView.setVisibility(View.GONE);
                                }
                            });
                        }
                    });
                    iv.setImageFile(file.getAbsolutePath(), new FixedSubsamplingScaleImageView.FailedCallback() {
                        @Override
                        public void onFail() {
                            setWebView(tag, file);
                        }
                    });
                    if (tag.downloadingTask.isCancelled()) return;
                    tag.layout.setVisibility(View.VISIBLE);
                    tag.layout.addView(iv);
                } catch (Throwable t) {
                    System.gc();
                    Logger.e(TAG, t);
                    if (tag.downloadingTask.isCancelled()) return;
                    setWebView(tag, file);
                }
            }
        });
    }
    
    private void setGif(final GalleryItemViewTag tag, final File file) {
        if (!settings.useNativeGif()) {
            setWebView(tag, file);
            return;
        }
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                ImageView iv = Build.VERSION.SDK_INT < Build.VERSION_CODES.FROYO ?
                        new ImageView(GalleryActivity.this) : new TouchGifView(GalleryActivity.this);
                try {
                    GifDrawable drawable = new GifDrawable(file);
                    iv.setTag(drawable);
                    iv.setImageDrawable(drawable);
                } catch (Throwable e) {
                    System.gc();
                    Logger.e(TAG, "cannot init GifDrawable", e);
                    if (tag.downloadingTask.isCancelled()) return;
                    setWebView(tag, file);
                    return;
                }
                
                if (tag.downloadingTask.isCancelled()) return;
                
                tag.thumbnailView.setVisibility(View.GONE);
                tag.loadingView.setVisibility(View.GONE);
                
                tag.layout.setVisibility(View.VISIBLE);
                tag.layout.addView(iv);
            }
        });
    }
    
    private void setSvg(GalleryItemViewTag tag, File file) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) setWebView(tag, file); else setOtherFile(tag, file);
    }
    
    private void setVideo(final GalleryItemViewTag tag, final File file) {
        final Runnable startVideoView = new Runnable() {
            @Override
            public void run() {
                recycleTag(tag, false);
                tag.thumbnailView.setVisibility(View.GONE);
                tag.loadingView.setVisibility(View.GONE);
                tag.layout.setVisibility(View.VISIBLE);
                View videoContainer = inflater.inflate(R.layout.gallery_videoplayer, tag.layout);
                final TextureVideoView videoView = (TextureVideoView) videoContainer.findViewById(R.id.gallery_video_view);
                final TextView durationView = (TextView) videoContainer.findViewById(R.id.gallery_video_duration);

                videoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                    @Override
                    public void onPrepared(final MediaPlayer mp) {
                        videoView.setLooping(settings.loopMedia());

                        durationView.setText("00:00 / " + formatMediaPlayerTime(videoView.getDuration()));

                        tag.timer = new Timer();
                        tag.timer.schedule(new TimerTask() {
                            @Override
                            public void run() {
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        try {
                                            durationView.setText(formatMediaPlayerTime(videoView.getCurrentPosition()) + " / " +
                                                    formatMediaPlayerTime(videoView.getDuration()));
                                        } catch (Exception e) {
                                            Logger.e(TAG, e);
                                            tag.timer.cancel();
                                        }
                                    }
                                });
                            }
                        }, 1000, 1000);

                        videoView.start();
                    }
                });
                videoView.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                    @Override
                    public boolean onError(MediaPlayer mp, int what, int extra) {
                        Logger.e(TAG, "(Video) Error code: " + what);
                        if (tag.timer != null) tag.timer.cancel();
                        showError(tag, getString(R.string.gallery_error_play));
                        return true;
                    }
                });
                videoView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if (videoView.isPlaying()) {
                            videoView.pause();
                        } else {
                            videoView.start();
                        }
                    }
                });

                videoView.setVideoPath(file.getAbsolutePath());
            }
        };
        if (!settings.useInternalVideoPlayer()) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    setOnClickView(tag, getString(R.string.gallery_tap_to_play), new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            openExternal();
                        }
                    });
                }
            });
        } else if (!settings.autoplayMedia()) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    setOnClickView(tag, getString(R.string.gallery_tap_to_play), new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            runOnUiThread(startVideoView);
                        }
                    });
                }
            });
        } else {
            runOnUiThread(startVideoView);
        }
    }
    
    private void setExoPlayer(final GalleryItemViewTag tag, final File file) {
        final Runnable startExoPlayerView = new Runnable() {
            @Override
            public void run() {
                recycleTag(tag, false);
                tag.thumbnailView.setVisibility(View.GONE);
                tag.loadingView.setVisibility(View.GONE);
                tag.layout.setVisibility(View.VISIBLE);
                View videoContainer = inflater.inflate(R.layout.gallery_exoplayer, tag.layout);
                final View zoomContainer = videoContainer.findViewById(R.id.gallery_exoplayer_zoom_container);
                final View touchOverlay = videoContainer.findViewById(R.id.gallery_exoplayer_touch_overlay);
                final PlayerView playerView = (PlayerView) videoContainer.findViewById(R.id.gallery_exoplayer_view);
                final View controllerView = videoContainer.findViewById(R.id.gallery_exoplayer_controller);
                final PlayerControlView controller = controllerView instanceof PlayerControlView ? (PlayerControlView) controllerView : null;
                tag.exoController = controller;
                final View bottomBar = videoContainer.findViewById(R.id.gallery_exoplayer_bottom_bar);
                final SeekBar seekBar = (SeekBar) videoContainer.findViewById(R.id.gallery_exoplayer_seekbar);
                final TextView durationView = (TextView) videoContainer.findViewById(R.id.gallery_exoplayer_duration);
                final TextView overlayIndicator = (TextView) videoContainer.findViewById(R.id.gallery_exoplayer_overlay_indicator);

                ExoPlayer.Builder builder = new ExoPlayer.Builder(GalleryActivity.this);
                String userAgent = remote != null ? remote.getUserAgentString() : null;
                if (userAgent != null && !userAgent.isEmpty()) {
                    DefaultHttpDataSource.Factory httpDataSourceFactory = new DefaultHttpDataSource.Factory().setUserAgent(userAgent);
                    DefaultDataSource.Factory dataSourceFactory = new DefaultDataSource.Factory(GalleryActivity.this, httpDataSourceFactory);
                    builder.setMediaSourceFactory(new DefaultMediaSourceFactory(dataSourceFactory));
                }
                final ExoPlayer player = builder.build();
                tag.exoPlayer = player;
                playerView.setPlayer(player);
                playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
                playerView.setUseController(false);

                final boolean isController = settings.isExoControlsController();
                final boolean isSeekBarMode = settings.isExoControlsSeekBar();
                final boolean isGesturesMode = settings.isExoControlsGestures();

                final float[] currentScale = new float[]{1.0f};
                final float[] currentTransX = new float[]{0.0f};
                final float[] currentTransY = new float[]{0.0f};
                final boolean[] isScaling = new boolean[]{false};
                final float[] lastTouchX = new float[]{0.0f};
                final float[] lastTouchY = new float[]{0.0f};

                final ScaleGestureDetector scaleDetector = new ScaleGestureDetector(GalleryActivity.this, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override
                    public boolean onScaleBegin(ScaleGestureDetector detector) {
                        isScaling[0] = true;
                        if (zoomContainer != null && zoomContainer.getParent() != null) {
                            zoomContainer.getParent().requestDisallowInterceptTouchEvent(true);
                        }
                        if (isController && controller != null && controller.isVisible()) {
                            controller.hide();
                        }
                        return true;
                    }

                    @Override
                    public boolean onScale(ScaleGestureDetector detector) {
                        float scaleFactor = detector.getScaleFactor();
                        float focusX = detector.getFocusX();
                        float focusY = detector.getFocusY();
                        float prevScale = currentScale[0];
                        float newScale = Math.max(1.0f, Math.min(5.0f, prevScale * scaleFactor));
                        if (newScale <= 1.01f) {
                            newScale = 1.0f;
                            currentTransX[0] = 0f;
                            currentTransY[0] = 0f;
                        } else {
                            float contentX = (focusX - currentTransX[0]) / prevScale;
                            float contentY = (focusY - currentTransY[0]) / prevScale;
                            currentTransX[0] = focusX - contentX * newScale;
                            currentTransY[0] = focusY - contentY * newScale;
                            clampExoTranslation(currentTransX, currentTransY, newScale,
                                    zoomContainer != null && zoomContainer.getWidth() > 0 ? zoomContainer.getWidth() : playerView.getWidth(),
                                    zoomContainer != null && zoomContainer.getHeight() > 0 ? zoomContainer.getHeight() : playerView.getHeight());
                        }
                        currentScale[0] = newScale;
                        applyExoTransform(zoomContainer, currentScale[0], currentTransX[0], currentTransY[0]);
                        return true;
                    }

                    @Override
                    public void onScaleEnd(ScaleGestureDetector detector) {
                        isScaling[0] = false;
                        if (currentScale[0] <= 1.01f) {
                            currentScale[0] = 1.0f;
                            currentTransX[0] = 0f;
                            currentTransY[0] = 0f;
                            applyExoTransform(zoomContainer, 1.0f, 0f, 0f);
                            if (zoomContainer != null && zoomContainer.getParent() != null) {
                                zoomContainer.getParent().requestDisallowInterceptTouchEvent(false);
                            }
                        }
                    }
                });

                final GestureDetector gestureDetector = new GestureDetector(GalleryActivity.this, new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onDown(MotionEvent e) {
                        return true;
                    }

                    @Override
                    public boolean onSingleTapConfirmed(MotionEvent e) {
                        if (isController) {
                            if (controller != null) {
                                if (controller.isVisible()) {
                                    controller.hide();
                                } else {
                                    controller.show();
                                }
                            }
                        } else {
                            if (player.isPlaying()) {
                                player.pause();
                            } else {
                                player.play();
                            }
                        }
                        return true;
                    }

                    @Override
                    public boolean onDoubleTap(MotionEvent e) {
                        if (currentScale[0] > 1.05f) {
                            animateExoZoom(zoomContainer, currentScale, currentTransX, currentTransY, 1.0f, e.getX(), e.getY());
                            return true;
                        }

                        if (isGesturesMode || isSeekBarMode) {
                            float width = zoomContainer != null && zoomContainer.getWidth() > 0 ? zoomContainer.getWidth() : playerView.getWidth();
                            float x = e.getX();
                            long current = player.getCurrentPosition();
                            long duration = player.getDuration();
                            if (x < width * 0.5f) {
                                long newPos = Math.max(0, current - 5000);
                                player.seekTo(newPos);
                                showExoIndicator(tag, overlayIndicator, getString(R.string.gallery_exoplayer_seek_rewind));
                            } else {
                                long newPos = duration > 0 ? Math.min(duration, current + 5000) : current + 5000;
                                player.seekTo(newPos);
                                showExoIndicator(tag, overlayIndicator, getString(R.string.gallery_exoplayer_seek_forward));
                            }
                        } else {
                            animateExoZoom(zoomContainer, currentScale, currentTransX, currentTransY, 2.5f, e.getX(), e.getY());
                        }
                        return true;
                    }
                });

                final View.OnTouchListener overlayTouchListener = new View.OnTouchListener() {
                    @SuppressLint("ClickableViewAccessibility")
                    @Override
                    public boolean onTouch(View v, MotionEvent event) {
                        scaleDetector.onTouchEvent(event);
                        gestureDetector.onTouchEvent(event);

                        int action = event.getActionMasked();
                        switch (action) {
                            case MotionEvent.ACTION_DOWN:
                                lastTouchX[0] = event.getX();
                                lastTouchY[0] = event.getY();
                                if (currentScale[0] > 1.01f) {
                                    if (v.getParent() != null) v.getParent().requestDisallowInterceptTouchEvent(true);
                                }
                                break;

                            case MotionEvent.ACTION_POINTER_DOWN:
                                if (v.getParent() != null) v.getParent().requestDisallowInterceptTouchEvent(true);
                                break;

                            case MotionEvent.ACTION_POINTER_UP:
                                int upIndex = event.getActionIndex();
                                int remainingIndex = upIndex == 0 ? 1 : 0;
                                if (event.getPointerCount() > 1) {
                                    lastTouchX[0] = event.getX(remainingIndex);
                                    lastTouchY[0] = event.getY(remainingIndex);
                                }
                                break;

                            case MotionEvent.ACTION_MOVE:
                                if (!isScaling[0] && currentScale[0] > 1.01f) {
                                    float currX = event.getX();
                                    float currY = event.getY();
                                    float dx = currX - lastTouchX[0];
                                    float dy = currY - lastTouchY[0];
                                    currentTransX[0] += dx;
                                    currentTransY[0] += dy;
                                    clampExoTranslation(currentTransX, currentTransY, currentScale[0],
                                            zoomContainer != null && zoomContainer.getWidth() > 0 ? zoomContainer.getWidth() : playerView.getWidth(),
                                            zoomContainer != null && zoomContainer.getHeight() > 0 ? zoomContainer.getHeight() : playerView.getHeight());
                                    applyExoTransform(zoomContainer, currentScale[0], currentTransX[0], currentTransY[0]);
                                    lastTouchX[0] = currX;
                                    lastTouchY[0] = currY;
                                    if (v.getParent() != null) v.getParent().requestDisallowInterceptTouchEvent(true);
                                }
                                break;

                            case MotionEvent.ACTION_UP:
                            case MotionEvent.ACTION_CANCEL:
                                isScaling[0] = false;
                                if (currentScale[0] <= 1.01f) {
                                    currentScale[0] = 1.0f;
                                    currentTransX[0] = 0f;
                                    currentTransY[0] = 0f;
                                    applyExoTransform(zoomContainer, 1.0f, 0f, 0f);
                                    if (v.getParent() != null) v.getParent().requestDisallowInterceptTouchEvent(false);
                                }
                                break;
                        }
                        return true;
                    }
                };

                if (isController) {
                    if (bottomBar != null) bottomBar.setVisibility(View.GONE);
                    if (controller != null) {
                        controller.setVisibility(View.VISIBLE);
                        controller.setPlayer(player);
                        controller.setShowTimeoutMs(3500);
                        controller.show();
                    }
                } else {
                    if (controller != null) {
                        controller.setVisibility(View.GONE);
                        controller.setPlayer(null);
                    }
                    if (bottomBar != null) bottomBar.setVisibility(View.VISIBLE);
                    if (seekBar != null) {
                        seekBar.setVisibility(isSeekBarMode ? View.VISIBLE : View.GONE);
                    }
                }

                if (touchOverlay != null) {
                    touchOverlay.setVisibility(View.VISIBLE);
                    touchOverlay.setOnTouchListener(overlayTouchListener);
                }

                if (controller != null) {
                    final boolean[] isForwarding = new boolean[]{false};
                    controller.setOnTouchListener(new View.OnTouchListener() {
                        @SuppressLint("ClickableViewAccessibility")
                        @Override
                        public boolean onTouch(View v, MotionEvent event) {
                            int action = event.getActionMasked();
                            if (event.getPointerCount() >= 2 || currentScale[0] > 1.01f || isForwarding[0]) {
                                if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
                                    isForwarding[0] = true;
                                }
                                boolean res = overlayTouchListener.onTouch(touchOverlay != null ? touchOverlay : v, event);
                                if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                                    isForwarding[0] = false;
                                }
                                return res;
                            }
                            return false;
                        }
                    });
                }

                final boolean[] isUserTracking = new boolean[]{false};
                if (!isController && isSeekBarMode && seekBar != null) {
                    seekBar.setMax(1000);
                    seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                        @Override
                        public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                            if (fromUser) {
                                long total = player.getDuration();
                                if (total > 0) {
                                    long newPosition = ((long) progress * total) / sb.getMax();
                                    durationView.setText(formatMediaPlayerTime((int) newPosition) + " / " +
                                            formatMediaPlayerTime((int) total));
                                }
                            }
                        }

                        @Override
                        public void onStartTrackingTouch(SeekBar sb) {
                            isUserTracking[0] = true;
                        }

                        @Override
                        public void onStopTrackingTouch(SeekBar sb) {
                            long total = player.getDuration();
                            if (total > 0) {
                                long newPosition = ((long) sb.getProgress() * total) / sb.getMax();
                                player.seekTo(newPosition);
                            }
                            isUserTracking[0] = false;
                        }
                    });
                }

                player.addListener(new Player.Listener() {
                    @Override
                    public void onPlaybackStateChanged(int playbackState) {
                        if (playbackState == Player.STATE_BUFFERING) {
                            tag.loadingView.setVisibility(View.VISIBLE);
                        } else if (playbackState == Player.STATE_READY) {
                            tag.loadingView.setVisibility(View.GONE);
                            long duration = player.getDuration();
                            if (duration > 0 && !isController) {
                                durationView.setText(formatMediaPlayerTime((int) player.getCurrentPosition()) + " / " +
                                        formatMediaPlayerTime((int) duration));
                                if (isSeekBarMode && seekBar != null && !isUserTracking[0]) {
                                    seekBar.setProgress((int) ((player.getCurrentPosition() * 1000) / duration));
                                }
                            }
                        } else if (playbackState == Player.STATE_ENDED) {
                            tag.loadingView.setVisibility(View.GONE);
                        }
                    }

                    @Override
                    public void onPlayerError(PlaybackException error) {
                        Logger.e(TAG, "ExoPlayer error", error);
                        tag.loadingView.setVisibility(View.GONE);
                        if (tag.timer != null) tag.timer.cancel();
                        showError(tag, getString(R.string.gallery_error_play));
                    }
                });

                if (!isController) {
                    tag.timer = new Timer();
                    tag.timer.schedule(new TimerTask() {
                        @Override
                        public void run() {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    try {
                                        if (tag.exoPlayer != null) {
                                            long current = tag.exoPlayer.getCurrentPosition();
                                            long total = tag.exoPlayer.getDuration();
                                            if (total > 0) {
                                                durationView.setText(formatMediaPlayerTime((int) current) + " / " +
                                                        formatMediaPlayerTime((int) total));
                                                if (isSeekBarMode && seekBar != null && !isUserTracking[0]) {
                                                    seekBar.setProgress((int) ((current * 1000) / total));
                                                }
                                            }
                                        }
                                    } catch (Exception e) {
                                        Logger.e(TAG, e);
                                        if (tag.timer != null) tag.timer.cancel();
                                    }
                                }
                            });
                        }
                    }, 500, 500);
                }

                String videoUrl = file != null ? Uri.fromFile(file).toString() : (remote != null ? remote.getAbsoluteUrl(tag.attachmentModel.path) : null);
                if (videoUrl == null) {
                    showError(tag, getString(R.string.gallery_error_play));
                    return;
                }
                MediaItem mediaItem = MediaItem.fromUri(Uri.parse(videoUrl));
                player.setMediaItem(mediaItem);
                player.setRepeatMode(settings.loopMedia() ? Player.REPEAT_MODE_ONE : Player.REPEAT_MODE_OFF);
                player.prepare();
                player.play();
            }
        };
        if (!settings.useInternalVideoPlayer()) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    setOnClickView(tag, getString(R.string.gallery_tap_to_play), new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            openExternal();
                        }
                    });
                }
            });
        } else if (!settings.autoplayMedia()) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    setOnClickView(tag, getString(R.string.gallery_tap_to_play), new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            runOnUiThread(startExoPlayerView);
                        }
                    });
                }
            });
        } else {
            runOnUiThread(startExoPlayerView);
        }
    }
    
    private void setAudio(final GalleryItemViewTag tag, final File file) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                setOnClickView(tag, getString(R.string.gallery_tap_to_play), new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (!settings.useInternalAudioPlayer()) {
                            openExternal();
                        } else {
                            recycleTag(tag, false);
                            final TextView durationView = new TextView(GalleryActivity.this);
                            durationView.setGravity(Gravity.CENTER);
                            tag.layout.setVisibility(View.VISIBLE);
                            tag.layout.addView(durationView);
                            tag.audioPlayer = new MediaPlayer();
                            tag.audioPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                                @Override
                                public void onPrepared(final MediaPlayer mp) {
                                    mp.setLooping(settings.loopMedia());
                                    
                                    durationView.setText(getSpannedText("00:00 / " + formatMediaPlayerTime(mp.getDuration())));
                                    
                                    tag.timer = new Timer();
                                    tag.timer.schedule(new TimerTask() {
                                        @Override
                                        public void run() {
                                            runOnUiThread(new Runnable() {
                                                @Override
                                                public void run() {
                                                    try {
                                                        durationView.setText(getSpannedText(formatMediaPlayerTime(mp.getCurrentPosition()) + " / " +
                                                                formatMediaPlayerTime(mp.getDuration())));
                                                    } catch (Exception e) {
                                                        Logger.e(TAG, e);
                                                        tag.timer.cancel();
                                                    }
                                                }
                                            });
                                        }
                                    }, 1000, 1000);
                                    
                                    mp.start();
                                }
                            });
                            tag.audioPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                                @Override
                                public boolean onError(MediaPlayer mp, int what, int extra) {
                                    Logger.e(TAG, "(Audio) Error code: " + what);
                                    if (tag.timer != null) tag.timer.cancel();
                                    showError(tag, getString(R.string.gallery_error_play));
                                    return true;
                                }
                            });
                            durationView.setOnClickListener(new View.OnClickListener() {
                                @Override
                                public void onClick(View view) {
                                    if (!tag.audioPlayer.isPlaying()) {
                                        tag.audioPlayer.start();
                                    }
                                }
                            });
                            try {
                                tag.audioPlayer.setDataSource(file.getAbsolutePath());
                                tag.audioPlayer.prepareAsync();
                            } catch (Exception e) {
                                Logger.e(TAG, "audio player error", e);
                                if (tag.timer != null) tag.timer.cancel();
                                showError(tag, getString(R.string.gallery_error_play));
                            }
                        }
                    }
                });
            }
        });
    }
    
    private String formatMediaPlayerTime(int milliseconds) {
        int seconds = milliseconds / 1000 % 60;
        int minutes = milliseconds / 60000;
        return String.format(Locale.US, "%02d:%02d", minutes, seconds);
    }
    
    private void setOtherFile(final GalleryItemViewTag tag, final File file) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                setOnClickView(tag, getString(R.string.gallery_tap_to_open), new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        openExternal();
                    }
                });
            }
        });
    }
    
    private void setExternalLink(final GalleryItemViewTag tag) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                int stringResId = R.string.gallery_tap_to_external_link;
                try {
                    if (settings.doNotDownloadVideos() && tag.attachmentModel.type == AttachmentModel.TYPE_VIDEO)
                        stringResId = R.string.gallery_tap_to_play;
                } catch (Exception e) {}
                setOnClickView(tag, getString(stringResId), new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        openBrowser();
                    }
                });
            }
        });
    }
    
    private void setOnClickView(GalleryItemViewTag tag, String message, View.OnClickListener handler) {
        tag.thumbnailView.setVisibility(View.VISIBLE);
        tag.loadingView.setVisibility(View.GONE);
        TextView v = new TextView(GalleryActivity.this);
        v.setGravity(Gravity.CENTER);
        v.setText(getSpannedText(message));
        tag.layout.setVisibility(View.VISIBLE);
        tag.layout.addView(v);
        v.setOnClickListener(handler);
    }
    
    private Spanned getSpannedText(String message) {
        message = " " + message + " ";
        SpannableStringBuilder spanned = new SpannableStringBuilder(message);
        for (Object span : new Object[] { new ForegroundColorSpan(Color.WHITE), new BackgroundColorSpan(Color.parseColor("#88000000")) }) { 
            spanned.setSpan(span, 0, message.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return spanned;
    }
    
    private void setWebView(final GalleryItemViewTag tag, final File file) {
        final Runnable startWebView = new Runnable() {
            private boolean oomFlag = false;
            
            private final ViewGroup.LayoutParams MATCH_PARAMS =
                    new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            
            private void prepareWebView(WebView webView) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                    webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                }
                webView.setWebChromeClient(new WebChromeClient());
                webView.setBackgroundColor(Color.TRANSPARENT);
                webView.setInitialScale(100);
                webView.setScrollBarStyle(WebView.SCROLLBARS_OUTSIDE_OVERLAY);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ECLAIR) {
                    CompatibilityImpl.setScrollbarFadingEnabled(webView, true);
                }
                WebSettings settings = webView.getSettings();
                settings.setBuiltInZoomControls(true);
                settings.setSupportZoom(true);
                settings.setAllowFileAccess(true);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                    settings.setAllowFileAccessFromFileURLs(true);
                    settings.setAllowUniversalAccessFromFileURLs(true);
                }
                settings.setDomStorageEnabled(true);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ECLAIR_MR1) {
                    CompatibilityImpl.setDefaultZoomFAR(settings);
                    CompatibilityImpl.setLoadWithOverviewMode(settings, true);
                }
                settings.setUseWideViewPort(true);
                settings.setJavaScriptEnabled(true);
                settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
                settings.setUserAgentString(remote.getUserAgentString());
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO) {
                    CompatibilityImpl.setBlockNetworkLoads(settings, false);
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                    settings.setMediaPlaybackRequiresUserGesture(false);
                }
                setScaleWebView(webView);
            }
            
            private void setScaleWebView(final WebView webView) {
                Runnable callSetScaleWebView = new Runnable() {
                    @Override
                    public void run() {
                        setPrivateScaleWebView(webView);
                    }
                };

                Point resolution = new Point(tag.layout.getWidth(), tag.layout.getHeight());
                if (resolution.equals(0, 0)) {
                    // wait until the view is measured and its size is known
                    AppearanceUtils.callWhenLoaded(tag.layout, callSetScaleWebView);
                } else {
                    callSetScaleWebView.run();
                }
            }
            
            private void setPrivateScaleWebView(WebView webView) {
                Point imageSize = getImageSize(file);
                Point resolution = new Point(tag.layout.getWidth(), tag.layout.getHeight());

                //Logger.d(TAG, "Resolution: "+resolution.x+"x"+resolution.y);
                double scaleX = (double)resolution.x / (double)imageSize.x;
                double scaleY = (double)resolution.y / (double)imageSize.y;
                int scale = (int)Math.round(Math.min(scaleX, scaleY) * 100d);
                scale = Math.max(scale, 1);
                //Logger.d(TAG, "Scale: "+(Math.min(scaleX, scaleY) * 100d));
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ECLAIR_MR1) {
                    double picdpi = (getResources().getDisplayMetrics().density * 160d) / scaleX;
                    if (picdpi >= 240) {
                        CompatibilityImpl.setDefaultZoomFAR(webView.getSettings());
                    } else if (picdpi <= 120) {
                        CompatibilityImpl.setDefaultZoomCLOSE(webView.getSettings());
                    } else {
                        CompatibilityImpl.setDefaultZoomMEDIUM(webView.getSettings());
                    }
                }
                
                webView.setInitialScale(scale);
                webView.setPadding(0, 0, 0, 0);
            }
            
            private Point getImageSize(File file) {
                if (file == null) {
                    return new Point(0, 0);
                }
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(file.getAbsolutePath(), options);
                return new Point(options.outWidth, options.outHeight);
            }
            
            private boolean useFallback(File file) {
                if (file == null) {
                    return true;
                }
                String path = file.getPath().toLowerCase(Locale.US);
                if (path.endsWith(".png")) return false;
                if (path.endsWith(".jpg")) return false;
                if (path.endsWith(".gif")) return false;
                if (path.endsWith(".jpeg")) return false;
                if (path.endsWith(".webp") && Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) return false;
                return true;
            }
            
            @Override
            public void run() {
                try {
                    recycleTag(tag, false);
                    final String injectedJS = "javascript:var v = document.getElementsByTagName('video'); if (v.length > 0) v[0].loop = " +
                        (settings.loopMedia() ? "true" : "false") + ";";
                    webView = new WebViewFixed(GalleryActivity.this);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        webView.setWebViewClient(new WebViewClient() {
                            @Override
                            public void onReceivedError(WebView webView, WebResourceRequest request, WebResourceError error) {
                                if ((error.getErrorCode() == WebViewClient.ERROR_TOO_MANY_REQUESTS) ||
                                    (error.getErrorCode() == WebViewClient.ERROR_UNKNOWN)) {
                                    return;
                                }
                                showError(tag, "LOAD ERROR " + error.getErrorCode() + "\n" + error.getDescription().toString());
                            }
                            @Override
                            public void onReceivedHttpError(WebView webView, WebResourceRequest request, WebResourceResponse error) {
                                if (error.getStatusCode() == 404) {
                                    showError(tag, "404 - Not Found");
                                    return;
                                }
                                showError(tag, "HTTP ERROR " + error.getStatusCode());
                            }
                            @Override
                            public void onPageFinished(WebView webView, String url) {
                                super.onPageFinished(webView, url);
                                webView.loadUrl(injectedJS);
                            }
                        });
                    } else {
                        webView.setWebViewClient(new WebViewClient() {
                            @Override
                            public void onReceivedError(WebView webView, int errorCode, String description, String failingUrl) {
                                if ((errorCode == WebViewClient.ERROR_TOO_MANY_REQUESTS) ||
                                    (errorCode == WebViewClient.ERROR_UNKNOWN)) {
                                    return;
                                }
                                showError(tag, "LOAD ERROR " + errorCode + "\n" + description);
                            }
                            @Override
                            public void onPageFinished(WebView webView, String url) {
                                super.onPageFinished(webView, url);
                                webView.loadUrl(injectedJS);
                            }
                        });
                    }
                    webView.setLayoutParams(MATCH_PARAMS);
                    tag.layout.addView(webView);
                    if (tag.attachmentModel.type == AttachmentModel.TYPE_VIDEO) {
                        String videoUrl = file != null ? Uri.fromFile(file).toString() : remote.getAbsoluteUrl(tag.attachmentModel.path);
                        JSWebView.setVideo(webView, videoUrl, settings.loopMedia());
                    } else if (settings.fallbackWebView() || useFallback(file)) {
                        prepareWebView(webView);
                        if (file == null) {
                            webView.loadUrl(remote.getAbsoluteUrl(tag.attachmentModel.path));
                        } else {
                            webView.loadUrl(Uri.fromFile(file).toString());
                        }
                    } else {
                        JSWebView.setImage(webView, file);
                    }
                    tag.thumbnailView.setVisibility(View.GONE);
                    tag.loadingView.setVisibility(View.GONE);
                    tag.layout.setVisibility(View.VISIBLE);
                } catch (OutOfMemoryError oom) {
                    System.gc();
                    Logger.e(TAG, oom);
                    if (!oomFlag) {
                        oomFlag = true;
                        run();
                    } else showError(tag, getString(R.string.error_out_of_memory));
                }
            }
            
        };
        if (tag.attachmentModel.type == AttachmentModel.TYPE_VIDEO && !settings.autoplayMedia()) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    setOnClickView(tag, getString(R.string.gallery_tap_to_play), new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            runOnUiThread(startWebView);
                        }
                    });
                }
            });
        } else {
            runOnUiThread(startWebView);
        }
    }
    
    public static interface FullscreenCallback {
        void showUI(boolean hideAfterDelay);
        void keepUI(boolean hideAfterDelay);
        void hideUI();
        boolean isUIVisible();
    }
    
    private FullscreenCallback fullscreenCallback;
    private GestureDetector fullscreenGestureDetector;
    private boolean ignoreNextUp = false;
    
    public void setFullscreenCallback(FullscreenCallback fullscreenCallback) {
        if (fullscreenGestureDetector == null) {
            fullscreenGestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
                @Override
                public void onLongPress(MotionEvent event) {
                    FullscreenCallback fullscreenCallback = GalleryActivity.this.fullscreenCallback;
                    if (fullscreenCallback != null) {
                        if (fullscreenCallback.isUIVisible())
                            fullscreenCallback.hideUI();
                        else
                            fullscreenCallback.showUI(true);
                        ignoreNextUp = true;
                    }
                }
            });
        }
        this.fullscreenCallback = fullscreenCallback;
    }
    
    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (fullscreenCallback != null) {
            fullscreenCallback.keepUI(MotionEventCompat.getActionMasked(ev) == MotionEvent.ACTION_UP);
            fullscreenGestureDetector.onTouchEvent(ev);
        }
        if (ignoreNextUp && MotionEventCompat.getActionMasked(ev) == MotionEvent.ACTION_UP) {
            ignoreNextUp = false;
            return true;
        }
        return super.dispatchTouchEvent(ev);
    }
    
    @Override
    public void onPanelClosed(int featureId, Menu menu) {
        if (fullscreenCallback != null) fullscreenCallback.showUI(true);
        super.onPanelClosed(featureId, menu);
    }
    
    @Override
    public boolean onMenuOpened(int featureId, Menu menu) {
        if (fullscreenCallback != null) fullscreenCallback.showUI(false);
        return super.onMenuOpened(featureId, menu);
    }
    
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_BACK:
                event.startTracking();
                return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyLongPress(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_BACK:
                if (fromDialog || fromThread) {
                    remote.tryScrollParent(attachments.get(currentPosition).getRight(), fromDialog);
                }
                finish();
                return true;
        }
        return super.onKeyLongPress(keyCode, event);
    }

    private class GalleryItemViewTag {
        public CancellableTask downloadingTask;
        public Timer timer;
        public MediaPlayer audioPlayer;
        public ExoPlayer exoPlayer;
        public PlayerControlView exoController;
        public Handler indicatorHandler;
        public Runnable indicatorRunnable;
        public AttachmentModel attachmentModel;
        public String attachmentHash;
        public File file;
        
        public ImageView thumbnailView;
        public FrameLayout layout;
        public View errorView;
        public TextView errorText;
        public View loadingView;
    }
    
}
