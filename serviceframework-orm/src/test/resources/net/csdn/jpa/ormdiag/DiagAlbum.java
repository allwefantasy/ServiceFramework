package net.csdn.jpa.ormdiag;

import net.csdn.jpa.model.Model;

import javax.persistence.OneToMany;
import javax.persistence.Table;
import java.util.List;

@Table(name = "sf_orm_diag_album")
public class DiagAlbum extends Model {
    private Integer id;
    private String title;

    @OneToMany
    private List<DiagPhoto> photos;
}
