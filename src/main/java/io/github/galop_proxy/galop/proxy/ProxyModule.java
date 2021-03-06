package io.github.galop_proxy.galop.proxy;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;

public final class ProxyModule extends AbstractModule {

    @Override
    protected void configure() {
        bindFactories();
        bindServer();
    }

    private void bindFactories() {
        bind(ProxySocketFactory.class).to(ProxySocketFactoryImpl.class).in(Singleton.class);
        bind(TargetSocketFactory.class).to(TargetSocketFactoryImpl.class).in(Singleton.class);
        bind(ConnectionHandlerFactory.class).to(ConnectionHandlerFactoryImpl.class).in(Singleton.class);
    }

    private void bindServer() {
        bind(Server.class).to(ServerImpl.class).in(Singleton.class);
    }

}
