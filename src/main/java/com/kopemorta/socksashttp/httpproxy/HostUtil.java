package com.kopemorta.socksashttp.httpproxy;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Optional;

public class HostUtil {

    public static Optional<SocketAddress> parseUri(String uri, final boolean ssl) {
        uri = uri.trim();

        String host;
        if(uri.startsWith("https://"))
            host = uri.replace("https://", "");
        else if(uri.startsWith("http://"))
            host = uri.replace("http://", "");
        else
            host = uri;

        if(host.isEmpty())
            return Optional.empty();

        final int pathSplitterIndex = host.indexOf("/");
        if(pathSplitterIndex > 0)
            host = host.substring(0, pathSplitterIndex);

        return Optional.of(parseHost(host, ssl));
    }


    public static SocketAddress parseHost(final String host, final boolean ssl) {
        final String resultHost;
        final int resultPort;

        final int splitPortCharIndex = host.indexOf(':');
        if (splitPortCharIndex == -1) {
            resultHost = host;
            resultPort = getStdPort(ssl);
        } else {
            final String portStr = host.substring(splitPortCharIndex + 1);

            resultHost = host.substring(0, splitPortCharIndex);
            resultPort = parsePort(portStr) // try parse port, if can't - using std
                    .orElse(getStdPort(ssl));
        }


        return InetSocketAddress.createUnresolved(resultHost, resultPort);
    }

    private static Optional<Integer> parsePort(final String str) {
        Integer result = null;
        try {
            final int port = Integer.parseInt(str);
            if (port > 0 && port < 65535)
                result = port;
        } catch (NumberFormatException ignored) {
        }


        return Optional.ofNullable(result);
    }

    private static int getStdPort(final boolean ssl) {
        return ssl ? 443 : 80;
    }
}
