/**
 * Copyright (c) 2014-2016 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.knx.handler;

import static org.openhab.binding.knx.KNXBindingConstants.SERIAL_PORT;

import java.util.Enumeration;

import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.link.ItemChannelLinkRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gnu.io.CommPortIdentifier;
import gnu.io.RXTXVersion;
import tuwien.auto.calimero.exception.KNXException;
import tuwien.auto.calimero.link.KNXNetworkLinkFT12;
import tuwien.auto.calimero.link.medium.TPSettings;

/**
 * The {@link IPBridgeThingHandler} is responsible for handling commands, which are
 * sent to one of the channels. It implements a KNX Serial/USB Gateway, that either acts a a
 * conduit for other {@link KNXGenericThingHandler}s, or for Channels that are
 * directly defined on the bridge
 *
 * @author Karel Goderis - Initial contribution
 */
public class SerialBridgeThingHandler extends KNXBridgeBaseThingHandler {

    private final Logger logger = LoggerFactory.getLogger(SerialBridgeThingHandler.class);

    public SerialBridgeThingHandler(Thing thing, ItemChannelLinkRegistry itemChannelLinkRegistry) {
        super(thing, itemChannelLinkRegistry);
    }

    public SerialBridgeThingHandler() {
        // Default constructor required in order to have OSGi Declarative Services working
        super(null, null);
    }

    @Override
    public void establishConnection() throws KNXException {
        String serialPort = (String) getConfig().get(SERIAL_PORT);

        try {

            RXTXVersion.getVersion();

            if (logger.isInfoEnabled()) {
                if (link instanceof KNXNetworkLinkFT12) {
                    logger.info("Establishing connection to KNX bus through FT1.2 on serial port {}.", serialPort);
                }
            }

            link = new KNXNetworkLinkFT12(serialPort, new TPSettings());

        } catch (NoClassDefFoundError e) {
            throw new KNXException(
                    "The serial FT1.2 KNX connection requires the RXTX libraries to be available, but they could not be found!",
                    e);
        } catch (KNXException knxe) {
            if (knxe.getMessage().startsWith("can not open serial port")) {
                StringBuilder sb = new StringBuilder("Available ports are:\n");
                Enumeration<?> portList = CommPortIdentifier.getPortIdentifiers();
                while (portList.hasMoreElements()) {
                    CommPortIdentifier id = (CommPortIdentifier) portList.nextElement();
                    if (id.getPortType() == CommPortIdentifier.PORT_SERIAL) {
                        sb.append(id.getName() + "\n");
                    }
                }
                sb.deleteCharAt(sb.length() - 1);
                knxe = new KNXException("Serial port '" + serialPort + "' could not be opened. " + sb.toString());
            }
            throw knxe;
        }
    }
}
