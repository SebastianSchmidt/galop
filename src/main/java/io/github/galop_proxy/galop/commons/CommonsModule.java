package io.github.galop_proxy.galop.commons;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class CommonsModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(Runtime.class).toInstance(Runtime.getRuntime());
        bind(InetAddressFactory.class).to(InetAddressFactoryImpl.class).in(Singleton.class);
        bind(ExecutorService.class).toInstance(Executors.newCachedThreadPool());
    }

}
