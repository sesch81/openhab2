/**
 * Copyright (c) 2014-2016 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.knx.handler;

import static org.openhab.binding.knx.KNXBindingConstants.*;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.smarthome.core.library.types.DecimalType;
import org.eclipse.smarthome.core.thing.Bridge;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.binding.BaseBridgeHandler;
import org.eclipse.smarthome.core.thing.link.ItemChannelLinkRegistry;
import org.eclipse.smarthome.core.types.Command;
import org.eclipse.smarthome.core.types.State;
import org.eclipse.smarthome.core.types.Type;
import org.openhab.binding.knx.GroupAddressListener;
import org.openhab.binding.knx.IndividualAddressListener;
import org.openhab.binding.knx.KNXBindingConstants;
import org.openhab.binding.knx.KNXBusListener;
import org.openhab.binding.knx.internal.dpt.KNXCoreTypeMapper;
import org.openhab.binding.knx.internal.dpt.KNXTypeMapper;
import org.openhab.binding.knx.internal.logging.LogAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tuwien.auto.calimero.CloseEvent;
import tuwien.auto.calimero.DetachEvent;
import tuwien.auto.calimero.FrameEvent;
import tuwien.auto.calimero.GroupAddress;
import tuwien.auto.calimero.IndividualAddress;
import tuwien.auto.calimero.cemi.CEMILData;
import tuwien.auto.calimero.datapoint.CommandDP;
import tuwien.auto.calimero.datapoint.Datapoint;
import tuwien.auto.calimero.exception.KNXException;
import tuwien.auto.calimero.exception.KNXIllegalArgumentException;
import tuwien.auto.calimero.exception.KNXRemoteException;
import tuwien.auto.calimero.exception.KNXTimeoutException;
import tuwien.auto.calimero.link.KNXLinkClosedException;
import tuwien.auto.calimero.link.KNXNetworkLink;
import tuwien.auto.calimero.link.NetworkLinkListener;
import tuwien.auto.calimero.log.LogManager;
import tuwien.auto.calimero.mgmt.Destination;
import tuwien.auto.calimero.mgmt.KNXDisconnectException;
import tuwien.auto.calimero.mgmt.ManagementClient;
import tuwien.auto.calimero.mgmt.ManagementClientImpl;
import tuwien.auto.calimero.mgmt.ManagementProcedures;
import tuwien.auto.calimero.mgmt.ManagementProceduresImpl;
import tuwien.auto.calimero.process.ProcessCommunicator;
import tuwien.auto.calimero.process.ProcessCommunicatorImpl;
import tuwien.auto.calimero.process.ProcessEvent;
import tuwien.auto.calimero.process.ProcessListenerEx;

/**
 * The {@link KNXBridgeBaseThingHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Kai Kreuzer / Karel Goderis - Initial contribution
 */
public abstract class KNXBridgeBaseThingHandler extends BaseBridgeHandler {

    public final static int ERROR_INTERVAL_MINUTES = 5;

    private final Logger logger = LoggerFactory.getLogger(KNXBridgeBaseThingHandler.class);

    private Collection<GroupAddressListener> groupAddressListeners = new HashSet<GroupAddressListener>();
    private Collection<IndividualAddressListener> individualAddressListeners = new HashSet<IndividualAddressListener>();
    private Collection<KNXBusListener> knxBusListeners = new HashSet<KNXBusListener>();
    private Collection<KNXTypeMapper> typeMappers;
    private LinkedBlockingQueue<RetryDatapoint> readDatapoints = new LinkedBlockingQueue<RetryDatapoint>();
    protected ConcurrentHashMap<IndividualAddress, Destination> destinations = new ConcurrentHashMap<IndividualAddress, Destination>();

    protected ItemChannelLinkRegistry itemChannelLinkRegistry;
    private ProcessCommunicator pc = null;
    private ProcessListenerEx pl = null;
    private NetworkLinkListener nll = null;
    private ManagementProcedures mp;
    private ManagementClient mc;
    protected KNXNetworkLink link;
    private final LogAdapter logAdapter = new LogAdapter();

    private ScheduledFuture<?> reconnectJob;
    private ScheduledFuture<?> busJob;
    private List<ScheduledFuture<?>> readFutures = new ArrayList<ScheduledFuture<?>>();

    // signals that the connection is shut down on purpose
    public boolean shutdown = false;
    private long intervalTimestamp;
    private long errorsSinceStart;
    private long errorsSinceInterval;

    public KNXBridgeBaseThingHandler(Bridge bridge, ItemChannelLinkRegistry itemChannelLinkRegistry,
            Collection<KNXTypeMapper> typeMappers) {
        super(bridge);
        this.itemChannelLinkRegistry = itemChannelLinkRegistry;
        this.typeMappers = typeMappers;
    }

    @Override
    public void initialize() {

        // reset the counters
        errorsSinceStart = 0;
        errorsSinceInterval = 0;

        LogManager.getManager().addWriter(null, logAdapter);

        scheduler.schedule(connectRunnable, 0, TimeUnit.SECONDS);
    }

    @Override
    public void dispose() {

        if (reconnectJob != null) {
            reconnectJob.cancel(true);
        }

        disconnect();

        LogManager.getManager().removeWriter(null, logAdapter);
    }

    /**
     * Returns the KNXNetworkLink for talking to the KNX bus.
     * The link can be null, if it has not (yet) been established successfully.
     *
     * @return the KNX network link
     */
    public synchronized ProcessCommunicator getCommunicator() {
        if (link != null && !link.isOpen()) {
            connect();
        }
        return pc;
    }

    public int getReadRetriesLimit() {
        return ((BigDecimal) getConfig().get(READ_RETRIES_LIMIT)).intValue();
    }

    public boolean isDiscoveryEnabled() {
        return ((Boolean) getConfig().get(ENABLE_DISCOVERY));
    }

    public abstract void establishConnection() throws KNXException;

    Runnable connectRunnable = new Runnable() {
        @Override
        public void run() {
            connect();
        }
    };

    public synchronized void connect() {
        try {
            shutdown = false;

            if (mp != null) {
                mp.detach();
            }

            if (mc != null) {
                mc.detach();
            }

            if (pc != null) {
                if (pl != null) {
                    pc.removeProcessListener(pl);
                }
                pc.detach();
            }

            if (link != null && link.isOpen()) {
                link.close();
            }

            establishConnection();

            nll = new NetworkLinkListener() {
                @Override
                public void linkClosed(CloseEvent e) {
                    // if the link is lost, we want to reconnect immediately

                    updateStatus(ThingStatus.OFFLINE);

                    if (!link.isOpen() && !(CloseEvent.USER_REQUEST == e.getInitiator()) && !shutdown) {
                        logger.warn("KNX link has been lost (reason: {} on object {}) - reconnecting...", e.getReason(),
                                e.getSource().toString());
                        if (((BigDecimal) getConfig().get(AUTO_RECONNECT_PERIOD)).intValue() > 0) {
                            logger.info("KNX link will be retried in "
                                    + ((BigDecimal) getConfig().get(AUTO_RECONNECT_PERIOD)).intValue() + " seconds");

                            Runnable reconnectRunnable = new Runnable() {
                                @Override
                                public void run() {
                                    if (shutdown) {
                                        reconnectJob.cancel(true);
                                    } else {
                                        logger.info("Trying to reconnect to KNX...");
                                        connect();
                                        if (link.isOpen()) {
                                            reconnectJob.cancel(true);
                                        }
                                    }
                                }
                            };

                            reconnectJob = scheduler.scheduleWithFixedDelay(reconnectRunnable,
                                    ((BigDecimal) getConfig().get(AUTO_RECONNECT_PERIOD)).intValue(),
                                    ((BigDecimal) getConfig().get(AUTO_RECONNECT_PERIOD)).intValue(), TimeUnit.SECONDS);

                        }
                    }
                }

                @Override
                public void indication(FrameEvent e) {

                    CEMILData cemid = (CEMILData) e.getFrame();

                    if (intervalTimestamp == 0) {
                        intervalTimestamp = System.currentTimeMillis();
                        updateState(new ChannelUID(getThing().getUID(), KNXBindingConstants.ERRORS_STARTUP),
                                new DecimalType(errorsSinceStart));
                        updateState(new ChannelUID(getThing().getUID(), KNXBindingConstants.ERRORS_INTERVAL),
                                new DecimalType(errorsSinceInterval));
                    } else if ((System.currentTimeMillis() - intervalTimestamp) > 60 * 1000 * ERROR_INTERVAL_MINUTES) {
                        intervalTimestamp = System.currentTimeMillis();
                        errorsSinceInterval = 0;
                        updateState(new ChannelUID(getThing().getUID(), KNXBindingConstants.ERRORS_INTERVAL),
                                new DecimalType(errorsSinceInterval));
                    }

                    int messageCode = e.getFrame().getMessageCode();

                    switch (messageCode) {
                        case CEMILData.MC_LDATA_IND: {
                            CEMILData cemi = (CEMILData) e.getFrame();

                            if (cemi.isRepetition()) {
                                errorsSinceStart++;
                                errorsSinceInterval++;

                                updateState(new ChannelUID(getThing().getUID(), KNXBindingConstants.ERRORS_STARTUP),
                                        new DecimalType(errorsSinceStart));
                                updateState(new ChannelUID(getThing().getUID(), KNXBindingConstants.ERRORS_INTERVAL),
                                        new DecimalType(errorsSinceInterval));

                            }
                            break;
                        }
                    }
                }

                @Override
                public void confirmation(FrameEvent e) {
                    CEMILData cemid = (CEMILData) e.getFrame();

                    if (intervalTimestamp == 0) {
                        intervalTimestamp = System.currentTimeMillis();
                        updateState(new ChannelUID(getThing().getUID(), KNXBindingConstants.ERRORS_STARTUP),
                                new DecimalType(errorsSinceStart));
                        updateState(new ChannelUID(getThing().getUID(), KNXBindingConstants.ERRORS_INTERVAL),
                                new DecimalType(errorsSinceInterval));
                    } else if ((System.currentTimeMillis() - intervalTimestamp) > 60 * 1000 * ERROR_INTERVAL_MINUTES) {
                        intervalTimestamp = System.currentTimeMillis();
                        errorsSinceInterval = 0;
                        updateState(new ChannelUID(getThing().getUID(), KNXBindingConstants.ERRORS_INTERVAL),
                                new DecimalType(errorsSinceInterval));
                    }

                    int messageCode = e.getFrame().getMessageCode();
                    switch (messageCode) {
                        case CEMILData.MC_LDATA_CON: {
                            CEMILData cemi = (CEMILData) e.getFrame();
                            if (!cemi.isPositiveConfirmation()) {
                                errorsSinceStart++;
                                errorsSinceInterval++;

                                updateState(new ChannelUID(getThing().getUID(), KNXBindingConstants.ERRORS_STARTUP),
                                        new DecimalType(errorsSinceStart));
                                updateState(new ChannelUID(getThing().getUID(), KNXBindingConstants.ERRORS_INTERVAL),
                                        new DecimalType(errorsSinceInterval));
                            }
                            break;
                        }

                    }
                }
            };

            pl = new ProcessListenerEx() {

                @Override
                public void detached(DetachEvent e) {
                    logger.error("Received detach Event");
                }

                @Override
                public void groupWrite(ProcessEvent e) {
                    onGroupWriteEvent(e);
                }

                @Override
                public void groupReadRequest(ProcessEvent e) {
                    onGroupReadEvent(e);
                }

                @Override
                public void groupReadResponse(ProcessEvent e) {
                    onGroupReadResponseEvent(e);
                }

            };

            if (link != null) {
                mp = new ManagementProceduresImpl(link);

                mc = new ManagementClientImpl(link);
                mc.setResponseTimeout((((BigDecimal) getConfig().get(RESPONSE_TIME_OUT)).intValue() / 1000));

                pc = new ProcessCommunicatorImpl(link);
                pc.setResponseTimeout(((BigDecimal) getConfig().get(RESPONSE_TIME_OUT)).intValue() / 1000);
                pc.addProcessListener(pl);

                link.addLinkListener(nll);
            }

            if (busJob != null) {
                busJob.cancel(true);
            }

            if (readFutures != null) {
                for (ScheduledFuture<?> readJob : readFutures) {
                    readJob.cancel(true);
                }
            } else {
                readFutures = new ArrayList<ScheduledFuture<?>>();
            }

            readDatapoints = new LinkedBlockingQueue<RetryDatapoint>();

            errorsSinceStart = 0;
            errorsSinceInterval = 0;

            busJob = scheduler.scheduleWithFixedDelay(new BusRunnable(), 0,
                    ((BigDecimal) getConfig().get(READING_PAUSE)).intValue(), TimeUnit.MILLISECONDS);

            updateStatus(ThingStatus.ONLINE);

        } catch (KNXException e) {
            logger.error("An exception occurred while connecting to the KNX bus: {}", e.getMessage());
            disconnect();
            updateStatus(ThingStatus.OFFLINE);
        }
    }

    public synchronized void disconnect() {
        shutdown = true;

        if (busJob != null) {
            busJob.cancel(true);
        }

        if (readFutures != null) {
            for (ScheduledFuture<?> readJob : readFutures) {
                if (!readJob.isDone()) {
                    readJob.cancel(true);
                }
            }
        }

        if (pc != null) {
            if (pl != null) {
                pc.removeProcessListener(pl);
            }
            pc.detach();
        }

        if (nll != null) {
            link.removeLinkListener(nll);
        }

        if (link != null) {
            link.close();
        }

    }

    public boolean registerGroupAddressListener(GroupAddressListener listener) {
        if (listener == null) {
            throw new NullPointerException("It's not allowed to pass a null GroupAddressListener.");
        }

        return groupAddressListeners.contains(listener) ? true : groupAddressListeners.add(listener);

    }

    public boolean unregisterGroupAddressListener(GroupAddressListener listener) {
        if (listener == null) {
            throw new NullPointerException("It's not allowed to pass a null GroupAddressListener.");
        }

        return groupAddressListeners.remove(listener);
    }

    public boolean registerIndividualAddressListener(IndividualAddressListener listener) {
        if (listener == null) {
            throw new NullPointerException("It's not allowed to pass a null IndividualAddressListener.");
        }

        return individualAddressListeners.contains(listener) ? true : individualAddressListeners.add(listener);

    }

    public boolean unregisterIndividualAddressListener(IndividualAddressListener listener) {
        if (listener == null) {
            throw new NullPointerException("It's not allowed to pass a null IndividualAddressListener.");
        }

        return individualAddressListeners.remove(listener);
    }

    public void addKNXTypeMapper(KNXTypeMapper typeMapper) {
        typeMappers.add(typeMapper);
    }

    public void removeKNXTypeMapper(KNXTypeMapper typeMapper) {
        typeMappers.remove(typeMapper);
    }

    public void registerKNXBusListener(KNXBusListener knxBusListener) {
        if (knxBusListener != null) {
            knxBusListeners.add(knxBusListener);
        }
    }

    public void unregisterKNXBusListener(KNXBusListener knxBusListener) {
        if (knxBusListener != null) {
            knxBusListeners.remove(knxBusListener);
        }
    }

    @Override
    public void handleUpdate(ChannelUID channelUID, State newState) {
        // Nothing to do here
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        // Nothing to do here
    }

    public class BusRunnable implements Runnable {

        @Override
        public void run() {

            if (getThing().getStatus() == ThingStatus.ONLINE && pc != null) {

                RetryDatapoint datapoint = readDatapoints.poll();

                if (datapoint != null) {
                    datapoint.incrementRetries();

                    boolean success = false;
                    try {
                        logger.trace("Sending read request on the KNX bus for datapoint {}",
                                datapoint.getDatapoint().getMainAddress());
                        pc.read(datapoint.getDatapoint());
                        success = true;
                    } catch (KNXException e) {
                        logger.warn("Cannot read value for datapoint '{}' from KNX bus: {}",
                                datapoint.getDatapoint().getMainAddress(), e.getMessage());
                    } catch (KNXIllegalArgumentException e) {
                        logger.warn("Error sending KNX read request for datapoint '{}': {}",
                                datapoint.getDatapoint().getMainAddress(), e.getMessage());
                    } catch (InterruptedException e) {
                        logger.warn("Error sending KNX read request for datapoint '{}': {}",
                                datapoint.getDatapoint().getMainAddress(), e.getMessage());
                    }
                    if (!success) {
                        if (datapoint.getRetries() < datapoint.getLimit()) {
                            logger.debug(
                                    "Adding the read request (after attempt '{}') for datapoint '{}' at position '{}' in the queue",
                                    datapoint.getRetries(), datapoint.getDatapoint().getMainAddress(),
                                    readDatapoints.size() + 1);
                            readDatapoints.add(datapoint);
                        } else {
                            logger.debug("Giving up reading datapoint {} - nubmer of maximum retries ({}) reached.",
                                    datapoint.getDatapoint().getMainAddress(), datapoint.getLimit());
                        }
                    }
                }
            }
        }
    };

    public class RetryDatapoint {

        private Datapoint datapoint;
        private int retries;
        private int limit;

        public Datapoint getDatapoint() {
            return datapoint;
        }

        public int getRetries() {
            return retries;
        }

        public void incrementRetries() {
            this.retries++;
        }

        public int getLimit() {
            return limit;
        }

        public RetryDatapoint(Datapoint datapoint, int limit) {
            this.datapoint = datapoint;
            this.retries = 0;
            this.limit = limit;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + getOuterType().hashCode();
            result = prime * result
                    + ((datapoint.getMainAddress() == null) ? 0 : datapoint.getMainAddress().hashCode());
            result = prime * result + limit;
            result = prime * result + retries;
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            RetryDatapoint other = (RetryDatapoint) obj;
            if (!getOuterType().equals(other.getOuterType())) {
                return false;
            }
            if (datapoint == null) {
                if (other.datapoint != null) {
                    return false;
                }
            } else if (!datapoint.getMainAddress().equals(other.datapoint.getMainAddress())) {
                return false;
            }

            return true;
        }

        private KNXBridgeBaseThingHandler getOuterType() {
            return KNXBridgeBaseThingHandler.this;
        }
    }

    public void readDatapoint(Datapoint datapoint, int retriesLimit) {
        synchronized (this) {
            if (datapoint != null) {
                RetryDatapoint retryDatapoint = new RetryDatapoint(datapoint, retriesLimit);

                if (!readDatapoints.contains(retryDatapoint)) {
                    logger.debug("Adding the read request for datapoint '{}' at position '{}' in the queue",
                            datapoint.getMainAddress(), readDatapoints.size() + 1);
                    readDatapoints.add(retryDatapoint);
                } else {
                    logger.debug("A read request for datapoint '{}' is already queued", datapoint.getMainAddress());
                }
            }
        }
    }

    /**
     * Handles the given {@link ProcessEvent}. If the KNX ASDU is valid
     * it is passed on to the {@link IndividualAddressListener}s and {@link GroupAddressListener}s that are interested
     * in the telegram, and subsequently to the
     * {@link KNXBusListener}s that are interested in all KNX bus activity
     *
     * @param e the {@link ProcessEvent} to handle.
     */
    private void onGroupWriteEvent(ProcessEvent e) {
        try {
            GroupAddress destination = e.getDestination();
            IndividualAddress source = e.getSourceAddr();
            byte[] asdu = e.getASDU();
            if (asdu.length == 0) {
                return;
            }

            logger.trace("Received a Group Write telegram from '{}' for destination '{}'", e.getSourceAddr(),
                    destination);

            for (IndividualAddressListener listener : individualAddressListeners) {
                if (listener.listensTo(source)) {
                    if (listener instanceof GroupAddressListener
                            && ((GroupAddressListener) listener).listensTo(destination)) {
                        listener.onGroupWrite(this, source, destination, asdu);
                    } else {
                        listener.onGroupWrite(this, source, destination, asdu);
                    }

                }
            }

            for (GroupAddressListener listener : groupAddressListeners) {
                if (listener.listensTo(destination)) {
                    if (listener instanceof IndividualAddressListener
                            && !((IndividualAddressListener) listener).listensTo(source)) {
                        listener.onGroupWrite(this, source, destination, asdu);
                    } else {
                        listener.onGroupWrite(this, source, destination, asdu);
                    }
                }
            }

            for (KNXBusListener listener : knxBusListeners) {
                listener.onActivity(e.getSourceAddr(), destination, asdu);
            }

        } catch (RuntimeException re) {
            logger.error("Error while receiving event from KNX bus: " + re.toString());
        }
    }

    /**
     * Handles the given {@link ProcessEvent}. If the KNX ASDU is valid
     * it is passed on to the {@link IndividualAddressListener}s and {@link GroupAddressListener}s that are interested
     * in the telegram, and subsequently to the
     * {@link KNXBusListener}s that are interested in all KNX bus activity
     *
     * @param e the {@link ProcessEvent} to handle.
     */
    private void onGroupReadEvent(ProcessEvent e) {
        try {
            GroupAddress destination = e.getDestination();
            IndividualAddress source = e.getSourceAddr();

            logger.trace("Received a Group Read telegram from '{}' for destination '{}'", e.getSourceAddr(),
                    destination);

            byte[] asdu = e.getASDU();

            for (IndividualAddressListener listener : individualAddressListeners) {
                if (listener.listensTo(source)) {
                    if (listener instanceof GroupAddressListener
                            && ((GroupAddressListener) listener).listensTo(destination)) {
                        listener.onGroupRead(this, source, destination, asdu);
                    } else {
                        listener.onGroupRead(this, source, destination, asdu);
                    }

                }
            }

            for (GroupAddressListener listener : groupAddressListeners) {
                if (listener.listensTo(destination)) {
                    if (listener instanceof IndividualAddressListener
                            && !((IndividualAddressListener) listener).listensTo(source)) {
                        listener.onGroupRead(this, source, destination, asdu);
                    } else {
                        listener.onGroupRead(this, source, destination, asdu);
                    }
                }
            }

            for (KNXBusListener listener : knxBusListeners) {
                listener.onActivity(e.getSourceAddr(), destination, asdu);
            }

        } catch (RuntimeException re) {
            logger.error("Error while receiving event from KNX bus: " + re.toString());
        }
    }

    /**
     * Handles the given {@link ProcessEvent}. If the KNX ASDU is valid
     * it is passed on to the {@link IndividualAddressListener}s and {@link GroupAddressListener}s that are interested
     * in the telegram, and subsequently to the
     * {@link KNXBusListener}s that are interested in all KNX bus activity
     *
     * @param e the {@link ProcessEvent} to handle.
     */
    private void onGroupReadResponseEvent(ProcessEvent e) {
        try {
            GroupAddress destination = e.getDestination();
            IndividualAddress source = e.getSourceAddr();
            byte[] asdu = e.getASDU();
            if (asdu.length == 0) {
                return;
            }

            logger.trace("Received a Group Read Response telegram from '{}' for destination '{}'", e.getSourceAddr(),
                    destination);

            for (IndividualAddressListener listener : individualAddressListeners) {
                if (listener.listensTo(source)) {
                    if (listener instanceof GroupAddressListener
                            && ((GroupAddressListener) listener).listensTo(destination)) {
                        listener.onGroupReadResponse(this, source, destination, asdu);
                    } else {
                        listener.onGroupReadResponse(this, source, destination, asdu);
                    }

                }
            }

            for (GroupAddressListener listener : groupAddressListeners) {
                if (listener.listensTo(destination)) {
                    if (listener instanceof IndividualAddressListener
                            && !((IndividualAddressListener) listener).listensTo(source)) {
                        listener.onGroupReadResponse(this, source, destination, asdu);
                    } else {
                        listener.onGroupReadResponse(this, source, destination, asdu);
                    }
                }
            }

            for (KNXBusListener listener : knxBusListeners) {
                listener.onActivity(e.getSourceAddr(), destination, asdu);
            }

        } catch (RuntimeException re) {
            logger.error("Error while receiving event from KNX bus: " + re.toString());
        }
    }

    public void writeToKNX(GroupAddress address, String dpt, Type value) {

        if (dpt != null && address != null && value != null) {

            ProcessCommunicator pc = getCommunicator();
            Datapoint datapoint = new CommandDP(address, getThing().getUID().toString(), 0, dpt);

            if (pc != null && datapoint != null) {
                try {
                    String mappedValue = toDPTValue(value, datapoint.getDPT());
                    if (mappedValue != null) {
                        pc.write(datapoint, mappedValue);
                        logger.debug("Wrote value '{}' to datapoint '{}'", value, datapoint);
                    } else {
                        logger.debug("Value '{}' can not be mapped to datapoint '{}'", value, datapoint);
                    }
                } catch (KNXException e) {
                    logger.debug(
                            "Value '{}' could not be sent to the KNX bus using datapoint '{}' - retrying one time: {}",
                            new Object[] { value, datapoint, e.getMessage() });
                    try {
                        // do a second try, maybe the reconnection was successful
                        pc = getCommunicator();
                        pc.write(datapoint, toDPTValue(value, datapoint.getDPT()));
                        logger.debug("Wrote value '{}' to datapoint '{}' on second try", value, datapoint);
                    } catch (KNXException e1) {
                        logger.error(
                                "Value '{}' could not be sent to the KNX bus using datapoint '{}' - giving up after second try: {}",
                                new Object[] { value, datapoint, e1.getMessage() });
                    }
                }
            } else {
                logger.error("Could not get hold of KNX Process Communicator");
            }
        }
    }

    /**
     * Transforms an openHAB type (command or state) into a datapoint type value for the KNX bus.
     *
     * @param type
     *            the openHAB command or state to transform
     * @param dpt
     *            the datapoint type to which should be converted
     *
     * @return the corresponding KNX datapoint type value as a string
     */
    public String toDPTValue(Type type, String dpt) {
        for (KNXTypeMapper typeMapper : typeMappers) {
            String value = typeMapper.toDPTValue(type, dpt);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    public String toDPTid(Class<? extends Type> type) {
        return KNXCoreTypeMapper.toDPTid(type);
    }

    /**
     * Transforms the raw KNX bus data of a given datapoint into an openHAB type (command or state)
     *
     * @param datapoint
     *            the datapoint to which the data belongs
     * @param asdu
     *            the byte array of the raw data from the KNX bus
     * @return the openHAB command or state that corresponds to the data
     */
    public Type getType(Datapoint datapoint, byte[] asdu) {
        for (KNXTypeMapper typeMapper : typeMappers) {
            Type type = typeMapper.toType(datapoint, asdu);
            if (type != null) {
                return type;
            }
        }
        return null;
    }

    public Type getType(GroupAddress destination, String dpt, byte[] asdu) {
        Datapoint datapoint = new CommandDP(destination, getThing().getUID().toString(), 0, dpt);
        return getType(datapoint, asdu);
    }

    synchronized public boolean isReachable(IndividualAddress address) {
        if (mp != null) {
            try {
                return mp.isAddressOccupied(address);
            } catch (KNXException | InterruptedException e) {
                logger.error("An exception occurred while trying to reach address '{}' : {}", address.toString(),
                        e.getMessage());
            }
        }

        return false;
    }

    synchronized public void restartNetworkDevice(IndividualAddress address) {
        if (address != null) {
            Destination destination = mc.createDestination(address, true);
            try {
                mc.restart(destination);
            } catch (KNXTimeoutException | KNXLinkClosedException e) {
                logger.error("An exception occurred while resetting the device with address {} : {}", address,
                        e.getMessage());
            }
        }
    }

    synchronized public IndividualAddress[] scanNetworkDevices(final int area, final int line) {
        try {
            return mp.scanNetworkDevices(area, line);
        } catch (final Exception e) {
            logger.error("An exception occurred while scanning the KNX bus : {}", e.getMessage());
        }

        return null;
    }

    synchronized public IndividualAddress[] scanNetworkRouters() {
        try {
            return mp.scanNetworkRouters();
        } catch (final Exception e) {
            logger.error("An exception occurred while scanning the KNX bus : {}", e.getMessage());
        }

        return null;
    }

    synchronized public byte[] readDeviceDescription(IndividualAddress address, int descType, boolean authenticate,
            long timeout) {
        Destination destination = null;

        boolean success = false;
        byte[] result = null;
        long now = System.currentTimeMillis();

        while (!success && (System.currentTimeMillis() - now) < timeout) {

            try {

                logger.debug("Reading Device Description of {} ", address);

                destination = mc.createDestination(address, true);

                if (authenticate) {
                    int access = mc.authorize(destination, (ByteBuffer.allocate(4)).put((byte) 0xFF).put((byte) 0xFF)
                            .put((byte) 0xFF).put((byte) 0xFF).array());
                }

                result = mc.readDeviceDesc(destination, descType);
                logger.debug("Reading Device Description of {} yields {} bytes", address,
                        result == null ? null : result.length);

                success = true;

            } catch (Exception e) {
                logger.error("An exception occurred while trying to read the device description for address '{}' : {}",
                        address.toString(), e.getMessage());
            } finally {
                if (destination != null) {
                    destination.destroy();
                }
            }
        }

        return result;
    }

    synchronized public byte[] readDeviceMemory(IndividualAddress address, int startAddress, int bytes,
            boolean authenticate, long timeout) {

        boolean success = false;
        byte[] result = null;
        long now = System.currentTimeMillis();

        while (!success && (System.currentTimeMillis() - now) < timeout) {
            Destination destination = null;
            try {

                logger.debug("Reading {} bytes at memory location {} of device {}",
                        new Object[] { bytes, startAddress, address });

                destination = mc.createDestination(address, true);

                if (authenticate) {
                    int access = mc.authorize(destination, (ByteBuffer.allocate(4)).put((byte) 0xFF).put((byte) 0xFF)
                            .put((byte) 0xFF).put((byte) 0xFF).array());
                }

                result = mc.readMemory(destination, startAddress, bytes);
                logger.debug("Reading {} bytes at memory location {} of device {} yields {} bytes",
                        new Object[] { bytes, startAddress, address, result == null ? null : result.length });

                success = true;
            } catch (KNXTimeoutException e) {
                logger.error("An KNXTimeoutException occurred while trying to read the memory for address '{}' : {}",
                        address.toString(), e.getMessage());
            } catch (KNXRemoteException e) {
                logger.error("An KNXRemoteException occurred while trying to read the memory for '{}' : {}",
                        address.toString(), e.getMessage());
            } catch (KNXDisconnectException e) {
                logger.error("An KNXDisconnectException occurred while trying to read the memory for '{}' : {}",
                        address.toString(), e.getMessage());
            } catch (KNXLinkClosedException e) {
                logger.error("An KNXLinkClosedException occurred while trying to read the memory for '{}' : {}",
                        address.toString(), e.getMessage());
            } catch (KNXException e) {
                logger.error("An KNXException occurred while trying to read the memory for '{}' : {}",
                        address.toString(), e.getMessage());
            } catch (InterruptedException e) {
                logger.error("An exception occurred while trying to read the memory for '{}' : {}", address.toString(),
                        e.getMessage());
                e.printStackTrace();
            } finally {
                if (destination != null) {
                    destination.destroy();
                }
            }
        }

        return result;
    }

    synchronized public byte[] readDeviceProperties(IndividualAddress address, final int interfaceObjectIndex,
            final int propertyId, final int start, final int elements, boolean authenticate, long timeout) {

        boolean success = false;
        byte[] result = null;
        long now = System.currentTimeMillis();

        while (!success && (System.currentTimeMillis() - now) < timeout) {
            Destination destination = null;
            try {
                logger.debug("Reading device property {} at index {} for {}", new Object[] { propertyId,
                        interfaceObjectIndex, address, result == null ? null : result.length });

                destination = mc.createDestination(address, true);

                if (authenticate) {
                    int access = mc.authorize(destination, (ByteBuffer.allocate(4)).put((byte) 0xFF).put((byte) 0xFF)
                            .put((byte) 0xFF).put((byte) 0xFF).array());
                }

                result = mc.readProperty(destination, interfaceObjectIndex, propertyId, start, elements);

                logger.debug("Reading device property {} at index {} for {} yields {} bytes", new Object[] { propertyId,
                        interfaceObjectIndex, address, result == null ? null : result.length });
                success = true;
            } catch (final Exception e) {
                logger.error("An exception occurred while reading a device property : {}", e.getMessage());
            } finally {
                if (destination != null) {
                    destination.destroy();
                }
            }
        }

        return result;
    }

}
