package net.csdn.remotenotes;

import net.csdn.ServiceFramwork;
import net.csdn.bootstrap.Application;
import net.csdn.remotenotes.model.ServiceFrameworkPackageAnchor;

public class RemoteNotes {
    public static void main(String[] args) {
        ServiceFramwork.scanService.setLoader(ServiceFrameworkPackageAnchor.class);
        Application.main(args);
    }
}
