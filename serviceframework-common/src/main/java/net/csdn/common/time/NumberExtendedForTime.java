package net.csdn.common.time;

import net.csdn.common.collections.WowCollections;
import net.csdn.common.reflect.ReflectHelper;
import org.joda.time.DateTime;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 4/14/13 WilliamZhu(allwefantasy@gmail.com)
 */
public class NumberExtendedForTime {
    DateTime time = new DateTime();
    private long extened;
    private String type;
    private String operate;

    public NumberExtendedForTime(int int_extened) {
        this.extened = int_extened;
    }

    public NumberExtendedForTime(long int_extened) {
        this.extened = int_extened;
    }

    public NumberExtendedForTime day() {
        type = "Days";
        return this;
    }

    public NumberExtendedForTime minute() {
        type = "Minutes";
        return this;
    }

    public NumberExtendedForTime second() {
        type = "Seconds";
        return this;
    }

    public NumberExtendedForTime hour() {
        type = "Hours";
        return this;
    }

    public NumberExtendedForTime week() {
        type = "Weeks";
        return this;
    }

    public NumberExtendedForTime month() {
        type = "Months";
        return this;
    }

    public NumberExtendedForTime millis() {
        type = "Millis";
        return this;
    }

    public NumberExtendedForTime year() {
        type = "Years";
        return this;
    }

    public DateTime ago() {
        operate = "minus";
        return (DateTime) ReflectHelper.method(time, operate + type, extened);
    }

    public DateTime fromNow() {
        operate = "plus";
        return (DateTime) ReflectHelper.method(time, operate + type, extened);
    }

    public DateTime from_now() {
        return fromNow();
    }

    public static void main(String[] args) {
        List jack = WowCollections.list(3, 5, 1);
        Collections.sort(jack, new Comparator<Integer>() {
            @Override
            public int compare(Integer integer, Integer integer2) {
                return integer - integer2;
            }
        });
        System.out.println(WowCollections.join(jack, ","));
    }

}
