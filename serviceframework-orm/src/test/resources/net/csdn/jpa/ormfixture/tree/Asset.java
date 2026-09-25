package net.csdn.jpa.ormfixture.tree;

import net.csdn.jpa.model.Model;

public abstract class Asset extends Model {
    private String rootTag;

    public String getRootTag() {
        return rootTag;
    }

    public void setRootTag(String rootTag) {
        this.rootTag = rootTag == null ? null : rootTag.trim();
    }

    public void setRootTag(String rootTag, String suffix) {
        this.rootTag = (rootTag == null ? "" : rootTag) + (suffix == null ? "" : suffix);
    }
}
