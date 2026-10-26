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

package nya.miku.wishmaster.chans.diochan;

import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import androidx.core.content.res.ResourcesCompat;

import java.io.OutputStream;

import nya.miku.wishmaster.R;
import nya.miku.wishmaster.api.AbstractVichanModule;
import nya.miku.wishmaster.api.interfaces.CancellableTask;
import nya.miku.wishmaster.api.interfaces.ProgressListener;
import nya.miku.wishmaster.api.models.AttachmentModel;
import nya.miku.wishmaster.api.models.BoardModel;
import nya.miku.wishmaster.api.models.PostModel;
import nya.miku.wishmaster.api.models.SendPostModel;
import nya.miku.wishmaster.api.models.SimpleBoardModel;
import nya.miku.wishmaster.api.util.ChanModels;
import nya.miku.wishmaster.http.streamer.HttpWrongStatusCodeException;
import nya.miku.wishmaster.lib.org_json.JSONObject;

public class DiochanModule extends AbstractVichanModule {
    public static final String[] DOMAINS = {"www.diochan.com", "diochan.com"};
    private static final String CHAN_NAME = "www.diochan.com";
    private static final String DISPLAYING_NAME = "Diochan";
    private static final SimpleBoardModel[] BOARDS = new SimpleBoardModel[] {
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "b", "Random", "NSFW", true),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "s", "( ͡° ͜ʖ ͡°)", "NSFW", true),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "hd", "Help Desk", "NSFW", true),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "420", "Quattro Venti", "NSFW", true),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "v", "Videogiochi", "SFW", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "aff", "Affari", "SFW", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "cul", "Cultura", "SFW", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "pol", "Politica", "SFW", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "ck", "Cucina", "SFW", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "sug", "Suggerimenti & Lamentele", "Altro", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "p", "Prova", "Altro", false),
    };

    public DiochanModule(SharedPreferences preferences, Resources resources) {
        super(preferences, resources);
    }

    @Override
    protected String getUsingDomain() {
        return DOMAINS[0];
    }

    @Override
    public String getChanName() {
        return CHAN_NAME;
    }

    @Override
    public String getDisplayingName() {
        return DISPLAYING_NAME;
    }

    @Override
    protected boolean canHttps() {
        return true;
    }

    @Override
    protected boolean canCloudflare() {
        return true;
    }

    @Override
    public Drawable getChanFavicon() {
        return ResourcesCompat.getDrawable(resources, R.drawable.favicon_diochan, null);
    }

    @Override
    protected SimpleBoardModel[] getBoardsList() {
        return BOARDS;
    }

    @Override
    public BoardModel getBoard(String shortName, ProgressListener listener, CancellableTask task) throws Exception {
        BoardModel model = super.getBoard(shortName, listener, task);
        model.defaultUserName = "Anonimo";
        model.allowCustomMark = true;
        model.customMarkDescription = "Spoiler";
        model.attachmentsMaxCount = 3;
        return model;
    }

    @Override
    protected PostModel mapPostModel(JSONObject object, String boardName) {
        PostModel post = super.mapPostModel(object, boardName);
        post.sage = post.sage || post.email.equalsIgnoreCase("Salvia");
        if (object.has("embed")) {
            String embed = object.optString("embed");
            if (embed.length() > 0) {
                AttachmentModel attachment = parseEmbed(embed);
                if (attachment != null) {
                    int oldCount = post.attachments != null ? post.attachments.length : 0;
                    AttachmentModel[] attachments = new AttachmentModel[oldCount + 1];
                    if (post.attachments != null)
                        System.arraycopy(post.attachments, 0, attachments, 0, oldCount);
                    attachments[oldCount] = attachment;
                    post.attachments = attachments;
                }
            }
        }
        return post;
    }
    
    private AttachmentModel parseEmbed(String html) {
        int start, end;
        if (html.contains("<iframe") && html.contains("src=\"")) {
            start = html.indexOf("src=\"") + 5;
            end = html.indexOf("\"", start);
            html = html.substring(start, end);
        }
        String url = null, thumb = null;
        if (html.contains("youtube")) {
            html = html.replace("embed/", "watch?v=");
            start = html.indexOf("v=");
            if (start != -1) {
                String id = html.substring(start + 2);
                if (id.contains("&")) id = id.substring(0, id.indexOf("&"));
                url = "https://www.youtube.com/watch?v=" + id;
                thumb = "https://img.youtube.com/vi/" + id + "/default.jpg";
            }
        } else if (html.contains("vimeo.com")) {
            html = html.replace("video/", "");
            start = html.indexOf("vimeo.com/");
            if (start != -1) {
                url = "https://vimeo.com/" + html.substring(start + 10);
            }
        } else if (html.contains("soundcloud.com")) {
            html = html.replace("soundcloud.com/oembed", "oembed");
            start = html.indexOf("soundcloud.com/");
            if (start != -1) {
                end = html.indexOf("\"", start + 15);
                if (end != -1) {
                    url = "https://soundcloud.com/" + html.substring(start + 15, end);
                }
            }
        }
        if (url != null) {
            AttachmentModel attachment = new AttachmentModel();
            attachment.type = AttachmentModel.TYPE_OTHER_NOTFILE;
            attachment.size = -1;
            attachment.path = url;
            attachment.thumbnail = thumb;
            return attachment;
        }
        return null;
    }

    @Override
    public void downloadFile(String url, OutputStream out, ProgressListener listener, CancellableTask task) throws Exception {
        try {
            super.downloadFile(url, out, listener, task);
        } catch (HttpWrongStatusCodeException e) {
            if (e.getStatusCode() == 404 && url.contains("/thumb/")) {
                String ext = url.substring(url.lastIndexOf(".")+1).toLowerCase();
                if (!ext.equals(".webp")) {
                    String filePath = url.substring(0, url.lastIndexOf("."));
                    downloadFile(filePath + ".webp", out, listener, task);
                }
            }
        }
    }

    @Override
    public String sendPost(SendPostModel model, ProgressListener listener, CancellableTask task) throws Exception {
        super.sendPost(model, listener, task);
        return null;
    }
}
