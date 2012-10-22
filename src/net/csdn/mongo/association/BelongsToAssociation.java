package net.csdn.mongo.association;

import net.csdn.mongo.Document;

/**
 * User: WilliamZhu
 * Date: 12-10-17
 * Time: 上午10:09
 */
public class BelongsToAssociation {

    private Object foreignKey;
    private Document document;


    public BelongsToAssociation(Document document,Options options) {


    }

    public BelongsToAssociation(Document document,Object foreignKey,Options options) {


    }



}
