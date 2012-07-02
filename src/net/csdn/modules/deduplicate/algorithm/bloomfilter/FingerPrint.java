package net.csdn.modules.deduplicate.algorithm.bloomfilter;

import java.io.Serializable;

/**
 * User: william
 * Date: 12-4-13
 * Time: 上午11:39
 */
public class FingerPrint implements Serializable {
    public int id;
    public byte[] finger_print;

    public FingerPrint(int _id, byte[] _finger_print) {
        this.id = _id;
        this.finger_print = _finger_print;
    }

    @Override
    public boolean equals(Object obj) {
        return this.id == ((FingerPrint) (obj)).id;
    }

    @Override
    public int hashCode() {
        return this.id;
    }

}
