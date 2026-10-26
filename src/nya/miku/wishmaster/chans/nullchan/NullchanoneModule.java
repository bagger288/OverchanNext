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

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.preference.EditTextPreference;
import android.preference.PreferenceGroup;
import androidx.core.content.res.ResourcesCompat;
import android.text.InputType;
import android.text.TextUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import nya.miku.wishmaster.R;
import nya.miku.wishmaster.api.interfaces.CancellableTask;
import nya.miku.wishmaster.api.interfaces.ProgressListener;
import nya.miku.wishmaster.api.models.CaptchaModel;
import nya.miku.wishmaster.http.streamer.HttpRequestModel;
import nya.miku.wishmaster.http.streamer.HttpStreamer;

public class NullchanoneModule extends AbstractInstant0chan {
    private static final String CHAN_NAME = "metator.onion";
    private static final String DEFAULT_DOMAIN = "metatorrkdagnx2njwvnzqeclsk3qbwabr6hori4vmivj25qy6s6gsad.onion";
    private static final String[] DOMAINS = new String[] { DEFAULT_DOMAIN, "metatorjq65tshfy.onion" };
    private static final String PREF_KEY_DOMAIN = "PREF_KEY_DOMAIN";
    private static final Pattern IMAGE_URL_PATTERN = Pattern.compile("<img[^>]+src=\"(.+?)\"");
    
    public NullchanoneModule(SharedPreferences preferences, Resources resources) {
        super(preferences, resources);
    }
    
    @Override
    public String getChanName() {
        return CHAN_NAME;
    }
    
    @Override
    public String getDisplayingName() {
        return "METATOR";
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
        String domain = getUsingDomain();
        for (String d : DOMAINS) {
            if (d.equals(domain)) return DOMAINS;
        }
        String[] domains = new String[DOMAINS.length + 1];
        System.arraycopy(DOMAINS, 0, domains, 0, DOMAINS.length);
        domains[DOMAINS.length] = domain;
        return domains;
    }
    
    @Override
    protected boolean canHttps() {
        return true;
    }
    
    @Override
    protected boolean useHttpsDefaultValue() {
        return false;
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
    public CaptchaModel getNewCaptcha(String boardName, String threadNumber, ProgressListener listener, CancellableTask task) throws Exception {
        String url = getUsingUrl() + "nojscaptcha.php?show=1";  //?show=ru,en,num
        String html = HttpStreamer.getInstance().getStringFromUrl(url, HttpRequestModel.DEFAULT_GET, httpClient, listener, task, false);
        Matcher matcher = IMAGE_URL_PATTERN.matcher(html);
        if (matcher.find()) {
            String captchaUrl = fixRelativeUrl(matcher.group(1));
            return downloadCaptcha(captchaUrl, listener, task);
        } else {
            throw new Exception();
        }
    }
}
