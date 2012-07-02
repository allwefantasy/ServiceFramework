package net.csdn.cluster.routing;

import net.csdn.CsdnSearchIllegalArgumentException;

import java.io.Serializable;

/**
 * User: william
 * Date: 11-10-2
 * Time: 上午9:22
 */
public class ShardState implements Serializable {


    private static final long serialVersionUID = 1L;
    private State state;
    private OperationState operationState;

    public ShardState(String state, String operationState) {
        this.state = State.fromString(state);
        this.operationState = OperationState.fromString(operationState);

    }

    public State getState() {
        return state;
    }

    public void setState(State state) {
        this.state = state;
    }

    public OperationState getOperationState() {
        return operationState;
    }

    public void setOperationState(OperationState operationState) {
        this.operationState = operationState;
    }

    public static enum State implements Serializable {
        OPEN((byte) 0),
        CLOSE((byte) 1);

        private final byte id;

        State(byte id) {
            this.id = id;
        }

        public int id() {
            return this.id;
        }

        public static State fromId(byte id) {
            if (id == 0) {
                return OPEN;
            } else if (id == 1) {
                return CLOSE;
            }
            throw new CsdnSearchIllegalArgumentException("No state match for id [" + id + "]");
        }

        public static State fromString(String state) {
            if ("open".equals(state)) {
                return OPEN;
            } else if ("close".equals(state)) {
                return CLOSE;
            }
            throw new CsdnSearchIllegalArgumentException("No state match for [" + state + "]");
        }
    }

    public static enum OperationState implements Serializable {
        FLUSH(0),
        OPTIMIZE(1),
        REFRESH(2),
        NORMAL(3);

        private final int id;

        OperationState(int id) {
            this.id = id;
        }

        public int id() {
            return this.id;
        }

        public static OperationState fromId(int id) {
            if (id == 0) {
                return FLUSH;
            } else if (id == 1) {
                return OPTIMIZE;
            } else if (id == 2) {
                return REFRESH;
            } else if (id == 3) {
                return NORMAL;
            }
            throw new CsdnSearchIllegalArgumentException("No state match for id [" + id + "]");
        }

        public static OperationState fromString(String state) {
            if ("flush".equals(state)) {
                return FLUSH;
            } else if ("optimize".equals(state)) {
                return OPTIMIZE;
            } else if ("refresh".equals(state)) {
                return REFRESH;
            } else if ("normal".equals(state)) {
                return NORMAL;
            }
            throw new CsdnSearchIllegalArgumentException("No state match for [" + state + "]");
        }
    }
}
