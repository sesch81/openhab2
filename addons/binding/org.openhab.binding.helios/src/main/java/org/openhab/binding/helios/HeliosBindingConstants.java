/**
 * Copyright (c) 2014 openHAB UG (haftungsbeschraenkt) and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.helios;

import org.eclipse.smarthome.core.thing.ThingTypeUID;

/**
 * The {@link HeliosBinding} class defines common constants, which are used
 * across the whole binding.
 *
 * @author Karel Goderis - Initial contribution
 */
public class HeliosBindingConstants {

    public static final String BINDING_ID = "helios";

    // List of all Thing Type UIDs
    public final static ThingTypeUID HELIOS_VARIO_IP_2_7_TYPE = new ThingTypeUID(BINDING_ID, "ipvario27");
    public final static ThingTypeUID HELIOS_VARIO_IP_2_13_TYPE = new ThingTypeUID(BINDING_ID, "ipvario213");

    // List of all Channel ids
    public final static String KEY_PRESSED = "keypressed";
    public final static String KEY_PRESSED_STAMP = "keypressedstamp";
    public final static String CALL_STATE = "callstate";
    public final static String CALL_DIRECTION = "calldirection";
    public final static String CALL_STATE_STAMP = "callstatestamp";
    public final static String CARD = "card";
    public final static String CARD_VALID = "cardvalid";
    public final static String CARD_STAMP = "cardstamp";
    public final static String CODE = "code";
    public final static String CODE_VALID = "codevalid";
    public final static String CODE_STAMP = "codestamp";
    public final static String DEVICE_STATE = "devicestate";
    public final static String DEVICE_STATE_STAMP = "devicestamp";
    public final static String AUDIO_LOOP_TEST = "audiolooptest";
    public final static String AUDIO_LOOP_TEST_STAMP = "audioloopteststamp";
    public final static String MOTION = "motion";
    public final static String MOTION_STAMP = "motionstamp";
    public final static String KEY_RELEASED = "keyreleased";
    public final static String KEY_RELEASED_STAMP = "keyreleasedstamp";

    // List of all Channel type ids
    public final static String SWITCH_ENABLER = "switchenabler";
    public final static String SWITCH_TRIGGER = "trigger";
    public final static String IO_TRIGGER = "io";

    // List of all Thing properties
    public static final String VARIANT = "variant";
    public static final String SERIAL_NUMBER = "serialNumber";
    public static final String HW_VERSION = "hardwareVersion";
    public static final String SW_VERSION = "softwareVersion";
    public static final String BUILD_TYPE = "buildType";
    public static final String DEVICE_NAME = "deviceName";

}
