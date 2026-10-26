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

import android.os.Parcel;
import android.os.Parcelable;
import nya.miku.wishmaster.ui.settings.ApplicationSettings;
import nya.miku.wishmaster.ui.theme.GenericThemeEntry;

public class GallerySettings implements Parcelable {
    private static final int FLAG_FULLSCREEN = 1 << 0;
    private static final int FLAG_DO_NOT_DOWNLOAD_VIDEOS = 1 << 1;
    private static final int FLAG_SWIPE_TO_CLOSE = 1 << 2;
    private static final int FLAG_SCROLL_THREAD = 1 << 3;
    private static final int FLAG_SCALEIMAGEVIEW = 1 << 4;
    private static final int FLAG_NATIVE_GIF = 1 << 5;
    private static final int FLAG_INTERNAL_VIDEO_PLAYER = 1 << 6;
    private static final int FLAG_INTERNAL_AUDIO_PLAYER = 1 << 7;
    private static final int FLAG_FALLBACK_WEBVIEW = 1 << 8;
    private static final int FLAG_WEBVIEW_VIDEO_PLAYER = 1 << 9;
    private static final int FLAG_ORIGINAL_NAMES = 1 << 10;
    private static final int FLAG_AUTOPLAY_MEDIA = 1 << 11;
    private static final int FLAG_LOOP_MEDIA = 1 << 12;
    private static final int FLAG_EXOPLAYER_VIDEO_PLAYER = 1 << 13;
    private static final int FLAG_SYSTEM_VIDEO_PLAYER = 1 << 14;
    private static final int FLAG_EXO_CONTROLS_GESTURES = 1 << 15;
    private static final int FLAG_EXO_CONTROLS_SEEKBAR = 1 << 16;
    private static final int FLAG_EXO_CONTROLS_CONTROLLER = 1 << 17;
    private static final int FLAG_EXO_CONTROLS_CLASSIC = 1 << 18;
    
    private final int flags;
    private final File downloadsDir;
    private final GenericThemeEntry theme;
    
    private GallerySettings(Parcel in) {
        this.flags = in.readInt();
        this.downloadsDir = new File(in.readString());
        this.theme = GenericThemeEntry.CREATOR.createFromParcel(in);
    }
    
    private GallerySettings(int flags, File downloadsDir, GenericThemeEntry theme) {
        this.flags = flags;
        this.downloadsDir = downloadsDir;
        this.theme = theme;
    }
    
    public static final Parcelable.Creator<GallerySettings> CREATOR = new Parcelable.Creator<GallerySettings>() {
        @Override
        public GallerySettings createFromParcel(Parcel source) {
            return new GallerySettings(source);
        }
        
        @Override
        public GallerySettings[] newArray(int size) {
            return new GallerySettings[size];
        }
    };
    
    @Override
    public int describeContents() {
        return 0;
    }
    
    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(this.flags);
        dest.writeString(this.downloadsDir.getPath());
        this.theme.writeToParcel(dest, flags);
    }
    
    public static GallerySettings fromSettings(ApplicationSettings settings) {
        int flags = 0;
        if (settings.fullscreenGallery()) flags |= FLAG_FULLSCREEN;
        if (settings.doNotDownloadVideos()) flags |= FLAG_DO_NOT_DOWNLOAD_VIDEOS;
        if (settings.swipeToCloseGallery()) flags |= FLAG_SWIPE_TO_CLOSE;
        if (settings.scrollThreadFromGallery()) flags |= FLAG_SCROLL_THREAD;
        if (settings.useScaleImageView()) flags |= FLAG_SCALEIMAGEVIEW;
        if (settings.useNativeGif()) flags |= FLAG_NATIVE_GIF;
        if (settings.useInternalVideoPlayer()) flags |= FLAG_INTERNAL_VIDEO_PLAYER;
        if (settings.useInternalAudioPlayer()) flags |= FLAG_INTERNAL_AUDIO_PLAYER;
        if (settings.fallbackWebView()) flags |= FLAG_FALLBACK_WEBVIEW;
        if (settings.useWebViewVideoPlayer()) flags |= FLAG_WEBVIEW_VIDEO_PLAYER;
        if (settings.isDownloadOriginalNames()) flags |= FLAG_ORIGINAL_NAMES;
        if (settings.autoplayMedia()) flags |= FLAG_AUTOPLAY_MEDIA;
        if (settings.loopMedia()) flags |= FLAG_LOOP_MEDIA;
        if (settings.useExoPlayer()) flags |= FLAG_EXOPLAYER_VIDEO_PLAYER;
        if (settings.useSystemVideoPlayer()) flags |= FLAG_SYSTEM_VIDEO_PLAYER;
        if (settings.isExoControlsGestures()) flags |= FLAG_EXO_CONTROLS_GESTURES;
        if (settings.isExoControlsSeekBar()) flags |= FLAG_EXO_CONTROLS_SEEKBAR;
        if (settings.isExoControlsController()) flags |= FLAG_EXO_CONTROLS_CONTROLLER;
        if (settings.isExoControlsClassic()) flags |= FLAG_EXO_CONTROLS_CLASSIC;
        return new GallerySettings(flags, settings.getDownloadDirectory(), settings.getTheme());
    }
    
    public File getDownloadDirectory() {
        return downloadsDir;
    }
    
    public GenericThemeEntry getTheme() {
        return theme;
    }
    
    public boolean fullscreenGallery() {
        return (flags & FLAG_FULLSCREEN) != 0;
    }
    
    public boolean doNotDownloadVideos() {
        return (flags & FLAG_DO_NOT_DOWNLOAD_VIDEOS) != 0;
    }
    
    public boolean swipeToCloseGallery() {
        return (flags & FLAG_SWIPE_TO_CLOSE) != 0;
    }
    
    public boolean scrollThreadFromGallery() {
        return (flags & FLAG_SCROLL_THREAD) != 0;
    }
    
    public boolean useScaleImageView() {
        return (flags & FLAG_SCALEIMAGEVIEW) != 0;
    }
    
    public boolean useNativeGif() {
        return (flags & FLAG_NATIVE_GIF) != 0;
    }
    
    public boolean useInternalVideoPlayer() {
        return (flags & FLAG_INTERNAL_VIDEO_PLAYER) != 0;
    }

    public boolean useExoPlayer() {
        return (flags & FLAG_EXOPLAYER_VIDEO_PLAYER) != 0;
    }

    public boolean useSystemVideoPlayer() {
        return (flags & FLAG_SYSTEM_VIDEO_PLAYER) != 0;
    }

    public boolean isExoControlsGestures() {
        return (flags & FLAG_EXO_CONTROLS_GESTURES) != 0;
    }

    public boolean isExoControlsSeekBar() {
        return (flags & FLAG_EXO_CONTROLS_SEEKBAR) != 0;
    }

    public boolean isExoControlsController() {
        return (flags & FLAG_EXO_CONTROLS_CONTROLLER) != 0;
    }

    public boolean isExoControlsClassic() {
        return (flags & FLAG_EXO_CONTROLS_CLASSIC) != 0;
    }
    
    public boolean useInternalAudioPlayer() {
        return (flags & FLAG_INTERNAL_AUDIO_PLAYER) != 0;
    }
    
    public boolean fallbackWebView() {
        return (flags & FLAG_FALLBACK_WEBVIEW) != 0;
    }
    
    public boolean useWebViewVideoPlayer() {
        return (flags & FLAG_WEBVIEW_VIDEO_PLAYER) != 0;
    }

    public boolean isDownloadOriginalNames() {
        return (flags & FLAG_ORIGINAL_NAMES) != 0;
    }

    public boolean autoplayMedia() {
        return (flags & FLAG_AUTOPLAY_MEDIA) != 0;
    }

    public boolean loopMedia() {
        return (flags & FLAG_LOOP_MEDIA) != 0;
    }
}
