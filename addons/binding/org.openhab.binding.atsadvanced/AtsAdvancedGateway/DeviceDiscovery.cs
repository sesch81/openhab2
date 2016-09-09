﻿// <copyright file="DeviceDiscovery.cs" company="UTC Fire &amp; Security">
// (C) UTC Fire &amp; Security 2012. All right reserved.
// </copyright>
namespace AtsAdvancedTest
{
    using System;
    using System.Diagnostics;
    using System.Net.NetworkInformation;
    using System.Text.RegularExpressions;
    using Ace;

    /// <summary>
    /// Implements device discovery information.
    /// </summary>
    internal class DeviceDiscovery
    {
        /// <summary>
        /// The discovery response message.
        /// </summary>
        private readonly IMessage response;

        /// <summary>
        /// Initializes a new instance of the <see cref="DeviceDiscovery"/> class.
        /// </summary>
        /// <param name="discoveryResponse">The discovery response message.</param>
        internal DeviceDiscovery(IMessage discoveryResponse)
        {
            Debug.Assert(discoveryResponse != null, "Missing discovery response message.");
            Debug.Assert(discoveryResponse.Info != null, "Unrecognized message.");
            Debug.Assert(discoveryResponse.Info.Id == "device.Description", "Expected 'device.Description' message.");
			this.response = discoveryResponse;
        }

        /// <summary>
        /// Gets the name of the device.
        /// </summary>
        /// <value>The name of the device.</value>
        internal string DeviceName
        {
            get
            {
                return this.GetStringProperty("device.name");
            }
        }

        /// <summary>
        /// Gets the device model.
        /// </summary>
        /// <value>The device model.</value>
        internal string Model
        {
            get
            {
                return this.GetStringProperty("device.FWID_ProductName");
            }
        }

        /// <summary>
        /// Gets the device version.
        /// </summary>
        /// <value>The device version.</value>
        internal string Version
        {
            get
            {
                return this.GetStringProperty("device.FWID_FirmwareVersion");
            }
        }

        /// <summary>
        /// Gets the device serial number.
        /// </summary>
        /// <value>The device serial number.</value>
        internal string SerialNumber
        {
            get
            {
                return this.GetStringProperty("device.FWID_SerialNumber");
            }
        }

        /// <summary>
        /// Gets the device MAC address.
        /// </summary>
        /// <value>The device MAC address.</value>
        internal PhysicalAddress Mac
        {
            get
            {
                if (this.response.GetPropertyStatus("device.FWID_MAC", 0) != PropertyStatus.Ok)
                {
                    return null;
                }

                var address = (byte[])this.response.GetProperty("device.FWID_MAC", 0, typeof(byte[]));
                if (Array.TrueForAll(address, (b) => b == 0))
                {
                    return null;
                }

                return new PhysicalAddress(address);
            }
        }

        /// <summary>
        /// Gets the encryption mode.
        /// </summary>
        /// <value>The encryption mode.</value>
        internal int EncryptionMode
        {
            get
            {
                if (this.response.GetPropertyStatus("device.FWID_EncMode", 0) != PropertyStatus.Ok)
                {
                    return 0;
                }

                var encryption = (int)this.response.GetProperty("device.FWID_EncMode", 0, typeof(int));
                return encryption;
            }
        }

        /// <summary>
        /// Helper method to get the string property.
        /// </summary>
        /// <param name="name">The name of the property to get.</param>
        /// <returns>The property value.</returns>
        private string GetStringProperty(string name)
        {
            if (this.response.GetPropertyStatus(name, 0) != PropertyStatus.Ok)
            {
                return null;
            }

            var value = (string)this.response.GetProperty(name, 0, typeof(string));
            return value.TrimEnd('\0');
        }
    }
}
