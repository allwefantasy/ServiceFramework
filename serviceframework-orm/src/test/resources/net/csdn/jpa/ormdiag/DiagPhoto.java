package net.csdn.jpa.ormdiag;

import javax.persistence.ManyToOne;
import javax.persistence.Table;

@Table(name = "sf_orm_diag_photo")
public class DiagPhoto extends DiagAsset {
    private Integer id;
    private String name;

    @ManyToOne
    private DiagAlbum album;

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
