/**
 * Copyright (c) 2014-2016 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.knx;

import org.openhab.binding.knx.handler.KNXBridgeBaseThingHandler;

import tuwien.auto.calimero.GroupAddress;
import tuwien.auto.calimero.IndividualAddress;

/**
 * The {@link IndividualAddressListener} is an interface that needs to be
 * implemented by classes that want to listen to specific Individual Addresses
 * on the KNX bus
 *
 * @author Karel Goderis - Initial contribution
 */
public interface IndividualAddressListener {

    /**
     * Called to verify if the IndividualAddressListener has an interest in the given Individual Address
     *
     * @param destination
     */
    public boolean listensTo(IndividualAddress source);

    /**
     *
     * Called when the KNX bridge receives a group write telegram
     *
     * @param bridge
     * @param destination
     * @param asdu
     */
    public void onGroupWrite(KNXBridgeBaseThingHandler bridge, IndividualAddress source, GroupAddress destination,
            byte[] asdu);

    /**
     *
     * Called when the KNX bridge receives a group read telegram
     *
     * @param bridge
     * @param destination
     * @param asdu
     */
    public void onGroupRead(KNXBridgeBaseThingHandler bridge, IndividualAddress source, GroupAddress destination,
            byte[] asdu);

    /**
     *
     * Called when the KNX bridge receives a group read response telegram
     *
     * @param bridge
     * @param destination
     * @param asdu
     */
    public void onGroupReadResponse(KNXBridgeBaseThingHandler bridge, IndividualAddress source,
            GroupAddress destination, byte[] asdu);

}
