package net.csdn.modules.gateway;

/**
 * User: WilliamZhu
 * Date: 12-6-7
 * Time: 下午8:49
 */
public class Host {
    private String host;
    private int port;
    private boolean master = false;


    private boolean isOnline = false;


    public Host() {
    }

    public Host(String hostAndPort, boolean master, boolean isOnline) {
        String[] hostAndPortArray = hostAndPort.split(":");
        this.host = hostAndPortArray[0];
        this.port = Integer.parseInt(hostAndPortArray[1]);
        this.master = master;
        this.isOnline = isOnline;
    }

    public String hostAndPort() {
        return host + ":" + port;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Host host1 = (Host) o;

        if (port != host1.port) return false;
        if (host != null ? !host.equals(host1.host) : host1.host != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = host != null ? host.hashCode() : 0;
        result = 31 * result + port;
        return result;
    }

    //getter and setter
    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public boolean isMaster() {
        return master;
    }

    public void setMaster(boolean master) {
        this.master = master;
    }

    public boolean isOnline() {
        return isOnline;
    }

    public void setOnline(boolean online) {
        isOnline = online;
    }
}
