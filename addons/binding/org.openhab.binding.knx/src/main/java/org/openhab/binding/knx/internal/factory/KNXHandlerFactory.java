/**
 * Copyright (c) 2014-2016 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.knx.internal.factory;

import static org.openhab.binding.knx.KNXBindingConstants.*;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Map;
import java.util.Set;

import org.eclipse.smarthome.config.core.Configuration;
import org.eclipse.smarthome.config.discovery.DiscoveryService;
import org.eclipse.smarthome.core.autoupdate.AutoUpdateBindingConfigProvider;
import org.eclipse.smarthome.core.thing.Bridge;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.eclipse.smarthome.core.thing.binding.BaseThingHandlerFactory;
import org.eclipse.smarthome.core.thing.binding.ThingHandler;
import org.eclipse.smarthome.core.thing.link.ItemChannelLinkRegistry;
import org.openhab.binding.knx.discovery.IndividualAddressDiscoveryService;
import org.openhab.binding.knx.handler.IPBridgeThingHandler;
import org.openhab.binding.knx.handler.KNXBridgeBaseThingHandler;
import org.openhab.binding.knx.handler.KNXGenericThingHandler;
import org.openhab.binding.knx.handler.SerialBridgeThingHandler;
import org.openhab.binding.knx.internal.dpt.KNXTypeMapper;
import org.osgi.framework.ServiceRegistration;

import com.google.common.collect.Lists;

/**
 * The {@link KNXHandlerFactory} is responsible for creating things and thing
 * handlers.
 *
 * @author Karel Goderis - Initial contribution
 */
public class KNXHandlerFactory extends BaseThingHandlerFactory implements AutoUpdateBindingConfigProvider {

    public final static Collection<ThingTypeUID> SUPPORTED_THING_TYPES_UIDS = Lists.newArrayList(THING_TYPE_GENERIC,
            THING_TYPE_IP_BRIDGE, THING_TYPE_SERIAL_BRIDGE);

    private Map<ThingUID, ServiceRegistration<?>> discoveryServiceRegs = new HashMap<>();
    private ItemChannelLinkRegistry itemChannelLinkRegistry;
    private Collection<KNXTypeMapper> typeMappers = new HashSet<KNXTypeMapper>();
    private Collection<KNXBridgeBaseThingHandler> bridges = new HashSet<KNXBridgeBaseThingHandler>();

    protected void setItemChannelLinkRegistry(ItemChannelLinkRegistry registry) {
        itemChannelLinkRegistry = registry;
    }

    protected void unsetItemChannelLinkRegistry(ItemChannelLinkRegistry registry) {
        itemChannelLinkRegistry = null;
    }

    public void addKNXTypeMapper(KNXTypeMapper typeMapper) {
        typeMappers.add(typeMapper);
        for (KNXBridgeBaseThingHandler aBridge : bridges) {
            aBridge.addKNXTypeMapper(typeMapper);
        }
    }

    public void removeKNXTypeMapper(KNXTypeMapper typeMapper) {
        typeMappers.remove(typeMapper);
        for (KNXBridgeBaseThingHandler aBridge : bridges) {
            aBridge.removeKNXTypeMapper(typeMapper);
        }
    }

    @Override
    public boolean supportsThingType(ThingTypeUID thingTypeUID) {
        return SUPPORTED_THING_TYPES_UIDS.contains(thingTypeUID);
    }

    @Override
    public Thing createThing(ThingTypeUID thingTypeUID, Configuration configuration, ThingUID thingUID,
            ThingUID bridgeUID) {
        if (THING_TYPE_IP_BRIDGE.equals(thingTypeUID)) {
            ThingUID IPBridgeUID = getIPBridgeThingUID(thingTypeUID, thingUID, configuration);
            return super.createThing(thingTypeUID, configuration, IPBridgeUID, null);
        }
        if (THING_TYPE_SERIAL_BRIDGE.equals(thingTypeUID)) {
            ThingUID serialBridgeUID = getSerialBridgeThingUID(thingTypeUID, thingUID, configuration);
            return super.createThing(thingTypeUID, configuration, serialBridgeUID, null);
        }
        if (THING_TYPE_GENERIC.equals(thingTypeUID)) {
            ThingUID gaUID = getGenericThingUID(thingTypeUID, thingUID, configuration, bridgeUID);
            return super.createThing(thingTypeUID, configuration, gaUID, bridgeUID);
        }
        throw new IllegalArgumentException("The thing type " + thingTypeUID + " is not supported by the KNX binding.");
    }

    @Override
    protected ThingHandler createHandler(Thing thing) {
        if (thing.getThingTypeUID().equals(THING_TYPE_IP_BRIDGE)) {
            IPBridgeThingHandler handler = new IPBridgeThingHandler((Bridge) thing, itemChannelLinkRegistry,
                    typeMappers);
            registerIndividualAddressDiscoveryService(handler);
            return handler;
        } else if (thing.getThingTypeUID().equals(THING_TYPE_SERIAL_BRIDGE)) {
            SerialBridgeThingHandler handler = new SerialBridgeThingHandler((Bridge) thing, itemChannelLinkRegistry,
                    typeMappers);
            registerIndividualAddressDiscoveryService(handler);
            return handler;
        } else if (thing.getThingTypeUID().equals(THING_TYPE_GENERIC)) {
            return new KNXGenericThingHandler(thing, itemChannelLinkRegistry);
        }

        return null;

    }

    private ThingUID getIPBridgeThingUID(ThingTypeUID thingTypeUID, ThingUID thingUID, Configuration configuration) {
        if (thingUID == null) {
            String ipAddress = (String) configuration.get(IP_ADDRESS);
            thingUID = new ThingUID(thingTypeUID, ipAddress);
        }
        return thingUID;
    }

    private ThingUID getSerialBridgeThingUID(ThingTypeUID thingTypeUID, ThingUID thingUID,
            Configuration configuration) {
        if (thingUID == null) {
            String serialPort = (String) configuration.get(SERIAL_PORT);
            thingUID = new ThingUID(thingTypeUID, serialPort);
        }
        return thingUID;
    }

    private ThingUID getGenericThingUID(ThingTypeUID thingTypeUID, ThingUID thingUID, Configuration configuration,
            ThingUID bridgeUID) {

        String address = ((String) configuration.get(ADDRESS));

        if (thingUID == null && address != null) {
            thingUID = new ThingUID(thingTypeUID, address.replace(".", "_"), bridgeUID.getId());
        }
        return thingUID;
    }

    private synchronized void registerIndividualAddressDiscoveryService(KNXBridgeBaseThingHandler bridgeHandler) {
        IndividualAddressDiscoveryService discoveryService = new IndividualAddressDiscoveryService(bridgeHandler);
        this.discoveryServiceRegs.put(bridgeHandler.getThing().getUID(), bundleContext
                .registerService(DiscoveryService.class.getName(), discoveryService, new Hashtable<String, Object>()));
    }

    @Override
    public Boolean autoUpdate(String itemName) {
        // The principle we maintain is that it is up to KNX devices to emit the actual state of a variable, rather
        // than us auto-updating the channel. Most KNX devices have an Communication Object for both writing/updating a
        // variable, and next to that another Communication Object to read out the state, or the device (T)ransmits the
        // actual state after an update. In other words, implementing classes can either do nothing and wait for a
        // (T)ransmit, or implement an explicit read operation to read out the actual value from the KNX device

        if (itemChannelLinkRegistry != null) {
            Set<ChannelUID> boundChannels = itemChannelLinkRegistry.getBoundChannels(itemName);
            for (ChannelUID channelUID : boundChannels) {
                if (channelUID.getBindingId().equals(BINDING_ID)) {
                    return false;
                }
            }
        }

        return null;
    }

}
