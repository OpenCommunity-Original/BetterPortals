package org.envel.betterportals.bukkit.net.requests;

import org.envel.betterportals.api.PortalPosition;
import org.envel.betterportals.bukkit.portal.selection.IPortalSelection;
import org.envel.betterportals.shared.net.requests.Request;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.UUID;

/**
 * Sent by servers whenever the player joins so that they can find the destination position of a cross-server portal.
 *
 * The response to this is a single {@link ExternalSelectionInfo}, or null if there was no selection.
 */
@Getter
@Setter
public class GetSelectionRequest extends Request {
    private static final long serialVersionUID = 1L;

    @Getter
    public static class ExternalSelectionInfo implements Serializable {
        private final PortalPosition position;
        private final int sizeX;
        private final int sizeY;

        public ExternalSelectionInfo(IPortalSelection portalSelection) {
            assert portalSelection.isValid();

            this.position = portalSelection.getPortalPosition();
            this.sizeX = portalSelection.getPortalSize().getBlockX();
            this.sizeY = portalSelection.getPortalSize().getBlockY();
        }
    }

    private UUID playerId;
}
