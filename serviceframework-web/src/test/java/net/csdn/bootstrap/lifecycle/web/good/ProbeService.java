package net.csdn.bootstrap.lifecycle.web.good;

import net.csdn.annotation.Service;

@Service(implementedBy = ProbeService.class)
public class ProbeService {
    public String value() {
        return "svc";
    }
}
