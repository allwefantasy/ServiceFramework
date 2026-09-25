package net.csdn.remotenotes.model;

import net.csdn.annotation.validate.Validate;
import net.csdn.common.exception.AutoGeneration;
import net.csdn.jpa.association.Association;
import net.csdn.jpa.model.Model;

import javax.persistence.OneToMany;
import javax.persistence.Table;
import java.util.List;
import java.util.Map;

import static net.csdn.common.collections.WowCollections.list;
import static net.csdn.common.collections.WowCollections.map;
import static net.csdn.validate.ValidateHelper.presence;
import static net.csdn.validate.ValidateHelper.uniqueness;

@Table(name = "sf_remote_tag")
public class Tag extends Model {
    @Validate
    private static final Map $name = map(
            presence, map("message", "{} is required"),
            uniqueness, map("message", "{} is not uniq"));

    private Integer id;
    private String name;

    @OneToMany
    private List<Note> notes = list();

    public Association notes() {
        throw new AutoGeneration();
    }
}
