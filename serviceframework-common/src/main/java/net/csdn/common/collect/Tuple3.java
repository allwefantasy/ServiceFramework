package net.csdn.common.collect;

/**
 * BlogInfo: william
 * Date: 11-9-2
 * Time: 上午10:01
 */
public class Tuple3<V1, V2, V3> {

    public static <V1, V2, V3> Tuple3<V1, V2, V3> tuple(V1 v1, V2 v2, V3 v3) {
        return new Tuple3<V1, V2, V3>(v1, v2, v3);
    }

    private final V1 v1;
    private final V2 v2;
    private final V3 v3;

    public Tuple3(V1 v1, V2 v2, V3 v3) {
        this.v1 = v1;
        this.v2 = v2;
        this.v3 = v3;
    }

    public V1 v1() {
        return v1;
    }

    public V2 v2() {
        return v2;
    }

    public V3 v3() {
        return v3;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Tuple3 tuple = (Tuple3) o;

        if (v1 != null ? !v1.equals(tuple.v1) : tuple.v1 != null) return false;
        if (v2 != null ? !v2.equals(tuple.v2) : tuple.v2 != null) return false;
        if (v3 != null ? !v3.equals(tuple.v3) : tuple.v3 != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = v1 != null ? v1.hashCode() : 0;
        result = 31 * result + (v2 != null ? v2.hashCode() : 0);
        result = 31 * result + (v3 != null ? v3.hashCode() : 0);
        return result;
    }

    //for json generation
    public V1 getV1() {
        return v1;
    }

    public V2 getV2() {
        return v2;
    }

    public V3 getV3() {
        return v3;
    }
}
