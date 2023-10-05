package io.questdb.desktop.ui.editor;

import io.questdb.desktop.GTk;
import io.questdb.desktop.model.StoreEntry;

public class Content extends StoreEntry {
    private static final String ATTR_NAME = "content";

    public Content() {
        this("default");
    }

    public Content(String name) {
        super(name);
        setAttr(ATTR_NAME, GTk.BANNER);
    }

    @SuppressWarnings("unused")
    public Content(StoreEntry other) {
        super(other);
    }

    @Override
    public final String getUniqueId() {
        return getName();
    }

    public String getContent() {
        return getAttr(ATTR_NAME);
    }

    public void setContent(String content) {
        setAttr(ATTR_NAME, content);
    }
}
