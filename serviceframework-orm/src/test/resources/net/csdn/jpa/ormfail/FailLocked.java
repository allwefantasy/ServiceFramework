package net.csdn.jpa.ormfail;

import javax.persistence.Table;

@Table(name = "sf_orm_fail_locked")
public class FailLocked extends FailBase {
    private String note;
}
