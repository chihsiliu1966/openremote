/*
 * Copyright 2016, OpenRemote Inc.
 *
 * See the CONTRIBUTORS.txt file in the distribution for a
 * full listing of individual contributors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.openremote.container;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.openremote.container.json.ElementalJsonModule;
import org.openremote.container.util.LogUtil;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class Container {

    public static final Logger LOG;

    static {
        LogUtil.configureLogging("logging.properties");
        LOG = Logger.getLogger(Container.class.getName());
    }

    public static final String DEV_MODE = "DEV_MODE";
    public static final boolean DEV_MODE_DEFAULT = true;

    public static final ObjectMapper JSON = new ObjectMapper()
        .setSerializationInclusion(JsonInclude.Include.NON_NULL)
        .configure(SerializationFeature.WRITE_NULL_MAP_VALUES, false)
        .configure(SerializationFeature.WRITE_EMPTY_JSON_ARRAYS, false)
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY)
        .setVisibility(PropertyAccessor.GETTER, JsonAutoDetect.Visibility.NONE)
        .setVisibility(PropertyAccessor.SETTER, JsonAutoDetect.Visibility.NONE)
        .setVisibility(PropertyAccessor.IS_GETTER, JsonAutoDetect.Visibility.NONE)
        .setVisibility(PropertyAccessor.CREATOR, JsonAutoDetect.Visibility.NONE)
        .registerModule(new ElementalJsonModule());

    protected final ObjectNode config;
    protected final boolean devMode;

    protected boolean running;
    protected final Map<Class<? extends ContainerService>, ContainerService> services = new LinkedHashMap<>();

    public Container() {
        this(
            System.getenv(),
            StreamSupport.stream(ServiceLoader.load(ContainerService.class).spliterator(), false)
        );
    }

    public Container(Map<String, String> config) {
        this(
            config,
            StreamSupport.stream(ServiceLoader.load(ContainerService.class).spliterator(), false)
        );
    }

    public Container(ContainerService... services) {
        this(
            System.getenv(),
            Stream.concat(
                StreamSupport.stream(ServiceLoader.load(ContainerService.class).spliterator(), false),
                Stream.of(services)
            )
        );
    }

    public Container(Map<String, String> config, Stream<ContainerService> servicesStream) {
        this.config = JSON.createObjectNode();
        for (Map.Entry<String, String> entry : config.entrySet()) {
            this.config.put(entry.getKey(), entry.getValue());
        }

        this.devMode = getConfigBoolean(DEV_MODE, DEV_MODE_DEFAULT);

        if (this.devMode) {
            JSON.enable(SerializationFeature.INDENT_OUTPUT);
        }

        if (servicesStream != null) {
            servicesStream.forEach(this::addService);
        }

        Runtime.getRuntime().addShutdownHook(new Thread(this::stop));
    }

    public void putConfig(String variable, String value) {
        config.put(variable, value);
    }

    public void putConfigIfEmpty(String variable, String value) {
        if (!config.hasNonNull(variable)) {
            putConfig(variable, value);
        }
    }

    public String getConfig(String variable, String defaultValue) {
        return config.has(variable) ? config.get(variable).asText() : defaultValue;
    }

    public boolean getConfigBoolean(String variable, boolean defaultValue) {
        return config.has(variable) ? config.get(variable).asBoolean() : defaultValue;
    }

    public int getConfigInteger(String variable, int defaultValue) {
        return config.has(variable) ? config.get(variable).asInt() : defaultValue;
    }

    public boolean isDevMode() {
        return devMode;
    }

    public CompletableFuture start() {
        return CompletableFuture.runAsync(() -> {
            synchronized (services) {
                if (running)
                    return;
                LOG.info(">>> Starting runtime container...");
                try {
                    for (ContainerService service : getServices()) {
                        LOG.fine("Initializing service: " + service);
                        service.init(Container.this);
                    }
                    for (int counter = getServices().length - 1; counter >= 0; counter--) {
                        ContainerService service = getServices()[counter];
                        LOG.fine("Configuring service: " + service);
                        service.configure(Container.this);
                    }
                    for (ContainerService service : getServices()) {
                        LOG.fine("Starting service: " + service);
                        service.start(Container.this);
                    }
                } catch (RuntimeException ex) {
                    throw ex;
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
                running = true;
                LOG.info(">>> Runtime container startup complete");
            }
        });
    }

    public void stop() {
        synchronized (services) {
            if (!running)
                return;
            LOG.info("<<< Stopping runtime container...");
            List<ContainerService> servicesToStop = Arrays.asList(getServices());
            Collections.reverse(servicesToStop);
            try {
                for (ContainerService service : servicesToStop) {
                    LOG.fine("Stopping service: " + service);
                    service.stop(this);
                }
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            } finally {
                running = false;
            }
            LOG.info("<<< Runtime container stopped");
        }
    }

    /**
     * Starts the container and a non-daemon thread that waits forever.
     */
    public void startBackground() throws Throwable {
        // We block here so we die fast if startup fails
        try {
            start().get();
        } catch (ExecutionException ex) {
            throw ex.getCause();
        }
        Thread containerThread = new Thread("container") {
            @Override
            public void run() {
                try {
                    new CountDownLatch(1).await();
                } catch (InterruptedException ex) {
                    // Shutdown
                }
            }
        };
        containerThread.setDaemon(false);
        containerThread.start();
    }

    public void addService(ContainerService service) {
        synchronized (service) {
            services.put(service.getClass(), service);
        }
    }

    public ContainerService[] getServices() {
        synchronized (services) {
            return services.values().toArray(new ContainerService[services.size()]);
        }
    }

    /**
     * Get a service instance matching the specified type exactly, or if that yields
     * no result, try to get the first service instance that has a matching interface.
     */
    public <T extends ContainerService> T getService(Class<T> type) {
        synchronized (services) {
            //noinspection unchecked
            T service = (T) services.get(type);
            if (service == null) {
                for (ContainerService containerService : services.values()) {
                    if (type.isAssignableFrom(containerService.getClass())) {
                        //noinspection unchecked
                        service = (T) containerService;
                        break;
                    }
                }
            }
            if (service == null)
                throw new IllegalStateException("Missing required service: " + type);
            return service;
        }
    }

}
