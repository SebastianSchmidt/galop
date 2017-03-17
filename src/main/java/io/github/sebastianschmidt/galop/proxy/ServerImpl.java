package io.github.sebastianschmidt.galop.proxy;

import io.github.sebastianschmidt.galop.commons.ServerSocketFactory;
import io.github.sebastianschmidt.galop.commons.SocketFactory;
import io.github.sebastianschmidt.galop.configuration.Configuration;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;

import static java.util.Objects.requireNonNull;

final class ServerImpl implements Server {

    private static final Logger LOGGER = LogManager.getLogger(Server.class);

    private final Configuration configuration;
    private final ServerSocketFactory serverSocketFactory;
    private final SocketFactory socketFactory;
    private final ConnectionHandlerFactory connectionHandlerFactory;
    private final ExecutorService executorService;

    private ServerSocket serverSocket;

    ServerImpl(final Configuration configuration, final ServerSocketFactory serverSocketFactory,
               final SocketFactory socketFactory, final ConnectionHandlerFactory connectionHandlerFactory,
               final ExecutorService executorService) {
        this.configuration = requireNonNull(configuration, "configuration must not be null.");
        this.serverSocketFactory = requireNonNull(serverSocketFactory, "serverSocketFactory must not be null.");
        this.socketFactory = requireNonNull(socketFactory, "socketFactory must not be null.");
        this.connectionHandlerFactory = requireNonNull(connectionHandlerFactory,
                "connectionHandlerFactory must not be null.");
        this.executorService = requireNonNull(executorService, "executorService must not be null.");
    }

    @Override
    public void run() {

        try {
            serverSocket = serverSocketFactory.create(configuration.getProxyPort().getValue());
        } catch (final IOException ex) {
            throw new RuntimeException("Could not create server socket.", ex);
        }

        while (!serverSocket.isClosed()) {

            Socket source = null;
            Socket target = null;

            try {

                source = serverSocket.accept();
                target = socketFactory.create(configuration.getTargetAddress(),
                        configuration.getTargetPort().getValue());

                executorService.execute(connectionHandlerFactory.create(configuration, source, target));

            } catch (final Exception ex) {

                if (!"socket closed".equals(ex.getMessage())) {
                    LOGGER.error("An error occurred while processing a new connection.", ex);
                }

                IOUtils.closeQuietly(source);
                IOUtils.closeQuietly(target);

            }

        }

    }

    @Override
    public void close() throws IOException {
        IOUtils.closeQuietly(serverSocket);
    }

}