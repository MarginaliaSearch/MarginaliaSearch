package nu.marginalia.mq.inbox;

import nu.marginalia.mq.MqMessageState;

public record MqInboxResponse(String message, MqMessageState state) {

    public static MqInboxResponse ok(String message) {
        return new MqInboxResponse(message, MqMessageState.OK);
    }

    public static MqInboxResponse ok() {
        return new MqInboxResponse("", MqMessageState.OK);
    }

    public static MqInboxResponse err(String message) {
        return new MqInboxResponse(message, MqMessageState.ERR);
    }

    public static MqInboxResponse err() {
        return new MqInboxResponse("", MqMessageState.ERR);
    }
}
