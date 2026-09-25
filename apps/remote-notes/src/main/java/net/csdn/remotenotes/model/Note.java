package net.csdn.remotenotes.model;

import net.csdn.annotation.validate.Validate;
import net.csdn.common.exception.AutoGeneration;
import net.csdn.jpa.association.Association;
import net.csdn.jpa.model.Model;

import javax.persistence.ManyToOne;
import javax.persistence.Table;
import java.util.Map;

import static net.csdn.common.collections.WowCollections.map;
import static net.csdn.validate.ValidateHelper.presence;

@Table(name = "sf_remote_note")
public class Note extends Model {
    @Validate
    private static final Map $title = map(presence, map("message", "{} is required"));

    private Integer id;
    private String title;
    private String body;

    @ManyToOne
    private Tag tag;

    public Association tag() {
        throw new AutoGeneration();
    }
}
