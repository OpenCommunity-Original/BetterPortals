package org.envel.betterportals.bukkit.net.requests;

import org.envel.betterportals.api.IntVector;
import org.envel.betterportals.shared.net.requests.Request;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TestForwardedRequest extends Request {
    private static final long serialVersionUID = 1L;

    private IntVector testField;
}
