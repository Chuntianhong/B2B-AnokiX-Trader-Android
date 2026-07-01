package com.anokix.traderapp.model;

public class MenuItem {
    public final String key;
    public final String title;
    public final String subtitle;
    public final int iconRes;

    public MenuItem(String key, String title, int iconRes) {
        this(key, title, null, iconRes);
    }

    public MenuItem(String key, String title, String subtitle, int iconRes) {
        this.key = key;
        this.title = title;
        this.subtitle = subtitle;
        this.iconRes = iconRes;
    }
}
