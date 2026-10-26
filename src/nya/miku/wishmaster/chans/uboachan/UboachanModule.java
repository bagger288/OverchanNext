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

package nya.miku.wishmaster.chans.uboachan;

import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import androidx.core.content.res.ResourcesCompat;

import nya.miku.wishmaster.R;
import nya.miku.wishmaster.api.AbstractVichanModule;
import nya.miku.wishmaster.api.interfaces.CancellableTask;
import nya.miku.wishmaster.api.interfaces.ProgressListener;
import nya.miku.wishmaster.api.models.AttachmentModel;
import nya.miku.wishmaster.api.models.BoardModel;
import nya.miku.wishmaster.api.models.SendPostModel;
import nya.miku.wishmaster.api.models.SimpleBoardModel;
import nya.miku.wishmaster.api.util.ChanModels;
import nya.miku.wishmaster.lib.org_json.JSONObject;

public class UboachanModule extends AbstractVichanModule {
    private static final String CHAN_NAME = "uboachan.net";
    private static final String DEFAULT_DOMAIN = "uboachan.net";
    private static final SimpleBoardModel[] BOARDS = new SimpleBoardModel[] {
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "yn", "Yume Nikki General", "Yume Nikki", false),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "yndd", "Yume Nikki - Dream Diary", "Yume Nikki", false),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "fg", "Fangames", "Yume Nikki", false),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "yume", "Dreams", "Yume Nikki", false),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "o", "Art / Oekaki", "Arts & Entertainment", true),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "lit", "Literature / Fanfic / Poetry", "Arts & Entertainment", false),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "media", "Music / Uploads", "Arts & Entertainment", false), 
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "og", "Other Games", "Arts & Entertainment", false),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "ig", "RPGMaker / Gamedev", "Arts & Entertainment", false),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "2", "日本 ~redux~", "Arts & Entertainment", false),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "ot", "Off-topic", "Miscellaneous", true),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "cc", "Creepy-Cute", "Miscellaneous", false),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "x", "Paranormal / Occult", "Miscellaneous", false),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "sugg", "Suggestions / Meta", "Miscellaneous", false),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "hikki", "NEET / Advice", "NEET", false),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "rec", "Ex-NEET / Recovery", "NEET", false),
    };

    public UboachanModule(SharedPreferences preferences, Resources resources) {
        super(preferences, resources);
    }

    @Override
    protected String getUsingDomain() {
        return DEFAULT_DOMAIN;
    }

    @Override
    protected boolean canHttps() {
        return true;
    }

    @Override
    public String getChanName() {
        return CHAN_NAME;
    }

    @Override
    public String getDisplayingName() {
        return "Uboachan";
    }

    @Override
    public Drawable getChanFavicon() {
        return ResourcesCompat.getDrawable(resources, R.drawable.favicon_uboachan, null);
    }

    @Override
    protected SimpleBoardModel[] getBoardsList() {
        return BOARDS;
    }

    @Override
    public BoardModel getBoard(String shortName, ProgressListener listener, CancellableTask task) throws Exception {
        BoardModel model = super.getBoard(shortName, listener, task);
        model.attachmentsMaxCount = 4;
        model.allowCustomMark = true;
        model.customMarkDescription = "Spoiler";
        model.markType = BoardModel.MARK_INFINITY;
        return model;
    }

    @Override
    protected AttachmentModel mapAttachment(JSONObject object, String boardName, boolean isSpoiler) {
        AttachmentModel attachment = super.mapAttachment(object, boardName, isSpoiler);
        if (attachment != null && attachment.thumbnail != null) {
            switch (attachment.type) {
                case AttachmentModel.TYPE_VIDEO:
                    attachment.thumbnail = null;
                    break;
                case AttachmentModel.TYPE_IMAGE_STATIC:
                case AttachmentModel.TYPE_IMAGE_GIF:
                case AttachmentModel.TYPE_IMAGE_SVG:
                    attachment.thumbnail = attachment.thumbnail.substring(0, attachment.thumbnail.lastIndexOf('.')) + ".png";
                    break;
            }
        }
        return attachment;
    }

    @Override
    public String sendPost(SendPostModel model, ProgressListener listener, CancellableTask task) throws Exception {
        super.sendPost(model, listener, task);
        return null;
    }

}
