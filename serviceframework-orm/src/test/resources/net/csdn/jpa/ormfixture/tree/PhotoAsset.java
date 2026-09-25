package net.csdn.jpa.ormfixture.tree;

import javax.persistence.Table;

@Table(name = "sf_orm_bcr_photo")
public class PhotoAsset extends TaggedAsset {
    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name == null ? null : name.trim();
    }

    public void setName(String name, String suffix) {
        this.name = (name == null ? "" : name) + ":" + (suffix == null ? "" : suffix);
    }
}
