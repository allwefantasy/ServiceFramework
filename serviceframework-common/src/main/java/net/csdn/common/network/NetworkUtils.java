package net.csdn.common.network;

import tech.mlsql.common.utils.collect.Lists;
import net.csdn.common.logging.CSLogger;
import net.csdn.common.logging.Loggers;

import java.lang.reflect.Method;
import java.net.*;
import java.util.*;

/**
 * BlogInfo: william
 * Date: 11-12-6
 * Time: 上午10:29
 * 主要获取本机内网IP.避免需要手动在配置文件配置本机IP内网地址。
 */
public class NetworkUtils {
    private final static CSLogger logger = Loggers.getLogger(NetworkUtils.class);

    public static enum StackType {
        IPv4, IPv6, Unknown
    }

    public static void main(String[] args) {

        System.out.println(intranet_ip.getHostAddress());


    }

    public static final String IPv4_SETTING = "java.net.preferIPv4Stack";
    public static final String IPv6_SETTING = "java.net.preferIPv6Addresses";

    public static final String NON_LOOPBACK_ADDRESS = "non_loopback_address";


    private static InetAddress localAddress;
    private static InetAddress intranet_ip;

    static {
        InetAddress localAddressX = null;
        InetAddress intranet_ip_x = null;
        try {
            localAddressX = InetAddress.getLocalHost();
        } catch (Exception e) {
            logger.warn("Failed to find local hostAndPort", e);
        }

        localAddress = localAddressX;

        Collection<InetAddress> inetAddresses = getAllAvailableAddresses();
        for (InetAddress ia : inetAddresses) {
            if (ia.getHostAddress().startsWith("192.168")) {
                intranet_ip = ia;
                break;
            }
        }
        if (intranet_ip == null) {
            try {
                intranet_ip = getFirstNonLoopbackAddress(StackType.IPv4);
            } catch (Exception e) {

            }
        }
    }

    public static InetAddress local_address() {
        return localAddress;
    }

    public static InetAddress intranet_ip() {
        return intranet_ip;
    }


    public static boolean isIPv4() {
        return System.getProperty("java.net.preferIPv4Stack") != null && System.getProperty("java.net.preferIPv4Stack").equals("true");
    }

    public static InetAddress getFirstNonLoopbackAddress(StackType ip_version) {
        try {
            InetAddress address = null;

            Enumeration intfs = NetworkInterface.getNetworkInterfaces();


            List<NetworkInterface> intfsList = Lists.newArrayList();
            while (intfs.hasMoreElements()) {
                intfsList.add((NetworkInterface) intfs.nextElement());
            }

            // order by index, assuming first ones are more interesting
            try {
                final Method getIndexMethod = NetworkInterface.class.getDeclaredMethod("getIndex");
                getIndexMethod.setAccessible(true);

                Collections.sort(intfsList, new Comparator<NetworkInterface>() {
                    @Override
                    public int compare(NetworkInterface o1, NetworkInterface o2) {
                        try {
                            return ((Integer) getIndexMethod.invoke(o1)).intValue() - ((Integer) getIndexMethod.invoke(o2)).intValue();
                        } catch (Exception e) {
                            throw new IllegalArgumentException("failed to fetch index of network interface");
                        }
                    }
                });
            } catch (Exception e) {
                // ignore
            }

            for (NetworkInterface intf : intfsList) {
                if (!intf.isUp() || intf.isLoopback())
                    continue;
                address = getFirstNonLoopbackAddress(intf, ip_version);
                if (address != null) {
                    return address;
                }
            }
        } catch (SocketException e) {
            e.printStackTrace();
        }

        return null;
    }

    public static InetAddress getFirstNonLoopbackAddress(NetworkInterface intf, StackType ipVersion) throws SocketException {
        if (intf == null)
            throw new IllegalArgumentException("Network interface pointer is null");

        for (Enumeration addresses = intf.getInetAddresses(); addresses.hasMoreElements(); ) {
            InetAddress address = (InetAddress) addresses.nextElement();
            if (!address.isLoopbackAddress()) {
                if ((address instanceof Inet4Address && ipVersion == StackType.IPv4) ||
                        (address instanceof Inet6Address && ipVersion == StackType.IPv6))
                    return address;
            }
        }
        return null;
    }

    public static List<NetworkInterface> getAllAvailableInterfaces() throws SocketException {
        List<NetworkInterface> allInterfaces = new ArrayList<NetworkInterface>(10);
        NetworkInterface intf;
        for (Enumeration en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements(); ) {
            intf = (NetworkInterface) en.nextElement();
            allInterfaces.add(intf);
        }
        return allInterfaces;
    }

    public static Collection<InetAddress> getAllAvailableAddresses() {
        Set<InetAddress> retval = new HashSet<InetAddress>();
        Enumeration en;

        try {
            en = NetworkInterface.getNetworkInterfaces();
            if (en == null)
                return retval;
            while (en.hasMoreElements()) {
                NetworkInterface intf = (NetworkInterface) en.nextElement();
                Enumeration<InetAddress> addrs = intf.getInetAddresses();
                while (addrs.hasMoreElements())
                    retval.add(addrs.nextElement());
            }
        } catch (SocketException e) {
            logger.warn("Failed to derive all available interfaces", e);
        }

        return retval;
    }

    private NetworkUtils() {

    }

}
