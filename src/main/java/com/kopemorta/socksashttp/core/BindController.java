package com.kopemorta.socksashttp.core;

import com.kopemorta.socksashttp.entities.BindConfig;
import com.kopemorta.socksashttp.exceptions.CannotBindException;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import lombok.Value;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.BindException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class BindController {

    private static final Logger LOGGER = LogManager.getLogger();

    private final List<SocketAddressHolder> addressesToBind;

    public BindController(final Collection<BindConfig> bindConfigs) {
        final List<SocketAddressHolder> socketAddressHolders = new ArrayList<>();
        for (BindConfig bindConfig : bindConfigs) {
            final InetAddress bindAdr = bindConfig.getBindAdr();
            final int startPort = bindConfig.getPortRange().getStartPort();
            final int endPort = bindConfig.getPortRange().getEndPort();

            for (int port = startPort; port <= endPort; port++) {
                final SocketAddress socketAddress = new InetSocketAddress(bindAdr, port);
                socketAddressHolders.add(new SocketAddressHolder(socketAddress));
            }
        }

        this.addressesToBind = Collections.unmodifiableList(socketAddressHolders);
    }


    /**
     * Try bind bootstrap to any free address which were distributed when the object was created
     * Only if in list not have any free port - throw exception
     *
     * @param serverBootstrap bootstrap to bind
     * @return binded channel
     * @throws CannotBindException throw exception only if in addressesToBind list
     * not have any free address
     */
    public Channel bindBootstrap(final ServerBootstrap serverBootstrap) throws CannotBindException {
        while (true) {
            final Optional<SocketAddressHolder> optHolder = getAdr();
            if (!optHolder.isPresent())
                throw new CannotBindException("Not have free address to bind.");

            final SocketAddressHolder holder = optHolder.get();
            try {
                final ChannelFuture channelFuture = serverBootstrap.bind(holder.getBindAdr()).syncUninterruptibly();
                if (channelFuture.isSuccess()) {
                    holder.lock();
                    LOGGER.debug("Bootstrap successful bind to {}", holder.getBindAdr());
                    return channelFuture.channel();
                } else {
                    holder.tempLock();
                    LOGGER.debug("Bootstrap failed bind to {}", holder.getBindAdr());
                }
            } catch (Exception e) {
                /* idk wtf is this but it works.
                * i think bind exception should be in failed channel future but no.
                * seems need catch BindException in catch block, but it don't work,
                * BECAUSE BindException is checked exception and bind() not throw it.
                */
                if(e instanceof BindException) {
                    holder.tempLock();
                    LOGGER.debug("Bootstrap failed bind to {}", holder.getBindAdr());
                } else
                    throw e;
            }
        }
    }

    /**
     * Release binded address to reuse it.
     * If this address not contains in list - method do nothing
     * @param socketAddress already binded address
     */
    public void releaseBindAdr(final SocketAddress socketAddress) {
        for (SocketAddressHolder socketAddressHolder : addressesToBind) {
            if(socketAddressHolder.getBindAdr().equals(socketAddress))
                socketAddressHolder.unlock();
        }
    }


    private Optional<SocketAddressHolder> getAdr() {
        return addressesToBind.stream().filter(SocketAddressHolder::isFree).findAny();
    }


    @Value
    private static class SocketAddressHolder {
        private static final long TEMP_LOCK_MILLIS = TimeUnit.SECONDS.toMillis(10);

        SocketAddress bindAdr;

        /**
         * Address free for bind indicator.
         * May change in two cases:
         * if this app is binded to this address
         * if not this app is binded to this address
         * in this case the timestamp of when this address was blocked will be set, for an attempt in the future.
         * The time for which the address is blocked is set in constant TEMP_LOCK_MILLIS
         */
        AtomicBoolean free = new AtomicBoolean(true);
        // to avoid excessive computing
        AtomicBoolean tempLock = new AtomicBoolean(false);
        // last bind attempt time
        AtomicLong lastTempLock = new AtomicLong(0);


        private boolean isFree() {
            final boolean isFreeComputingResult;
            final boolean isFree = free.get();
            if (isFree) {
                isFreeComputingResult = true;
            } else {
                if (tempLock.get()) {
                    final long lastTempLockTime = lastTempLock.get();
                    if ((System.currentTimeMillis() - lastTempLockTime) > TEMP_LOCK_MILLIS) {
                        tempLock.set(false);
                        isFreeComputingResult = true;
                    } else {
                        isFreeComputingResult = false;
                    }
                } else {
                    isFreeComputingResult = false;
                }
            }

            return isFreeComputingResult;
        }

        private void lock() {
            free.set(false);
        }

        private void tempLock() {
            free.set(false);
            tempLock.set(true);
            lastTempLock.set(System.currentTimeMillis());
        }

        private void unlock() {
            free.set(true);
        }


        @Override
        public int hashCode() {
            return bindAdr.hashCode();
        }

        @Override
        public boolean equals(Object o) {
            if(o == null)
                return false;
            if(o == this)
                return true;
            if(o instanceof SocketAddressHolder)
                return this.bindAdr.equals(((SocketAddressHolder) o).bindAdr);

            return false;
        }

        @Override
        public String toString() {
            return bindAdr.toString();
        }
    }
}
