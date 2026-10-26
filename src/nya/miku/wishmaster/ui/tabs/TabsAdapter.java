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

package nya.miku.wishmaster.ui.tabs;

import nya.miku.wishmaster.R;
import nya.miku.wishmaster.api.ChanModule;
import nya.miku.wishmaster.api.models.UrlPageModel;
import nya.miku.wishmaster.common.MainApplication;
import nya.miku.wishmaster.ui.CompatibilityUtils;
import nya.miku.wishmaster.ui.HistoryFragment;
import nya.miku.wishmaster.ui.theme.ThemeUtils;
import android.content.Context;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import androidx.core.content.res.ResourcesCompat;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

public class TabsAdapter extends ArrayAdapter<TabModel> {
    
    private final LayoutInflater inflater;
    private final Context context;
    private final TabsState tabsState;
    private final TabsIdStack tabsIdStack;
    private final TabSelectListener selectListener;
    
    private int selectedItem;
    private int draggingItem = -1;
    
    static final ColorFilter disabledIconColorFilter = new ColorMatrixColorFilter(new ColorMatrix(new float[]{
        0.7f,0.0f,0.0f,0.0f,0.0f,
        0.0f,0.7f,0.0f,0.0f,0.0f,
        0.0f,0.0f,0.7f,0.0f,0.0f,
        0.0f,0.0f,0.0f,1.0f,0.0f
    }));

    private final View.OnTouchListener onCloseTouch = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View v, MotionEvent e) {
            switch (e.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    v.setPressed(true);
                    break;
                case MotionEvent.ACTION_UP:
                    v.setPressed(false);
                    if (e.getEventTime() - e.getDownTime() > ViewConfiguration.getLongPressTimeout())
                        toggleTabIsPinned((Integer) v.getTag());
                    else
                        closeTab((Integer) v.getTag());
                    break;
                case MotionEvent.ACTION_CANCEL:
                    v.setPressed(false);
                    break;
            }
            return true;
        }
    };

    private final View.OnTouchListener onIconTouch = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View v, MotionEvent e) {
            switch (e.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    v.setPressed(true);
                    break;
                case MotionEvent.ACTION_UP:
                    v.setPressed(false);
                    if (e.getEventTime() - e.getDownTime() > ViewConfiguration.getLongPressTimeout())
                        setDraggingItem((Integer) v.getTag());
                    else
                        toggleTabAutoupdate((Integer) v.getTag());
                    break;
            }
            return true;
        }
    };

    private final View.OnTouchListener onIconLongTouch = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View v, MotionEvent e) {
            switch (e.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    break;
                case MotionEvent.ACTION_UP:
                    if (e.getEventTime() - e.getDownTime() > ViewConfiguration.getLongPressTimeout())
                        setDraggingItem((Integer) v.getTag());
                    break;
            }
            return true;
        }
    };

    private final Drawable updateStateDrawableHidden;
    private final Drawable updateStateDrawablePlanned;
    private final Drawable updateStateDrawableUpdated;

    /**
     * Конструктор адаптера.
     * После создания и привязки к объекту списка необходимо дополнительно установить позицию текущей вкладки ({@link #setSelectedItem(int)})
     * @param context контекст активности для получения темы (стиля) и инфлатера
     * @param tabsState объект состояния вкладок
     * @param selectListener интерфейс {@link TabSelectListener}, слушающий событие выбора (переключения) вкладки
     */
    public TabsAdapter(Context context, TabsState tabsState, TabSelectListener selectListener) {
        super(context, 0, tabsState.tabsArray);
        this.inflater = LayoutInflater.from(context);
        this.context = context;
        this.tabsState = tabsState;
        this.tabsIdStack = tabsState.tabsIdStack;
        this.selectListener = selectListener;
        this.updateStateDrawableHidden = getUpdateStateDrawable(android.R.color.transparent);
        this.updateStateDrawablePlanned = getUpdateStateDrawable(R.attr.urlLinkForeground);
        this.updateStateDrawableUpdated = getUpdateStateDrawable(R.attr.postQuoteForeground);
    }
    
    /**
     * Выбрать текущую вкладку (и переключиться на неё). Объект состояния вкладок будет сериализован
     * @param position позиция вкладки в списке
     */
    public void setSelectedItem(int position) {
        setSelectedItem(position, true, true);
    }
    
    /**
     * Выбрать текущую вкладку (и переключиться на неё)
     * @param position позиция вкладки в списке
     * @param serialize если true, сериализовать объект состояния вкладок
     */
    public void setSelectedItem(int position, boolean serialize) {
        setSelectedItem(position, serialize, true);
    }
    
    /**
     * Выбрать текущую вкладку (с возможностью переключения на неё)
     * @param position позиция вкладки в списке
     * @param serialize если true, сериализовать объект состояния вкладок
     * @param switchTo если true, переключиться на выбранную вкладку
     */
    public void setSelectedItem(int position, boolean serialize, boolean switchTo) {
        selectedItem = position;
        tabsState.position = position;
        if (position >= 0) {
            tabsIdStack.addTab(getItem(position).id);
        }
        notifyDataSetChanged(serialize);
        if (switchTo) selectListener.onTabSelected(position);
    }
    
    /**
     * Выбрать текущую вкладку (и переключиться на неё) с поиском по ID вкладки
     * @param id ID вкладки
     */
    public void setSelectedItemId(long id) {
        for (int i=0; i<getCount(); ++i) {
            if (getItem(i).id == id) {
                setSelectedItem(i);
                break;
            }
        }
    }
    
    /**
     * Установить или убрать маркер перемещения вкладки
     * @param position позиция вкладки в списке или -1, если необходимо убрать маркер перемещения
     */
    public void setDraggingItem(int position) {
        draggingItem = position;
        notifyDataSetChanged(false);
    }
    
    /**
     * @return Возвращает позицию текущей выбранной вкладки
     */
    public int getSelectedItem() {
        return selectedItem;
    }
    
    /**
     * @return Возвращает позицию текущей перемещаемой вкладки (с маркером перемещения)
     * или -1, если перемещение не активно в данный момент
     */
    public int getDraggingItem() {
        return draggingItem;
    }
    
    /**
     * @return Возвращает, если возможно, позицию последней выбранной обычной вкладки (position >= 0)
     */
    public int getSelectedTab() {
        final long id;
        if (selectedItem < 0 && (id = tabsIdStack.getCurrentTab()) != -1) {
            for (int i = 0; i < getCount(); ++i) {
                if (getItem(i).id == id) {
                    return i;
                }
            }
        }
        return selectedItem;
    }

    /**
     * Закрыть вкладку
     * @param position позиция вкладки в списке
     */
    public void closeTab(int position) {
        setDraggingItem(-1);
        if (position >= getCount()) return;
        if (getItem(position).isPinned) return;
        HistoryFragment.setLastClosed(tabsState.tabsArray.get(position));
        tabsIdStack.removeTab(getItem(position).id);
        remove(getItem(position), false);
        if (position == selectedItem) {
            if (!tabsIdStack.isEmpty()) {
                setSelectedItemId(tabsIdStack.getCurrentTab());
            } else {
                if (getCount() == 0) {
                    setSelectedItem(TabModel.POSITION_NEWTAB);
                } else {
                    if (getCount() <= position) --position;
                    setSelectedItem(position); //serialize
                }
            }
        } else {
            if (position < selectedItem) --selectedItem;
            setSelectedItem(selectedItem, true, MainApplication.getInstance().settings.scrollToActiveTab()); //serialize
        }
    }
    
    public void clearTabs() {
        boolean selectedClosed = false;
        setDraggingItem(-1);
        for (int i = 0; i < getCount();) {
            if (getItem(i).isPinned) {++i; continue;}
            tabsIdStack.removeTab(getItem(i).id);
            remove(getItem(i), false);
            if (i == selectedItem) {
                selectedClosed = true;
            } else if (i < selectedItem) {
                --selectedItem;
            }
        }
        if (selectedClosed) {
            if (!tabsIdStack.isEmpty()) {
                setSelectedItemId(tabsIdStack.getCurrentTab()); //serialize
            } else {
                setSelectedItem(TabModel.POSITION_NEWTAB); //serialize
            }
        } else {
            setSelectedItem(selectedItem, true, MainApplication.getInstance().settings.scrollToActiveTab()); //serialize
        }
    }

    public void toggleTabIsPinned(int position) {
        if (position >= getCount()) return;
        TabModel model = getItem(position);
        if (model == null) return;
        model.isPinned = !model.isPinned;
        notifyDataSetChanged();
    }

    public void toggleTabAutoupdate(int position) {
        if (position >= getCount()) return;
        TabModel model = getItem(position);
        if (model == null) return;
        model.autoupdateBackground = !model.autoupdateBackground;
        notifyDataSetChanged();
    }

    /**
     * Метод для обработки нажатия клавиши "Назад"
     * @return
     */
    public boolean back(boolean longPress) {
        if (selectedItem < 0) {
            if (!tabsIdStack.isEmpty()) {
                setSelectedItemId(tabsIdStack.getCurrentTab());
                return true;
            }
        } else {
            if (getItem(selectedItem).isPinned ||
                MainApplication.getInstance().settings.doNotCloseTabs() ^ longPress) {
                tabsIdStack.removeTab(getItem(selectedItem).id);
                if (tabsIdStack.isEmpty()) {
                    setSelectedItem(TabModel.POSITION_NEWTAB);
                } else {
                    setSelectedItemId(tabsIdStack.getCurrentTab());
                }
            } else {
                closeTab(selectedItem);
            }
            return true;
        }
        return false;
    }

    private Drawable getUpdateStateDrawable(int colorId) {
        TypedValue typedValue = ThemeUtils.resolveAttribute(context.getTheme(), colorId, true);
        int color;
        if (typedValue.type >= TypedValue.TYPE_FIRST_COLOR_INT && typedValue.type <= TypedValue.TYPE_LAST_COLOR_INT) {
            color = typedValue.data;
        } else {
            try {
                color = CompatibilityUtils.getColor(context.getResources(), typedValue.resourceId);
            } catch (Exception e) {
                color = 0;
            }
        }
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.RECTANGLE);
        shape.setColor(color);
        return shape;
    }

    private Drawable getUpdateStateDrawable(TabModel model) {
        if ((TabsTrackerService.getCurrentUpdatingTabId() == -1) ||
            (!MainApplication.getInstance().settings.isAutoupdateProgress())) {
            return updateStateDrawableHidden;
        } else if (model.autoupdateComplete) {
            return updateStateDrawableUpdated;
        } else {
            return updateStateDrawablePlanned;
        }
    }

    @Override
    public View getView(final int position, View convertView, ViewGroup parent) {
        View view = convertView == null ? inflater.inflate(R.layout.sidebar_tabitem, parent, false) : convertView;
        View dragHandler = view.findViewById(R.id.tab_drag_handle);
        ImageView stateIndicator = (ImageView)view.findViewById(R.id.tab_state_indicator);
        ImageView favIcon = (ImageView)view.findViewById(R.id.tab_favicon);
        TextView title = (TextView)view.findViewById(R.id.tab_text_view);
        ImageView closeBtn = (ImageView)view.findViewById(R.id.tab_close_button);
        
        dragHandler.getLayoutParams().width = position == draggingItem ? ViewGroup.LayoutParams.WRAP_CONTENT : 0;
        dragHandler.setLayoutParams(dragHandler.getLayoutParams());
                
        if (position == selectedItem) {
            TypedValue typedValue = ThemeUtils.resolveAttribute(context.getTheme(), R.attr.sidebarSelectedItem, true);
            if (typedValue.type >= TypedValue.TYPE_FIRST_COLOR_INT && typedValue.type <= TypedValue.TYPE_LAST_COLOR_INT) {
                view.setBackgroundColor(typedValue.data);
            } else {
                view.setBackgroundResource(typedValue.resourceId); 
            }
        } else {
            view.setBackgroundColor(Color.TRANSPARENT);
        }
        
        TabModel model = this.getItem(position);
        
        switch (model.type) {
            case TabModel.TYPE_NORMAL:
            case TabModel.TYPE_LOCAL:
                closeBtn.setImageResource(ThemeUtils.getThemeResId(context.getTheme(),
                            model.isPinned ? R.attr.iconBtnPin : R.attr.iconBtnClose));
                closeBtn.setVisibility(View.VISIBLE);
                String titleText = model.title;
                if (model.unreadPostsCount > 0 || model.autoupdateError) {
                    StringBuilder titleStringBuilder = new StringBuilder();
                    if (model.unreadSubscriptions) titleStringBuilder.append("[*] ");
                    if (model.autoupdateError) titleStringBuilder.append("[X] ");
                    if (model.unreadPostsCount > 0) titleStringBuilder.append('[').append(model.unreadPostsCount).append("] ");
                    titleText = titleStringBuilder.append(titleText).toString();
                }
                title.setText(titleText);
                ChanModule chan = MainApplication.getInstance().getChanModule(model.pageModel.chanName);
                Drawable icon = chan != null ? chan.getChanFavicon() :
                    ResourcesCompat.getDrawable(context.getResources(), android.R.drawable.ic_delete, null);
                ColorFilter filter = null;
                View.OnTouchListener onIconTouch = this.onIconLongTouch;
                if (icon != null) {
                    if (model.type == TabModel.TYPE_LOCAL) {
                        Drawable[] layers = new Drawable[] {
                                icon, ResourcesCompat.getDrawable(context.getResources(), R.drawable.favicon_overlay_local, null) };
                        icon = new LayerDrawable(layers);
                    } else if (model.type == TabModel.TYPE_NORMAL && model.pageModel != null && model.pageModel.type == UrlPageModel.TYPE_THREADPAGE) {
                        filter = model.autoupdateBackground ? null : disabledIconColorFilter;
                        onIconTouch = this.onIconTouch;
                    }
                    favIcon.setTag(position);
                    favIcon.setOnClickListener(null);
                    favIcon.setOnTouchListener(onIconTouch);
                    favIcon.setColorFilter(filter);
                    favIcon.setImageDrawable(icon);
                    favIcon.setVisibility(View.VISIBLE);
                } else {
                    favIcon.setVisibility(View.GONE);
                }
                if ((TabsTrackerService.getCurrentUpdatingTabId() == -1) ||
                    (!MainApplication.getInstance().settings.isAutoupdateProgress())) {
                    stateIndicator.setVisibility(View.INVISIBLE);
                } else {
                    stateIndicator.setImageDrawable(getUpdateStateDrawable(model));
                    stateIndicator.setVisibility(View.VISIBLE);
                }
                break;
            default:
                closeBtn.setVisibility(View.GONE);
                title.setText(R.string.error_deserialization);
                favIcon.setVisibility(View.GONE);
        }
        
        closeBtn.setTag(position);
        closeBtn.setOnClickListener(null); // Prevent undesired ripple effects
        closeBtn.setOnTouchListener(onCloseTouch);
        return view;
    }
    
    @Override
    public void notifyDataSetChanged() {
        notifyDataSetChanged(true);
    }
    
    /**
     * @param serialize если true, сериализовать объект состояния вкладок
     */
    public void notifyDataSetChanged(boolean serialize) {
        super.notifyDataSetChanged();
        if (serialize) MainApplication.getInstance().serializer.serializeTabsState(tabsState);
    }
    
    @Override
    public void add(TabModel object) {
        add(object, true);
    }
    
    /**
     * Добавить объект в конец массива
     * @param object объект
     * @param serialize если true, сериализовать объект состояния вкладок
     */
    public void add(TabModel object, boolean serialize) {
        setNotifyOnChange(false);
        super.add(object);
        notifyDataSetChanged(serialize);
    }
    
    @Override
    public void remove(TabModel object) {
        remove(object, true);
    }
    
    /**
     * Удалить объект из массива
     * @param object объект
     * @param serialize если true, сериализовать объект состояния вкладок
     */
    public void remove(TabModel object, boolean serialize) {
        setNotifyOnChange(false);
        super.remove(object);
        notifyDataSetChanged(serialize);
    }
    
    @Override
    public void insert(TabModel object, int index) {
        insert(object, index, true);
    }
    
    /**
     * Вставить объект в массив на заданную позицию (индекс)
     * @param object объект
     * @param index индекс, на который объект должен быть вставлен
     * @param serialize если true, сериализовать объект состояния вкладок
     */
    public void insert(TabModel object, int index, boolean serialize) {
        setNotifyOnChange(false);
        super.insert(object, index);
        notifyDataSetChanged(serialize);
    }
    
    /**
     * Интерфейс, слушающий событие выбора (переключения) вкладки.
     * @author miku-nyan
     *
     */
    public static interface TabSelectListener {
        /** Вызывается при переключении вкладки.
         *  Переключение может быть на ту же самую (открытую в данный момент) вкладку, например, при изменении её позиции в списке. */
        public void onTabSelected(int position);
    }
}
