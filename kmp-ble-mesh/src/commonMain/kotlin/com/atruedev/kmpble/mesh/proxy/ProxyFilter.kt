package com.atruedev.kmpble.mesh.proxy

import com.atruedev.kmpble.mesh.MeshAddress

/**
 * Proxy filter configuration for the GATT Proxy bearer.
 *
 * The proxy filter controls which mesh messages the proxy node forwards
 * to the smartphone over the GATT connection. Setting an appropriate filter
 * reduces bandwidth and power consumption.
 *
 * The filter is sent to the proxy node via Proxy Configuration messages.
 */
public sealed interface ProxyFilter {
    /**
     * Only receive messages addressed to the specified addresses.
     * This is the recommended filter for smartphones — only receive
     * messages for our unicast address and subscribed group addresses.
     */
    public data class Whitelist(val addresses: Set<MeshAddress>) : ProxyFilter

    /**
     * Receive all messages EXCEPT those addressed to the specified addresses.
     * Use with caution — broad filters increase GATT bandwidth usage.
     */
    public data class Blacklist(val addresses: Set<MeshAddress>) : ProxyFilter

    /**
     * Receive all mesh messages forwarded by the proxy node.
     * Maximum bandwidth usage. Only use for debugging/monitoring.
     */
    public data object AcceptAll : ProxyFilter

    /** Filter type code for Proxy Configuration messages. */
    public val filterTypeCode: Int get() = when (this) {
        is Whitelist -> 0x00
        is Blacklist -> 0x01
        is AcceptAll -> 0x00 // AcceptAll uses whitelist with no addresses
    }
}
