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

package nya.miku.wishmaster.chans.nullchan;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.text.DateFormat;
import java.text.DateFormatSymbols;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.preference.EditTextPreference;
import android.preference.PreferenceGroup;
import androidx.core.content.res.ResourcesCompat;
import android.text.InputType;
import android.text.TextUtils;

import nya.miku.wishmaster.R;
import nya.miku.wishmaster.api.interfaces.CancellableTask;
import nya.miku.wishmaster.api.interfaces.ProgressListener;
import nya.miku.wishmaster.api.models.AttachmentModel;
import nya.miku.wishmaster.api.models.BoardModel;
import nya.miku.wishmaster.api.models.UrlPageModel;
import nya.miku.wishmaster.api.util.WakabaReader;

public class NullchanccModule extends AbstractInstant0chan {
    private static final String CHAN_NAME = "0chan.cc";
    private static final String DEFAULT_DOMAIN = "0chan.cc";
    private static final String PREF_KEY_DOMAIN = "PREF_KEY_DOMAIN";
    
    public NullchanccModule(SharedPreferences preferences, Resources resources) {
        super(preferences, resources);
    }
    
    @Override
    public String getChanName() {
        return CHAN_NAME;
    }
    
    @Override
    public String getDisplayingName() {
        return "Øчан (0chan.cc)";
    }
    
    @Override
    public Drawable getChanFavicon() {
        return ResourcesCompat.getDrawable(resources, R.drawable.favicon_0chan, null);
    }
    
    @Override
    protected String getUsingDomain() {
        String domain = preferences.getString(getSharedKey(PREF_KEY_DOMAIN), DEFAULT_DOMAIN);
        return TextUtils.isEmpty(domain) ? DEFAULT_DOMAIN : domain;
    }
    
    @Override
    protected String[] getAllDomains() {
        if (!getChanName().equals(CHAN_NAME) || getUsingDomain().equals(DEFAULT_DOMAIN))
            return super.getAllDomains();
        return new String[] { DEFAULT_DOMAIN, getUsingDomain() };
    }
    
    @Override
    protected boolean canHttps() {
        return true;
    }
    
    @Override
    protected boolean canCloudflare() {
        return true;
    }
    
    private void addDomainPreference(PreferenceGroup group) {
        Context context = group.getContext();
        EditTextPreference domainPref = new EditTextPreference(context);
        domainPref.setTitle(R.string.pref_domain);
        domainPref.setDialogTitle(R.string.pref_domain);
        domainPref.setKey(getSharedKey(PREF_KEY_DOMAIN));
        domainPref.getEditText().setHint(DEFAULT_DOMAIN);
        domainPref.getEditText().setSingleLine();
        domainPref.getEditText().setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        group.addPreference(domainPref);
    }
    
    @Override
    public void addPreferencesOnScreen(PreferenceGroup preferenceGroup) {
        addDomainPreference(preferenceGroup);
        super.addPreferencesOnScreen(preferenceGroup);
    }
    
    @Override
    public BoardModel getBoard(String shortName, ProgressListener listener, CancellableTask task) throws Exception {
        BoardModel model = super.getBoard(shortName, listener, task);
        model.defaultUserName = "Аноним";
        return model;
    }
    
    @Override
    protected WakabaReader getKusabaReader(InputStream stream, UrlPageModel urlModel) {
        if ((urlModel != null) && (urlModel.chanName != null) && urlModel.chanName.equals("expand")) {
            stream = new SequenceInputStream(new ByteArrayInputStream("<form id=\"delform\">".getBytes()), stream);
        }
        return new NullccReader(stream, canCloudflare());
    }
    
    private static class NullccReader extends Instant0chanReader {
        private static final DateFormat DATE_FORMAT;
        static {
            DateFormatSymbols symbols = new DateFormatSymbols();
            symbols.setShortMonths(new String[] { "Янв", "Фев", "Мар", "Апр", "Май", "Июн", "Июл", "Авг", "Сен", "Окт", "Ноя", "Дек"});
            DATE_FORMAT = new SimpleDateFormat("yyyy MMM dd HH:mm:ss", symbols) {
                @Override
                public Date parse(String string) throws ParseException {
                    return super.parse(string.replaceFirst("\\D+(?=\\d{4})", ""));
                }
            };
            DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("GMT+3"));
        }
        private static final Pattern PATTERN_ORIGINAL_FILENAME = Pattern.compile("<a.+?title=\"(.+?)\"");
        
        public NullccReader(InputStream stream, boolean canCloudflare) {
            super(stream, DATE_FORMAT, canCloudflare);
        }
        
        @Override
        protected void parseAttachment(String html) {
            int oldAttachmentsSize = currentAttachments.size();
            super.parseAttachment(html);
            if (currentAttachments.size() > oldAttachmentsSize) {
                AttachmentModel attachment = currentAttachments.get(currentAttachments.size() - 1);
                if (attachment.originalName == null) {
                    Matcher matcher = PATTERN_ORIGINAL_FILENAME.matcher(html);
                    if (matcher.find()) {
                        String originalName = matcher.group(1);
                        if (originalName.length() > 0 && !attachment.path.endsWith(originalName)) {
                            attachment.originalName = originalName;
                        }
                    }
                }
            }
        }
    }
    
}
