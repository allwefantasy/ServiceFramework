package net.csdn.modules.deduplicate.algorithm.bloomfilter;

/**
 * User: william
 * Date: 12-4-13
 * Time: 下午3:10
 */
public class SimpleTuple<V1, V2> {

    private final V1 v1;
    private final V2 v2;

    public SimpleTuple(V1 v1, V2 v2) {
        this.v1 = v1;
        this.v2 = v2;
    }

    public V1 v1() {
        return v1;
    }

    public V2 v2() {
        return v2;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SimpleTuple tuple = (SimpleTuple) o;
        if ((tuple.v1.equals(v1) && tuple.v2.equals(v2)) || (tuple.v1.equals(v2) && tuple.v2.equals(v1))) return true;
        return false;
    }

    @Override
    public int hashCode() {
        int a = 31 * (v1.hashCode() | v2.hashCode()) & 0xffff0000;
        int b = (v1.hashCode() & v2.hashCode()) & 0xffff;
        return a | b;
    }

    //for json generation
    public V1 getV1() {
        return v1;
    }

    public V2 getV2() {
        return v2;
    }
}
