package net.csdn.jpa.ormfail;

import net.csdn.jpa.model.Model;

public abstract class FailBase extends Model {
    public final String getNote() {
        return "locked";
    }
}
