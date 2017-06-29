package net.csdn.modules.http;

import net.csdn.common.exception.ExceptionHandler;

import java.lang.reflect.Method;

/**
 * User: WilliamZhu
 * Date: 12-12-4
 * Time: 下午9:00
 */
public class WowAroundFilter {
    private WowAroundFilter next;
    private Method currentFilter;
    private Method action;
    private ApplicationController applicationController;

    public WowAroundFilter(Method currentFilter, Method action, ApplicationController applicationController) {
        this.currentFilter = currentFilter;
        this.action = action;
        this.applicationController = applicationController;
    }

    public void invoke() throws Exception {
        try {
            WowAroundFilter wowAroundFilter = this.next;
            if (wowAroundFilter == null) {
                wowAroundFilter = new WowAroundFilter(null, action, applicationController) {
                    @Override
                    public void invoke() throws Exception {
                        try {
                            WowAroundFilter.this.action.invoke(applicationController);
                        } catch (Exception e) {
                            ExceptionHandler.renderHandle(e);
                        }
                    }
                };

            }
            currentFilter.setAccessible(true);
            currentFilter.invoke(applicationController, wowAroundFilter);

        } catch (Exception e) {
            ExceptionHandler.renderHandle(e);
        }

    }

    public void setNext(WowAroundFilter next) {
        this.next = next;
    }

    public WowAroundFilter getNext() {
        return this;
    }
}
