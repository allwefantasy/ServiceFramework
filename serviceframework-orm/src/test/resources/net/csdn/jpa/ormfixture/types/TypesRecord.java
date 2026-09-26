package net.csdn.jpa.ormfixture.types;

import net.csdn.jpa.model.Model;

import javax.persistence.Table;
import java.math.BigDecimal;
import java.util.Date;
import java.util.UUID;

@Table(name = "sf_orm_pg_types")
public class TypesRecord extends Model {
    private Integer id;
    private Long bigValue;
    private Boolean flag;
    private BigDecimal amount;
    private Date happenedAt;
    private Date day;
    private byte[] payload;
    private UUID token;
    private String label;
}
