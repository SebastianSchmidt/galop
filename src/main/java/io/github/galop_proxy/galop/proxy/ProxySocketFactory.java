package io.github.galop_proxy.galop.proxy;

import java.io.IOException;
import java.net.ServerSocket;

public interface ProxySocketFactory {

    ServerSocket create() throws IOException;

}
