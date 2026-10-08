package com.gokulsweets.restaurant.customer.identity;

import com.gokulsweets.restaurant.observability.MethodTiming;

import jakarta.servlet.http.HttpServletRequest;

import lombok.RequiredArgsConstructor;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;

/** Reads forwarding headers only from explicitly configured proxy addresses. */
@Service
@RequiredArgsConstructor
public class IdentityClientConnection {

    private final Environment settings;

    /**
     * Resolves the operation.
     *
     * @param request the request
     * @return the resolve result
     */
    public Connection resolve(HttpServletRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(IdentityClientConnection.class, "resolve(HttpServletRequest)");
        try {
            var remote = numericAddress(request.getRemoteAddr());
            var trusted =
                    trustedRanges(settings.getProperty("gokul.identity.trusted-proxy-cidrs", ""));
            if (trusted.stream().noneMatch(range -> range.includes(remote))) {
                return new Connection(remote.getHostAddress(), request.isSecure());
            }
            String forwarded = request.getHeader("X-Forwarded-For");
            String proto = request.getHeader("X-Forwarded-Proto");
            if (forwarded == null
                    || proto == null
                    || forwarded.length() > 512
                    || proto.length() > 64) {
                throw new IllegalStateException("Trusted proxy forwarding is unavailable");
            }
            // Walk backwards from our trusted proxy. Any user-supplied prefix is ignored.
            var hops = forwarded.split(",", -1);
            if (hops.length > 8) throw new IllegalStateException("Too many forwarded hops");
            InetAddress client = null;
            for (int index = hops.length - 1; index >= 0; index--) {
                var address = numericAddress(hops[index].trim());
                if (trusted.stream().noneMatch(range -> range.includes(address))) {
                    client = address;
                    break;
                }
            }
            if (client == null) throw new IllegalStateException("Client address unavailable");
            var protocols = proto.split(",", -1);
            // The rightmost value must have been written by the nearest trusted proxy.
            boolean secure = "https".equalsIgnoreCase(protocols[protocols.length - 1].trim());
            return new Connection(client.getHostAddress(), secure);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    IdentityClientConnection.class,
                    "resolve(HttpServletRequest)");
        }
    }

    /**
     * Trusteds ranges.
     *
     * @param raw the raw
     * @return the trusted ranges result
     */
    private static List<Range> trustedRanges(String raw) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(IdentityClientConnection.class, "trustedRanges(String)");
        try {
            if (raw == null || raw.isBlank()) return List.of();
            return Arrays.stream(raw.split(",", -1)).map(String::trim).map(Range::parse).toList();
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    IdentityClientConnection.class,
                    "trustedRanges(String)");
        }
    }

    /**
     * Numerics address.
     *
     * @param raw the raw
     * @return the numeric address result
     */
    private static InetAddress numericAddress(String raw) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(IdentityClientConnection.class, "numericAddress(String)");
        try {
            try {
                if (raw == null || raw.isBlank() || raw.length() > 45)
                    throw new IllegalArgumentException();
                if (raw.matches("[0-9.]+")) {
                    var octets = raw.split("\\.", -1);
                    if (octets.length != 4) throw new IllegalArgumentException();
                    byte[] bytes = new byte[4];
                    for (int i = 0; i < 4; i++) {
                        if (octets[i].isEmpty() || octets[i].length() > 3)
                            throw new IllegalArgumentException();
                        int value = Integer.parseInt(octets[i]);
                        if (value < 0 || value > 255) throw new IllegalArgumentException();
                        bytes[i] = (byte) value;
                    }
                    return InetAddress.getByAddress(bytes);
                }
                if (!raw.contains(":") || !raw.matches("[0-9a-fA-F:.]+")) {
                    throw new IllegalArgumentException();
                }
                // numeric IPv6 only; hostnames never reach DNS
                return InetAddress.getByName(raw);
            } catch (UnknownHostException | NumberFormatException exception) {
                throw new IllegalStateException("Invalid client address", exception);
            } catch (IllegalArgumentException exception) {
                throw new IllegalStateException("Invalid client address", exception);
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    IdentityClientConnection.class,
                    "numericAddress(String)");
        }
    }

    /**
     * Immutable connection data contract.
     *
     * @param sourceAddress the source address
     * @param secure the secure
     */
    public record Connection(String sourceAddress, boolean secure) {}

    /**
     * Immutable range data contract.
     *
     * @param network the network
     * @param bits the bits
     */
    private record Range(byte[] network, int bits) {

        /**
         * Parses the operation.
         *
         * @param raw the raw
         * @return the parse result
         */
        static Range parse(String raw) {
            final long __gokulMethodStartedNanos =
                    MethodTiming.start(IdentityClientConnection.Range.class, "parse(String)");
            try {
                var parts = raw.split("/", -1);
                if (parts.length > 2)
                    throw new IllegalStateException("Invalid trusted proxy range");
                var address = numericAddress(parts[0]);
                int maximum = address.getAddress().length * 8;
                int bits;
                try {
                    bits = parts.length == 1 ? maximum : Integer.parseInt(parts[1]);
                } catch (NumberFormatException exception) {
                    throw new IllegalStateException("Invalid trusted proxy range", exception);
                }
                if (bits <= 0 || bits > maximum)
                    throw new IllegalStateException("Invalid trusted proxy range");
                return new Range(address.getAddress(), bits);
            } finally {
                MethodTiming.finish(
                        __gokulMethodStartedNanos,
                        IdentityClientConnection.Range.class,
                        "parse(String)");
            }
        }

        /**
         * Includeses the operation.
         *
         * @param address the address
         * @return the includes result
         */
        boolean includes(InetAddress address) {
            final long __gokulMethodStartedNanos =
                    MethodTiming.start(
                            IdentityClientConnection.Range.class, "includes(InetAddress)");
            try {
                byte[] candidate = address.getAddress();
                if (candidate.length != network.length) return false;
                for (int bit = 0; bit < bits; bit++) {
                    int mask = 1 << (7 - bit % 8);
                    if ((candidate[bit / 8] & mask) != (network[bit / 8] & mask)) return false;
                }
                return true;
            } finally {
                MethodTiming.finish(
                        __gokulMethodStartedNanos,
                        IdentityClientConnection.Range.class,
                        "includes(InetAddress)");
            }
        }
    }
}
