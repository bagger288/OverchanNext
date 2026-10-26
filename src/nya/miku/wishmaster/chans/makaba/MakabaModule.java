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

package nya.miku.wishmaster.chans.makaba;

import static nya.miku.wishmaster.chans.makaba.MakabaConstants.*;
import static nya.miku.wishmaster.chans.makaba.MakabaJsonMapper.*;

import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.CRC32;

import cz.msebera.android.httpclient.Header;
import cz.msebera.android.httpclient.HttpHeaders;
import cz.msebera.android.httpclient.client.entity.EntityBuilder;
import cz.msebera.android.httpclient.message.BasicHeader;
import nya.miku.wishmaster.R;
import nya.miku.wishmaster.api.CloudflareChanModule;
import nya.miku.wishmaster.api.interfaces.CancellableTask;
import nya.miku.wishmaster.api.interfaces.ProgressListener;
import nya.miku.wishmaster.api.models.BoardModel;
import nya.miku.wishmaster.api.models.CaptchaModel;
import nya.miku.wishmaster.api.models.DeletePostModel;
import nya.miku.wishmaster.api.models.PostModel;
import nya.miku.wishmaster.api.models.SendPostModel;
import nya.miku.wishmaster.api.models.SimpleBoardModel;
import nya.miku.wishmaster.api.models.ThreadModel;
import nya.miku.wishmaster.api.models.UrlPageModel;
import nya.miku.wishmaster.api.util.ChanModels;
import nya.miku.wishmaster.api.util.LazyPreferences;
import nya.miku.wishmaster.api.util.UrlPathUtils;
import nya.miku.wishmaster.common.Logger;
import nya.miku.wishmaster.http.ExtendedMultipartBuilder;
import nya.miku.wishmaster.http.hashwall.HashwallExceptionAntiDDOS;
import nya.miku.wishmaster.http.interactive.InteractiveException;
import nya.miku.wishmaster.http.recaptcha.Recaptcha2;
import nya.miku.wishmaster.http.recaptcha.Recaptcha2solved;
import nya.miku.wishmaster.http.streamer.HttpRequestModel;
import nya.miku.wishmaster.http.streamer.HttpResponseModel;
import nya.miku.wishmaster.http.streamer.HttpStreamer;
import nya.miku.wishmaster.http.streamer.HttpWrongResponseDetector;
import nya.miku.wishmaster.http.streamer.HttpWrongResponseException;
import nya.miku.wishmaster.http.streamer.HttpWrongStatusCodeException;
import nya.miku.wishmaster.lib.org_json.JSONArray;
import nya.miku.wishmaster.lib.org_json.JSONException;
import nya.miku.wishmaster.lib.org_json.JSONObject;

import cz.msebera.android.httpclient.HttpEntity;
import cz.msebera.android.httpclient.cookie.Cookie;
import cz.msebera.android.httpclient.impl.cookie.BasicClientCookie;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.widget.EditText;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceGroup;
import androidx.core.content.res.ResourcesCompat;
import android.text.InputType;
import android.util.Base64;

/**
 * Класс, осуществляющий взаимодействия с АИБ 2ch.hk (движок makaba)
 * @author miku-nyan
 *
 */

public class MakabaModule extends CloudflareChanModule {
    private static final String TAG = "MakabaModule"; 
    
    /** что-то типа '2ch.hk' */
    private String domain;
    /** что-то типа 'https://2ch.hk/' */
    private String domainUrl;
    
    /** id текущей mail.ru капчи*/
    private String captchaMailRuId;

    /** id текущей капчи*/
    private String captchaId;
    /** тип текущей капчи*/
    private int captchaType;

    private String emojiCaptchaSolvedId;
    private int emojiCaptchaClickCount = 0;
    private byte[][] emojiCaptchaCurrentButtons;
    
    /** основная карта досок */
    private Map<String, BoardModel> boardsMap = null;
    /** дополнительная карта досок (для досок, которые отсутствуют в основной карте) */
    private Map<String, BoardModel> customBoardsMap = new HashMap<>();
    
    public MakabaModule(SharedPreferences preferences, Resources resources) {
        super(preferences, resources);
        String savedDomain = preferences.getString(getSharedKey(PREF_KEY_DOMAIN), DEFAULT_DOMAIN);
        if (savedDomain == null || savedDomain.isEmpty() || savedDomain.equalsIgnoreCase("2ch.hk")) {
            savedDomain = DEFAULT_DOMAIN;
            preferences.edit().putString(getSharedKey(PREF_KEY_DOMAIN), DEFAULT_DOMAIN).commit();
        }
        updateDomain(
                savedDomain,
                preferences.getBoolean(getSharedKey(PREF_KEY_USE_HTTPS_MAKABA), true));
    }

    public boolean canHashwall() {
        return true;
    }
    
    @Override
    public String getChanName() {
        return CHAN_NAME;
    }
    
    @Override
    public String getDisplayingName() {
        return "Два.ч (2ch.su)";
    }
    
    @Override
    public Drawable getChanFavicon() {
        return ResourcesCompat.getDrawable(resources, R.drawable.favicon_makaba, null);
    }

    private static final HttpWrongResponseDetector hashwallDetector = new HttpWrongResponseDetector() {
        @Override
        public void check(final HttpResponseModel model) {
            if (model.statusCode == 303 && model.locationHeader != null) {
                throw new HttpWrongResponseException("Hashwall");
            }
            for (Header header: model.headers) {
                if (header.getName().equalsIgnoreCase("Set-Cookie")) {
                    Matcher m = PATTERN_HASHWALL_COOKIE.matcher(header.getValue());
                    if (m.find()) throw new HttpWrongResponseException("Hashwall");
                }
            }
        }
    };

    private void handleWrongResponse(String url, HttpWrongResponseException e) throws HttpWrongResponseException, InteractiveException {
        if ("Hashwall".equals(e.getMessage())) {
            String fixedUrl = fixRelativeUrl(url);
            InteractiveException hwe = HashwallExceptionAntiDDOS.newInstance(fixedUrl, getChanName());
            if (hwe != null) throw hwe;
            else throw new HttpWrongResponseException(resources.getString(R.string.error_antiddos));
        }
        throw e;
    }

    private void checkForHashwall(String url, HttpResponseModel model) throws HttpWrongResponseException, InteractiveException {
        try {
            hashwallDetector.check(model);
        } catch (HttpWrongResponseException e) {
            handleWrongResponse(url, e);
        }
    }
    
    @Override
    protected void initHttpClient() {
        super.initHttpClient();
        setCookie(
                preferences.getString(getSharedKey(PREF_KEY_USERCODE_COOKIE_DOMAIN), null),
                USERCODE_COOKIE_NAME,
                preferences.getString(getSharedKey(PREF_KEY_USERCODE_COOKIE_VALUE), null));
        setPasscodeCookie();
        if (canHashwall()) {
            String name = preferences.getString(getSharedKey(PREF_KEY_HASHWALL_COOKIE_NAME), null);
            String value = preferences.getString(getSharedKey(PREF_KEY_HASHWALL_COOKIE_VALUE), null);
            String domain = preferences.getString(getSharedKey(PREF_KEY_HASHMWALL_COOKIE_DOMAIN), null);
            if (name != null && value != null && domain != null) {
                BasicClientCookie c = new BasicClientCookie(name, value);
                c.setDomain(domain);
                httpClient.getCookieStore().addCookie(c);
            }
        }
    }
    
    private void updateDomain(String domain, boolean useHttps) {
        if (domain.endsWith("/")) domain = domain.substring(0, domain.length() - 1);
        if (domain.contains("//")) domain = domain.substring(domain.indexOf("//") + 2);
        if (domain.equals("") || domain.equalsIgnoreCase("2ch.hk")) domain = DEFAULT_DOMAIN;
        this.domain = domain;
        this.domainUrl = (useHttps ? "https://" : "http://") + domain + "/";
    }
    
    /** Установить cookie к текущему клиенту */
    private void setCookie(String domain, String name, String value) {
        if (value == null || value.equals("")) return;
        BasicClientCookie c = new BasicClientCookie(name, value);
        c.setDomain(domain == null || domain.equals("") ? ("." + this.domain) : domain);
        c.setPath("/");
        httpClient.getCookieStore().addCookie(c);
    }
    
    @Override
    public void saveCookie(Cookie cookie) {
        super.saveCookie(cookie);
        if (cookie != null) {
            if (cookie.getName().equals(USERCODE_COOKIE_NAME)) {
                preferences.edit().
                        putString(getSharedKey(PREF_KEY_USERCODE_COOKIE_DOMAIN), cookie.getDomain()).
                        putString(getSharedKey(PREF_KEY_USERCODE_COOKIE_VALUE), cookie.getValue()).commit();
            } else if (canHashwall() && "Hashwall".equals(cookie.getComment())) {
                preferences.edit().
                        putString(getSharedKey(PREF_KEY_HASHWALL_COOKIE_NAME), cookie.getName()).
                        putString(getSharedKey(PREF_KEY_HASHWALL_COOKIE_VALUE), cookie.getValue()).
                        putString(getSharedKey(PREF_KEY_HASHMWALL_COOKIE_DOMAIN), cookie.getDomain()).commit();
            }
        }
    }
    
    @Override
    public void clearCookies() {
        super.clearCookies();
        preferences.edit().
            remove(getSharedKey(PREF_KEY_USERCODE_COOKIE_DOMAIN)).
            remove(getSharedKey(PREF_KEY_USERCODE_COOKIE_VALUE)).
            remove(getSharedKey(PREF_KEY_NOCAPTCHA_COOKIE_DOMAIN)).
            remove(getSharedKey(PREF_KEY_NOCAPTCHA_COOKIE_VALUE)).
            remove(getSharedKey(PREF_KEY_HASHWALL_COOKIE_NAME)).
            remove(getSharedKey(PREF_KEY_HASHWALL_COOKIE_VALUE)).
            remove(getSharedKey(PREF_KEY_HASHMWALL_COOKIE_DOMAIN)).
            commit();
    }

    private void saveUsercodeCookie() {
        for (Cookie cookie : httpClient.getCookieStore().getCookies()) {
            if (cookie.getName().equals(USERCODE_COOKIE_NAME) && cookie.getDomain().contains(domain)) saveCookie(cookie);
        }
    }
    
    private void setPasscodeCookie() {
        setCookie(
                preferences.getString(getSharedKey(PREF_KEY_NOCAPTCHA_COOKIE_DOMAIN), null),
                USERCODE_NOCAPTCHA_COOKIE_NAME,
                preferences.getString(getSharedKey(PREF_KEY_NOCAPTCHA_COOKIE_VALUE), null));
    }

    private void addMobileAPIPreference(PreferenceGroup group) {
        final Context context = group.getContext();
        CheckBoxPreference mobileAPIPref = new LazyPreferences.CheckBoxPreference(context);
        mobileAPIPref.setTitle(R.string.makaba_prefs_mobile_api);
        mobileAPIPref.setSummary(R.string.pref_only_new_posts_summary);
        mobileAPIPref.setKey(getSharedKey(PREF_KEY_MOBILE_API));
        mobileAPIPref.setDefaultValue(true);
        group.addPreference(mobileAPIPref);
    }
    
    private void addCaptchaPreferences(PreferenceGroup group) {
        final Context context = group.getContext();
        final ListPreference captchaPreference = new LazyPreferences.ListPreference(context); //captcha_type
        captchaPreference.setTitle(R.string.pref_captcha_type);
        captchaPreference.setDialogTitle(R.string.pref_captcha_type);
        captchaPreference.setKey(getSharedKey(PREF_KEY_CAPTCHA_TYPE));
        captchaPreference.setEntryValues(CAPTCHA_TYPES_KEYS);
        captchaPreference.setEntries(CAPTCHA_TYPES);
        captchaPreference.setDefaultValue(CAPTCHA_TYPE_DEFAULT);
        int i = Arrays.asList(CAPTCHA_TYPES_KEYS).indexOf(preferences.getString(getSharedKey(PREF_KEY_CAPTCHA_TYPE), CAPTCHA_TYPE_DEFAULT));
        if (i >= 0) captchaPreference.setSummary(CAPTCHA_TYPES[i]);
        group.addPreference(captchaPreference);
    }

    private void addPasscodePreferences(PreferenceGroup group) {
        final Context context = group.getContext();
        EditTextPreference passcodePref = new EditTextPreference(context);
        passcodePref.setTitle(R.string.pref_makaba_passcode);
        passcodePref.setKey(getSharedKey(PREF_KEY_PASSCODE));
        group.addPreference(passcodePref);
    }

    public boolean getCaptchaAutoUpdatePreference(){
        return preferences.getBoolean(getSharedKey(PREF_KEY_CAPTCHA_AUTO_UPDATE), false);
    }
    
    /** Добавить категорию настроек домена (в т.ч. https) */
    private void addDomainPreferences(PreferenceGroup group) {
        final Context context = group.getContext();
        PreferenceCategory domainCat = new PreferenceCategory(context);
        domainCat.setTitle(R.string.makaba_prefs_domain_category);
        group.addPreference(domainCat);
        
        final ListPreference domainPref = new LazyPreferences.ListPreference(context);
        domainPref.setTitle(R.string.pref_domain);
        domainPref.setDialogTitle(R.string.pref_domain);
        domainPref.setKey(getSharedKey(PREF_KEY_DOMAIN));
        
        final String[] entries = new String[] {
            "2ch.su (по умолчанию)",
            "2ch.org",
            "2ch.life",
            "Ввести вручную..."
        };
        final String[] entryValues = new String[] {
            "2ch.su",
            "2ch.org",
            "2ch.life",
            "custom"
        };
        domainPref.setEntries(entries);
        domainPref.setEntryValues(entryValues);
        domainPref.setDefaultValue(DEFAULT_DOMAIN);
        domainPref.setSummary(this.domain + "\n" + resources.getString(R.string.pref_domain_summary, DOMAINS_HINT));
        domainPref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                String val = (String) newValue;
                if ("custom".equals(val)) {
                    final EditText input = new EditText(context);
                    input.setSingleLine();
                    input.setText(MakabaModule.this.domain);
                    new AlertDialog.Builder(context)
                        .setTitle(R.string.pref_domain)
                        .setView(input)
                        .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                String customVal = input.getText().toString().trim();
                                if (customVal.isEmpty() || customVal.equalsIgnoreCase("2ch.hk")) customVal = DEFAULT_DOMAIN;
                                preferences.edit().putString(getSharedKey(PREF_KEY_DOMAIN), customVal).commit();
                                updateDomain(customVal, preferences.getBoolean(getSharedKey(PREF_KEY_USE_HTTPS_MAKABA), true));
                                domainPref.setValue(customVal);
                                domainPref.setSummary(customVal + "\n" + resources.getString(R.string.pref_domain_summary, DOMAINS_HINT));
                            }
                        })
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
                    return false;
                } else {
                    updateDomain(val, preferences.getBoolean(getSharedKey(PREF_KEY_USE_HTTPS_MAKABA), true));
                    domainPref.setSummary(val + "\n" + resources.getString(R.string.pref_domain_summary, DOMAINS_HINT));
                    return true;
                }
            }
        });
        domainCat.addPreference(domainPref);
        
        CheckBoxPreference httpsPref = new LazyPreferences.CheckBoxPreference(context);
        httpsPref.setTitle(R.string.pref_use_https);
        httpsPref.setSummary(R.string.pref_use_https_summary);
        httpsPref.setKey(getSharedKey(PREF_KEY_USE_HTTPS_MAKABA));
        httpsPref.setDefaultValue(true);
        httpsPref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                updateDomain(preferences.getString(getSharedKey(PREF_KEY_DOMAIN), DEFAULT_DOMAIN), (boolean) newValue);
                return true;
            }
        });
        domainCat.addPreference(httpsPref);
    }
    
    @Override
    public void addPreferencesOnScreen(final PreferenceGroup preferenceScreen) {
        addMobileAPIPreference(preferenceScreen);
        addCaptchaPreferences(preferenceScreen);
        addPasscodePreferences(preferenceScreen);
        addCaptchaAutoUpdatePreference(preferenceScreen);
        addDomainPreferences(preferenceScreen);
        addProxyPreferences(preferenceScreen);
        addClearCookiesPreference(preferenceScreen);
    }

   private boolean checkPasscode(String boardName, ProgressListener listener, CancellableTask task) {
       if (preferences.getString(getSharedKey(PREF_KEY_NOCAPTCHA_COOKIE_VALUE), "").equals("")) return false;
       setPasscodeCookie();
       String captchaUrl = domainUrl + "api/captcha/" +  getUsingCaptchaKey() + "/id?board=" + boardName;
       JSONObject jsonResponse;
       try {
           jsonResponse = downloadJSONObject(captchaUrl, false, listener, task);
       } catch (Exception e) {
           return false;
       }
       return jsonResponse.optInt("result", -1) == 2;
   }

    private boolean obtainNocaptchaUsercode(String boardName, ProgressListener listener, CancellableTask task) throws Exception {
        String passcode = preferences.getString(getSharedKey(PREF_KEY_PASSCODE), "");
        if (!passcode.equals("") && checkPasscode(boardName, listener, task)) return true;

        String url;
        ExtendedMultipartBuilder postEntityBuilder = ExtendedMultipartBuilder.create().setDelegates(listener, task);
        if (passcode.equals("")) {
            if (preferences.getString(getSharedKey(PREF_KEY_NOCAPTCHA_COOKIE_VALUE), "").equals("")) {
                setPasscodeCookie();
                return false;
            }
            url = domainUrl + "user/passlogout?json=1";
        } else {
            url = domainUrl + "user/passlogin?json=1";
            postEntityBuilder.addString("passcode", passcode);
        }

        HttpRequestModel request = HttpRequestModel.builder().setPOST(postEntityBuilder.build()).setNoRedirect(true).build();
        try {
            JSONObject jsonResponse = HttpStreamer.getInstance().getJSONObjectFromUrl(url, request, httpClient, null, task, true,
                    (canHashwall() ? hashwallDetector : null));
            if (jsonResponse.getInt("result") == 0) {
                throw new Exception(jsonResponse.getJSONObject("error").getString("message"));
            }
        } catch (HttpWrongStatusCodeException e) {
            if (canCloudflare()) checkCloudflareError(e, url);
            throw e;
        } catch (HttpWrongResponseException he) {
            handleWrongResponse(url, he);
        }

        String passcodeCookie = "";
        String passcodeDomain = "";
        for (Cookie cookie : httpClient.getCookieStore().getCookies()) {
            if (cookie.getName().equals(USERCODE_NOCAPTCHA_COOKIE_NAME) && cookie.getDomain().contains(domain)) {
                passcodeCookie = cookie.getValue();
                passcodeDomain = cookie.getDomain();
                break;
            }
        }
        preferences.edit()
                .putString(getSharedKey(PREF_KEY_NOCAPTCHA_COOKIE_VALUE), passcodeCookie)
                .putString(getSharedKey(PREF_KEY_NOCAPTCHA_COOKIE_DOMAIN), passcodeDomain)
                .commit();
        return checkPasscode(boardName, listener, task);
    }

    private int getUsingCaptchaType() {
        String key = preferences.getString(getSharedKey(PREF_KEY_CAPTCHA_TYPE), CAPTCHA_TYPE_DEFAULT);
        if (!Arrays.asList(CAPTCHA_TYPES_KEYS).contains(key)) key = CAPTCHA_TYPE_DEFAULT;
        switch (key) {
            case "2chcaptcha":
                return CAPTCHA_2CHAPTCHA;
            case "recaptcha":
                return CAPTCHA_RECAPTCHA;
            case "recaptcha-fallback":
                return CAPTCHA_RECAPTCHA_FALLBACK;
            case "mailru":
                return CAPTCHA_MAILRU;
            case "emoji":
                return CAPTCHA_EMOJI;
        }
        throw new IllegalStateException("wrong captcha settings");
    }

    private String getUsingCaptchaKey() {
        switch(getUsingCaptchaType()) {
            case CAPTCHA_2CHAPTCHA:
                return "2chcaptcha";
            case CAPTCHA_RECAPTCHA:
            case CAPTCHA_RECAPTCHA_FALLBACK:
                return "recaptcha";
            case CAPTCHA_MAILRU:
                return "mailru";
            case CAPTCHA_EMOJI:
                return "emoji";
            default:
                return CAPTCHA_TYPE_DEFAULT;
        }
    }
    
    @Override
    public SimpleBoardModel[] getBoardsList(ProgressListener listener, CancellableTask task, SimpleBoardModel[] oldBoardsList) throws Exception {
        List<SimpleBoardModel> list = new ArrayList<>();
        Map<String, BoardModel> newMap = new HashMap<>();

        String url = domainUrl + "api/mobile/v2/boards";

        JSONArray mobileBoardsList = downloadJSONArray(url, (oldBoardsList != null && this.boardsMap != null), listener, task);
        if (mobileBoardsList == null) return oldBoardsList;

        for (int i = 0, len=mobileBoardsList.length(); i < len; i++) {
            BoardModel model = mapBoardModel(mobileBoardsList.getJSONObject(i), true, resources);
            newMap.put(model.boardName, model);
            list.add(new SimpleBoardModel(model));
        }
        this.boardsMap = newMap;

        Collections.sort(list, new Comparator<SimpleBoardModel>() {
            @Override
            public int compare(SimpleBoardModel b1, SimpleBoardModel b2) {
                return b1.boardName.compareTo(b2.boardName);
            }
        });

        SimpleBoardModel[] result = new SimpleBoardModel[list.size()];
        boolean[] copied = new boolean[list.size()];
        int curIndex = 0;
        for (String category : CATEGORIES) {
            for (int i=0; i<list.size(); ++i) {
                if (list.get(i).boardCategory.equals(category)) {
                    result[curIndex++] = list.get(i);
                    copied[i] = true;
                }
            }
        }
        for (int i=0; i<list.size(); ++i) {
            if (!copied[i]) {
                result[curIndex++] = list.get(i);
            }
        }

        return result;
    }

    @Override
    public BoardModel getBoard(String shortName, ProgressListener listener, CancellableTask task) throws Exception {
        try {
            if (this.boardsMap == null) {
                try {
                    getBoardsList(listener, task, null);
                } catch (Exception e) {
                    Logger.d(TAG, "cannot update boards list from mobile API");
                }
            }
            if (this.boardsMap != null) {
                if (this.boardsMap.containsKey(shortName)) {
                    return this.boardsMap.get(shortName);
                }
            }

            if (this.customBoardsMap.containsKey(shortName)) {
                return this.customBoardsMap.get(shortName);
            }

            String url = domainUrl + shortName + "/index.json";
            JSONObject json;
            try {
                json = downloadJSONObject(url, false, listener, task);
            } finally {
                HttpStreamer.getInstance().removeFromModifiedMap(url);
            }
            BoardModel result = mapBoardModel(json, false, resources);
            if (!shortName.equals(result.boardName)) throw new Exception();
            this.customBoardsMap.put(result.boardName, result);
            return result;
        } catch (Exception e) {
            Logger.e(TAG, e);
            return defaultBoardModel(shortName, resources);
        }
    }

    @Override
    public ThreadModel[] getThreadsList(String boardName, int page, ProgressListener listener, CancellableTask task, ThreadModel[] oldList)
            throws Exception {
        String url = domainUrl + boardName + "/" + (page == 0 ? "index" : Integer.toString(page)) + ".json";
        JSONObject index = downloadJSONObject(url, (oldList != null), listener, task);
        if (index == null) return oldList;

        try { // кэширование модели BoardModel во время загрузки списка тредов
            BoardModel boardModel = mapBoardModel(index, false, resources);
            if (boardName.equals(boardModel.boardName)) {
                this.customBoardsMap.put(boardModel.boardName, boardModel);
            }
        } catch (Exception e) { /* если не получилось сейчас замапить модель доски, и фиг с ней */ }

        JSONArray threads = index.getJSONArray("threads");
        ThreadModel[] result = new ThreadModel[threads.length()];
        for (int i=0; i<threads.length(); ++i) {
            result[i] = mapThreadModel(threads.getJSONObject(i), boardName);
        }
        return result;
    }

    @Override
    public ThreadModel[] getCatalog(String boardName, int catalogType, ProgressListener listener, CancellableTask task, ThreadModel[] oldList)
            throws Exception {
        Exception last = null;
        for (String url : new String[] {
                domainUrl + boardName + "/" + CATALOG_TYPES[catalogType] + ".json",
                domainUrl + boardName + "/catalog.json"
        }) {
            try {
                JSONObject json = downloadJSONObject(url, (oldList != null), listener, task);
                if (json == null) return oldList;
                JSONArray threads = json.getJSONArray("threads");
                ThreadModel[] result = new ThreadModel[threads.length()];
                for (int i=0; i<threads.length(); ++i) {
                    JSONObject curThread = threads.getJSONObject(i);
                    ThreadModel model = new ThreadModel();
                    model.posts = new PostModel[] { mapPostModel(curThread, boardName) };
                    model.threadNumber = model.posts[0].number;
                    model.postsCount = curThread.optInt("posts_count", -1);
                    model.attachmentsCount = curThread.optInt("files_count", -1);
                    model.isSticky = curThread.optInt("sticky", 0) != 0;
                    model.isClosed = curThread.optInt("closed", 0) != 0;
                    model.isCyclical = curThread.optInt("endless", 0) != 0;
                    result[i] = model;
                }
                return result;
            } catch (HttpWrongStatusCodeException cf) {
                checkCloudflareError(cf, url);
                last = cf;
            } catch (Exception e) {
                last = e;
            }
        }
        throw last;
    }

    @Override
    public PostModel[] getPostsList(String boardName, String threadNumber, ProgressListener listener, CancellableTask task, PostModel[] oldList)
            throws Exception {
        boolean mobileAPI = preferences.getBoolean(getSharedKey(PREF_KEY_MOBILE_API), true);
        if (!mobileAPI || oldList == null || oldList.length == 0) {
            String url = domainUrl + boardName + "/res/" + threadNumber + ".json";
            JSONObject object = downloadJSONObject(url, (oldList != null), listener, task);
            if (object == null) return oldList;
            JSONArray postsArray = object.getJSONArray("threads").getJSONObject(0).getJSONArray("posts");
            PostModel[] posts = new PostModel[postsArray.length()];
            for (int i=0; i<postsArray.length(); ++i) {
                posts[i] = mapPostModel(postsArray.getJSONObject(i), boardName);
            }
            if (oldList != null) {
                posts = ChanModels.mergePostsLists(Arrays.asList(oldList), Arrays.asList(posts));
            }
            return posts;
        }
        try {
            String lastPost = oldList[oldList.length-1].number;
            String url = domainUrl + "api/mobile/v2/after/" + boardName + "/" + threadNumber + "/" + lastPost;
            JSONObject object = downloadJSONObject(url, false, listener, task);
            if (object.getInt("result") == 0) {
                throw new Exception(object.getJSONObject("error").getString("message"));
            }
            JSONArray newPostsArray = object.getJSONArray("posts");
            PostModel[] newPosts = new PostModel[newPostsArray.length()];
            for (int i=0; i<newPostsArray.length(); ++i) {
                newPosts[i] = mapPostModel(newPostsArray.getJSONObject(i), boardName);
            }
            long lastNum = Long.parseLong(lastPost);
            ArrayList<PostModel> list = new ArrayList<>(Arrays.asList(oldList));
            for (PostModel newPost : newPosts) {
                if (Long.parseLong(newPost.number) > lastNum) {
                    list.add(newPost);
                }
            }
            return list.toArray(new PostModel[0]);
        } catch (JSONException e) {
            throw new Exception("Mobile API error");
        }
    }

    @Override
    public PostModel[] search(String boardName, String searchRequest, ProgressListener listener, CancellableTask task) throws Exception {
        if (searchRequest.startsWith(HASHTAG_PREFIX)) {
            String hashtag = " /" + searchRequest.substring(HASHTAG_PREFIX.length()) + "/";
            ThreadModel[] threads = getCatalog(boardName, 0, listener, task, null);
            List<PostModel> posts = new ArrayList<>();
            for (ThreadModel thread : threads) {
                if (thread.posts[0].subject.contains(hashtag))
                    posts.add(thread.posts[0]);
            }
            return posts.toArray(new PostModel[0]);
        } else {
            String url = domainUrl + "user/search?json=1";
            HttpEntity postEntity = ExtendedMultipartBuilder.create().
                    addString("board", boardName).
                    addString("text", searchRequest).
                    build();
            HttpRequestModel request = HttpRequestModel.builder().setPOST(postEntity).setNoRedirect(true).build();
            try {
                JSONObject response = HttpStreamer.getInstance().getJSONObjectFromUrl(url, request, httpClient, listener, task, true,
                        (canHashwall() ? hashwallDetector : null));
                if (listener != null) listener.setIndeterminate();
                if (response.has("error")) {
                    throw new Exception(response.getJSONObject("error").getString("message"));
                }
                JSONArray posts = response.optJSONArray("posts");
                if (posts == null || posts.length() == 0) throw new Exception("Ничего не найдено!");
                PostModel[] result = new PostModel[posts.length()];
                for (int i=0; i<posts.length(); ++i) {
                    result[i] = mapPostModel(posts.getJSONObject(i), boardName);
                }
                return result;
            } catch (HttpWrongStatusCodeException e) {
                checkCloudflareError(e, url);
                throw e;
            } catch (HttpWrongResponseException he) {
                handleWrongResponse(url, he);
            }
            throw new Exception("Unknown error");
        }
    }

    @Override
    public CaptchaModel getNewCaptcha(String boardName, String threadNumber, ProgressListener listener, CancellableTask task) throws Exception {
        if (obtainNocaptchaUsercode(boardName, listener, task)) {
            return null;
        }
        int captchaType = getUsingCaptchaType();
        String captchaKey = getUsingCaptchaKey();

        String url;

        if(captchaType != CAPTCHA_EMOJI)
            url = domainUrl + "api/captcha/" + captchaKey + "/id?board=" + boardName + (threadNumber != null ? "&thread=" + threadNumber : "");
        else
            url = domainUrl + "api/captcha/emoji/id?board=" + boardName + (threadNumber != null ? "&thread=" + threadNumber : "");
        JSONObject response = downloadJSONObject(url, false, listener, task);
        switch (response.optInt("result", -1)) {
            case 1: //Enabled
                CaptchaModel captchaModel;
                captchaMailRuId = null;
                this.captchaType = captchaType;
                captchaId = response.optString("id");
                switch (captchaType) {
                    case CAPTCHA_2CHAPTCHA:
                        url = domainUrl + "api/captcha/" + captchaKey + "/show?id=" + captchaId;
                        captchaModel = downloadCaptcha(url, listener, task);
                        captchaModel.type = CaptchaModel.TYPE_NORMAL;
                        return captchaModel;
                    case CAPTCHA_RECAPTCHA:
                    case CAPTCHA_RECAPTCHA_FALLBACK:
                        return null;
                    case CAPTCHA_MAILRU:
                        UrlPageModel refererPage = new UrlPageModel();
                        refererPage.chanName = CHAN_NAME;
                        refererPage.boardName = boardName;
                        if (threadNumber == null) {
                            refererPage.type = UrlPageModel.TYPE_BOARDPAGE;
                            refererPage.boardPage = 0;
                        } else {
                            refererPage.type = UrlPageModel.TYPE_THREADPAGE;
                            refererPage.threadNumber = threadNumber;
                        }
                        String refererUrl = buildUrl(refererPage);
                        Header[] customHeaders = new Header[] { new BasicHeader(HttpHeaders.REFERER, refererUrl) };
                        captchaKey = response.optString("id");
                        String jsUrl = MAILRU_JS_URL + captchaKey;
                        Bitmap captchaBitmap = null;
                        HttpRequestModel requestModel = HttpRequestModel.builder().setGET().setCustomHeaders(customHeaders).build();
                        String jsResponse = HttpStreamer.getInstance().getStringFromUrl(jsUrl, requestModel, httpClient, listener, task, true);
                        Matcher mailRuIdMatcher = MAILRU_ID_PATTERN.matcher(jsResponse);
                        if (!mailRuIdMatcher.find()) throw new Exception("Couldn't get Mail.Ru captcha ID");
                        captchaMailRuId = mailRuIdMatcher.group(1);

                        Matcher mailRuUrlMatcher = MAILRU_URL_PATTERN.matcher(jsResponse);
                        String captchaUrl = mailRuUrlMatcher.find() ? mailRuUrlMatcher.group(1) : MAILRU_DEFAULT_CAPTCHA_URL;
                        HttpResponseModel responseModel = HttpStreamer.getInstance().getFromUrl(captchaUrl, requestModel, httpClient, listener, task);
                        try {
                            InputStream imageStream = responseModel.stream;
                            captchaBitmap = BitmapFactory.decodeStream(imageStream);
                        } finally {
                            responseModel.release();
                        }
                        captchaModel = new CaptchaModel();
                        captchaModel.type = CaptchaModel.TYPE_NORMAL;
                        captchaModel.bitmap = captchaBitmap;
                        return captchaModel;
                    case CAPTCHA_EMOJI:
                        Logger.d(TAG, "new emojiCaptcha Id=" + captchaId);
                        emojiCaptchaClickCount = 0;
                        emojiCaptchaSolvedId = "";
                        url = domainUrl + "api/captcha/emoji/show?id=" + captchaId;
                        JSONObject emojiCaptchaStateResponse = downloadJSONObject(url, false, listener, task);
                        String base64EncodedCaptcha = emojiCaptchaStateResponse.optString("image");
                        byte[] decodedCaptchaBytes = android.util.Base64.decode(base64EncodedCaptcha, Base64.DEFAULT);
                        captchaModel = new CaptchaModel();
                        captchaModel.type = CaptchaModel.TYPE_NORMAL;
                        captchaModel.bitmap = BitmapFactory.decodeByteArray(decodedCaptchaBytes, 0, decodedCaptchaBytes.length);
                        captchaModel.emoji = true;
                        captchaModel.emojiCaptchaButtons = new Bitmap[8];
                        CRC32 crc = new CRC32();
                        emojiCaptchaCurrentButtons = new byte[8][];
                        for(int i = 0; i < 8; i++)
                        {
                            String base64EncodedCaptchaButton = emojiCaptchaStateResponse.getJSONArray("keyboard").optString(i);
                            byte[] decodedCaptchaButtonBytes = android.util.Base64.decode(base64EncodedCaptchaButton, Base64.DEFAULT);
                            emojiCaptchaCurrentButtons[i] = decodedCaptchaButtonBytes;
                            crc.update(decodedCaptchaButtonBytes);
                            Logger.d(TAG, "got step0 emojiCaptchaButton " + i + " hash=" + Long.toHexString(crc.getValue()));
                            crc.reset();
                            captchaModel.emojiCaptchaButtons[i] = BitmapFactory.decodeByteArray(decodedCaptchaButtonBytes, 0, decodedCaptchaButtonBytes.length);
                        }
                        return captchaModel;
                    default:
                        throw new IllegalStateException();
                }
            case 2: //VIP
            case 3: //Disabled
                this.captchaType = CAPTCHA_DISABLED;
                return null;
            case 0: //Fail
                throw new Exception(response.getJSONObject("error").getString("message"));
            default:
                throw new Exception("Invalid captcha response");
        }
    }
    
    @Override
    public String sendPost(SendPostModel model, ProgressListener listener, CancellableTask task) throws Exception {
        String usercode_nocaptcha = preferences.getString(getSharedKey(PREF_KEY_NOCAPTCHA_COOKIE_VALUE), "");

        String url = domainUrl + "user/posting";
        ExtendedMultipartBuilder postEntityBuilder = ExtendedMultipartBuilder.create().setDelegates(listener, task).
                setCharset(Charset.forName("UTF-8")).
                addString("task", "post").
                addString("board", model.boardName).
                addString("thread", model.threadNumber == null ? "0" : model.threadNumber);
        
        postEntityBuilder.addString("comment", model.comment);
        if (model.captchaAnswer.equals("") && !usercode_nocaptcha.equals("")) {
            postEntityBuilder.addString("usercode", usercode_nocaptcha);
        }
        String recaptcha2 = null;
        if (usercode_nocaptcha.equals("")) {
            switch (captchaType) {
                case CAPTCHA_2CHAPTCHA:
                    postEntityBuilder.
                        addString("captcha_type", "2chcaptcha").
                        addString("2chcaptcha_id", captchaId).
                        addString("2chcaptcha_value", model.captchaAnswer);
                    break;
                case CAPTCHA_RECAPTCHA:
                case CAPTCHA_RECAPTCHA_FALLBACK:
                    recaptcha2 = Recaptcha2solved.pop(captchaId);
                    if (recaptcha2 == null) {
                        boolean fallback = getUsingCaptchaType() == CAPTCHA_RECAPTCHA_FALLBACK;
                        throw Recaptcha2.obtain(domainUrl, captchaId, null, CHAN_NAME, fallback);
                    }
                    postEntityBuilder.
                        addString("captcha_type", "recaptcha").
                        addString("2chcaptcha_id", captchaId).
                        addString("g-recaptcha-response", recaptcha2);
                    break;
                case CAPTCHA_MAILRU:
                    if (captchaMailRuId != null) {
                        postEntityBuilder.addString("captcha_id", captchaMailRuId);
                        postEntityBuilder.addString("captcha_value", model.captchaAnswer);
                        postEntityBuilder.addString("captcha_type", "mailru");
                        postEntityBuilder.addString("2chcaptcha_id", captchaId);
                    }
                    break;
                case CAPTCHA_EMOJI:
                    postEntityBuilder.addString("captcha_type", "emoji_captcha");
                    postEntityBuilder.addString("emoji_captcha_id", emojiCaptchaSolvedId);
                    break;
            }
        }
        if (task != null && task.isCancelled()) throw new InterruptedException("interrupted");
        
        if (model.subject != null) postEntityBuilder.addString("subject", model.subject);
        if (model.name != null) postEntityBuilder.addString("name", model.name);
        if (model.sage) postEntityBuilder.addString("email", "sage");
        else if (model.email != null) postEntityBuilder.addString("email", model.email);
        
        if (model.attachments != null) {
            for (int i=0; i<model.attachments.length; ++i) {
                postEntityBuilder.addFile("file[]", model.attachments[i], model.randomHash);
            }
        }
        
        if (model.icon != -1) postEntityBuilder.addString("icon", Integer.toString(model.icon));

        if (model.custommark) postEntityBuilder.addString("op_mark", "1");
        
        HttpRequestModel request = HttpRequestModel.builder().setPOST(postEntityBuilder.build()).build();
        String response = null;
        try {
            response = HttpStreamer.getInstance().getStringFromUrl(url, request, httpClient, null, task, true,
                    (canHashwall() ? hashwallDetector : null));
        } catch (HttpWrongStatusCodeException e) {
            checkCloudflareError(e, url);
            throw e;
        } catch (HttpWrongResponseException he) {
            if (recaptcha2 != null) Recaptcha2solved.push(captchaId, recaptcha2);
            handleWrongResponse(url, he);
        }
        saveUsercodeCookie();
        JSONObject makabaResult = new JSONObject(response);
        if (makabaResult.optInt("result", 0) == 1) {
            try {
                UrlPageModel redirect = new UrlPageModel();
                redirect.type = UrlPageModel.TYPE_THREADPAGE;
                redirect.chanName = CHAN_NAME;
                redirect.boardName = model.boardName;
                if (model.threadNumber != null) {
                    redirect.threadNumber = model.threadNumber;
                    redirect.postNumber = Long.toString(makabaResult.getLong("num"));
                } else {
                    redirect.threadNumber = Long.toString(makabaResult.getLong("thread"));
                }
                return buildUrl(redirect);
            } catch (Exception e) {
                Logger.e(TAG, e);
            }
            return null;
        }
        throw new Exception(makabaResult.getJSONObject("error").getString("message"));
    }
    
    @Override
    public String reportPost(DeletePostModel model, ProgressListener listener, CancellableTask task) throws Exception {
        String url = domainUrl + "user/report";
        ExtendedMultipartBuilder postEntityBuilder = ExtendedMultipartBuilder.create().setDelegates(listener, task).
                addString("board", model.boardName).
                addString("thread", model.threadNumber).
                addString("post", model.postNumber).
                addString("comment", model.reportReason);
        
        HttpRequestModel request = HttpRequestModel.builder().setPOST(postEntityBuilder.build()).setNoRedirect(true).build();
        String response = null;
        try {
            response = HttpStreamer.getInstance().getStringFromUrl(url, request, httpClient, null, task, true,
                    (canHashwall() ? hashwallDetector : null));
        } catch (HttpWrongStatusCodeException e) {
            checkCloudflareError(e, url);
            throw e;
        } catch (HttpWrongResponseException he) {
            handleWrongResponse(url, he);
        }
        try {
            JSONObject json = new JSONObject(response);
            if (json.getInt("result") == 1) return null;
            throw new Exception(json.getJSONObject("error").getString("message"));
        } catch (JSONException e) {
            Logger.e(TAG, e);
            throw new Exception(response);
        }
    }

    @Override
    public String buildUrl(UrlPageModel model) throws IllegalArgumentException {
        if (!model.chanName.equals(CHAN_NAME)) throw new IllegalArgumentException("wrong chan");
        if (model.boardName != null && !model.boardName.matches("\\w*")) throw new IllegalArgumentException("wrong board name");
        StringBuilder url = new StringBuilder(this.domainUrl);
        switch (model.type) {
            case UrlPageModel.TYPE_INDEXPAGE:
                break;
            case UrlPageModel.TYPE_BOARDPAGE:
                url.append(model.boardName).append("/");
                if (model.boardPage == UrlPageModel.DEFAULT_FIRST_PAGE) model.boardPage = 0;
                if (model.boardPage != 0) url.append(model.boardPage).append(".html");
                break;
            case UrlPageModel.TYPE_CATALOGPAGE:
                url.append(model.boardName).append("/").append(CATALOG_TYPES[model.catalogType]).append(".html");
                break;
            case UrlPageModel.TYPE_SEARCHPAGE:
                if (model.searchRequest.startsWith(HASHTAG_PREFIX))
                    url.append(model.boardName).append("/catalog.html").append(model.searchRequest);
                break;
            case UrlPageModel.TYPE_THREADPAGE:
                url.append(model.boardName).append("/res/").append(model.threadNumber).append(".html");
                if (model.postNumber != null && model.postNumber.length() != 0) url.append("#").append(model.postNumber);
                break;
            case UrlPageModel.TYPE_OTHERPAGE:
                url.append(model.otherPath.startsWith("/") ? model.otherPath.substring(1) : model.otherPath);
        }
        return url.toString();
    }

    @Override
    public UrlPageModel parseUrl(String url) throws IllegalArgumentException {
        String path = UrlPathUtils.getUrlPath(url, MakabaModule.this.domain, DOMAINS_LIST);
        if (path == null) throw new IllegalArgumentException("wrong domain");
        path = path.toLowerCase(Locale.US);
        
        UrlPageModel model = new UrlPageModel();
        model.chanName = CHAN_NAME;
        try {
            if (path.contains("/catalog")) {
                Matcher matcher = Pattern.compile("(.+?)/(catalog(?:_.+?)?)\\.html(" + HASHTAG_PREFIX + "[a-z]+)?").matcher(path);
                if (!matcher.find()) throw new Exception();
                model.boardName = matcher.group(1);
                if (matcher.group(3) == null) {
                    model.type = UrlPageModel.TYPE_CATALOGPAGE;
                    int index = Arrays.asList(CATALOG_TYPES).indexOf(matcher.group(2));
                    model.catalogType = index == -1 ? 0 : index;
                } else {
                    model.type = UrlPageModel.TYPE_SEARCHPAGE;
                    model.searchRequest = matcher.group(3);
                }
            } else if (path.contains("/res/")) {
                model.type = UrlPageModel.TYPE_THREADPAGE;
                Matcher matcher = Pattern.compile("(.+?)/res/([0-9]+?).html(.*)").matcher(path);
                if (!matcher.find()) throw new Exception();
                model.boardName = matcher.group(1);
                model.threadNumber = matcher.group(2);
                if (matcher.group(3).startsWith("#")) {
                    String post = matcher.group(3).substring(1);
                    if (!post.equals("")) model.postNumber = post;
                }
            } else {
                model.type = UrlPageModel.TYPE_BOARDPAGE;
                
                if (!path.contains("/")) {
                    if (path.equals("")) throw new Exception();
                    model.boardName = path;
                    model.boardPage = 0;
                } else {
                    model.boardName = path.substring(0, path.indexOf("/"));
                    
                    String page = path.substring(path.lastIndexOf("/") + 1);
                    if (!page.equals("")) {
                        String pageNum = page.substring(0, page.indexOf(".html"));
                        model.boardPage = pageNum.equals("index") ? 0 : Integer.parseInt(pageNum);
                    } else model.boardPage = 0;
                }
            }
        } catch (Exception e) {
            if (path == null || path.length() == 0 || path.equals("/")) {
                model.type = UrlPageModel.TYPE_INDEXPAGE;
            } else {
                model.type = UrlPageModel.TYPE_OTHERPAGE;
                model.otherPath = path;
            }
        }
        return model;
    }

    @Override
    protected JSONObject downloadJSONObject(String url, boolean checkIfModified, ProgressListener listener, CancellableTask task) throws Exception {
        if (!canHashwall()) return super.downloadJSONObject(url, checkIfModified, listener, task);
        HttpRequestModel rqModel = HttpRequestModel.builder().setGET().setCheckIfModified(checkIfModified).build();
        try {
            JSONObject object = HttpStreamer.getInstance().getJSONObjectFromUrl(url, rqModel, httpClient, listener, task, true, hashwallDetector);
            if (task != null && task.isCancelled()) throw new Exception("interrupted");
            if (listener != null) listener.setIndeterminate();
            return object;
        } catch (HttpWrongStatusCodeException e) {
            checkCloudflareError(e, url);
            throw e;
        } catch (HttpWrongResponseException e) {
            handleWrongResponse(url, e);
            return null;
        }
    }

    @Override
    protected JSONArray downloadJSONArray(String url, boolean checkIfModified, ProgressListener listener, CancellableTask task) throws Exception {
        if (!canHashwall()) return super.downloadJSONArray(url, checkIfModified, listener, task);
        HttpRequestModel rqModel = HttpRequestModel.builder().setGET().setCheckIfModified(checkIfModified).build();
        try {
            JSONArray array = HttpStreamer.getInstance().getJSONArrayFromUrl(url, rqModel, httpClient, listener, task, true, hashwallDetector);
            if (task != null && task.isCancelled()) throw new Exception("interrupted");
            if (listener != null) listener.setIndeterminate();
            return array;
        } catch (HttpWrongStatusCodeException e) {
            checkCloudflareError(e, url);
            throw e;
        } catch (HttpWrongResponseException e) {
            handleWrongResponse(url, e);
            return null;
        }
    }

    public CaptchaModel clickEmojiCaptcha(int emojiIndex, ProgressListener listener, CancellableTask task)
    {
        CRC32 crc = new CRC32();
        JSONObject object = new JSONObject();
        object.accumulate("captchaTokenID", captchaId);
        object.accumulate("emojiNumber", emojiIndex);
        crc.update(emojiCaptchaCurrentButtons[emojiIndex]);
        Logger.d(TAG, "clicked emojiCaptchaButton " + emojiIndex + " hash=" + Long.toHexString(crc.getValue()));
        crc.reset();
        String url = domainUrl + "api/captcha/emoji/click";
        EntityBuilder builder = EntityBuilder.create();
        builder.setText(object.toString());
        HttpRequestModel request = HttpRequestModel.builder().setPOST(builder.build()).build();
        String response = null;
        try {
            response = HttpStreamer.getInstance().getStringFromUrl(url, request, httpClient, listener, task, true,
                    (canHashwall() ? hashwallDetector : null));
        } catch (Exception e) {
            String s = e.getMessage();
            String ss = e.toString();
        }
        JSONObject makabaResult = new JSONObject(response);

        if(makabaResult.has("success") || makabaResult.has("image"))
        {
            emojiCaptchaClickCount++;
            CaptchaModel captchaModel = new CaptchaModel();
            captchaModel.type = CaptchaModel.TYPE_NORMAL;
            captchaModel.emoji = true;
            if(makabaResult.has("success")) {
                emojiCaptchaSolvedId = makabaResult.optString("success");
                captchaModel.emojiSuccess = true;
                return captchaModel;
            }

            String base64EncodedCaptcha = makabaResult.optString("image");
            byte[] decodedCaptchaBytes = android.util.Base64.decode(base64EncodedCaptcha, Base64.DEFAULT);
            captchaModel.bitmap = BitmapFactory.decodeByteArray(decodedCaptchaBytes, 0, decodedCaptchaBytes.length);
            captchaModel.emojiCaptchaButtons = new Bitmap[8];

            emojiCaptchaCurrentButtons = new byte[8][];
            for(int i = 0; i < 8; i++)
            {
                String base64EncodedCaptchaButton = makabaResult.getJSONArray("keyboard").optString(i);
                byte[] decodedCaptchaButtonBytes = android.util.Base64.decode(base64EncodedCaptchaButton, Base64.DEFAULT);
                emojiCaptchaCurrentButtons[i] = decodedCaptchaButtonBytes;
                crc.update(decodedCaptchaButtonBytes);
                Logger.d(TAG, "got step" + emojiCaptchaClickCount + " emojiCaptchaButton " + i + " hash=" + Long.toHexString(crc.getValue()));
                crc.reset();
                captchaModel.emojiCaptchaButtons[i] = BitmapFactory.decodeByteArray(decodedCaptchaButtonBytes, 0, decodedCaptchaButtonBytes.length);
            }

            return captchaModel;
        }
        return null;
    }
}
